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

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;
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
		analysis.unprocessed(ConfigurationClassBeanDefinition.class).forEach((configurationBeanDefinition) -> {
			BeanDefinitionOrigin origin = lookup(analysis, configurationBeanDefinition);
			analysis.markAsProcessed(origin);
		});
		analysis.unprocessed(BeanMethodBeanDefinition.class).forEach((beanMethodBeanDefinition) -> {
			BeanDefinitionOrigin origin = lookup(analysis, beanMethodBeanDefinition);
			analysis.markAsProcessed(origin);
		});
		analysis.unprocessed().forEach((beanDefinition) -> {
			boolean usedAsParent = analysis.processed().map(BeanDefinitionOrigin::getOrigins)
					.flatMap(Collection::stream).anyMatch((candidate) -> candidate.equals(beanDefinition));
			if (usedAsParent) {
				analysis.markAsProcessed(new BeanDefinitionOrigin(beanDefinition, Type.CONFIGURATION, Collections.emptySet()));
			}
		});
	}

	private BeanDefinitionOrigin lookup(BeanFactoryStructureAnalysis analysis,
			ConfigurationClassBeanDefinition beanDefinition) {
		Set<BeanDefinition> origins = new LinkedHashSet<>();
		for (ConfigurationClass parentConfigurationClass : beanDefinition.getConfigurationClass().getImportedBy()) {
			BeanDefinition parent = findBeanDefinition(analysis, parentConfigurationClass);
			if (parent == null) {
				throw new IllegalStateException("No bean definition found for " + parentConfigurationClass);
			}
			origins.add(parent);
		}
		return new BeanDefinitionOrigin(beanDefinition, Type.CONFIGURATION, origins);
	}

	private BeanDefinitionOrigin lookup(BeanFactoryStructureAnalysis analysis,
			BeanMethodBeanDefinition beanDefinition) {
		ConfigurationClass configurationClass = beanDefinition.getBeanMethod().getConfigurationClass();
		BeanDefinition configurationClassBeanDef = findBeanDefinition(analysis, configurationClass);
		if (configurationClassBeanDef == null) {
			throw new IllegalStateException("No bean definition found for " + configurationClass);
		}
		return new BeanDefinitionOrigin(beanDefinition, Type.COMPONENT, Collections.singleton(configurationClassBeanDef));
	}

	/**
	 * Find a {@link BeanDefinition} that matches the specified {@link ConfigurationClass}.
	 * @return a bean definition for the specified {@link ConfigurationClass}
	 */
	@Nullable
	private BeanDefinition findBeanDefinition(BeanFactoryStructureAnalysis analysis, ConfigurationClass configurationClass) {
		ConfigurationClassBeanDefinition match = analysis.beanDefinitions(ConfigurationClassBeanDefinition.class)
				.filter((beanDefinition) -> beanDefinition.getConfigurationClass().equals(configurationClass))
				.findAny().orElse(null);
		if (match != null) {
			return match;
		}
		String targetClassName = configurationClass.getMetadata().getClassName();
		return analysis.beanDefinitions().filter((candidate) -> {
			Class<?> target = candidate.getResolvableType().resolve();
			return target != null && ClassUtils.getUserClass(target).getName().equals(targetClassName);
		}).findAny().orElse(null);
	}

}
