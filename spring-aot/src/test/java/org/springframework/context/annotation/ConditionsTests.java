package org.springframework.context.annotation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Test for {@link Conditions}.
 *
 * @author Stephane Nicoll
 */
class ConditionsTests {

	private static final MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

	@Test
	void getMetadata() {
		AnnotatedTypeMetadata metadata = getAnnotatedTypeMetadata(SingleCondition.class);
		Conditions conditions = Conditions.from(metadata);
		assertThat(conditions.getMetadata()).isSameAs(metadata);
	}

	@Test
	void conditionDefinitionsWithNoCondition() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(NoCondition.class));
		assertThat(conditions.getConditionDefinitions()).isEmpty();
	}

	@Test
	void conditionDefinitionsWithSingleCondition() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(SingleCondition.class));
		assertThat(conditions.getConditionDefinitions()).singleElement().satisfies(condition(
				"org.springframework.boot.autoconfigure.condition.OnClassCondition", ConditionalOnClass.class));
	}

	@Test
	void conditionDefinitionsWithTwoConditionsUsingConditional() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(TwoConditionsUsingConditional.class));
		assertThat(conditions.getConditionDefinitions()).hasSize(2);
		assertThat(conditions.getConditionDefinitions().get(0)).satisfies(condition(AlwaysCondition.class, Conditional.class));
		assertThat(conditions.getConditionDefinitions().get(1)).satisfies(condition(NeverCondition.class, Conditional.class));
	}

	@Test
	void conditionDefinitionsWithTwoConditionsUsingDedicatedAnnotations() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(TwoConditionsUsingDedicatedAnnotations.class));
		assertThat(conditions.getConditionDefinitions()).hasSize(2);
		assertThat(conditions.getConditionDefinitions().get(0)).satisfies(condition(
				"org.springframework.boot.autoconfigure.condition.OnClassCondition", ConditionalOnClass.class));
		assertThat(conditions.getConditionDefinitions().get(1)).satisfies(condition(
				"org.springframework.boot.autoconfigure.condition.OnPropertyCondition", ConditionalOnProperty.class));
	}

	@Test
	void getRequiredPhaseOnSimpleComponent() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(SingleCondition.class));
		assertThat(conditions.getRequiredPhase()).isEqualTo(ConfigurationPhase.REGISTER_BEAN);
	}

	@Test
	void getRequiredPhaseOnConfiguration() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(TwoConditionsUsingConditional.class));
		assertThat(conditions.getRequiredPhase()).isEqualTo(ConfigurationPhase.PARSE_CONFIGURATION);
	}


	private Consumer<ConditionDefinition> condition(Class<? extends Condition> implementation, Class<? extends Annotation> rootAnnotation) {
		return condition(implementation.getName(), rootAnnotation);
	}

	private Consumer<ConditionDefinition> condition(String implementationClassName, Class<? extends Annotation> rootAnnotation) {
		return (definition) -> {
			assertThat(definition.getClassName()).isEqualTo(implementationClassName);
			assertThat(definition.getAnnotation().getRoot().getType()).isEqualTo(rootAnnotation);
		};
	}

	private static AnnotatedTypeMetadata getAnnotatedTypeMetadata(Class<?> type) {
		try {
			return metadataReaderFactory.getMetadataReader(type.getName()).getAnnotationMetadata();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read type: " + type, ex);
		}
	}

	static class NoCondition {

	}

	@ConditionalOnClass(String.class)
	static class SingleCondition {

	}

	@Configuration(proxyBeanMethods = false)
	@Conditional({ AlwaysCondition.class, NeverCondition.class })
	static class TwoConditionsUsingConditional {

	}

	@ConditionalOnClass(String.class)
	@ConditionalOnProperty("test")
	static class TwoConditionsUsingDedicatedAnnotations {

	}

	private static class AlwaysCondition implements Condition {
		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return true;
		}
	}

	private static class NeverCondition implements Condition {
		@Override
		public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
			return false;
		}
	}

}
