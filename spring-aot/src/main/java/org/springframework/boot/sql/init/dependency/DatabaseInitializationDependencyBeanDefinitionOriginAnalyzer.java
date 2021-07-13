package org.springframework.boot.sql.init.dependency;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer.DependsOnDatabaseInitializationPostProcessor;
import org.springframework.context.annotation.Import;
import org.springframework.context.origin.BeanDefinitionDescriptor;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.context.origin.BeanDefinitionDescriptorPredicates;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
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
		BeanDefinitionDescriptorPredicates predicates = analysis.getPredicates();
		analysis.unresolved().filter(predicates.ofBeanClassName(DependsOnDatabaseInitializationPostProcessor.class))
				.findAny().ifPresent((candidate) -> {
			Set<String> origins = analysis.beanDefinitions()
					.filter(predicates.annotationMatching(this::hasImport)).map(BeanDefinitionDescriptor::getBeanName).collect(Collectors.toSet());
			analysis.markAsResolved(candidate.resolve(Type.INFRASTRUCTURE, origins));
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
