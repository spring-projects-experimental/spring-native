package org.springframework.boot.sql.init.dependency;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer.DependsOnDatabaseInitializationPostProcessor;
import org.springframework.context.annotation.Import;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanDefinitionPredicates;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.MultiValueMap;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's database initialization
 * support.
 *
 * @author Stephane Nicoll
 */
class DatabaseInitializationDependencyBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionPredicates predicates = analysis.getPredicates();
		analysis.unprocessed().filter(predicates.ofBeanClassName(DependsOnDatabaseInitializationPostProcessor.class))
				.findAny().ifPresent((candidate) -> {
			Set<BeanDefinition> origins = analysis.beanDefinitions()
					.filter(predicates.annotationMatching(this::hasImport)).collect(Collectors.toSet());
			analysis.markAsProcessed(new BeanDefinitionOrigin(candidate, Type.COMPONENT, origins));
		});
	}

	@SuppressWarnings("unchecked")
	private boolean hasImport(AnnotationMetadata metadata) {
		if (!metadata.isAnnotated(Import.class.getName())) {
			return false;
		}
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Import.class.getName(), true);
		Object values = (attributes != null ? attributes.get("value") : null);
		if (values != null) {
			List<String[]> arrayValues = (List<String[]>) values;
			for (String[] arrayValue : arrayValues) {
				if (Arrays.asList(arrayValue).contains(DatabaseInitializationDependencyConfigurer.class.getName())) {
					return true;
				}
			}
		}
		return false;
	}
}
