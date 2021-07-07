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

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.samples.compose.ImportConfiguration;
import org.springframework.context.annotation.samples.condition.ConditionalConfigurationOne;
import org.springframework.context.annotation.samples.scan.ScanConfiguration;
import org.springframework.context.annotation.samples.simple.ConfigurationOne;
import org.springframework.context.annotation.samples.simple.ConfigurationTwo;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BuildTimeBeanDefinitionsRegistrar}.
 *
 * @author Stephane Nicoll
 */
class BuildTimeBeanDefinitionsRegistrarTests {

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClasses() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new,
				ConfigurationOne.class, ConfigurationTwo.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ConfigurationOne.class.getName(),
				ConfigurationTwo.class.getName(), "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClassWithImport() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new, ImportConfiguration.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ImportConfiguration.class.getName(),
				ConfigurationOne.class.getName(), ConfigurationTwo.class.getName(), "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithClasspathScanning() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new, ScanConfiguration.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ScanConfiguration.class.getName(),
				"configurationOne", "configurationTwo", "simpleComponent", "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithConditionsOnConfigurationClassNotMatching() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new, ConditionalConfigurationOne.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ConditionalConfigurationOne.class.getName());
	}

	@Test
	void processBeanDefinitionsWithConditionsOnConfigurationClassMatching() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new,
				new MockEnvironment().withProperty("test.one.enabled", "true"), ConditionalConfigurationOne.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ConditionalConfigurationOne.class.getName(),
				ConfigurationOne.class.getName(), "beanOne", ConfigurationTwo.class.getName(), "beanTwo");
	}

	// Bean definitions

	@Test
	void processBeanDefinitionsForConfigurationClassCreateRelevantBeanDefinition() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new, ImportConfiguration.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinition(ConfigurationOne.class.getName()))
				.isInstanceOfSatisfying(ConfigurationClassBeanDefinition.class, (bd) -> {
					assertThat(bd.getConfigurationClass().getMetadata().getClassName()).isEqualTo(ConfigurationOne.class.getName());
					assertThat(bd.getConfigurationClass().getImportedBy()).hasSize(1);
				});
	}

	@Test
	void processBeanDefinitionsForBeanMethodCreateRelevantBeanDefinition() {
		GenericApplicationContext context = createApplicationContext(GenericApplicationContext::new, ImportConfiguration.class);
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinition("beanOne"))
				.isInstanceOfSatisfying(BeanMethodBeanDefinition.class, (bd) -> {
					assertThat(bd.getFactoryMethodMetadata().getMethodName()).isEqualTo("beanOne");
					assertThat(bd.getBeanMethod().getConfigurationClass().getBeanMethods())
							.singleElement().isEqualTo(bd.getBeanMethod());
				});
	}


	private <T extends GenericApplicationContext> T createApplicationContext(
			Supplier<T> contextFactory, Class<?>... componentClasses) {
		return createApplicationContext(contextFactory, new MockEnvironment(), componentClasses);
	}

	private <T extends GenericApplicationContext> T createApplicationContext(
			Supplier<T> contextFactory, ConfigurableEnvironment environment, Class<?>... componentClasses) {
		T context = contextFactory.get();
		context.setEnvironment(environment);
		for (Class<?> componentClass : componentClasses) {
			context.registerBean(componentClass);
		}
		return context;
	}

}
