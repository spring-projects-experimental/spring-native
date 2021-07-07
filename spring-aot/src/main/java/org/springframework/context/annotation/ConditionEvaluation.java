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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import org.springframework.lang.Nullable;

/**
 * Describe the result of a particular condition evaluation.
 *
 * @author Stephane Nicoll
 * @see ConditionEvaluationStateReport
 */
public final class ConditionEvaluation {

	@Nullable
	private final ConditionDefinition notMatching;

	private final List<ConditionDefinition> matching;

	private final List<ConditionDefinition> filtered;

	private final List<ConditionDefinition> skipped;

	ConditionEvaluation(@Nullable ConditionDefinition notMatching, List<ConditionDefinition> matching,
			List<ConditionDefinition> filtered, List<ConditionDefinition> skipped) {
		this.notMatching = notMatching;
		this.matching = matching;
		this.filtered = filtered;
		this.skipped = skipped;
	}

	/**
	 * Specify if this evaluation lead to the component to be skipped.
	 * @return {@link true} if a condition did not match
	 */
	public boolean shouldSkip() {
		return this.notMatching != null;
	}

	/**
	 * Return the {@link ConditionDefinition} that lead to the component to be skipped, or {@code null}
	 * if no such condition exists in this instance.
	 * @return the condition that did not match, or {@code null}
	 */
	@Nullable
	public ConditionDefinition getNotMatching() {
		return this.notMatching;
	}

	/**
	 * Return the conditions that were executed and that matches.
	 * @return the conditions that matched
	 */
	public List<ConditionDefinition> getMatching() {
		return this.matching;
	}

	/**
	 * Return the conditions that were not executed.
	 * @return the filtered conditions
	 */
	public List<ConditionDefinition> getFiltered() {
		return this.filtered;
	}

	/**
	 * Return the conditions that were skipped as one condition did not match and
	 * further processing stopped
	 * @return the skipped conditions
	 */
	public List<ConditionDefinition> getSkipped() {
		return this.skipped;
	}

	/**
	 * Initialize a {@link Builder} using the specified conditions.
	 * @param conditions the conditions to initialize the builder
	 * @return a builder
	 */
	public static Builder forConditions(List<ConditionDefinition> conditions) {
		return new Builder(conditions);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ConditionEvaluation.class.getSimpleName() + "[", "]")
				.add("notMatching=" + notMatching)
				.add("matching=" + matching)
				.add("filtered=" + filtered)
				.add("skipped=" + skipped)
				.toString();
	}

	public static class Builder {

		private final List<ConditionDefinition> conditions;

		private final List<ConditionDefinition> filtered = new ArrayList<>();

		private final List<ConditionDefinition> matching = new ArrayList<>();

		private Builder(List<ConditionDefinition> conditions) {
			this.conditions = new ArrayList<>(conditions);
		}

		public Builder filtered(ConditionDefinition condition) {
			this.filtered.add(condition);
			return processed(condition);
		}

		public Builder matchAndContinue(ConditionDefinition condition) {
			this.matching.add(condition);
			return processed(condition);
		}

		public ConditionEvaluation didNotMatch(ConditionDefinition condition) {
			processed(condition);
			return new ConditionEvaluation(condition, this.matching,
					this.filtered, new ArrayList<>(this.conditions));
		}

		public ConditionEvaluation match() {
			if (!this.conditions.isEmpty()) {
				throw new IllegalStateException("Conditions have not been evaluated");
			}
			return new ConditionEvaluation(null, this.matching,
					this.filtered, Collections.emptyList());

		}

		private Builder processed(ConditionDefinition condition) {
			if (!this.conditions.remove(condition)) {
				throw new IllegalArgumentException("Condition " + condition + " does not exist");
			}
			return this;
		}
	}

}
