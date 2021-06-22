package org.springframework.context.annotation;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.Assert;

/**
 * Parse the {@link Configuration @Configuration classes} and provide the bean definitions
 * at build time.
 *
 * @author Stephane Nicoll
 * @see ConditionEvaluationStateReport
 */
public class BuildTimeBeanDefinitionsRegistrar {

	private final GenericApplicationContext context;

	public BuildTimeBeanDefinitionsRegistrar(GenericApplicationContext context) {
		Assert.notNull(context, "Context must not be null");
		Assert.state(!context.isActive(), () -> "Context must not be active");
		this.context = context;
	}

	/**
	 * Process bean definitions without creating any instance and return the
	 * {@link ConfigurableListableBeanFactory bean factory}.
	 * @return the bean factory with the result of the processing
	 */
	public ConfigurableListableBeanFactory processBeanDefinitions() {
		parseConfigurationClasses();
		return this.context.getBeanFactory();
	}

	private void parseConfigurationClasses() {
		ConfigurationClassPostProcessor configurationClassPostProcessor = new ConfigurationClassPostProcessor();
		configurationClassPostProcessor.setApplicationStartup(this.context.getApplicationStartup());
		configurationClassPostProcessor.setBeanClassLoader(this.context.getClassLoader());
		configurationClassPostProcessor.setEnvironment(this.context.getEnvironment());
		configurationClassPostProcessor.setResourceLoader(this.context);
		configurationClassPostProcessor.postProcessBeanFactory(this.context.getBeanFactory());
	}

}
