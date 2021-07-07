/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.bootstrap.generator.bean;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.Order;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Default {@link BeanValueWriterSupplier} implementation, providing an instance based on
 * the actual class and resolved factory method.
 *
 * @author Stephane Nicoll
 */
@Order(Ordered.LOWEST_PRECEDENCE - 5)
class DefaultBeanValueWriterSupplier implements BeanValueWriterSupplier, BeanFactoryAware {

	private static final Log logger = LogFactory.getLog(DefaultBeanValueWriterSupplier.class);

	private BeanFactory beanFactory;

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public BeanValueWriter get(BeanDefinition beanDefinition, ClassLoader classLoader) {
		// Remove CGLIB classes
		Executable factoryExecutable = resolveBeanFactory(beanDefinition, classLoader);
		if (factoryExecutable instanceof Method) {
			return new MethodBeanValueWriter(beanDefinition, classLoader, (Method) factoryExecutable);
		}
		else if (factoryExecutable instanceof Constructor) {
			return new ConstructorBeanValueWriter(beanDefinition, classLoader, (Constructor<?>) factoryExecutable);
		}
		return null;
	}

	private Executable resolveBeanFactory(BeanDefinition beanDefinition, ClassLoader classLoader) {
		if (beanDefinition instanceof RootBeanDefinition) {
			RootBeanDefinition rootBeanDefinition = (RootBeanDefinition) beanDefinition;
			Supplier<Class<?>> beanClass = () -> getBeanClass(rootBeanDefinition, classLoader);
			List<Class<?>> parameterTypes = determineParameterTypes(beanDefinition, classLoader);
			Method resolvedFactoryMethod = resolveFactoryMethod(rootBeanDefinition, beanClass, parameterTypes);
			if (resolvedFactoryMethod != null) {
				return resolvedFactoryMethod;
			}
			Executable resolvedConstructor = resolveConstructor(rootBeanDefinition, beanClass);
			if (resolvedConstructor != null) {
				return resolvedConstructor;
			}
			Executable resolvedConstructorOrFactoryMethod = getField(beanDefinition,
					"resolvedConstructorOrFactoryMethod", Executable.class);
			if (resolvedConstructorOrFactoryMethod != null) {
				logger.error("resolvedConstructorOrFactoryMethod required for " + beanDefinition);
				return resolvedConstructorOrFactoryMethod;
			}
		}
		return null;
	}

	private List<Class<?>> determineParameterTypes(BeanDefinition beanDefinition, ClassLoader classLoader) {
		List<Class<?>> parameterTypes = new ArrayList<>();
		ConstructorArgumentValues constructorArgumentValues = beanDefinition.getConstructorArgumentValues();
		if (constructorArgumentValues.isEmpty()) {
			return parameterTypes;
		}
		for (ValueHolder valueHolder : constructorArgumentValues.getIndexedArgumentValues().values()) {
			if (valueHolder.getType() != null) {
				parameterTypes.add(loadClass(valueHolder.getType(), classLoader));
			}
			else {
				Object value = valueHolder.getValue();
				if (value instanceof BeanReference) {
					parameterTypes.add(this.beanFactory.getType(((BeanReference) value).getBeanName(), false));
				}
				else {
					parameterTypes.add(value.getClass());
				}
			}
		}
		return parameterTypes;
	}

	private Method resolveFactoryMethod(RootBeanDefinition beanDefinition, Supplier<Class<?>> beanClass,
			List<Class<?>> parameterTypes) {
		Method resolvedFactoryMethod = beanDefinition.getResolvedFactoryMethod();
		if (resolvedFactoryMethod != null) {
			return resolvedFactoryMethod;
		}
		String factoryMethodName = beanDefinition.getFactoryMethodName();
		if (factoryMethodName != null) {
			List<Method> methods = new ArrayList<>();
			ReflectionUtils.doWithMethods(beanClass.get(), methods::add,
					(method) -> method.getName().equals(factoryMethodName));
			if (methods.size() >= 1) {
				return (Method) filter(methods, parameterTypes);
			}
		}
		return null;
	}

	private Executable resolveConstructor(RootBeanDefinition beanDefinition, Supplier<Class<?>> beanClass) {
		Class<?> type = beanClass.get();
		Constructor<?>[] constructors = type.getDeclaredConstructors();
		if (constructors.length == 1) {
			return constructors[0];
		}
		for (Constructor<?> constructor : constructors) {
			if (MergedAnnotations.from(constructor).isPresent(Autowired.class)) {
				return constructor;
			}
		}
		return null;
	}

	private Executable filter(List<? extends Executable> executables, List<Class<?>> parameterTypes) {
		List<? extends Executable> matches = executables.stream()
				.filter((executable) -> match(executable, parameterTypes)).collect(Collectors.toList());
		if (matches.size() > 1) {
			throw new IllegalStateException("Multiple matches with parameters " + parameterTypes);
		}
		return (matches.size() == 1) ? matches.get(0) : null;
	}

	private boolean match(Executable executable, List<Class<?>> parameterTypes) {
		if (executable.getParameterCount() != parameterTypes.size()) {
			return false;
		}
		Class<?>[] types = executable.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			if (!types[i].isAssignableFrom(parameterTypes.get(i))) {
				return false;
			}
		}
		return true;
	}

	private Class<?> getBeanClass(RootBeanDefinition beanDefinition, ClassLoader classLoader) {
		if (beanDefinition.hasBeanClass()) {
			return beanDefinition.getBeanClass();
		}
		String beanClassName = beanDefinition.getBeanClassName();
		if (beanClassName != null) {
			return loadClass(beanClassName, classLoader);
		}
		throw new IllegalStateException("Failed to determine bean class of " + beanDefinition);
	}

	private Class<?> loadClass(String beanClassName, ClassLoader classLoader) {
		try {
			return ClassUtils.forName(beanClassName, classLoader);
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException("Failed to load class " + beanClassName);
		}
	}

	private <T> T getField(BeanDefinition beanDefinition, String fieldName, Class<T> targetType) {
		Field field = ReflectionUtils.findField(RootBeanDefinition.class, fieldName);
		ReflectionUtils.makeAccessible(field);
		return targetType.cast(ReflectionUtils.getField(field, beanDefinition));
	}

}
