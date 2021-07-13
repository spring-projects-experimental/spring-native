/*
 * Copyright 2019-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionDescriptor;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.context.origin.BeanDefinitionOriginAnalyzer;
import org.springframework.context.origin.BeanFactoryStructureAnalysis;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * A {@link BeanDefinitionOriginAnalyzer} that identifies configuration classes and
 * bean methods.
 *
 * @author Stephane Nicoll
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class CoreBeanDefinitionOriginAnalyzer implements BeanDefinitionOriginAnalyzer {

	@Override
	public void analyze(BeanFactoryStructureAnalysis analysis) {
		analysis.unresolved().filter(ofBeanDefinitionType(ConfigurationClassBeanDefinition.class))
				.forEach((descriptor) -> analysis.markAsResolved(resolveConfigurationClass(analysis, descriptor)));
		analysis.unresolved().filter(ofBeanDefinitionType(BeanMethodBeanDefinition.class))
				.forEach((descriptor) -> analysis.markAsResolved(resolveBeanMethod(analysis, descriptor)));
		analysis.unresolved().forEach((descriptor) -> {
			boolean usedAsParent = analysis.resolved().map(BeanDefinitionDescriptor::getOrigins)
					.flatMap(Collection::stream).anyMatch((candidate) -> candidate.equals(descriptor.getBeanName()));
			if (usedAsParent) {
				analysis.markAsResolved(descriptor.resolve(Type.CONFIGURATION, Collections.emptySet()));
			}
		});
	}

	private BeanDefinitionDescriptor resolveConfigurationClass(BeanFactoryStructureAnalysis analysis, BeanDefinitionDescriptor descriptor) {
		Set<String> origins = new LinkedHashSet<>();
		ConfigurationClass configurationClass = ((ConfigurationClassBeanDefinition) descriptor.getBeanDefinition()).getConfigurationClass();
		for (ConfigurationClass parentConfigurationClass : configurationClass.getImportedBy()) {
			String parentName = findBeanDefinitionName(analysis, parentConfigurationClass);
			if (parentName == null) {
				throw new IllegalStateException("No bean definition found for " + parentConfigurationClass);
			}
			origins.add(parentName);
		}
		return descriptor.resolve(Type.CONFIGURATION, origins);
	}

	private BeanDefinitionDescriptor resolveBeanMethod(BeanFactoryStructureAnalysis analysis, BeanDefinitionDescriptor descriptor) {
		ConfigurationClass configurationClass = ((BeanMethodBeanDefinition) descriptor.getBeanDefinition())
				.getBeanMethod().getConfigurationClass();
		String configurationClassBeanDef = findBeanDefinitionName(analysis, configurationClass);
		if (configurationClassBeanDef == null) {
			throw new IllegalStateException("No bean definition found for " + configurationClass);
		}
		return descriptor.resolve(Type.COMPONENT, Collections.singleton(configurationClassBeanDef));
	}

	/**
	 * Find a {@link BeanDefinition} name that matches the specified {@link ConfigurationClass}.
	 * @return a bean definition name for the specified {@link ConfigurationClass}
	 */
	@Nullable
	private String findBeanDefinitionName(BeanFactoryStructureAnalysis analysis,
			ConfigurationClass configurationClass) {
		BeanDefinitionDescriptor match = analysis.beanDefinitions().filter(ofBeanDefinitionType(ConfigurationClassBeanDefinition.class))
				.filter((descriptor) -> ((ConfigurationClassBeanDefinition) descriptor.getBeanDefinition()).getConfigurationClass().equals(configurationClass))
				.findAny().orElse(null);
		if (match != null) {
			return match.getBeanName();
		}
		String targetClassName = configurationClass.getMetadata().getClassName();
		return analysis.beanDefinitions().filter((candidate) -> {
			Class<?> target = candidate.getBeanDefinition().getResolvableType().resolve();
			return target != null && ClassUtils.getUserClass(target).getName().equals(targetClassName);
		}).findAny().map(BeanDefinitionDescriptor::getBeanName).orElse(null);
	}

	private Predicate<BeanDefinitionDescriptor> ofBeanDefinitionType(Class<? extends BeanDefinition> type) {
		return (candidate) -> type.isInstance(candidate.getBeanDefinition());
	}

}
