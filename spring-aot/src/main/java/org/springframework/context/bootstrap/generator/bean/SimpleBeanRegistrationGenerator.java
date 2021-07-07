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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec.Builder;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.bootstrap.infrastructure.BeanDefinitionCustomizers;
import org.springframework.util.ClassUtils;

/**
 * A {@link BeanRegistrationGenerator} implementation that uses {@code registerBean}.
 *
 * @author Stephane Nicoll
 */
public class SimpleBeanRegistrationGenerator implements BeanRegistrationGenerator {

	private final String beanName;

	private final BeanDefinition beanDefinition;

	private final BeanValueWriter beanValueWriter;

	public SimpleBeanRegistrationGenerator(String beanName, BeanDefinition beanDefinition,
			BeanValueWriter beanValueWriter) {
		this.beanName = beanName;
		this.beanDefinition = beanDefinition;
		this.beanValueWriter = beanValueWriter;
	}

	@Override
	public void writeBeanRegistration(Builder method) {
		CodeBlock.Builder code = CodeBlock.builder();
		code.add("context.registerBean($S, $T.class, ", this.beanName,
				ClassUtils.getUserClass(this.beanDefinition.getResolvableType().toClass()));
		this.beanValueWriter.writeValueSupplier(code);
		handleBeanMetadata(code);
		code.add(")"); // End of registerBean
		method.addStatement(code.build());
	}

	private void handleBeanMetadata(CodeBlock.Builder code) {
		if (this.beanDefinition.isPrimary()) {
			code.add(", $T.primary()", BeanDefinitionCustomizers.class);
		}
		if (this.beanDefinition instanceof AbstractBeanDefinition
				&& ((AbstractBeanDefinition) this.beanDefinition).isSynthetic()) {
			code.add(", $T.synthetic()", BeanDefinitionCustomizers.class);
		}
		if (this.beanDefinition.getRole() != BeanDefinition.ROLE_APPLICATION) {
			code.add(", $T.role($L)", BeanDefinitionCustomizers.class, this.beanDefinition.getRole());
		}
	}

	@Override
	public BeanValueWriter getBeanValueWriter() {
		return this.beanValueWriter;
	}

}
