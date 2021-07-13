package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.validation.beanvalidation.MethodValidationExcludeFilter;
import org.springframework.context.origin.BeanDefinitionDescriptor;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.context.origin.BeanDefinitionDescriptorPredicates;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's configuration properties
 * support.
 *
 * @author Stephane Nicoll
 */
class ConfigurationPropertiesBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionDescriptorPredicates predicates = analysis.getPredicates();
		analysis.unresolved().filter(predicates.annotatedWith(ConfigurationProperties.class.getName())).forEach((descriptor) -> {
			BeanDefinitionDescriptor origin = resolveOrigin(analysis, descriptor);
			if (origin != null) {
				analysis.markAsResolved(origin);
			}
		});
		// See EnableConfigurationPropertiesRegistrar - let's bind that to the auto-configuration
		analysis.beanDefinitions().filter(predicates
				.ofBeanClassName(ConfigurationPropertiesAutoConfiguration.class.getName())).findFirst().ifPresent((parent) ->
				analysis.unresolved().filter(predicates.ofBeanClassName(ConfigurationPropertiesBindingPostProcessor.class)
						.or(predicates.ofBeanClassName(MethodValidationExcludeFilter.class).or(predicates.ofBeanClassName(BoundConfigurationProperties.class)
								.or(predicates.ofBeanClassName(ConfigurationPropertiesBinder.Factory.class.getName())
										.or(predicates.ofBeanClassName(ConfigurationPropertiesBinder.class.getName()))))))
						.forEach((descriptor) -> analysis.markAsResolved(
								descriptor.resolve(Type.INFRASTRUCTURE, Collections.singleton(parent.getBeanName())))));
	}

	private BeanDefinitionDescriptor resolveOrigin(BeanFactoryStructureAnalysis analysis, BeanDefinitionDescriptor descriptor) {
		Set<String> origins = new LinkedHashSet<>();
		analysis.beanDefinitions().forEach((candidate) -> {
			if (getConfigurationPropertiesClasses(analysis.getPredicates().getAnnotationMetadata(candidate.getBeanDefinition()))
					.contains(descriptor.getBeanDefinition().getBeanClassName())) {
				origins.add(candidate.getBeanName());
			}
		});
		return (!origins.isEmpty()) ? descriptor.resolve(Type.COMPONENT, origins) : null;
	}

	@SuppressWarnings("unchecked")
	private List<String> getConfigurationPropertiesClasses(AnnotatedTypeMetadata metadata) {
		if (metadata != null) {
			MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(EnableConfigurationProperties.class.getName(), true);
			Object values = (attributes != null ? attributes.get("value") : null);
			if (values != null) {
				List<String[]> arrayValues = (List<String[]>) values;
				List<String> result = new ArrayList<>();
				for (String[] arrayValue : arrayValues) {
					result.addAll(Arrays.asList(arrayValue));
				}
				return result;
			}
		}
		return Collections.emptyList();
	}

}
