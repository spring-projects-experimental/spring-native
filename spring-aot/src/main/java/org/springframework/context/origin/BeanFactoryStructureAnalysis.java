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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Represent the analysis of the structure of a {@link ConfigurableListableBeanFactory BeanFactory}.
 *
 * @author Stephane Nicoll
 */
public final class BeanFactoryStructureAnalysis {

	private final ConfigurableListableBeanFactory beanFactory;

	private final BeanDefinitionPredicates predicates;

	private final Map<BeanDefinition, BeanDefinitionOrigin> processed;

	public BeanFactoryStructureAnalysis(ConfigurableListableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.predicates = new BeanDefinitionPredicates(beanFactory.getBeanClassLoader());
		this.processed = new LinkedHashMap<>();
	}

	public BeanDefinitionPredicates getPredicates() {
		return this.predicates;
	}

	public Stream<BeanDefinition> beanDefinitions() {
		List<BeanDefinition> result = new ArrayList<>();
		for (String name : this.beanFactory.getBeanDefinitionNames()) {
			result.add(beanFactory.getBeanDefinition(name));
		}
		return result.stream();
	}

	public <T extends BeanDefinition> Stream<T> beanDefinitions(Class<T> type) {
		return cast(beanDefinitions(), type);
	}

	public Stream<BeanDefinitionOrigin> processed() {
		return this.processed.values().stream();
	}

	public Stream<BeanDefinition> unprocessed() {
		return filterProcessed(beanDefinitions());
	}

	public <T extends BeanDefinition> Stream<T> unprocessed(Class<T> type) {
		return unprocessed().filter(type::isInstance).map(type::cast);
	}

	private <T extends BeanDefinition> Stream<T> filterProcessed(Stream<T> stream) {
		return stream.filter((beanDefinition) -> !this.processed.containsKey(beanDefinition));
	}

	private <T extends BeanDefinition> Stream<T> cast(Stream<BeanDefinition> stream, Class<T> type) {
		return stream.filter(type::isInstance).map(type::cast);
	}

	public void markAsProcessed(BeanDefinitionOrigin origin) {
		BeanDefinition beanDefinition = origin.getBeanDefinition();
		BeanDefinitionOrigin existing = this.processed.putIfAbsent(beanDefinition, origin);
		if (existing != null) {
			throw new IllegalStateException("Bean definition has already been processed " + beanDefinition);
		}
	}

	BeanFactoryStructure toBeanFactoryStructure() {
		List<BeanDefinitionOrigin> origins = new ArrayList<>(this.processed.values());
		List<BeanDefinition> unprocessed = unprocessed().collect(Collectors.toList());
		return new BeanFactoryStructure(origins, unprocessed);
	}
}
