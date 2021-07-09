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

package org.springframework.aot.factories;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aot.BootstrapContributor;
import org.springframework.aot.BuildContext;
import org.springframework.aot.CodeGenerationException;
import org.springframework.aot.SourceFiles;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.classreading.TypeSystem;
import org.springframework.nativex.AotOptions;
import org.springframework.nativex.support.Mode;
import org.springframework.nativex.type.SpringFactoriesProcessor;
import org.springframework.util.StringUtils;

/**
 * Contribute source code for registering {@code spring.factories} at build time.
 * Currently this is supported by a substitution on {@link SpringFactoriesLoader}.
 *
 * @author Brian Clozel
 */
public class SpringFactoriesContributor implements BootstrapContributor {

	private static final Log logger = LogFactory.getLog(SpringFactoriesContributor.class);
	
	private List<SpringFactoriesProcessor> springFactoriesProcessors;

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	@Override
	public void contribute(BuildContext context, AotOptions aotOptions) {
		try {
			Set<SpringFactory> springFactories = loadSpringFactories(context.getTypeSystem());
			FactoriesCodeContributors contributors = new FactoriesCodeContributors(aotOptions);
			CodeGenerator codeGenerator = contributors.createCodeGenerator(springFactories, context, aotOptions);

			context.addSourceFiles(SourceFiles.fromJavaFile(codeGenerator.generateStaticSpringFactories()));
			codeGenerator.generateStaticFactoryClasses().forEach(javaFile -> {
				context.addSourceFiles(SourceFiles.fromJavaFile(javaFile));
			});
			ClassPathResource factoriesLoader = new ClassPathResource("SpringFactoriesLoader.java", getClass());
			context.addSourceFiles(SourceFiles.fromStaticFile("org.springframework.core.io.support",
					"SpringFactoriesLoader", factoriesLoader.getInputStream()));
		}
		catch (Exception exc) {
			throw new CodeGenerationException("Could not generate spring.factories source code", exc);
		}
	}

	Set<SpringFactory> loadSpringFactories(TypeSystem typeSystem) throws IOException {
		Set<SpringFactory> factories = new LinkedHashSet<>();
		Enumeration<URL> factoriesLocations = typeSystem.getResourceLoader()
				.getClassLoader().getResources(SpringFactoriesLoader.FACTORIES_RESOURCE_LOCATION);
		while (factoriesLocations.hasMoreElements()) {
			URL url = factoriesLocations.nextElement();
			UrlResource resource = new UrlResource(url);
			Properties properties = PropertiesLoaderUtils.loadProperties(resource);
			properties = filterProperties(properties);
			for (Map.Entry<?, ?> entry : properties.entrySet()) {
				String factoryTypeName = ((String) entry.getKey()).trim();

				// Filter out build time entries
				// TODO To be refactored to avoid harcoding that
				if (factoryTypeName.startsWith("org.springframework.context.bootstrap") ||
								factoryTypeName.startsWith("org.springframework.context.origin") ||
								factoryTypeName.startsWith("org.springframework.boot.autoconfigure")) {
					logger.debug("Skip build time factory Type:" + factoryTypeName);
					continue;
				}

				logger.debug("Loading factory Type:" + factoryTypeName);
				String[] factoryNames = StringUtils.commaDelimitedListToStringArray((String) entry.getValue());

				// TODO: sorting required here
				// AnnotationAwareOrderComparator.sort();

				for (String factoryName : factoryNames) {
					logger.debug("Loading factory Impl:" + factoryName);
					SpringFactory springFactory = SpringFactory.resolve(factoryTypeName, factoryName, typeSystem);
					if (springFactory != null) {
						factories.add(springFactory);
					}
					else {
						logger.debug("Could not load factory: " + factoryTypeName + " " + factoryName);
					}
				}
			}
		}
		return factories;
	}
	
	private List<SpringFactoriesProcessor> getSpringFactoriesProcessors() {
		if (springFactoriesProcessors == null) {
			springFactoriesProcessors = new ArrayList<>();
			ServiceLoader<SpringFactoriesProcessor> sfps = ServiceLoader.load(SpringFactoriesProcessor.class);
			for (SpringFactoriesProcessor springFactoryProcessor: sfps) {
				springFactoriesProcessors.add(springFactoryProcessor);
			}
		}
		return springFactoriesProcessors;
	}
	
	private Properties filterProperties(Properties properties) {
		List<SpringFactoriesProcessor> springFactoriesProcessors = getSpringFactoriesProcessors();
		boolean modified = false;
		Properties filteredProperties = new Properties();
		for (Map.Entry<Object, Object> factoriesEntry : properties.entrySet()) {
			String key = (String) factoriesEntry.getKey();
			String valueString = (String) factoriesEntry.getValue();
			List<String> values = new ArrayList<>();
			for (String value : valueString.split(",")) {
				values.add(value);
			}
			for (SpringFactoriesProcessor springFactoriesProcessor : springFactoriesProcessors) {
				int len = values.size();
				if (springFactoriesProcessor.filter(key, values)) {
					logger.debug("Spring factory filtered by "+springFactoriesProcessor.getClass().getName()+" removing "+(len-values.size())+" entries");
					modified = true;
				}
			}
			if (modified) {
				filteredProperties.put(key, String.join(",", values));
			} else {
				filteredProperties.put(key, valueString);
			}
		}
		return filteredProperties;
	}
}

