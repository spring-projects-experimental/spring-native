package org.springframework.context.annotation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.lang.Nullable;

/**
 * Provide a complete state of the condition evaluation of a component.
 *
 * @author Stephane Nicoll
 */
public class ConditionEvaluationState {

	private final Conditions conditions;

	private final Map<ConfigurationPhase, ConditionEvaluation> recordedEvaluations = new LinkedHashMap<>();

	ConditionEvaluationState(Conditions conditions) {
		this.conditions = conditions;
	}

	/**
	 * Record the {@link ConditionEvaluation} for the specified {@link ConfigurationPhase phase}.
	 * @param requiredPhase the required phase
	 * @param conditionEvaluation the evaluation for that phase
	 * @throws IllegalStateException if an evaluation already exists for that phase
	 */
	void recordConditionEvaluation(ConfigurationPhase requiredPhase,
			ConditionEvaluation conditionEvaluation) {
		ConditionEvaluation existing = this.recordedEvaluations.putIfAbsent(requiredPhase, conditionEvaluation);
		if (existing != null) {
			throw new IllegalStateException("Conditions evaluation for phase " + requiredPhase + " already recorded.");
		}
	}

	/**
	 * Return the {@link Conditions conditions} of this instance
	 * @return the conditions
	 */
	public Conditions getConditions() {
		return this.conditions;
	}

	/**
	 * Return the {@link ConditionEvaluation} for the specified {@link ConfigurationPhase phase}.
	 * @param phase the configuration phase of interest
	 * @return the evaluation, or {@code null} if the evaluation for that phase has not been recorded yet
	 */
	@Nullable
	public ConditionEvaluation getConditionEvaluation(ConfigurationPhase phase) {
		return this.recordedEvaluations.get(phase);
	}

}

