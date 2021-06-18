/*
 * Copyright 2002-2018 the original author or authors.
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

import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;

	private final ConditionEvaluationStateReport report;


	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
			@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
		this.report = ConditionEvaluationStateReport.get(this.context.getBeanFactory());
	}

	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	// Used by classpath scanning and for beans that are registered manually
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		ConditionEvaluationState ignoredState = new ConditionEvaluationState(Conditions.from(metadata));
		return shouldSkip(ignoredState, ignoredState.getConditions().getRequiredPhase());
	}

	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * @param metadata the meta data
	 * @param phase the phase of the call
	 * @return if the item should be skipped
	 */
	// This should not been necessary, @ComponentScan checking conditions on the wrong class, gh-27077
	public boolean shouldSkip(AnnotatedTypeMetadata metadata, ConfigurationPhase phase) {
		ConditionEvaluationState ignoredState = new ConditionEvaluationState(Conditions.from(metadata));
		return shouldSkip(ignoredState, phase);
	}

	public boolean shouldSkip(ConfigurationClass configurationClass, ConfigurationPhase phase) {
		return shouldSkip(this.report.forConfigurationClass(configurationClass), phase);
	}

	public boolean shouldSkip(BeanMethod beanMethod, ConfigurationPhase phase) {
		return shouldSkip(this.report.forBeanMethod(beanMethod), phase);
	}

	public boolean shouldSkip(ConditionEvaluationState state, @Nullable ConfigurationPhase phase) {
		Conditions conditions = state.getConditions();
		if (phase == null) {
			return shouldSkip(state, conditions.getRequiredPhase());
		}
		ConditionEvaluation existingEvaluation = state.getConditionEvaluation(phase);
		if (existingEvaluation != null) {
			//System.out.println("====== WARNING ======");
			//System.out.println("We already have an evaluation for " + conditions.determineAnnotatedTypeId() + " and phase " + phase);
			return existingEvaluation.shouldSkip();
		}
		ConditionEvaluation evaluation = evaluate(conditions, phase);
		state.recordConditionEvaluation(phase, evaluation);
		return evaluation.shouldSkip();
	}

	private ConditionEvaluation evaluate(Conditions conditions, ConfigurationPhase phase) {
		List<Condition> conditionInstances = conditions.instantiate(this.context.classLoader);
		ConditionEvaluation.Builder stateBuilder = ConditionEvaluation.forConditions(conditionInstances);
		for (Condition condition : conditionInstances) {
			if (!hasRequiredPhase(condition, phase)) {
				stateBuilder.notMatchingPhase(condition);
			}
			else {
				boolean matches = condition.matches(context, conditions.getMetadata());
				if (!matches) {
					return stateBuilder.didNotMatch(condition);
				}
				stateBuilder.matchAndContinue(condition);
			}
		}
		return stateBuilder.match();
	}

	private boolean hasRequiredPhase(Condition condition, ConfigurationPhase phase) {
		ConfigurationPhase requiredPhase = null;
		if (condition instanceof ConfigurationCondition) {
			requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
		}
		return (requiredPhase == null || requiredPhase == phase);
	}


	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
				@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
				@Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
