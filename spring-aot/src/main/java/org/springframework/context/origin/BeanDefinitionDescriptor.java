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

import java.util.Set;
import java.util.StringJoiner;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * Provide a descriptor of a {@link BeanDefinition}.
 *
 * @author Stephane Nicoll
 */
public final class BeanDefinitionDescriptor {

	private final String beanName;

	private final BeanDefinition beanDefinition;

	private final Type type;

	private final Set<String> origins;

	BeanDefinitionDescriptor(String beanName, BeanDefinition beanDefinition, Type type,
			Set<String> origins) {
		this.beanName = beanName;
		this.beanDefinition = beanDefinition;
		this.type = type;
		this.origins = origins;
	}

	/**
	 * Create a {@link BeanDefinitionDescriptor} for the specified bean definition.
	 * @param beanName the name of the bean
	 * @param beanDefinition the definition of the bean
	 * @return a unresolved descriptor
	 */
	public static BeanDefinitionDescriptor unresolved(String beanName, BeanDefinition beanDefinition) {
		return new BeanDefinitionDescriptor(beanName, beanDefinition, Type.UNKNOWN, null);
	}

	/**
	 * Return a new {@link BeanDefinitionDescriptor} with the specified resolution.
	 * @param type the detected type
	 * @param origins the origins or an empty set if this descriptor has no parent
	 * @return a resolved descriptor
	 */
	public BeanDefinitionDescriptor resolve(Type type, Set<String> origins) {
		return new BeanDefinitionDescriptor(this.beanName, this.beanDefinition, type, origins);
	}

	public String getBeanName() {
		return this.beanName;
	}

	public BeanDefinition getBeanDefinition() {
		return this.beanDefinition;
	}

	public Type getType() {
		return this.type;
	}

	public Set<String> getOrigins() {
		return this.origins;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", BeanDefinitionDescriptor.class.getSimpleName() + "[", "]")
				.add("name='" + beanName + "'")
				.add("beanDefinition=" + beanDefinition)
				.add("type=" + type)
				.add("origins=" + origins)
				.toString();
	}

	public enum Type {

		/**
		 * Contributes components and/or other configuration classes.
		 */
		CONFIGURATION,

		/**
		 * A single component.
		 */
		COMPONENT,

		/**
		 * An infrastructure component, not meant to be user facing.
		 */
		INFRASTRUCTURE,

		/**
		 * A component that hasn't been identified thus far.
		 */
		UNKNOWN

	}

}
