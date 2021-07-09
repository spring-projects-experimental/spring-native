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

package org.springframework.context.bootstrap.generator;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.context.bootstrap.generator.sample.SimpleConfiguration;
import org.springframework.context.bootstrap.generator.sample.autoconfigure.AutoConfigurationPackagesConfiguration;
import org.springframework.context.bootstrap.generator.sample.dependency.DependencyConfiguration;
import org.springframework.context.bootstrap.generator.sample.exception.ExceptionConfiguration;
import org.springframework.context.bootstrap.generator.sample.exception.ExceptionConstructorConfiguration;
import org.springframework.context.bootstrap.generator.sample.generic.GenericConfiguration;
import org.springframework.context.bootstrap.generator.sample.generic.GenericObjectProviderConfiguration;
import org.springframework.context.bootstrap.generator.sample.generic.Repository;
import org.springframework.context.bootstrap.generator.sample.generic.RepositoryHolder;
import org.springframework.context.bootstrap.generator.sample.infrastructure.ArgumentValueRegistrarConfiguration;
import org.springframework.context.bootstrap.generator.sample.metadata.MetadataConfiguration;
import org.springframework.context.bootstrap.generator.sample.visibility.ProtectedConfigurationImport;
import org.springframework.context.bootstrap.generator.sample.visibility.ProtectedConstructorParameterConfiguration;
import org.springframework.context.bootstrap.generator.sample.visibility.ProtectedMethodParameterConfiguration;
import org.springframework.context.bootstrap.generator.sample.visibility.PublicInnerClassConfigurationImport;
import org.springframework.context.bootstrap.generator.sample.visibility.PublicOuterClassConfiguration;
import org.springframework.context.bootstrap.generator.test.ContextBootstrapGeneratorTester;
import org.springframework.context.bootstrap.generator.test.ContextBootstrapStructure;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ContextBootstrapGenerator}.
 *
 * @author Stephane Nicoll
 */
class ContextBootstrapGeneratorTests {

	private ContextBootstrapGeneratorTester generatorTester;

	@BeforeEach
	void setup(@TempDir Path directory) {
		this.generatorTester = new ContextBootstrapGeneratorTester(directory);
	}

	@Test
	void bootstrapClassGeneratesStructure() {
		ContextBootstrapStructure structure = this.generatorTester.generate();
		assertThat(structure).contextBootstrap().lines().containsSubsequence("public class ContextBootstrap {",
				"  public void bootstrap(GenericApplicationContext context) {", "  }", "}");
		assertThat(structure).contextBootstrap().contains("import " + GenericApplicationContext.class.getName() + ";");
	}

	@Test
	void bootstrapClassWithBeanMethodAndNoParameter() {
		ContextBootstrapStructure structure = this.generatorTester.generate(SimpleConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"simpleConfiguration\", SimpleConfiguration.class, SimpleConfiguration::new);",
				"context.registerBean(\"stringBean\", String.class, () -> context.getBean(SimpleConfiguration.class).stringBean());",
				"context.registerBean(\"integerBean\", Integer.class, () -> context.getBean(SimpleConfiguration.class).integerBean());");
	}

