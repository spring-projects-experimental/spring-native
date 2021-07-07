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

package org.springframework.boot.autoconfigure.context.properties;

import com.squareup.javapoet.CodeBlock.Builder;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.validation.beanvalidation.MethodValidationExcludeFilter;
import org.springframework.context.bootstrap.generator.bean.AbstractBeanValueWriter;
import org.springframework.context.bootstrap.generator.bean.BeanValueWriter;
import org.springframework.context.bootstrap.generator.bean.BeanValueWriterSupplier;
import org.springframework.core.annotation.Order;

/**
 * {@link BeanValueWriterSupplier} for {@link ConfigurationProperties} support.
 *
 * @author Stephane Nicoll
 */
@Order(0)
class ConfigurationPropertiesBeanValueWriterSupplier implements BeanValueWriterSupplier {

	@Override
	public BeanValueWriter get(BeanDefinition beanDefinition, ClassLoader classLoader) {
		if (MethodValidationExcludeFilter.class.getName().equals(beanDefinition.getBeanClassName())) {
			return new MethodValidationExcludeFilterBeanValueWriter(beanDefinition, classLoader);
		}
		return null;
	}

	private static class MethodValidationExcludeFilterBeanValueWriter extends AbstractBeanValueWriter {

		MethodValidationExcludeFilterBeanValueWriter(BeanDefinition beanDefinition, ClassLoader classLoader) {
			super(beanDefinition, classLoader);
		}

		@Override
		public Class<?> getDeclaringType() {
			return MethodValidationExcludeFilter.class;
		}

		@Override
		public void writeValueSupplier(Builder code) {
			code.add("() -> $T.byAnnotation($T.class)", MethodValidationExcludeFilter.class,
					ConfigurationProperties.class);
		}

	}

}
