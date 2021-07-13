package org.springframework.boot.autoconfigure;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.AutoConfigurationPackages.BasePackages;
import org.springframework.context.origin.BeanDefinitionDescriptor;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.context.origin.BeanDefinitionDescriptorPredicates;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's auto-configuration packages
 * support.
 *
 * @author Stephane Nicoll
 */
class AutoConfigurationPackagesBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionDescriptorPredicates predicates = analysis.getPredicates();
		BeanDefinitionDescriptor descriptor = analysis.unresolved().filter(predicates.ofBeanClassName(BasePackages.class))
				.findAny().orElse(null);
		if (descriptor != null) {
			Set<String> origins = analysis.beanDefinitions().filter(predicates.annotatedWith(AutoConfigurationPackage.class))
					.map(BeanDefinitionDescriptor::getBeanName).collect(Collectors.toSet());
			analysis.markAsResolved(descriptor.resolve(Type.INFRASTRUCTURE, origins));
		}
	}

}
