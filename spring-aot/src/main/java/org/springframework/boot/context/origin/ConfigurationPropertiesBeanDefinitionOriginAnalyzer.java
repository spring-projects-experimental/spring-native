package org.springframework.boot.context.origin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's configuration properties
 * support.
 *
 * @author Stephane Nicoll
 */
public final class ConfigurationPropertiesBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	private static final MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

	private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

	private static final String ENABLE_CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.EnableConfigurationProperties";

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		analysis.unprocessed().forEach((beanDefinition) -> {
			AnnotationMetadata annotationMetadata = getAnnotationMetadata(beanDefinition.getBeanClassName());
			if (annotationMetadata != null && annotationMetadata.isAnnotated(CONFIGURATION_PROPERTIES)) {
				BeanDefinitionOrigin origin = locateOrigin(analysis, beanDefinition);
				if (origin != null) {
					analysis.markAsProcessed(origin);
				}
			}
		});
	}

	private BeanDefinitionOrigin locateOrigin(BeanFactoryStructureAnalysis analysis, BeanDefinition beanDefinition) {
		Set<BeanDefinition> origins = new LinkedHashSet<>();
		analysis.beanDefinitions(AnnotatedBeanDefinition.class).forEach((candidate) -> {
			if (getConfigurationPropertiesClasses(candidate.getMetadata())
					.contains(beanDefinition.getBeanClassName())) {
				origins.add(beanDefinition);
			}
		});
		return (!origins.isEmpty()) ? new BeanDefinitionOrigin(beanDefinition, Type.COMPONENT, origins) : null;
	}

	@SuppressWarnings("unchecked")
	private List<String> getConfigurationPropertiesClasses(AnnotatedTypeMetadata metadata) {
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
		return Collections.emptyList();
	}

	@Nullable
	private static AnnotationMetadata getAnnotationMetadata(String type) {
		try {
			return metadataReaderFactory.getMetadataReader(type).getAnnotationMetadata();
		}
		catch (IOException ex) {
			return null;
		}
	}

}
