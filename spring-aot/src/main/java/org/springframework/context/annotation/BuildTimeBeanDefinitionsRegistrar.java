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

package org.springframework.context.annotation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;

/**
 * Parse the {@link Configuration @Configuration classes} and provide the bean definitions
 * at build time.
 *
 * @author Stephane Nicoll
 * @see ConditionEvaluationStateReport
 */
public class BuildTimeBeanDefinitionsRegistrar {

	private static final Log logger = LogFactory.getLog(BuildTimeBeanDefinitionsRegistrar.class);

	private final GenericApplicationContext context;

	public BuildTimeBeanDefinitionsRegistrar(GenericApplicationContext context) {
		Assert.notNull(context, "Context must not be null");
		Assert.state(!context.isActive(), () -> "Context must not be active");
		this.context = context;
	}

	/**
	 * Process bean definitions without creating any instance and return the
	 * {@link ConfigurableListableBeanFactory bean factory}.
	 * @return the bean factory with the result of the processing
	 */
	public ConfigurableListableBeanFactory processBeanDefinitions() {
		if (logger.isDebugEnabled()) {
			logger.debug("Parsing configuration classes");
		}
		parseConfigurationClasses();
		ConfigurableListableBeanFactory beanFactory = this.context.getBeanFactory();
		if (logger.isDebugEnabled()) {
			logger.debug("Resolving types for " + beanFactory.getBeanDefinitionCount() + " bean definitions");
		}
		for (String beanDefinitionName : beanFactory.getBeanDefinitionNames()) {
			beanFactory.getType(beanDefinitionName);
		}
		return beanFactory;
	}

	private void parseConfigurationClasses() {
		ConfigurationClassPostProcessor configurationClassPostProcessor = new ConfigurationClassPostProcessor();
		configurationClassPostProcessor.setApplicationStartup(this.context.getApplicationStartup());
		configurationClassPostProcessor.setBeanClassLoader(this.context.getClassLoader());
		configurationClassPostProcessor.setEnvironment(this.context.getEnvironment());
		configurationClassPostProcessor.setResourceLoader(this.context);
		configurationClassPostProcessor.postProcessBeanFactory(this.context.getBeanFactory());
	}

}
