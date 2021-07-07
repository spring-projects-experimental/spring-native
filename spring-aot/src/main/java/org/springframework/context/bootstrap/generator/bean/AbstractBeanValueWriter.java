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

import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.squareup.javapoet.CodeBlock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ClassUtils;

/**
 * Base {@link BeanValueWriter} implementation.
 *
 * @author Stephane Nicoll
 */
public abstract class AbstractBeanValueWriter implements BeanValueWriter {

	private final BeanDefinition beanDefinition;

	private final ClassLoader classLoader;

	private final Class<?> type;

	public AbstractBeanValueWriter(BeanDefinition beanDefinition, ClassLoader classLoader) {
		this.classLoader = classLoader;
		this.beanDefinition = beanDefinition;
		this.type = ClassUtils.getUserClass(beanDefinition.getResolvableType().toClass());
	}

	protected final BeanDefinition getBeanDefinition() {
		return this.beanDefinition;
	}

	@Override
	public final Class<?> getType() {
		return this.type;
	}

	@Override
	public boolean isAccessibleFrom(String packageName) {
		return isAccessible(this.beanDefinition.getResolvableType())
				&& isAccessible(ResolvableType.forClass(getDeclaringType()));
	}

	protected boolean isAccessible(ResolvableType target) {
		// resolve to the actual class as the proxy won't have the same characteristics
		ResolvableType nonProxyTarget = target.as(ClassUtils.getUserClass(target.toClass()));
		if (!Modifier.isPublic(nonProxyTarget.toClass().getModifiers())) {
			return false;
		}
		Class<?> declaringClass = nonProxyTarget.toClass().getDeclaringClass();
		if (declaringClass != null) {
			if (!isAccessible(ResolvableType.forClass(declaringClass))) {
				return false;
			}
		}
		if (nonProxyTarget.hasGenerics()) {
			for (ResolvableType generic : nonProxyTarget.getGenerics()) {
				if (!isAccessible(generic)) {
					return false;
				}
			}
		}
		return true;
	}

	protected boolean hasCheckedException(Class<?>... exceptionTypes) {
		return Arrays.stream(exceptionTypes).anyMatch((ex) -> !RuntimeException.class.isAssignableFrom(ex));
	}

	protected void handleParameters(CodeBlock.Builder code, Parameter[] parameters,
			Function<Integer, ResolvableType> parameterTypeFactory) {
		for (int i = 0; i < parameters.length; i++) {
			ResolvableType parameterType = parameterTypeFactory.apply(i);
			ValueHolder userValue = this.beanDefinition.getConstructorArgumentValues().getIndexedArgumentValue(i,
					parameterType.toClass());
			if (userValue != null) {
				Object value = userValue.getValue();
				if (value instanceof BeanReference) {
					writeParameterBeanDependency(code, ((BeanReference) value).getBeanName(), parameterType);
				}
				writeParameterValue(code, value, parameterType);
			}
			else {
				writeParameterDependency(code, parameters[i], parameterType);
			}
			if (i < parameters.length - 1) {
				code.add(", ");
			}
		}
	}

	// workaround to account for the Spring Boot use case for now.
	protected void writeParameterValue(CodeBlock.Builder code, Object value, ResolvableType parameterType) {
		Object targetValue = convertValueIfNecessary(value, parameterType);
		if (parameterType.isArray()) {
			code.add("new $T { ", parameterType.toClass());
			if (targetValue instanceof char[]) {
				char[] array = (char[]) targetValue;
				for (int i = 0; i < array.length; i++) {
					writeParameterValue(code, array[i], ResolvableType.forClass(char.class));
					if (i < array.length - 1) {
						code.add(", ");
					}
				}
			}
			else if (targetValue instanceof String[]) {
				String[] array = (String[]) targetValue;
				for (int i = 0; i < array.length; i++) {
					writeParameterValue(code, array[i], ResolvableType.forClass(String.class));
					if (i < array.length - 1) {
						code.add(", ");
					}
				}
			}
			code.add(" }");
		}
		else if (targetValue instanceof Character) {
			String result = '\'' + characterLiteralWithoutSingleQuotes((Character) targetValue) + '\'';
			code.add(result);
		}
		else if (isPrimitiveOrWrapper(targetValue)) {
			code.add("$L", targetValue);
		}
		else if (targetValue instanceof String) {
			code.add("$S", targetValue);
		}
		else if (targetValue instanceof Class) {
			code.add("$T.class", targetValue);
		}
	}

