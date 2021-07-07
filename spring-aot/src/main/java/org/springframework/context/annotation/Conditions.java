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
import java.util.List;

import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;

/**
 * Abstractions for {@link Conditions} read on an annotated type.
 *
 * @author Stephane Nicoll
 */
final class Conditions {

	private final AnnotatedTypeMetadata metadata;

	private final List<ConditionDefinition> conditionDefinitions;

	private Conditions(@Nullable AnnotatedTypeMetadata metadata, @Nullable List<ConditionDefinition> conditionDefinitions) {
		this.metadata = metadata;
		this.conditionDefinitions = (conditionDefinitions != null)
				? new ArrayList<>(conditionDefinitions) : new ArrayList<>();
	}

	AnnotatedTypeMetadata getMetadata() {
		return this.metadata;
	}

	List<ConditionDefinition> getConditionDefinitions() {
		return this.conditionDefinitions;
	}

	ConfigurationPhase getRequiredPhase() {
		if (this.metadata instanceof AnnotationMetadata &&
				ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
			return ConfigurationPhase.PARSE_CONFIGURATION;
		}
		return ConfigurationPhase.REGISTER_BEAN;
	}

	String determineAnnotatedTypeId() {
		if (this.metadata instanceof ClassMetadata) {
			return ((ClassMetadata) this.metadata).getClassName();
		}
		if (this.metadata instanceof MethodMetadata) {
			MethodMetadata methodMetadata = (MethodMetadata) this.metadata;
			return String.format("%s#%s", methodMetadata.getDeclaringClassName(), methodMetadata.getMethodName());
		}
		return this.metadata.toString();
	}

	/**
	 * Create an instance based on the specified {@link AnnotatedTypeMetadata metadata}.
	 * @param metadata the metadata
	 * @return the conditions
	 */
	static Conditions from(@Nullable AnnotatedTypeMetadata metadata) {
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return new Conditions(metadata, null);
		}
		return new Conditions(metadata, getConditionClasses(metadata));
	}

	private static List<ConditionDefinition> getConditionClasses(AnnotatedTypeMetadata metadata) {
		List<ConditionDefinition> definitions = new ArrayList<>();
		metadata.getAnnotations().stream(Conditional.class).forEach((annotation) -> {
			for (String conditionType : annotation.getStringArray("value")) {
				definitions.add(new ConditionDefinition(annotation, conditionType));
			}
		});
		return definitions;
	}

}
