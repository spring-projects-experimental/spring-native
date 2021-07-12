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

import java.util.function.Consumer;

import com.squareup.javapoet.CodeBlock.Builder;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.bootstrap.generator.sample.factory.SampleFactory;
import org.springframework.context.bootstrap.generator.test.CodeSnippet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimpleBeanRegistrationGenerator}.
 *
 * @author Stephane Nicoll
 */
class SimpleBeanRegistrationGeneratorTests {

	@Test
	void writeWithSyntheticFlag() {
		AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
				.rootBeanDefinition(SampleFactory.class.getName(), "create").getBeanDefinition();
		beanDefinition.setSynthetic(true);
		assertThat(generateCode(beanDefinition, (code) -> code.add("() -> SampleFactory::new"))).lines()
				.contains("context.registerBean(\"test\", Object.class, () -> SampleFactory::new, BeanDefinitionCustomizers.synthetic());");
	}


	private CodeSnippet generateCode(BeanDefinition beanDefinition, Consumer<Builder> instanceSupplier) {
		return CodeSnippet.of((code) -> new SimpleBeanRegistrationGenerator("test", beanDefinition,
				new TestBeanValueWriter(beanDefinition, instanceSupplier)).writeBeanRegistration(code));
	}

	private static class TestBeanValueWriter extends AbstractBeanValueWriter {

		private final Consumer<Builder> instanceSupplier;

		public TestBeanValueWriter(BeanDefinition beanDefinition, Consumer<Builder> instanceSupplier) {
			super(beanDefinition, SimpleBeanRegistrationGeneratorTests.class.getClassLoader());
			this.instanceSupplier = instanceSupplier;
		}

		@Override
		public Class<?> getDeclaringType() {
			return SimpleBeanRegistrationGeneratorTests.class;
		}

		@Override
		public void writeValueSupplier(Builder code) {
			this.instanceSupplier.accept(code);

		}
	}

}
