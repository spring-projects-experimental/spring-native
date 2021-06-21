package org.springframework.context.annotation;

import java.io.IOException;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.context.annotation.samples.condition.ConditionalConfigurationOne;
import org.springframework.context.annotation.samples.simple.ConfigurationOne;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConditionEvaluator}.
 *
 * @author Stephane Nicoll
 */
class ConditionEvaluatorTests {

	private static final MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory();

	private final DefaultListableBeanFactory beanFactory;

	private final MockEnvironment environment;

	private final ConditionEvaluator conditionEvaluator;

	ConditionEvaluatorTests() {
		this.beanFactory = new DefaultListableBeanFactory();
		this.environment = new MockEnvironment();
		this.conditionEvaluator = new ConditionEvaluator(this.beanFactory, this.environment,
				new DefaultResourceLoader());
	}

	@Test
	void evaluateConfigurationClassWithNoConditionOnParseConfiguration() {
		ConfigurationClass configurationClass = asConfigurationClass(ConfigurationOne.class);
		assertThat(this.conditionEvaluator.shouldSkip(configurationClass, ConfigurationPhase.PARSE_CONFIGURATION)).isFalse();
		assertConditionEvaluationState(configurationClass, (state) -> {
			assertThat(state.getConditionEvaluation(ConfigurationPhase.PARSE_CONFIGURATION)).satisfies(hasNoCondition());
			assertThat(state.getConditionEvaluation(ConfigurationPhase.REGISTER_BEAN)).isNull();
		});
	}

	@Test
	void evaluateConfigurationClassWithNoConditionOnRegisterBean() {
		ConfigurationClass configurationClass = asConfigurationClass(ConfigurationOne.class);
		assertThat(this.conditionEvaluator.shouldSkip(configurationClass, ConfigurationPhase.REGISTER_BEAN)).isFalse();
		assertConditionEvaluationState(configurationClass, (state) -> {
			assertThat(state.getConditionEvaluation(ConfigurationPhase.PARSE_CONFIGURATION)).isNull();
			assertThat(state.getConditionEvaluation(ConfigurationPhase.REGISTER_BEAN)).satisfies(hasNoCondition());
		});
	}

	@Test
	void evaluateConfigurationClassWithSingleConditionNotMatching() {
		ConfigurationClass configurationClass = asConfigurationClass(ConditionalConfigurationOne.class);
		assertThat(this.conditionEvaluator.shouldSkip(configurationClass, ConfigurationPhase.PARSE_CONFIGURATION)).isTrue();
		assertConditionEvaluationState(configurationClass, (state) -> {
			assertThat(state.getConditionEvaluation(ConfigurationPhase.PARSE_CONFIGURATION)).satisfies((evaluation) -> {
				assertThat(evaluation.shouldSkip()).isTrue();
				assertThat(evaluation.getNotMatching()).isNotNull();
				assertThat(evaluation.getNotMatching().getAnnotation().getRoot().getType()).isEqualTo(ConditionalOnProperty.class);
				assertThat(evaluation.getMatching()).isEmpty();
				assertThat(evaluation.getFiltered()).isEmpty();
				assertThat(evaluation.getSkipped()).isEmpty();
			});
			assertThat(state.getConditionEvaluation(ConfigurationPhase.REGISTER_BEAN)).isNull();
		});
	}

	@Test
	void evaluateConfigurationClassWithSingleConditionMatching() {
		this.environment.setProperty("test.one.enabled", "true");
		ConfigurationClass configurationClass = asConfigurationClass(ConditionalConfigurationOne.class);
		assertThat(this.conditionEvaluator.shouldSkip(configurationClass, ConfigurationPhase.PARSE_CONFIGURATION)).isFalse();
		assertConditionEvaluationState(configurationClass, (state) -> {
			assertThat(state.getConditionEvaluation(ConfigurationPhase.PARSE_CONFIGURATION)).satisfies((evaluation) -> {
				assertThat(evaluation.shouldSkip()).isFalse();
				assertThat(evaluation.getNotMatching()).isNull();
				assertThat(evaluation.getMatching()).singleElement().satisfies((match) ->
						assertThat(match.getAnnotation().getRoot().getType()).isEqualTo(ConditionalOnProperty.class));
				assertThat(evaluation.getFiltered()).isEmpty();
				assertThat(evaluation.getSkipped()).isEmpty();
			});
			assertThat(state.getConditionEvaluation(ConfigurationPhase.REGISTER_BEAN)).isNull();
		});
	}

	private Consumer<ConditionEvaluation> hasNoCondition() {
		return (evaluation) -> {
			assertThat(evaluation.shouldSkip()).isFalse();
			assertThat(evaluation.getNotMatching()).isNull();
			assertThat(evaluation.getMatching()).isEmpty();
			assertThat(evaluation.getFiltered()).isEmpty();
			assertThat(evaluation.getSkipped()).isEmpty();
		};
	}

	private void assertConditionEvaluationState(ConfigurationClass configurationClass,
			Consumer<ConditionEvaluationState> state) {
		assertConditionEvaluationStateReport((stateReport) -> {
			ConditionEvaluationState stateToAssert = stateReport.getConditionEvaluationState(configurationClass);
			assertThat(stateToAssert).isNotNull();
			state.accept(stateToAssert);
		});
	}

	private void assertConditionEvaluationStateReport(Consumer<ConditionEvaluationStateReport> stateReport) {
		assertThat(this.beanFactory.containsSingleton(ConditionEvaluationStateReport.BEAN_NAME)).isTrue();
		stateReport.accept((ConditionEvaluationStateReport) this.beanFactory.getBean(ConditionEvaluationStateReport.BEAN_NAME));
	}

	static ConfigurationClass asConfigurationClass(Class<?> type) {
		return new ConfigurationClass(getMetadataReader(type.getName()), type.getSimpleName());
	}

	private static MetadataReader getMetadataReader(String type) {
		try {
			return metadataReaderFactory.getMetadataReader(type);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read type: " + type, ex);
		}
	}

}
