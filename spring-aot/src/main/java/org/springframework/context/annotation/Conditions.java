package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Abstractions for {@link Conditions} read on an annotated type.
 *
 * @author Stephane Nicoll
 */
final class Conditions {

	private final AnnotatedTypeMetadata metadata;

	private final List<String> conditionClassNames;

	Conditions(@Nullable AnnotatedTypeMetadata metadata, @Nullable List<String> conditionClassNames) {
		this.metadata = metadata;
		this.conditionClassNames = (conditionClassNames != null)
				? new ArrayList<>(conditionClassNames) : new ArrayList<>();
	}

	List<Condition> instantiate(@Nullable ClassLoader classLoader) {
		List<Condition> conditions = new ArrayList<>();
		conditionClassNames.forEach((conditionClassName) -> {
			Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classLoader);
			conditions.add((Condition) BeanUtils.instantiateClass(conditionClass));
		});
		AnnotationAwareOrderComparator.sort(conditions);
		return conditions;
	}

	AnnotatedTypeMetadata getMetadata() {
		return this.metadata;
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
	 * @param metadata the meta data
	 * @return the conditions
	 */
	static Conditions from(@Nullable AnnotatedTypeMetadata metadata) {
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return new Conditions(metadata, null);
		}
		List<String> conditions = new ArrayList<>();
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			Collections.addAll(conditions, conditionClasses);
		}
		return new Conditions(metadata, conditions);
	}

	@SuppressWarnings("unchecked")
	private static List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

}
