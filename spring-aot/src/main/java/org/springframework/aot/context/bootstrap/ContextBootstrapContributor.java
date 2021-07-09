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
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
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
 * @author Sebastien Deleuze
 */
public class ContextBootstrapContributor implements BootstrapContributor {

	// Copied from Spring Boot WebApplicationType
	private static final String[] SERVLET_INDICATOR_CLASSES = { "javax.servlet.Servlet",
			"org.springframework.web.context.ConfigurableWebApplicationContext" };
	private static final String WEBMVC_INDICATOR_CLASS = "org.springframework.web.servlet.DispatcherServlet";
	private static final String WEBFLUX_INDICATOR_CLASS = "org.springframework.web.reactive.DispatcherHandler";
	private static final String JERSEY_INDICATOR_CLASS = "org.glassfish.jersey.servlet.ServletContainer";

	private static Log logger = LogFactory.getLog(ContextBootstrapContributor.class);

	@Override
	public void contribute(BuildContext context, AotOptions aotOptions) {
		TypeSystem typeSystem = context.getTypeSystem();
		ClassLoader classLoader = typeSystem.getResourceLoader().getClassLoader();

		// TODO: detect correct type of application context
		GenericApplicationContext applicationContext = createApplicationContext(typeSystem);
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

	// TODO Avoid duplication with WebApplicationType and SpringAotApplication.AOT_FACTORY
	private GenericApplicationContext createApplicationContext(TypeSystem typeSystem) {
		if (typeSystem.resolveClass(WEBFLUX_INDICATOR_CLASS) != null && typeSystem.resolveClass(WEBMVC_INDICATOR_CLASS) == null
				&& typeSystem.resolveClass(JERSEY_INDICATOR_CLASS) == null) {
			return ReactiveContextDelegate.createApplicationContext();
		}
		for (String className : SERVLET_INDICATOR_CLASSES) {
			if (typeSystem.resolveClass(className) == null) {
				return new GenericApplicationContext();
			}
		}
		return ServletContextDelegate.createApplicationContext();
	}

	// To avoid NoClassDefFoundError:
	static class ServletContextDelegate {

		public static GenericApplicationContext createApplicationContext() {
			return new ServletWebServerApplicationContext();
		}
	}

	// To avoid NoClassDefFoundError:
	static class ReactiveContextDelegate {

		public static GenericApplicationContext createApplicationContext() {
			return new ReactiveWebServerApplicationContext();
		}
	}
}
