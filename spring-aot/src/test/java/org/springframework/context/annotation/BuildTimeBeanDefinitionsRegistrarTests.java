package org.springframework.context.annotation;

import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.samples.condition.ConditionalConfigurationOne;
import org.springframework.context.annotation.samples.scan.ScanConfiguration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.TypeSystem;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BuildTimeBeanDefinitionsRegistrar}.
 *
 * @author Stephane Nicoll
 */
class BuildTimeBeanDefinitionsRegistrarTests {

	private static final String CONFIGURATION_ONE = "org.springframework.context.annotation.samples.simple.ConfigurationOne";

	private static final String CONFIGURATION_TWO = "org.springframework.context.annotation.samples.simple.ConfigurationTwo";

	private final TypeSystem typeSystem = TypeSystem.getTypeSystem(new DefaultResourceLoader());

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClasses() {
		BuildTimeBeanDefinitionsRegistrar beanFactoryProvider = forApplicationContext(GenericApplicationContext::new);
		beanFactoryProvider.register(CONFIGURATION_ONE, CONFIGURATION_TWO);
		ConfigurableListableBeanFactory beanFactory = beanFactoryProvider.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("configurationOne",
				"configurationTwo", "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClassWithImport() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new);
		context.register("org.springframework.context.annotation.samples.compose.ImportConfiguration");
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("importConfiguration",
				CONFIGURATION_ONE, CONFIGURATION_TWO, "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithClasspathScanning() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new);
		context.register(ScanConfiguration.class.getName());
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("scanConfiguration",
				"configurationOne", "configurationTwo", "simpleComponent", "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithConditionsOnConfigurationClassNotMatching() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new);
		context.register(ConditionalConfigurationOne.class.getName());
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("conditionalConfigurationOne");
	}

	@Test
	void processBeanDefinitionsWithConditionsOnConfigurationClassMatching() {
		BuildTimeBeanDefinitionsRegistrar context = forApplicationContext(GenericApplicationContext::new,
				new MockEnvironment().withProperty("test.one.enabled", "true"));
		context.register(ConditionalConfigurationOne.class.getName());
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("conditionalConfigurationOne",
				CONFIGURATION_ONE, "beanOne", CONFIGURATION_TWO, "beanTwo");
	}

	private BuildTimeBeanDefinitionsRegistrar forApplicationContext(
			Supplier<? extends GenericApplicationContext> contextFactory) {
		return forApplicationContext(contextFactory, new MockEnvironment());
	}

	private BuildTimeBeanDefinitionsRegistrar forApplicationContext(
			Supplier<? extends GenericApplicationContext> contextFactory, ConfigurableEnvironment environment) {
		GenericApplicationContext context = contextFactory.get();
		context.setEnvironment(environment);
		return new BuildTimeBeanDefinitionsRegistrar(context, this.typeSystem);
	}

}
