package org.springframework.context.annotation;

import java.util.StringJoiner;

import org.springframework.beans.BeanUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Describe a {@link Condition} that is defined via {@link Conditional @Conditional}.
 *
 * @author Stephane Nicoll
 */
class ConditionDefinition {

	private final MergedAnnotation<Conditional> annotation;

	private final String conditionType;

	/**
	 * Create a new instance using the annotation and the class name of the condition
	 * @param annotation the annotation that defines this condition
	 * @param conditionType the condition type
	 */
	ConditionDefinition(MergedAnnotation<Conditional> annotation, String conditionType) {
		this.annotation = annotation;
		this.conditionType = conditionType;
	}

	/**
	 * Return the annotation that defines this condition.
	 * @return the annotation
	 */
	public MergedAnnotation<Conditional> getAnnotation() {
		return this.annotation;
	}

	/**
	 * Return the fully qualified name of the {@link Condition} implementation.
	 * @return the class name of the condition
	 */
	public String getConditionType() {
		return this.conditionType;
	}

	/**
	 * Create a new {@link Condition} instance using the specified {@link ClassLoader}.
	 * @param classLoader the classloader to use
	 * @return a condition instance
	 */
	Condition newInstance(@Nullable ClassLoader classLoader) {
		Class<?> conditionClass = ClassUtils.resolveClassName(this.conditionType, classLoader);
		return (Condition) BeanUtils.instantiateClass(conditionClass);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ConditionDefinition.class.getSimpleName() + "[", "]")
				.add("annotation=" + this.annotation)
				.add("className='" + this.conditionType + "'")
				.toString();
	}
}
