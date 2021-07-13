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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;

/**
 * Represent the analysis of the structure of a {@link ConfigurableListableBeanFactory BeanFactory}.
 *
 * @author Stephane Nicoll
 */
public final class BeanFactoryStructureAnalysis {

	private final BeanDefinitionDescriptorPredicates predicates;

	private final Map<String, BeanDefinitionDescriptor> descriptors;

	private BeanFactoryStructureAnalysis(ConfigurableListableBeanFactory beanFactory) {
		this.predicates = new BeanDefinitionDescriptorPredicates(beanFactory.getBeanClassLoader());
		this.descriptors = initialize(beanFactory);
	}

	private static Map<String, BeanDefinitionDescriptor> initialize(ConfigurableListableBeanFactory beanFactory) {
		Map<String, BeanDefinitionDescriptor> descriptors = new LinkedHashMap<>();
		for (String beanName : beanFactory.getBeanDefinitionNames()) {
			descriptors.put(beanName, BeanDefinitionDescriptor.unresolved(beanName,
					beanFactory.getBeanDefinition(beanName)));
		}
		return descriptors;
	}

	public static BeanFactoryStructureAnalysis of(ConfigurableListableBeanFactory beanFactory) {
		return new BeanFactoryStructureAnalysis(beanFactory);
	}

	public BeanDefinitionDescriptorPredicates getPredicates() {
		return this.predicates;
	}

	public Stream<BeanDefinitionDescriptor> beanDefinitions() {
		return this.descriptors.values().stream();
	}

	public Stream<BeanDefinitionDescriptor> resolved() {
		return beanDefinitions().filter(this.predicates.ofType(Type.UNKNOWN).negate());
	}

	public Stream<BeanDefinitionDescriptor> unresolved() {
		return beanDefinitions().filter(this.predicates.ofType(Type.UNKNOWN));
	}

	public void markAsResolved(BeanDefinitionDescriptor descriptor) {
		String name = descriptor.getBeanName();
		BeanDefinition beanDefinition = descriptor.getBeanDefinition();
		BeanDefinitionDescriptor existing = this.descriptors.put(name, descriptor);
		if (existing == null) {
			throw new IllegalStateException("No such bean definition with '" + name + "'");
		}
		if (existing.getType() != Type.UNKNOWN) {
			throw new IllegalStateException("Bean definition '" + name + "' has already been processed " + beanDefinition);
		}
	}

	BeanFactoryStructure toBeanFactoryStructure() {
		return new BeanFactoryStructure(this.descriptors);
	}

}
