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

package org.springframework.context.bootstrap.generator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.bootstrap.generator.bean.BeanRegistrationGenerator;
import org.springframework.context.bootstrap.generator.bean.BeanValueWriter;
import org.springframework.context.bootstrap.generator.bean.BeanValueWriterSupplier;
import org.springframework.context.bootstrap.generator.bean.GenericBeanRegistrationGenerator;
import org.springframework.context.bootstrap.generator.bean.SimpleBeanRegistrationGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * A simple experiment to generate a bootstrap class that represents the state of a fully
 * initialized {@link BeanFactory}.
 *
 * @author Stephane Nicoll
 */
public class ContextBootstrapGenerator {

	private static final Log logger = LogFactory.getLog(ContextBootstrapGenerator.class);

	private final List<BeanValueWriterSupplier> beanValueWriterSuppliers;

	private final Map<String, ProtectedBootstrapClass> protectedBootstrapClasses = new HashMap<>();

	public ContextBootstrapGenerator(ClassLoader classLoader) {
		this(SpringFactoriesLoader.loadFactories(BeanValueWriterSupplier.class, classLoader));
	}

	ContextBootstrapGenerator(List<BeanValueWriterSupplier> beanValueWriterSuppliers) {
		this.beanValueWriterSuppliers = beanValueWriterSuppliers;
	}

	/**
	 * Generate the code that is required to restore the state of the specified
	 * {@link BeanFactory}.
	 * @param beanFactory the bean factory state to replicate in code
	 * @param packageName the root package for the main {@code ContextBoostrap} class
	 * @param excludeTypes the types to exclude
	 * @return a list of {@linkplain JavaFile java source files}
	 */
	public List<JavaFile> generateBootstrapClass(ConfigurableListableBeanFactory beanFactory, String packageName,
			Class<?>... excludeTypes) {
		this.beanValueWriterSuppliers.stream().filter(BeanFactoryAware.class::isInstance)
				.map(BeanFactoryAware.class::cast).forEach((callback) -> callback.setBeanFactory(beanFactory));
		DefaultBeanDefinitionSelector selector = new DefaultBeanDefinitionSelector(
				Arrays.stream(excludeTypes).map(Class::getName).collect(Collectors.toList()));
		List<JavaFile> bootstrapClasses = new ArrayList<>();
		bootstrapClasses.add(createClass(packageName, "ContextBootstrap",
				generateBootstrapMethod(beanFactory, packageName, selector)));
		for (ProtectedBootstrapClass protectedBootstrapClass : this.protectedBootstrapClasses.values()) {
			bootstrapClasses.add(protectedBootstrapClass.build());
		}
		return bootstrapClasses;
	}

	public JavaFile createClass(String packageName, String bootstrapClassName, MethodSpec bootstrapMethod) {
		ParameterizedTypeName typeName = ParameterizedTypeName.get(
				ClassName.get(ApplicationContextInitializer.class),
				ClassName.get(GenericApplicationContext.class));
		return JavaFile.builder(packageName, TypeSpec.classBuilder(bootstrapClassName).addSuperinterface(typeName).addModifiers(Modifier.PUBLIC)
				.addMethod(bootstrapMethod).build()).build();
	}

	public MethodSpec generateBootstrapMethod(ConfigurableListableBeanFactory beanFactory, String packageName,
			BeanDefinitionSelector selector) {
		ClassLoader classLoader = beanFactory.getBeanClassLoader();
		MethodSpec.Builder method = MethodSpec.methodBuilder("initialize").addModifiers(Modifier.PUBLIC)
				.addParameter(GenericApplicationContext.class, "context");
		CodeBlock.Builder code = CodeBlock.builder();
		String[] beanNames = beanFactory.getBeanDefinitionNames();
		for (String beanName : beanNames) {
			BeanDefinition beanDefinition = beanFactory.getMergedBeanDefinition(beanName);
			if (selector.select(beanName, beanDefinition)) {
				BeanRegistrationGenerator beanRegistrationGenerator = getBeanRegistrationGenerator(beanName,
						beanDefinition, classLoader);
				if (beanRegistrationGenerator != null) {
					BeanValueWriter beanValueWriter = beanRegistrationGenerator.getBeanValueWriter();
					if (beanValueWriter.isAccessibleFrom(packageName)) {
						beanRegistrationGenerator.writeBeanRegistration(code);
					}
					else {
						String protectedPackageName = beanValueWriter.getDeclaringType().getPackage().getName();
						ProtectedBootstrapClass protectedBootstrapClass = this.protectedBootstrapClasses
								.computeIfAbsent(protectedPackageName, ProtectedBootstrapClass::new);
						protectedBootstrapClass.addBeanRegistrationMethod(beanName, beanValueWriter.getType(),
								beanRegistrationGenerator);
						ClassName protectedClassName = ClassName.get(protectedPackageName, "ContextBootstrap");
						code.addStatement("$T.$L(context)", protectedClassName,
								ProtectedBootstrapClass.registerBeanMethodName(beanName, beanValueWriter.getType()));
					}
				}
			}
		}
		method.addCode(code.build());
		return method.build();
	}

	private BeanRegistrationGenerator getBeanRegistrationGenerator(String beanName, BeanDefinition beanDefinition,
			ClassLoader classLoader) {
		ResolvableType beanType = beanDefinition.getResolvableType();
		BeanValueWriter beanValueWriter = getBeanValueSupplier(beanDefinition, classLoader);
		if (beanValueWriter != null) {
			if (beanType.hasGenerics()) {
				return new GenericBeanRegistrationGenerator(beanName, beanDefinition, beanValueWriter);
			}
			else {
				return new SimpleBeanRegistrationGenerator(beanName, beanDefinition, beanValueWriter);
			}
		}
		return null;
	}

	private BeanValueWriter getBeanValueSupplier(BeanDefinition beanDefinition, ClassLoader classLoader) {
		for (BeanValueWriterSupplier supplier : this.beanValueWriterSuppliers) {
			BeanValueWriter writer = supplier.get(beanDefinition, classLoader);
			if (writer != null) {
				return writer;
			}
		}
		logger.error("Failed to handle bean with definition " + beanDefinition);
		return null;
	}

}
