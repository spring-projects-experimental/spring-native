package org.springframework.context.bootstrap.generator.sample.dependency;

import java.util.Collection;
import java.util.function.Predicate;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GenericDependencyConfiguration {

	@Bean
	public String injectWildcard(Predicate<?> predicate) {
		return "wildcard";
	}

	@Bean
	public String injectWildcardCollection(Collection<Predicate<?>> collectionPredicate) {
		return "wildcardCollection";
	}

}
