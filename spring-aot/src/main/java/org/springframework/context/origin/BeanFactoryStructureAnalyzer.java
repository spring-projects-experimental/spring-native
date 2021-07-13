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

import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.lang.Nullable;

/**
 * Analyze the structure of a {@link ConfigurableListableBeanFactory bean factory}.
 *
 * @author Stephane Nicoll
 * @see BeanDefinitionOriginAnalyzer
 */
public class BeanFactoryStructureAnalyzer {

	private final List<BeanDefinitionOriginAnalyzer> analyzers;

	/**
	 * Create an instance with the specified {@link BeanDefinitionOriginAnalyzer analyzers}.
	 * @param analyzers the analyzers to use.
	 */
	public BeanFactoryStructureAnalyzer(List<BeanDefinitionOriginAnalyzer> analyzers) {
		this.analyzers = analyzers;
	}

	/**
	 * Create an instance using all the registered {@link BeanDefinitionOriginAnalyzer analyzers}
	 * available using the specified {@link ClassLoader}.
	 * @param classLoader the class loader to use.
	 */
	public BeanFactoryStructureAnalyzer(@Nullable ClassLoader classLoader) {
		this(SpringFactoriesLoader.loadFactories(BeanDefinitionOriginAnalyzer.class, classLoader));
	}

	/**
	 * Analyze the specified {@link ConfigurableListableBeanFactory bean factory} and
	 * return its {@link BeanFactoryStructure structure}.
	 * @param beanFactory the bean factory to analyze
	 * @return the result of the analysis
	 */
	public BeanFactoryStructure analyze(ConfigurableListableBeanFactory beanFactory) {
		BeanFactoryStructureAnalysis analysis = BeanFactoryStructureAnalysis.of(beanFactory);
		for (BeanDefinitionOriginAnalyzer locator : this.analyzers) {
			locator.analyze(analysis);
		}
		return analysis.toBeanFactoryStructure();
	}
}
