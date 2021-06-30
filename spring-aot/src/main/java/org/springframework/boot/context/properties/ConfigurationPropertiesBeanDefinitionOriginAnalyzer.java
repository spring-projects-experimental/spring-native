package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.validation.beanvalidation.MethodValidationExcludeFilter;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanDefinitionPredicates;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.MultiValueMap;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's configuration properties
 * support.
 *
 * @author Stephane Nicoll
 */
public final class ConfigurationPropertiesBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionPredicates predicates = analysis.getPredicates();
		analysis.unprocessed().filter(predicates.annotatedWith(ConfigurationProperties.class.getName())).forEach((beanDefinition) -> {
			BeanDefinitionOrigin origin = locateOrigin(analysis, beanDefinition);
			if (origin != null) {
				analysis.markAsProcessed(origin);
			}
		});
		// See EnableConfigurationPropertiesRegistrar - let's bind that to the auto-configuration
		analysis.beanDefinitions().filter(predicates
				.ofBeanClassName(ConfigurationPropertiesAutoConfiguration.class.getName())).findFirst().ifPresent((parent) ->
				analysis.unprocessed().filter(predicates.ofBeanClassName(ConfigurationPropertiesBindingPostProcessor.class)
						.or(predicates.ofBeanClassName(MethodValidationExcludeFilter.class).or(predicates.ofBeanClassName(BoundConfigurationProperties.class)
								.or(predicates.ofBeanClassName(ConfigurationPropertiesBinder.Factory.class.getName())
										.or(predicates.ofBeanClassName(ConfigurationPropertiesBinder.class.getName()))))))
						.forEach((beanDefinition) -> analysis.markAsProcessed(
								new BeanDefinitionOrigin(beanDefinition, Type.COMPONENT, Collections.singleton(parent)))));
	}

	private BeanDefinitionOrigin locateOrigin(BeanFactoryStructureAnalysis analysis, BeanDefinition beanDefinition) {
		Set<BeanDefinition> origins = new LinkedHashSet<>();
		analysis.beanDefinitions().forEach((candidate) -> {
			if (getConfigurationPropertiesClasses(analysis.getPredicates().getAnnotationMetadata(candidate))
					.contains(beanDefinition.getBeanClassName())) {
				origins.add(candidate);
			}
		});
		return (!origins.isEmpty()) ? new BeanDefinitionOrigin(beanDefinition, Type.COMPONENT, origins) : null;
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
