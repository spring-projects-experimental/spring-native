package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
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

	private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

	private static final String ENABLE_CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.EnableConfigurationProperties";

	private static final String CONFIGURATION_PROPERTIES_AUTO_CONFIGURATION = "org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration";

	private static final String CONFIGURATION_PROPERTIES_BINDING_POST_PROCESSOR = "org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor";

	private static final String METHOD_VALIDATION_EXCLUDE_FILTER = "org.springframework.boot.validation.beanvalidation.MethodValidationExcludeFilter";

	private static final String BOUND_CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.BoundConfigurationProperties";

	private static final String CONFIGURATION_PROPERTIES_BINDER_FACTORY = "org.springframework.boot.context.properties.ConfigurationPropertiesBinder$Factory";

	private static final String CONFIGURATION_PROPERTIES_BINDER = "org.springframework.boot.context.properties.ConfigurationPropertiesBinder";

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionPredicates predicates = analysis.getPredicates();
		analysis.unprocessed().filter(predicates.annotatedWith(CONFIGURATION_PROPERTIES)).forEach((beanDefinition) -> {
			BeanDefinitionOrigin origin = locateOrigin(analysis, beanDefinition);
			if (origin != null) {
				analysis.markAsProcessed(origin);
			}
		});
		// See EnableConfigurationPropertiesRegistrar - let's bind that to the auto-configuration
		analysis.beanDefinitions().filter(predicates
				.ofBeanClassName(CONFIGURATION_PROPERTIES_AUTO_CONFIGURATION)).findFirst().ifPresent((parent) ->
				analysis.unprocessed().filter(predicates.ofBeanClassName(CONFIGURATION_PROPERTIES_BINDING_POST_PROCESSOR)
						.or(predicates.ofBeanClassName(METHOD_VALIDATION_EXCLUDE_FILTER).or(predicates.ofBeanClassName(BOUND_CONFIGURATION_PROPERTIES)
								.or(predicates.ofBeanClassName(CONFIGURATION_PROPERTIES_BINDER_FACTORY)
										.or(predicates.ofBeanClassName(CONFIGURATION_PROPERTIES_BINDER))))))
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
			MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(ENABLE_CONFIGURATION_PROPERTIES, true);
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
