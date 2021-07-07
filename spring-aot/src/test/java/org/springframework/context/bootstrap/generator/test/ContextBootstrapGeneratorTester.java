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

package org.springframework.context.bootstrap.generator.test;

import java.beans.Introspector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.squareup.javapoet.JavaFile;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.BuildTimeBeanDefinitionsRegistrar;
import org.springframework.context.bootstrap.generator.ContextBootstrapGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

/**
 * A tester for {@link ContextBootstrapGenerator}.
 *
 * @author Stephane Nicoll
 */
public class ContextBootstrapGeneratorTester {

	private final Path directory;

	private final String packageName;

	private final List<Class<?>> excludeTypes;

	public ContextBootstrapGeneratorTester(Path directory, String packageName, List<Class<?>> excludeTypes) {
		this.directory = directory;
		this.packageName = packageName;
		this.excludeTypes = (!ObjectUtils.isEmpty(excludeTypes)) ? new ArrayList<>(excludeTypes) : new ArrayList<>();
	}

	public ContextBootstrapGeneratorTester(Path directory) {
		this(directory, "com.example", null);
	}

	public ContextBootstrapGeneratorTester withDirectory(Path directory) {
		return new ContextBootstrapGeneratorTester(directory, this.packageName, this.excludeTypes);
	}

	public ContextBootstrapGeneratorTester withPackage(String packageName) {
		return new ContextBootstrapGeneratorTester(this.directory, packageName, this.excludeTypes);
	}

	public ContextBootstrapGeneratorTester withExcludeTypes(Class<?>... excludeTypes) {
		return new ContextBootstrapGeneratorTester(this.directory, this.packageName, Arrays.asList(excludeTypes));
	}

	public ContextBootstrapStructure generate(Class<?>... candidates) {
		GenericApplicationContext context = new GenericApplicationContext();
		for (Class<?> candidate : candidates) {
			context.registerBean(generateShortName(candidate), candidate);
		}
		BuildTimeBeanDefinitionsRegistrar registrar = new BuildTimeBeanDefinitionsRegistrar(context);
		ConfigurableListableBeanFactory beanFactory = registrar.processBeanDefinitions();
		Path srcDirectory = generateSrcDirectory();
		List<JavaFile> javaFiles = new ContextBootstrapGenerator(context.getClassLoader()).generateBootstrapClass(
				beanFactory, this.packageName,
				this.excludeTypes.toArray(new Class<?>[0]));
		writeSources(srcDirectory, javaFiles);
		return new ContextBootstrapStructure(srcDirectory, this.packageName);
	}

	private String generateShortName(Class<?> target) {
		String shortClassName = ClassUtils.getShortName(target);
		return Introspector.decapitalize(shortClassName);
	}

	private Path generateSrcDirectory() {
		try {
			return Files.createTempDirectory(this.directory, "bootstrap-");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to create generate source structure ", ex);
		}
	}

	private void writeSources(Path srcDirectory, List<JavaFile> javaFiles) {
		try {
			for (JavaFile javaFile : javaFiles) {
				javaFile.writeTo(srcDirectory);
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write source code to disk ", ex);
		}
	}

}
