package org.springframework.context.origin;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.origin.ConfigurationPropertiesBeanDefinitionOriginAnalyzer;
import org.springframework.context.annotation.CoreBeanDefinitionOriginAnalyzer;

/**
 * Analyze the structure of  {@link ConfigurableListableBeanFactory bean factory}.
 *
 * @author Stephane Nicoll
 * @see BeanDefinitionOriginAnalyzer
 */
public class BeanFactoryStructureAnalyzer {

	private final List<BeanDefinitionOriginAnalyzer> analyzers;

	public BeanFactoryStructureAnalyzer(List<BeanDefinitionOriginAnalyzer> analyzers) {
		this.analyzers = analyzers;
	}

	public BeanFactoryStructureAnalyzer() {
		this(Arrays.asList(new CoreBeanDefinitionOriginAnalyzer(),
				new ConfigurationPropertiesBeanDefinitionOriginAnalyzer()));
	}

	/**
	 * Analyze the specified {@link ConfigurableListableBeanFactory bean factory} and
	 * return its {@link BeanFactoryStructure structure}.
	 * @param beanFactory the bean factory to analyze
	 * @return the result of the analysis
	 */
	public BeanFactoryStructure analyze(ConfigurableListableBeanFactory beanFactory) {
		BeanFactoryStructureAnalysis analysis = new BeanFactoryStructureAnalysis(beanFactory);
		for (BeanDefinitionOriginAnalyzer locator : this.analyzers) {
			locator.analyze(analysis);
		}
		return analysis.toBeanFactoryStructure();
	}
}
