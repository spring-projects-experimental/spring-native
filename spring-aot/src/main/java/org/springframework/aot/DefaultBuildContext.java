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

package org.springframework.aot;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.classreading.TypeSystem;
import org.springframework.nativex.domain.proxies.ProxiesDescriptor;
import org.springframework.nativex.domain.reflect.ReflectionDescriptor;
import org.springframework.nativex.domain.resources.ResourcesDescriptor;
import org.springframework.nativex.domain.serialization.SerializationDescriptor;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Default implementation for the {@link BuildContext}
 */
class DefaultBuildContext implements BuildContext {

	private final TypeSystem typeSystem;

	private final String mainClass;

	private final List<String> classpath;

	private final List<SourceFile> sourceFiles = new ArrayList<>();

	private final List<ResourceFile> resourceFiles = new ArrayList<>();

	private final ReflectionDescriptor reflectionDescriptor = new ReflectionDescriptor();

	private final ProxiesDescriptor proxiesDescriptor = new ProxiesDescriptor();

	private final ResourcesDescriptor resourcesDescriptor = new ResourcesDescriptor();
	
	private final SerializationDescriptor serializationDescriptor = new SerializationDescriptor();

	private final ReflectionDescriptor jniReflectionDescriptor = new ReflectionDescriptor();

	DefaultBuildContext(String mainClass, List<String> classpath) {
		this.mainClass = mainClass;
		this.classpath = classpath;
		this.typeSystem = TypeSystem.getTypeSystem(new DefaultResourceLoader(getBootstrapClassLoader(classpath)));
	}

	DefaultBuildContext(String mainClass, URLClassLoader classLoader) {
		this.mainClass = mainClass;
		this.classpath = Arrays.stream(classLoader.getURLs()).map(url -> url.getFile()).collect(Collectors.toList());
		this.typeSystem = TypeSystem.getTypeSystem(new DefaultResourceLoader(classLoader));
	}

	@Override
	public TypeSystem getTypeSystem() {
		return this.typeSystem;
	}

	@Override
	public List<String> getClasspath() {
		return this.classpath;
	}

	@Override
	public String getMainClass() {
		return this.mainClass;
	}

	@Override
	public void addSourceFiles(SourceFile... sourceFiles) {
		this.sourceFiles.addAll(Arrays.asList(sourceFiles));
	}

	@Override
	public void addResources(ResourceFile... resourceFiles) {
		this.resourceFiles.addAll(Arrays.asList(resourceFiles));
	}

	@Override
	public void describeReflection(Consumer<ReflectionDescriptor> consumer) {
		consumer.accept(this.reflectionDescriptor);
	}

	@Override
	public void describeJNIReflection(Consumer<ReflectionDescriptor> consumer) {
		consumer.accept(this.jniReflectionDescriptor);
	}

	@Override
	public void describeSerialization(Consumer<SerializationDescriptor> consumer) {
		consumer.accept(this.serializationDescriptor);
	}

	@Override
	public void describeProxies(Consumer<ProxiesDescriptor> consumer) {
		consumer.accept(this.proxiesDescriptor);
	}

	@Override
	public void describeResources(Consumer<ResourcesDescriptor> consumer) {
		consumer.accept(this.resourcesDescriptor);
	}

	List<SourceFile> getSourceFiles() {
		return this.sourceFiles;
	}

	List<ResourceFile> getResourceFiles() {
		return this.resourceFiles;
	}

	public ReflectionDescriptor getReflectionDescriptor() {
		return this.reflectionDescriptor;
	}
	
	public SerializationDescriptor getSerializationDescriptor() {
		return this.serializationDescriptor;
	}

	public ReflectionDescriptor getJNIReflectionDescriptor() {
		return this.jniReflectionDescriptor;
	}

	public ProxiesDescriptor getProxiesDescriptor() {
		return this.proxiesDescriptor;
	}

	public ResourcesDescriptor getResourcesDescriptor() {
		return this.resourcesDescriptor;
	}

	private URLClassLoader getBootstrapClassLoader(List<String> classpath) {
		try {
			List<URL> urls = new ArrayList<>();
			List<URI> uris = classpath.stream().map(File::new).map(File::toURI).collect(Collectors.toList());
			for (URI uri : uris) {
				urls.add(uri.toURL());
			}
			ClassLoader parentClassLoader =  null;
			// If we're on JDK9+, we need to use the PlatformClassLoader
			// or we'll miss JDK classes that aren't in the base module.
			if (ClassUtils.hasMethod(Optional.class, "stream", new Class[0])) {
				Method getPlatformClassLoader = ReflectionUtils.findMethod(ClassLoader.class, "getPlatformClassLoader");
				parentClassLoader = (ClassLoader) ReflectionUtils.invokeMethod(getPlatformClassLoader, null);
			}
			return new URLClassLoader(urls.toArray(new URL[0]), parentClassLoader);
		}
		catch (Exception ex) {
			throw new CodeGenerationException("Unable to build classpath", ex);
		}
	}

}
