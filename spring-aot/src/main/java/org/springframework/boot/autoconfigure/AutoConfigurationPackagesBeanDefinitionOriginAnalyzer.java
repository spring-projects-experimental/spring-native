package org.springframework.boot.autoconfigure;

import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.lang.Nullable;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's auto-configuration packages
 * support.
 *
 * @author Stephane Nicoll
 */
public class AutoConfigurationPackagesBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	private static final String BASE_PACKAGES_CLASS_NAME = "org.springframework.boot.autoconfigure.AutoConfigurationPackages$BasePackages";

	private static final String AUTO_CONFIGURATION_PACKAGES = "org.springframework.boot.autoconfigure.AutoConfigurationPackage";

	private static final MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinition beanDefinition = analysis.unprocessed().filter((candidate) -> BASE_PACKAGES_CLASS_NAME
				.equals(candidate.getBeanClassName())).findAny().orElse(null);
		if (beanDefinition != null) {
			Set<BeanDefinition> origins = analysis.beanDefinitions().filter(annotatedWith(AUTO_CONFIGURATION_PACKAGES))
					.collect(Collectors.toSet());
			analysis.markAsProcessed(new BeanDefinitionOrigin(beanDefinition, Type.COMPONENT, origins));
		}
	}

	Predicate<BeanDefinition> annotatedWith(String annotationName) {
		return (candidate) -> {
			AnnotationMetadata metadata = getAnnotationMetadata(candidate);
			return (metadata != null && metadata.isAnnotated(annotationName));
		};
	}

	private static AnnotationMetadata getAnnotationMetadata(BeanDefinition beanDefinition) {
		if (beanDefinition instanceof AnnotatedBeanDefinition) {
			((AnnotatedBeanDefinition) beanDefinition).getMetadata();
		}
		if (beanDefinition.getBeanClassName() != null) {
			return getAnnotationMetadata(beanDefinition.getBeanClassName());
		}
		return null;
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
