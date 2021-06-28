package org.springframework.boot.context.properties;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBeanDefinitionOriginAnalyzer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConfigurationPropertiesBeanDefinitionOriginAnalyzer}.
 *
 * @author Stephane Nicoll
 */
class ConfigurationPropertiesBeanDefinitionOriginAnalyzerTests {

	private final ConfigurationPropertiesBeanDefinitionOriginAnalyzer analyzer = new ConfigurationPropertiesBeanDefinitionOriginAnalyzer();

	@Test
	void testConfigurationPropertiesAnnotatedTypeIsAnalyzed() {
		DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
		beanFactory.registerBeanDefinition("configuration", new RootBeanDefinition(SampleConfiguration.class));
		beanFactory.registerBeanDefinition("configurationProperties", new RootBeanDefinition(SampleProperties.class));
		BeanFactoryStructureAnalysis analysis = new BeanFactoryStructureAnalysis(beanFactory);
		this.analyzer.analyze(analysis);
		assertThat(analysis.processed()).singleElement().satisfies((processed) -> {
			assertThat(processed.getBeanDefinition()).isEqualTo(beanFactory.getBeanDefinition("configurationProperties"));
			assertThat(processed.getType()).isEqualTo(Type.COMPONENT);
			assertThat(processed.getOrigins()).singleElement().isEqualTo(beanFactory.getBeanDefinition("configuration"));
		});
	}


	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(SampleProperties.class)
	static class SampleConfiguration {

	}

	@ConfigurationProperties("sample")
	static class SampleProperties {

	}

}
