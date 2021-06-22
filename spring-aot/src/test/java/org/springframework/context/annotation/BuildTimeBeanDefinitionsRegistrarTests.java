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
		BuildTimeBeanDefinitionsRegistrar beanFactoryProvider = forApplicationContext(GenericApplicationContext::new);
		beanFactoryProvider.register(ConfigurationOne.class, ConfigurationTwo.class);
		ConfigurableListableBeanFactory beanFactory = beanFactoryProvider.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ConfigurationOne.class.getName(),
				ConfigurationTwo.class.getName(), "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClassWithImport() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new);
		context.register(ImportConfiguration.class);
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ImportConfiguration.class.getName(),
				ConfigurationOne.class.getName(), ConfigurationTwo.class.getName(), "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithClasspathScanning() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new);
		context.register(ScanConfiguration.class);
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ScanConfiguration.class.getName(),
				"configurationOne", "configurationTwo", "simpleComponent", "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithConditionsOnConfigurationClassNotMatching() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new);
		context.register(ConditionalConfigurationOne.class);
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ConditionalConfigurationOne.class.getName());
	}

	@Test
	void processBeanDefinitionsWithConditionsOnConfigurationClassMatching() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new,
				new MockEnvironment().withProperty("test.one.enabled", "true"));
		context.register(ConditionalConfigurationOne.class);
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly(ConditionalConfigurationOne.class.getName(),
				ConfigurationOne.class.getName(), "beanOne", ConfigurationTwo.class.getName(), "beanTwo");
	}

	private BuildTimeBeanDefinitionsRegistrar forApplicationContext(
			Supplier<? extends GenericApplicationContext> contextFactory) {
		return forApplicationContext(contextFactory, new MockEnvironment());
	}

	private BuildTimeBeanDefinitionsRegistrar forApplicationContext(
			Supplier<? extends GenericApplicationContext> contextFactory, ConfigurableEnvironment environment) {
		GenericApplicationContext context = contextFactory.get();
		context.setEnvironment(environment);
		return new BuildTimeBeanDefinitionsRegistrar(context);
	}

}
