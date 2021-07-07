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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.squareup.javapoet.CodeBlock;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.bootstrap.generator.sample.dependency.DependencyConfiguration;
import org.springframework.context.bootstrap.generator.sample.factory.SampleFactory;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MethodBeanValueWriter}.
 *
 * @author Stephane Nicoll
 */
class MethodBeanValueWriterTests {

	@Test
	void writeParameterWithRuntimeBeanReference() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(SampleFactory.class.getName())
				.setFactoryMethod("create").addConstructorArgReference("testBean").getBeanDefinition();
		Method method = ReflectionUtils.findMethod(SampleFactory.class, "create", String.class);
		assertGeneratedCode(beanDefinition, method, (code) -> assertThat(code)
				.endsWith("SampleFactory.create(context.getBean(\"testBean\", java.lang.String.class))"));
	}

	@Test
	void writeParameterWithCharacterReferenceEscapeSpecialChar() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(SampleFactory.class.getName())
				.setFactoryMethod("create").addConstructorArgValue('\\').getBeanDefinition();
		Method method = ReflectionUtils.findMethod(SampleFactory.class, "create", char.class);
		assertGeneratedCode(beanDefinition, method,
				(code) -> assertThat(code).endsWith("SampleFactory.create('\\\\')"));
	}

	@Test
	void writeParameterWithList() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(DependencyConfiguration.class.getName()).setFactoryMethod("injectList")
				.getBeanDefinition();
		Method method = ReflectionUtils.findMethod(DependencyConfiguration.class, "injectList", List.class);
		assertGeneratedCode(beanDefinition, method, (code) -> assertThat(code).endsWith(
				".injectList(context.getBeanProvider(java.lang.String.class).orderedStream().collect(java.util.stream.Collectors.toList()))"));
	}

	@Test
	void writeParameterWithSet() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(DependencyConfiguration.class.getName()).setFactoryMethod("injectSet")
				.getBeanDefinition();
		Method method = ReflectionUtils.findMethod(DependencyConfiguration.class, "injectSet", Set.class);
		assertGeneratedCode(beanDefinition, method, (code) -> assertThat(code).endsWith(
				".injectSet(context.getBeanProvider(java.lang.String.class).orderedStream().collect(java.util.stream.Collectors.toSet()))"));
	}

	@Test
	void writeParameterWithClassAsString() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(SampleFactory.class.getName())
				.setFactoryMethod("create").addConstructorArgValue("java.lang.String").getBeanDefinition();
		Method method = ReflectionUtils.findMethod(SampleFactory.class, "create", Class.class);
		assertGeneratedCode(beanDefinition, method,
				(code) -> assertThat(code).endsWith("SampleFactory.create(java.lang.String.class)"));
	}

	private void assertGeneratedCode(BeanDefinition beanDefinition, Method method, Consumer<String> code) {
		CodeBlock.Builder builder = CodeBlock.builder();
		new MethodBeanValueWriter(beanDefinition, getClass().getClassLoader(), method).writeValueSupplier(builder);
		code.accept(builder.build().toString());
	}

}