	private Object convertValueIfNecessary(Object value, ResolvableType resolvableType) {
		if (value instanceof String && Class.class.isAssignableFrom(resolvableType.resolve())) {
			try {
				return ClassUtils.forName(((String) value), this.classLoader);
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalStateException("Failed to load " + value, ex);
			}
		}
		return value;
	}

	private boolean isPrimitiveOrWrapper(Object value) {
		Class<?> valueType = value.getClass();
		return (valueType.isPrimitive() || valueType == Double.class || valueType == Float.class
				|| valueType == Long.class || valueType == Integer.class || valueType == Short.class
				|| valueType == Character.class || valueType == Byte.class || valueType == Boolean.class);
	}

	protected void writeParameterDependency(CodeBlock.Builder code, Parameter parameter, ResolvableType parameterType) {
		Class<?> resolvedClass = parameterType.toClass();
		if (ObjectProvider.class.isAssignableFrom(resolvedClass)) {
			code.add("context.getBeanProvider(");
			TypeHelper.generateResolvableTypeFor(code, parameterType.as(ObjectProvider.class).getGeneric(0));
			code.add(")");
		}
		else if (Collection.class.isAssignableFrom(resolvedClass)) {
			code.add("context.getBeanProvider(");
			TypeHelper.generateResolvableTypeFor(code, parameterType.as(Collection.class).getGeneric(0));
			String collectors = (Set.class.isAssignableFrom(resolvedClass)) ? "toSet()" : "toList()";
			code.add(").orderedStream().collect($T.$L)", Collectors.class, collectors);
		}
		else if (resolvedClass.isAssignableFrom(GenericApplicationContext.class)) {
			code.add("context");
		}
		else if (resolvedClass.isAssignableFrom(ConfigurableListableBeanFactory.class)) {
			code.add("context.getBeanFactory()");
		}
		else if (resolvedClass.isAssignableFrom(ConfigurableEnvironment.class)) {
			code.add("context.getEnvironment()");
		}
		else {
			writeParameterBeanDependency(code, null, parameterType);
		}
	}

	private void writeParameterBeanDependency(CodeBlock.Builder code, String beanName, ResolvableType parameterType) {
		Class<?> resolvedClass = parameterType.toClass();
		if (beanName != null) {
			code.add("context.getBean($S, $T.class)", beanName, resolvedClass);
		}
		else {
			code.add("context.getBean($T.class)", resolvedClass);
		}
	}

	// Copied from com.squareup.javapoet.Util
	private static String characterLiteralWithoutSingleQuotes(char c) {
		// see https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6
		switch (c) {
		case '\b':
			return "\\b"; /* \u0008: backspace (BS) */
		case '\t':
			return "\\t"; /* \u0009: horizontal tab (HT) */
		case '\n':
			return "\\n"; /* \u000a: linefeed (LF) */
		case '\f':
			return "\\f"; /* \u000c: form feed (FF) */
		case '\r':
			return "\\r"; /* \u000d: carriage return (CR) */
		case '\"':
			return "\""; /* \u0022: double quote (") */
		case '\'':
			return "\\'"; /* \u0027: single quote (') */
		case '\\':
			return "\\\\"; /* \u005c: backslash (\) */
		default:
			return Character.isISOControl(c) ? String.format("\\u%04x", (int) c) : Character.toString(c);
		}
	}

}
