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

	@Test
	void analyzeSimpleStructure() {
		GenericApplicationContext context = new GenericApplicationContext();
		context.registerBean(ConfigurationOne.class);
		BeanFactoryStructure structure = analyze(context);
		assertThat(structure).isNotNull();
	}

	private BeanFactoryStructure analyze(GenericApplicationContext context) {
		return new BeanFactoryStructureAnalyzer(context.getClassLoader()).analyze(
				new BuildTimeBeanDefinitionsRegistrar(context).processBeanDefinitions());
	}

}
