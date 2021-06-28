package org.springframework.boot.autoconfigure;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanDefinitionPredicates;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's auto-configuration packages
 * support.
 *
 * @author Stephane Nicoll
 */
public class AutoConfigurationPackagesBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	private static final String BASE_PACKAGES_CLASS_NAME = "org.springframework.boot.autoconfigure.AutoConfigurationPackages$BasePackages";

	private static final String AUTO_CONFIGURATION_PACKAGES = "org.springframework.boot.autoconfigure.AutoConfigurationPackage";

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionPredicates predicates = analysis.getPredicates();
		BeanDefinition beanDefinition = analysis.unprocessed().filter(predicates.ofBeanClassName(BASE_PACKAGES_CLASS_NAME))
				.findAny().orElse(null);
		if (beanDefinition != null) {
			Set<BeanDefinition> origins = analysis.beanDefinitions().filter(predicates.annotatedWith(AUTO_CONFIGURATION_PACKAGES))
					.collect(Collectors.toSet());
			analysis.markAsProcessed(new BeanDefinitionOrigin(beanDefinition, Type.COMPONENT, origins));
		}
	}

}
