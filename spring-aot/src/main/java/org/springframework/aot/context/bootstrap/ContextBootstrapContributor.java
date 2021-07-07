/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.aot.context.bootstrap;

import java.util.List;

import com.squareup.javapoet.JavaFile;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.BootstrapContributor;
import org.springframework.aot.BuildContext;
import org.springframework.aot.SourceFiles;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.BuildTimeBeanDefinitionsRegistrar;
import org.springframework.context.bootstrap.generator.ContextBootstrapGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.classreading.ClassDescriptor;
import org.springframework.core.type.classreading.TypeSystem;
import org.springframework.nativex.AotOptions;
import org.springframework.util.ClassUtils;

/**
 * @author Brian Clozel
 */
public class ContextBootstrapContributor implements BootstrapContributor {

	private static Log logger = LogFactory.getLog(ContextBootstrapContributor.class);

	@Override
	public void contribute(BuildContext context, AotOptions aotOptions) {
		TypeSystem typeSystem = context.getTypeSystem();
		ClassLoader classLoader = typeSystem.getResourceLoader().getClassLoader();

		// TODO: detect correct type of application context
		GenericApplicationContext applicationContext = new GenericApplicationContext();
		applicationContext.setResourceLoader(typeSystem.getResourceLoader());

		// TODO: pre-compute environment from properties?
		applicationContext.setEnvironment(new StandardEnvironment());

		// TODO: auto-detect main class
		ClassDescriptor mainClass = typeSystem.resolveClass(context.getMainClass());
		logger.info("Detected main class: " + mainClass.getCanonicalClassName());
		try {
			applicationContext.registerBean(ClassUtils.forName(mainClass.getCanonicalClassName(), classLoader));
		}
		catch (ClassNotFoundException exc) {
			 throw new IllegalStateException("Could not load main class" + mainClass.getCanonicalClassName(), exc);
		}
		ConfigurableListableBeanFactory beanFactory = new BuildTimeBeanDefinitionsRegistrar(applicationContext).processBeanDefinitions();
		ContextBootstrapGenerator bootstrapGenerator = new ContextBootstrapGenerator(classLoader);
		List<JavaFile> javaFiles = bootstrapGenerator.generateBootstrapClass(beanFactory, "org.springframework.aot");
		javaFiles.forEach(javaFile -> context.addSourceFiles(SourceFiles.fromJavaFile(javaFile)));
	}
}
