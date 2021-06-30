package org.springframework.boot.web.server;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanDefinitionPredicates;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;

/**
 * A {@link BeanDefinitionOriginAnalyzer} for Spring Boot's web server support.
 *
 * @author Stephane Nicoll
 */
class WebServerFactoryAutoConfigurationBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		BeanDefinitionPredicates predicates = analysis.getPredicates();
		analysis.unprocessed().filter(predicates.ofBeanClassName(WebServerFactoryCustomizerBeanPostProcessor.class)
				.or(predicates.ofBeanClassName(ErrorPageRegistrarBeanPostProcessor.class))).forEach((candidate) -> {
			Set<BeanDefinition> origins = analysis.beanDefinitions()
					.filter(predicates.ofBeanClassName("org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration")
							.or(predicates.ofBeanClassName("org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration")))
					.collect(Collectors.toSet());
			analysis.markAsProcessed(new BeanDefinitionOrigin(candidate, Type.COMPONENT, origins));
		});
	}

}
