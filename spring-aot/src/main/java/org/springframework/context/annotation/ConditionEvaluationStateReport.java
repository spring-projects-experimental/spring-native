package org.springframework.context.annotation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;

/**
 * Record {@link ConditionEvaluation} of all the components of a given {@link BeanFactory}.
 *
 * @author Stephane Nicoll
 */
public class ConditionEvaluationStateReport {

	static final String BEAN_NAME = "conditionEvaluationStateReport";

	private final Map<ConfigurationClass, ConditionEvaluationState> configurationClassEvaluations = new HashMap<>();

	private final Map<BeanMethod, ConditionEvaluationState> beanMethodEvaluations = new HashMap<>();

	@Nullable
	private ConditionEvaluationStateReport parent;

	/**
	 * Return the {@link ConditionEvaluationState} of the specified {@link ConfigurationClass}.
	 * If no such state exists, it is created.
	 * @param configurationClass the configuration class
	 * @return the condition evaluation state of the specified configuration class
	 */
	public ConditionEvaluationState forConfigurationClass(ConfigurationClass configurationClass) {
		return this.configurationClassEvaluations.computeIfAbsent(configurationClass, (key) ->
				new ConditionEvaluationState(Conditions.from(configurationClass.getMetadata())));
	}

	/**
	 * Return the {@link ConditionEvaluationState} of the specified {@link BeanMethod}.
	 * If no such state exists, it is created.
	 * @param beanMethod the bean method
	 * @return the condition evaluation state of the specified bean method
	 */
	public ConditionEvaluationState forBeanMethod(BeanMethod beanMethod) {
		return this.beanMethodEvaluations.computeIfAbsent(beanMethod, (key) ->
				new ConditionEvaluationState(Conditions.from(beanMethod.getMetadata())));
	}

	@Nullable
	public ConditionEvaluationState getConditionEvaluationState(ConfigurationClass configurationClass) {
		return this.configurationClassEvaluations.get(configurationClass);
	}

	@Nullable
	public ConditionEvaluationState getConditionEvaluationState(BeanMethod beanMethod) {
		return this.beanMethodEvaluations.get(beanMethod);
	}

	/**
	 * Obtain the report for the specified {@link BeanFactory}. If the report does not
	 * exist, it is created.
	 * @param beanFactory the bean factory
	 * @return the report for that bean factory
	 */
	public static ConditionEvaluationStateReport get(ConfigurableBeanFactory beanFactory) {
		synchronized (beanFactory) {
			ConditionEvaluationStateReport report;
			if (beanFactory.containsSingleton(BEAN_NAME)) {
				report = beanFactory.getBean(BEAN_NAME, ConditionEvaluationStateReport.class);
			}
			else {
				report = new ConditionEvaluationStateReport();
				beanFactory.registerSingleton(BEAN_NAME, report);
			}
			locateParent(beanFactory.getParentBeanFactory(), report);
			return report;
		}
	}

	private static void locateParent(@Nullable BeanFactory beanFactory, ConditionEvaluationStateReport report) {
		if (beanFactory != null && report.parent == null && beanFactory.containsBean(BEAN_NAME)) {
			report.parent = beanFactory.getBean(BEAN_NAME, ConditionEvaluationStateReport.class);
		}
	}

}
