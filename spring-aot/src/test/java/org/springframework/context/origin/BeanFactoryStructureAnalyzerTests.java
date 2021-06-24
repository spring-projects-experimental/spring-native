package org.springframework.context.origin;

import org.junit.jupiter.api.Test;

import org.springframework.context.annotation.BuildTimeBeanDefinitionsRegistrar;
import org.springframework.context.annotation.samples.simple.ConfigurationOne;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BeanFactoryStructureAnalyzer}.
 *
 * @author Stephane Nicoll
 */
class BeanFactoryStructureAnalyzerTests {

	private final BeanFactoryStructureAnalyzer analyzer = new BeanFactoryStructureAnalyzer();

	@Test
	void analyzeSimpleStructure() {
		GenericApplicationContext context = new GenericApplicationContext();
		context.registerBean(ConfigurationOne.class);
		BeanFactoryStructure structure = this.analyzer.analyze(
				new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions());
		assertThat(structure).isNotNull();
	}

}
