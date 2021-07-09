package org.springframework.context.bootstrap.generator.bean;

import java.lang.reflect.Constructor;
import java.util.function.Consumer;

import com.squareup.javapoet.CodeBlock;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.context.bootstrap.generator.sample.generic.GenericWildcardComponent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConstructorBeanValueWriter}.
 *
 * @author Stephane Nicoll
 */
class ConstructorBeanValueWriterTests {

	@Test
	void writeConstructorWithGenericWildcard() {
		BeanDefinition beanDefinition = BeanDefinitionBuilder.rootBeanDefinition(GenericWildcardComponent.class.getName())
				.getBeanDefinition();
		assertGeneratedCode(beanDefinition, GenericWildcardComponent.class.getDeclaredConstructors()[0], (code) ->
				assertThat(code).containsSubsequence("() -> {",
						"org.springframework.beans.factory.ObjectProvider<org.springframework.context.bootstrap.generator.sample.generic.Repository<?>> repositoryProvider = context.getBeanProvider(org.springframework.core.ResolvableType.forClassWithGenerics(org.springframework.context.bootstrap.generator.sample.generic.Repository.class, java.lang.Object.class));",
						"return new org.springframework.context.bootstrap.generator.sample.generic.GenericWildcardComponent(repositoryProvider.getObject());",
						"}"));
	}

	private void assertGeneratedCode(BeanDefinition beanDefinition, Constructor constructor, Consumer<String> code) {
		CodeBlock.Builder builder = CodeBlock.builder();
		new ConstructorBeanValueWriter(beanDefinition, getClass().getClassLoader(), constructor).writeValueSupplier(builder);
		code.accept(builder.build().toString());
	}

}
