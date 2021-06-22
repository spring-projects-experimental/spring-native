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
