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
	private final Condition notMatching;

	private final List<Condition> matching;

	private final List<Condition> filtered;

	private final List<Condition> skipped;

	ConditionEvaluation(@Nullable Condition notMatching, List<Condition> matching,
			List<Condition> filtered, List<Condition> skipped) {
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
	 * Return the {@link Condition} that lead to the component to be skipped, or {@code null}
	 * if no such condition exists in this instance.
	 * @return the condition that did not match, or {@code null}
	 */
	@Nullable
	public Condition getNotMatching() {
		return this.notMatching;
	}

	/**
	 * Return the conditions that were executed and that matches.
	 * @return the conditions that matched
	 */
	public List<Condition> getMatching() {
		return this.matching;
	}

	/**
	 * Return the conditions that were not executed.
	 * @return the filtered conditions
	 */
	public List<Condition> getFiltered() {
		return this.filtered;
	}

	/**
	 * Return the conditions that were skipped as one condition did not match and
	 * further processing stopped
	 * @return the skipped conditions
	 */
	public List<Condition> getSkipped() {
		return this.skipped;
	}

	/**
	 * Initialize a {@link Builder} using the specified conditions.
	 * @param conditions the conditions to initialize the builder
	 * @return a builder
	 */
	public static Builder forConditions(List<Condition> conditions) {
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

		private final List<Condition> conditions;

		private final List<Condition> filtered = new ArrayList<>();

		private final List<Condition> matching = new ArrayList<>();

		private Builder(List<Condition> conditions) {
			this.conditions = new ArrayList<>(conditions);
		}

		public Builder notMatchingPhase(Condition condition) {
			return processed(condition);
		}

		public Builder filtered(Condition condition) {
			this.filtered.add(condition);
			return processed(condition);
		}

		public Builder matchAndContinue(Condition condition) {
			this.matching.add(condition);
			return processed(condition);
		}

		public ConditionEvaluation didNotMatch(Condition condition) {
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

		private Builder processed(Condition condition) {
			if (!this.conditions.remove(condition)) {
				throw new IllegalArgumentException("Condition " + condition + " does not exist");
			}
			return this;
		}
	}

}
