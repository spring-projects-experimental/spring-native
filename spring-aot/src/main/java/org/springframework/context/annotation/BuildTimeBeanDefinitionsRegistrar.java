package org.springframework.context.annotation;

import java.io.IOException;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.classreading.TypeDescriptor;
import org.springframework.core.type.classreading.TypeSystem;

/**
 * Parse the {@link Configuration @Configuration classes} and provide the bean definitions
 * at build time.
 *
 * @author Stephane Nicoll
 * @see ConditionEvaluationStateReport
 */
public class BuildTimeBeanDefinitionsRegistrar {

	private final GenericApplicationContext context;

	private final TypeSystem typeSystem;

	private final MetadataReaderFactory metadataReaderFactory;

	private final BeanNameGenerator beanNameGenerator = AnnotationBeanNameGenerator.INSTANCE;

	public BuildTimeBeanDefinitionsRegistrar(GenericApplicationContext context, TypeSystem typeSystem) {
		this.context = context;
		this.typeSystem = typeSystem;
		this.metadataReaderFactory = new SimpleMetadataReaderFactory();
	}

	/**
	 * Register root component classes that are meant to bootstrap the context.
	 * @param componentClasses the root component classes
	 */
	public void register(String... componentClasses) {
		for (String componentClass : componentClasses) {
			TypeDescriptor componentTypeDescriptor = this.typeSystem.resolve(componentClass);
			if (componentTypeDescriptor != null) {
				register(componentTypeDescriptor);
			}
		}
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

	private void register(TypeDescriptor typeDescriptor) {
		MetadataReader metadataReader = getMetadataReader(typeDescriptor);
		AnnotatedBeanDefinition beanDefinition = new AnnotatedMetadataGenericBeanDefinition(metadataReader);
		String beanName = this.beanNameGenerator.generateBeanName(beanDefinition, this.context);
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(beanDefinition, beanName);
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.context);
	}

	private MetadataReader getMetadataReader(TypeDescriptor typeDescriptor) {
		try {
			return this.metadataReaderFactory.getMetadataReader(typeDescriptor.getTypeName());
		}
		catch (IOException ex) {
			throw new BeanDefinitionStoreException(String.format("Failure while reading metadata for %s.", typeDescriptor), ex);
		}
	}

}