	@Test
	void bootstrapClassWithAutoConfiguration() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ProjectInfoAutoConfiguration.class);
		// NOTE: application context runner does not register auto-config as FQNs
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"projectInfoAutoConfiguration\", ProjectInfoAutoConfiguration.class, () -> new ProjectInfoAutoConfiguration(context.getBean(ProjectInfoProperties.class)));",
				"context.registerBean(\"spring.info-org.springframework.boot.autoconfigure.info.ProjectInfoProperties\", ProjectInfoProperties.class, ProjectInfoProperties::new);");
	}

	@Test
	void bootstrapClassWithAutoConfigurationPackages() {
		ContextBootstrapStructure structure = this.generatorTester.generate(AutoConfigurationPackagesConfiguration.class);
		assertThat(structure).contextBootstrap()
				.contains("org.springframework.boot.autoconfigure.ContextBootstrap.registerBasePackages(context)");
		assertThat(structure).source("org.springframework.boot.autoconfigure", "ContextBootstrap")
				.contains("context.registerBean(\"org.springframework.boot.autoconfigure.AutoConfigurationPackages\", "
						+ "AutoConfigurationPackages.BasePackages.class, "
						+ "() -> new AutoConfigurationPackages.BasePackages(new String[] { \"org.springframework.context.bootstrap.generator.sample.autoconfigure\" }), "
						+ "BeanDefinitionCustomizers.role(2));");
	}

	@Test
	void bootstrapClassWithConfigurationProperties() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ConfigurationPropertiesAutoConfiguration.class);
		assertThat(structure).contextBootstrap().contains("context.registerBean("
				+ "\"org.springframework.boot.context.properties.EnableConfigurationPropertiesRegistrar.methodValidationExcludeFilter\", "
				+ "MethodValidationExcludeFilter.class, "
				+ "() -> MethodValidationExcludeFilter.byAnnotation(ConfigurationProperties.class), BeanDefinitionCustomizers.role(2));");
	}

	@Test
	void bootstrapClassWithDependencyOnEnvironment() {
		ContextBootstrapStructure structure = this.generatorTester.generate(DependencyConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"injectEnvironment\", String.class, () -> context.getBean(DependencyConfiguration.class).injectEnvironment(context.getEnvironment()));");
	}

	@Test
	void bootstrapClassWithDependencyOnApplicationContext() {
		ContextBootstrapStructure structure = this.generatorTester.generate(DependencyConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"injectContext\", String.class, () -> context.getBean(DependencyConfiguration.class).injectContext(context));");
	}

	@Test
	void bootstrapClassWithDependencyOnBeanFactory() {
		ContextBootstrapStructure structure = this.generatorTester.generate(DependencyConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"injectBeanFactory\", String.class, () -> context.getBean(DependencyConfiguration.class).injectBeanFactory(context.getBeanFactory()));");
	}

	@Test
	void bootstrapClassWithDependencyOnObjectProvider() {
		ContextBootstrapStructure structure = this.generatorTester.generate(DependencyConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"injectObjectProvider\", String.class, () -> context.getBean(DependencyConfiguration.class).injectObjectProvider(context.getBeanProvider(ResolvableType.forClassWithGenerics(Repository.class, Integer.class))));");
	}

	@Test
	void bootstrapClassWithDependencyOnList() {
		ContextBootstrapStructure structure = this.generatorTester.generate(DependencyConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"injectList\", Integer.class, () -> context.getBean(DependencyConfiguration.class).injectList(context.getBeanProvider(String.class).orderedStream().collect(Collectors.toList())));");
	}

	@Test
	void bootstrapClassWithPrimaryBean() {
		ContextBootstrapStructure structure = this.generatorTester.generate(MetadataConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"primaryBean\", String.class, () -> context.getBean(MetadataConfiguration.class).primaryBean(), BeanDefinitionCustomizers.primary());");
	}

	@Test
	void bootstrapClassWithRoleInfrastructureBean() {
		ContextBootstrapStructure structure = this.generatorTester.generate(MetadataConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"infrastructureBean\", String.class, () -> context.getBean(MetadataConfiguration.class).infrastructureBean(), BeanDefinitionCustomizers.role(2));");
	}

	@Test
	void bootstrapClassWithPackageProtectedConfiguration() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ProtectedConfigurationImport.class);
		assertThat(structure)
				.source("org.springframework.context.bootstrap.generator.sample.visibility", "ContextBootstrap").lines()
				.containsSequence("  public static void registerAnotherStringBean(GenericApplicationContext context) {",
						"    context.registerBean(\"anotherStringBean\", String.class, () -> context.getBean(ProtectedConfiguration.class).anotherStringBean());",
						"  }");
		assertThat(structure).contextBootstrap().contains(
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerProtectedConfiguration(context);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerAnotherStringBean(context);");
	}

	@Test
	void bootstrapClassWithPublicInnerOnPackageProtectedOuterConfiguration() {
		ContextBootstrapStructure structure = this.generatorTester.generate(PublicInnerClassConfigurationImport.class);
		assertThat(structure)
				.source("org.springframework.context.bootstrap.generator.sample.visibility", "ContextBootstrap").lines()
				.containsSequence("  public static void registerInnerBean(GenericApplicationContext context) {",
						"    context.registerBean(\"innerBean\", String.class, () -> context.getBean(PublicInnerClassConfiguration.InnerConfiguration.class).innerBean());",
						"  }");
		assertThat(structure).contextBootstrap().contains(
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerPublicInnerClassConfiguration(context);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerInnerConfiguration(context);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerInnerBean(context);");
	}

	@Test
	void bootstrapClassWithPackageProtectedInnerConfiguration() {
		ContextBootstrapStructure structure = this.generatorTester.generate(PublicOuterClassConfiguration.class);
		assertThat(structure)
				.source("org.springframework.context.bootstrap.generator.sample.visibility", "ContextBootstrap").lines()
				.containsSequence("  public static void registerAnotherInnerBean(GenericApplicationContext context) {",
						"    context.registerBean(\"anotherInnerBean\", String.class, () -> context.getBean(PublicOuterClassConfiguration.ProtectedInnerConfiguration.class).anotherInnerBean());",
						"  }");
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"publicOuterClassConfiguration\", PublicOuterClassConfiguration.class, PublicOuterClassConfiguration::new);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerProtectedInnerConfiguration(context);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerAnotherInnerBean(context);");
	}

	@Test
	void bootstrapClassWithProtectedConstructorParameter() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ProtectedConstructorParameterConfiguration.class);
		assertThat(structure)
				.source("org.springframework.context.bootstrap.generator.sample.visibility", "ContextBootstrap").lines()
				.containsSequence(
						"  public static void registerProtectedParameter(GenericApplicationContext context) {",
						"    context.registerBean(\"org.springframework.context.bootstrap.generator.sample.visibility.ProtectedParameter\", ProtectedParameter.class, () -> new ProtectedParameter(context.getBean(ProtectedType.class)));",
						"  }");
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"protectedConstructorParameterConfiguration\", ProtectedConstructorParameterConfiguration.class, ProtectedConstructorParameterConfiguration::new);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerProtectedParameter(context);");
	}

	@Test
	void bootstrapClassWithProtectedMethodParameter() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ProtectedMethodParameterConfiguration.class);
		assertThat(structure)
				.source("org.springframework.context.bootstrap.generator.sample.visibility", "ContextBootstrap").lines()
				.containsSequence(
						"  public static void registerProtectedParameter(GenericApplicationContext context) {",
						"    context.registerBean(\"protectedParameter\", ProtectedParameter.class, () -> context.getBean(ProtectedMethodParameterConfiguration.class).protectedParameter(context.getBean(ProtectedType.class)));",
						"  }");
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"protectedMethodParameterConfiguration\", ProtectedMethodParameterConfiguration.class, ProtectedMethodParameterConfiguration::new);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerProtectedParameter(context);");
	}

	@Test
	void bootstrapClassWithProtectedMethodGenericParameter() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ProtectedMethodParameterConfiguration.class);
		assertThat(structure)
				.source("org.springframework.context.bootstrap.generator.sample.visibility", "ContextBootstrap").lines()
				.containsSequence(
						"  public static void registerProtectedGenericParameter(GenericApplicationContext context) {",
						"    context.registerBean(\"protectedGenericParameter\", ProtectedParameter.class, () -> context.getBean(ProtectedMethodParameterConfiguration.class).protectedGenericParameter(context.getBeanProvider(ProtectedType.class)));",
						"  }");
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"protectedMethodParameterConfiguration\", ProtectedMethodParameterConfiguration.class, ProtectedMethodParameterConfiguration::new);",
				"org.springframework.context.bootstrap.generator.sample.visibility.ContextBootstrap.registerProtectedGenericParameter(context);");
	}

	@Test
	void bootstrapClassWithSimpleGeneric() {
		ContextBootstrapStructure structure = this.generatorTester.generate(GenericConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"RootBeanDefinition stringRepositoryBeanDef = new RootBeanDefinition();",
				"stringRepositoryBeanDef.setTargetType(ResolvableType.forClassWithGenerics(Repository.class, String.class));",
				"stringRepositoryBeanDef.setInstanceSupplier(() -> context.getBean(GenericConfiguration.class).stringRepository());",
				"context.registerBeanDefinition(\"stringRepository\", stringRepositoryBeanDef);");
	}

	@Test
	void bootstrapClassWithMultipleGenerics() {
		ContextBootstrapStructure structure = this.generatorTester.generate(GenericConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"RootBeanDefinition stringRepositoryHolderBeanDef = new RootBeanDefinition();",
				"stringRepositoryHolderBeanDef.setTargetType(ResolvableType.forClassWithGenerics(RepositoryHolder.class, ResolvableType.forClass(String.class), ResolvableType.forClassWithGenerics(Repository.class, String.class)));",
				"stringRepositoryHolderBeanDef.setInstanceSupplier(() -> {",
				"ObjectProvider<Repository<String>> repoProvider = context.getBeanProvider(ResolvableType.forClassWithGenerics(Repository.class, String.class));",
				"return context.getBean(GenericConfiguration.class).stringRepositoryHolder(repoProvider.getObject());",
				" });",
				"context.registerBeanDefinition(\"stringRepositoryHolder\", stringRepositoryHolderBeanDef);");
	}

	@Test
	void bootstrapClassWithPrimaryGenericBean() {
		ContextBootstrapStructure structure = this.generatorTester.generate(MetadataConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"RootBeanDefinition primaryGenericBeanBeanDef = new RootBeanDefinition();",
				"primaryGenericBeanBeanDef.setTargetType(ResolvableType.forClassWithGenerics(Repository.class, String.class));",
				"primaryGenericBeanBeanDef.setInstanceSupplier(() -> context.getBean(MetadataConfiguration.class).primaryGenericBean());",
				"BeanDefinitionCustomizers.primary().customize(primaryGenericBeanBeanDef);",
				"context.registerBeanDefinition(\"primaryGenericBean\", primaryGenericBeanBeanDef);");
	}

	@Test
	void bootstrapClassWithRoleInfrastructureGenericBean() {
		ContextBootstrapStructure structure = this.generatorTester.generate(MetadataConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"RootBeanDefinition infrastructureGenericBeanBeanDef = new RootBeanDefinition();",
				"infrastructureGenericBeanBeanDef.setTargetType(ResolvableType.forClassWithGenerics(Repository.class, String.class));",
				"infrastructureGenericBeanBeanDef.setInstanceSupplier(() -> context.getBean(MetadataConfiguration.class).infrastructureGenericBean());",
				"BeanDefinitionCustomizers.role(2).customize(infrastructureGenericBeanBeanDef);",
				"context.registerBeanDefinition(\"infrastructureGenericBean\", infrastructureGenericBeanBeanDef);");
	}

	@Test
	void bootstrapClassWithObjectProviderTargetGeneric() {
		ContextBootstrapStructure structure = this.generatorTester.generate(
				GenericConfiguration.class, GenericObjectProviderConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"repositoryId\", String.class, () -> context.getBean(GenericObjectProviderConfiguration.class).repositoryId("
						+ "context.getBeanProvider(ResolvableType.forClassWithGenerics(RepositoryHolder.class, ResolvableType.forClass(String.class), ResolvableType.forClassWithGenerics(Repository.class, String.class)))));");
	}

	@Test
	@Disabled("Need to resolve constructor based on argument values")
	void bootstrapClassWithArgumentValue() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ArgumentValueRegistrarConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"argumentValueString\", String.class, () -> new String(new char[] { 'a', ' ', 't', 'e', 's', 't' }, 2, 4));");
	}

	@Test
	void bootstrapClassWithCheckedExceptionOnMethodWrapsException() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ExceptionConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"checkedException\", String.class, ExceptionHandler.wrapException(() -> context.getBean(ExceptionConfiguration.class).checkedException()));");
	}

	@Test
	void bootstrapClassWithCheckedExceptionOnConstructorWrapsException() {
		ContextBootstrapStructure structure = this.generatorTester.generate(ExceptionConstructorConfiguration.class);
		assertThat(structure).contextBootstrap().contains(
				"context.registerBean(\"exceptionConstructorConfiguration\", ExceptionConstructorConfiguration.class, ExceptionHandler.wrapException(ExceptionConstructorConfiguration::new));");
	}

	@Test
	void bootstrapClassWithExcludeDoesNotRegisterExcludedType() {
		ContextBootstrapStructure structure = this.generatorTester.withExcludeTypes(RepositoryHolder.class)
				.generate(Repository.class, RepositoryHolder.class);
		assertThat(structure).contextBootstrap()
				.contains("context.registerBeanDefinition(\"repository\", repositoryBeanDef);")
				.doesNotContain("RepositoryHolder");
	}

}
