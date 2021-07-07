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
