package org.springframework.boot.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.BuildTimeBeanDefinitionsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
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
		BeanFactoryStructureAnalysis analysis = new BeanFactoryStructureAnalysis(context.getBeanFactory());
		this.analyzer.analyze(analysis);
		assertThat(analysis.processed()).singleElement().satisfies((processed) -> {
			assertThat(processed.getType()).isEqualTo(Type.COMPONENT);
			assertThat(processed.getOrigins()).singleElement().isEqualTo(context.getBeanDefinition(SampleConfiguration.class.getName()));
		});
	}


	@Configuration(proxyBeanMethods = false)
	@AutoConfigurationPackage
	static class SampleConfiguration {

	}

}
