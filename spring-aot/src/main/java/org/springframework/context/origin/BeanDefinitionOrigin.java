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

package org.springframework.context.origin;

import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * Track the origin of a {@link BeanDefinition}.
 *
 * @author Stephane Nicoll
 */
public final class BeanDefinitionOrigin {

	private final BeanDefinition beanDefinition;

	private final Type type;

	private final Set<BeanDefinition> origins;

	public BeanDefinitionOrigin(BeanDefinition beanDefinition, Type type, Set<BeanDefinition> origins) {
		this.beanDefinition = beanDefinition;
		this.type = type;
		this.origins = origins;
	}

	public BeanDefinition getBeanDefinition() {
		return this.beanDefinition;
	}

	public Type getType() {
		return this.type;
	}

	public Set<BeanDefinition> getOrigins() {
		return this.origins;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", BeanDefinitionOrigin.class.getSimpleName() + "[", "]")
				.add("beanDefinition=" + determineId(beanDefinition))
				.add("type=" + type.name().toLowerCase(Locale.ROOT))
				.add("origins=" + origins.stream().map(BeanDefinition::getBeanClassName).collect(Collectors.joining(", ")))
				.toString();
	}

	private String determineId(BeanDefinition beanDefinition) {
		String beanClassName = beanDefinition.getBeanClassName();
		if (beanClassName != null) {
			return beanClassName;
		}
		String factoryBeanName = beanDefinition.getFactoryBeanName();
		if (factoryBeanName != null) {
			return String.format("%s#%s", factoryBeanName, beanDefinition.getFactoryMethodName());
		}
		return null;
	}

	public enum Type {

		/**
		 * Contributes components and/or other configuration classes.
		 */
		CONFIGURATION,

		/**
		 * A single component.
		 */
		COMPONENT

	}
}
