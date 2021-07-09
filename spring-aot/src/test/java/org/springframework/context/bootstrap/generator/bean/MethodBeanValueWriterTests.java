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
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.bootstrap.generator.sample.dependency.DependencyConfiguration;
import org.springframework.context.bootstrap.generator.sample.dependency.GenericDependencyConfiguration;
import org.springframework.context.bootstrap.generator.sample.factory.SampleFactory;
import org.springframework.context.bootstrap.generator.test.CodeSnippet;
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
		assertThat(generateCode(beanDefinition, method)).isEqualTo(
				"() -> SampleFactory.create(context.getBean(\"testBean\", String.class))");
	}

	@Test
	void writeParameterWithCharacterReferenceEscapeSpecialChar() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(SampleFactory.class.getName())
				.setFactoryMethod("create").addConstructorArgValue('\\').getBeanDefinition();
		Method method = ReflectionUtils.findMethod(SampleFactory.class, "create", char.class);
		assertThat(generateCode(beanDefinition, method)).isEqualTo(
				"() -> SampleFactory.create('\\\\')");
	}

	@Test
	void writeParameterWithList() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(DependencyConfiguration.class.getName()).setFactoryMethod("injectList")
				.getBeanDefinition();
		Method method = ReflectionUtils.findMethod(DependencyConfiguration.class, "injectList", List.class);
		assertThat(generateCode(beanDefinition, method)).containsSequence(
				"() -> context.getBean(DependencyConfiguration.class)",
				".injectList(context.getBeanProvider(String.class).orderedStream().collect(Collectors.toList()))");
	}

	@Test
	void writeParameterWithSet() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(DependencyConfiguration.class.getName()).setFactoryMethod("injectSet")
				.getBeanDefinition();
		Method method = ReflectionUtils.findMethod(DependencyConfiguration.class, "injectSet", Set.class);
		assertThat(generateCode(beanDefinition, method)).containsSequence(
				"() -> context.getBean(DependencyConfiguration.class)",
				".injectSet(context.getBeanProvider(String.class).orderedStream().collect(Collectors.toSet()))");
	}

	@Test
	void writeParameterWithClassAsString() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(SampleFactory.class.getName())
				.setFactoryMethod("create").addConstructorArgValue("java.lang.String").getBeanDefinition();
		Method method = ReflectionUtils.findMethod(SampleFactory.class, "create", Class.class);
		assertThat(generateCode(beanDefinition, method)).isEqualTo(
				"() -> SampleFactory.create(String.class)");
	}

	@Test
	void writeParameterWithWildcard() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(GenericDependencyConfiguration.class.getName())
				.setFactoryMethod("injectWildcard").getBeanDefinition();
		Method method = ReflectionUtils.findMethod(GenericDependencyConfiguration.class, "injectWildcard", Predicate.class);
		assertThat(generateCode(beanDefinition, method)).lines().containsOnly(
				"() -> {",
				"  ObjectProvider<Predicate<?>> predicateProvider = context.getBeanProvider(ResolvableType.forClassWithGenerics(Predicate.class, Object.class));",
				"  return context.getBean(GenericDependencyConfiguration.class).injectWildcard(predicateProvider.getObject());",
				"}");
	}

	@Test
	void writeParameterWithListOfGeneric() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(GenericDependencyConfiguration.class.getName())
				.setFactoryMethod("injectWildcardCollection").getBeanDefinition();
		Method method = ReflectionUtils.findMethod(GenericDependencyConfiguration.class, "injectWildcardCollection", Collection.class);
		assertThat(generateCode(beanDefinition, method)).lines().containsOnly(
				"() -> {",
				"  ObjectProvider<Predicate<?>> collectionPredicateProvider = context.getBeanProvider(ResolvableType.forClassWithGenerics(Predicate.class, Object.class));",
				"  return context.getBean(GenericDependencyConfiguration.class).injectWildcardCollection(collectionPredicateProvider.orderedStream().collect(Collectors.toList()));",
				"}");
	}

	private CodeSnippet generateCode(BeanDefinition beanDefinition, Method method) {
		return CodeSnippet.of((code) ->
				new MethodBeanValueWriter(beanDefinition, getClass().getClassLoader(), method).writeValueSupplier(code));
	}

}
