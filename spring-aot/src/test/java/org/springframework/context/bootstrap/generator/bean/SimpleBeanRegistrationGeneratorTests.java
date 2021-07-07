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
import java.util.function.Consumer;

import com.squareup.javapoet.MethodSpec;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.bootstrap.generator.sample.factory.SampleFactory;
import org.springframework.util.ReflectionUtils;

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

		Method method = ReflectionUtils.findMethod(SampleFactory.class, "create", String.class);
		assertGeneratedCode(beanDefinition, method,
				(code) -> assertThat(code).contains("BeanDefinitionCustomizers.synthetic()"));
	}

	private void assertGeneratedCode(BeanDefinition beanDefinition, Method method, Consumer<String> code) {
		beanDefinition = resolveBeanDefinition(beanDefinition);
		MethodSpec.Builder builder = MethodSpec.methodBuilder("test");
		MethodBeanValueWriter beanValueWriter = new MethodBeanValueWriter(beanDefinition, getClass().getClassLoader(),
				method);
		new SimpleBeanRegistrationGenerator("test", beanDefinition, beanValueWriter).writeBeanRegistration(builder);
		code.accept(builder.build().toString());
	}

	private BeanDefinition resolveBeanDefinition(BeanDefinition beanDefinition) {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("test", beanDefinition);
		beanFactory.getType("test");
		return beanFactory.getMergedBeanDefinition("test");
	}

}
