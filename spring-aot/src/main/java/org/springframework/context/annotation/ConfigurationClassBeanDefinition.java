package org.springframework.context.annotation;

import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;

/**
 * {@link AnnotatedGenericBeanDefinition} for a Configuration class.
 *
 * @author Stephane Nicoll
 */
final class ConfigurationClassBeanDefinition extends AnnotatedGenericBeanDefinition {

	private final ConfigurationClass configurationClass;

	ConfigurationClassBeanDefinition(ConfigurationClass configurationClass) {
		super(configurationClass.getMetadata());
		this.configurationClass = configurationClass;
	}

	public ConfigurationClass getConfigurationClass() {
		return this.configurationClass;
	}

}
