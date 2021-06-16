package org.springframework.context.build;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.TypeSystem;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BuildTimeBeanFactoryProvider}.
 *
 * @author Stephane Nicoll
 */
class BuildTimeBeanFactoryProviderTests {

	private static final String CONFIGURATION_ONE = "org.springframework.context.build.samples.simple.ConfigurationOne";

	private static final String CONFIGURATION_TWO = "org.springframework.context.build.samples.simple.ConfigurationTwo";

	private final TypeSystem typeSystem = TypeSystem.getTypeSystem(new DefaultResourceLoader());

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClasses() {
		BuildTimeBeanFactoryProvider beanFactoryProvider = forApplicationContext(new GenericApplicationContext());
		beanFactoryProvider.register(CONFIGURATION_ONE, CONFIGURATION_TWO);
		ConfigurableListableBeanFactory beanFactory = beanFactoryProvider.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("configurationOne",
				"configurationTwo", "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithRegisteredConfigurationClassWithImport() {
		BuildTimeBeanFactoryProvider context = forApplicationContext(new GenericApplicationContext());
		context.register("org.springframework.context.build.samples.compose.ImportConfiguration");
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("importConfiguration",
				CONFIGURATION_ONE, CONFIGURATION_TWO, "beanOne", "beanTwo");
	}

	@Test
	void processBeanDefinitionsWithClasspathScanning() {
		BuildTimeBeanFactoryProvider context = forApplicationContext(new GenericApplicationContext());
		context.register("org.springframework.context.build.samples.scan.ScanConfiguration");
		ConfigurableListableBeanFactory beanFactory = context.processBeanDefinitions();
		assertThat(beanFactory.getBeanDefinitionNames()).containsOnly("scanConfiguration",
				"configurationOne", "configurationTwo", "simpleComponent", "beanOne", "beanTwo");
	}

	private BuildTimeBeanFactoryProvider forApplicationContext(GenericApplicationContext context) {
		return new BuildTimeBeanFactoryProvider(context, this.typeSystem);
	}

}
