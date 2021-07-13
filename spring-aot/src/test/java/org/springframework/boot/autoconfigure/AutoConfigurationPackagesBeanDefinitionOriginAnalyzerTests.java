package org.springframework.boot.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.BuildTimeBeanDefinitionsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AutoConfigurationPackagesBeanDefinitionOriginAnalyzer}.
 *
 * @author Stephane Nicoll
 */
class AutoConfigurationPackagesBeanDefinitionOriginAnalyzerTests {

	private final AutoConfigurationPackagesBeanDefinitionOriginAnalyzer analyzer = new AutoConfigurationPackagesBeanDefinitionOriginAnalyzer();

	@Test
	void analyseAutoConfigurePackages() {
		GenericApplicationContext context = new GenericApplicationContext();
		context.registerBean(SampleConfiguration.class);
		new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions();
		BeanFactoryStructureAnalysis analysis = BeanFactoryStructureAnalysis.of(context.getBeanFactory());
		this.analyzer.analyze(analysis);
		assertThat(analysis.resolved()).singleElement().satisfies((processed) -> {
			assertThat(processed.getType()).isEqualTo(Type.INFRASTRUCTURE);
			assertThat(processed.getOrigins()).singleElement().satisfies((parent) -> {
				assertThat(context.containsBean(parent)).isTrue();
				assertThat(context.getBeanDefinition(parent).getBeanClassName()).isEqualTo(SampleConfiguration.class.getName());
			});
		});
	}


	@Configuration(proxyBeanMethods = false)
	@AutoConfigurationPackage
	static class SampleConfiguration {

	}

}
