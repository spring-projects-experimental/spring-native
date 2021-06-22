package org.springframework.context.annotation;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.lang.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;


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
	void getConditionDefinitionsWithNoCondition() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(NoCondition.class));
		assertThat(conditions.getConditionDefinitions()).isEmpty();
	}

	@Test
	void getConditionDefinitionsWithSingleCondition() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(SingleCondition.class));
		assertThat(conditions.getConditionDefinitions()).singleElement().satisfies(hasRootAnnotation(ConditionalOnClass.class));
	}

	@Test
	void getConditionDefinitionsWithTwoConditionsUsingConditional() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(TwoConditionsUsingConditional.class));
		assertThat(conditions.getConditionDefinitions()).hasSize(2);
		assertThat(conditions.getConditionDefinitions().get(0)).satisfies(hasDefinition(Conditional.class, AlwaysCondition.class));
		assertThat(conditions.getConditionDefinitions().get(1)).satisfies(hasDefinition(Conditional.class, NeverCondition.class));
	}

	@Test
	void getConditionDefinitionsWithTwoConditionsUsingDedicatedAnnotations() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(TwoConditionsUsingDedicatedAnnotations.class));
		assertThat(conditions.getConditionDefinitions()).hasSize(2);
		assertThat(conditions.getConditionDefinitions().get(0)).satisfies(hasRootAnnotation(ConditionalOnClass.class));
		assertThat(conditions.getConditionDefinitions().get(1)).satisfies(hasRootAnnotation(ConditionalOnProperty.class));
	}

	@Test
	void getConditionDefinitionsCanInstantiateCondition() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(TwoConditionsUsingConditional.class));
		assertThat(conditions.getConditionDefinitions()).hasSize(2);
		assertThat(conditions.getConditionDefinitions().get(0).newInstance(null)).isInstanceOf(AlwaysCondition.class);
		assertThat(conditions.getConditionDefinitions().get(1).newInstance(null)).isInstanceOf(NeverCondition.class);
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

	@Test
	void determineAnnotatedTypeIdWithConfigurationClass() {
		Conditions conditions = Conditions.from(getAnnotatedTypeMetadata(SingleCondition.class));
		assertThat(conditions.determineAnnotatedTypeId()).isEqualTo(SingleCondition.class.getName());
	}

	@Test
	void determineAnnotatedTypeIdWithBeanMethod() {
		Conditions conditions = Conditions.from(getMethodMetadata(
				getAnnotatedTypeMetadata(SingleCondition.class), ConditionalOnProperty.class));
		assertThat(conditions.determineAnnotatedTypeId())
				.isEqualTo("org.springframework.context.annotation.ConditionsTests$SingleCondition#test");
	}

	@Test
	void determineAnnotatedTypeIdWithUnsupportedMetadataUsesToString() {
		AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);
		given(metadata.toString()).willReturn("test");
		Conditions conditions = Conditions.from(metadata);
		assertThat(conditions.determineAnnotatedTypeId()).isEqualTo("test");
	}


	private Consumer<ConditionDefinition> hasRootAnnotation(Class<? extends Annotation> rootAnnotation) {
		return (definition) -> assertThat(definition.getAnnotation().getRoot().getType()).isEqualTo(rootAnnotation);
	}

	private Consumer<ConditionDefinition> hasDefinition(Class<? extends Annotation> rootAnnotation,
			Class<? extends Condition> conditionType) {
		return (definition) -> {
			assertThat(definition.getAnnotation().getRoot().getType()).isEqualTo(rootAnnotation);
			assertThat(definition.getConditionType()).isEqualTo(conditionType.getName());
		};
	}

	private static AnnotationMetadata getAnnotatedTypeMetadata(Class<?> type) {
		try {
			return metadataReaderFactory.getMetadataReader(type.getName()).getAnnotationMetadata();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read type: " + type, ex);
		}
	}

	@Nullable
	private static MethodMetadata getMethodMetadata(AnnotationMetadata classMetadata, Class<? extends Annotation> methodAnnotation) {
		Set<MethodMetadata> result = classMetadata.getAnnotatedMethods(methodAnnotation.getName());
		return (result.size() > 0) ? result.iterator().next() : null;
	}

	static class NoCondition {

	}

	@ConditionalOnClass(String.class)
	static class SingleCondition {

		@ConditionalOnProperty("test")
		public String test() {
			return "test";
		}

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
