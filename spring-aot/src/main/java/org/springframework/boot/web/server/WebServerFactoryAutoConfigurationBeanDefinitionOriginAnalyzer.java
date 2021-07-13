package org.springframework.boot.web.server;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.origin.BeanDefinitionDescriptor;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.context.origin.BeanDefinitionDescriptorPredicates;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's web server support.
 *
 * @author Stephane Nicoll
 */
class WebServerFactoryAutoConfigurationBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionDescriptorPredicates predicates = analysis.getPredicates();
		analysis.unresolved().filter(predicates.ofBeanClassName(WebServerFactoryCustomizerBeanPostProcessor.class)
				.or(predicates.ofBeanClassName(ErrorPageRegistrarBeanPostProcessor.class))).forEach((candidate) -> {
			Set<String> origins = analysis.beanDefinitions()
					.filter(predicates.ofBeanClassName("org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration")
							.or(predicates.ofBeanClassName("org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration")))
					.map(BeanDefinitionDescriptor::getBeanName)
					.collect(Collectors.toSet());
			analysis.markAsResolved(candidate.resolve(Type.INFRASTRUCTURE, origins));
		});
	}

}
