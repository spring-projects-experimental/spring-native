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

