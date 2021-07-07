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

import java.util.List;
import java.util.function.Consumer;

import org.springframework.core.type.classreading.TypeSystem;
import org.springframework.nativex.domain.proxies.ProxiesDescriptor;
import org.springframework.nativex.domain.reflect.ReflectionDescriptor;
import org.springframework.nativex.domain.resources.ResourcesDescriptor;
import org.springframework.nativex.domain.serialization.SerializationDescriptor;

/**
 * Provide build context information.
 *
 * @author Brian Clozel
 */
public interface BuildContext {


	@Deprecated // for org.springframework.nativex.type.TypeSystem
	List<String> getClasspath();
	
	/**
	 * @return The {@link TypeSystem} based on the "compile+runtime" application classpath.
	 */
	TypeSystem getTypeSystem();

	/**
	 * @return the main class of the application to build.
	 */
	String getMainClass();

	/**
	 * Contribute source files to the application.
	 * <p>This additional source code will be compiled and packaged with the application by a build plugin.
	 * @param sourceFile the source files to add
	 */
	void addSourceFiles(SourceFile... sourceFile);

	/**
	 * Contribute resource files to the application.
	 * <p>This additional packaged with the application by a build plugin.
	 * @param resourceFile the resource files to add
	 */
	void addResources(ResourceFile... resourceFile);

	/**
	 * Contribute reflection information to the application.
	 * @param consumer The reflection descriptor consumer
	 */
	void describeReflection(Consumer<ReflectionDescriptor> consumer);

	/**
	 * Contribute reflection information to the application, this is for types reflectively accessible from JNI.
	 * @param consumer The reflection descriptor consumer
	 */
	void describeJNIReflection(Consumer<ReflectionDescriptor> consumer);

	/**
	 * Contribute proxies information to the application.
	 * @param consumer The proxies descriptor consumer
	 */
	void describeProxies(Consumer<ProxiesDescriptor> consumer);

	/**
	 * Contribute serialization type information to the application.
	 * @param consumer The serialization descriptor consumer
	 */
	void describeSerialization(Consumer<SerializationDescriptor> consumer);

	/**
	 * Contribute resources information to the application.
	 * @param consumer The resources descriptor consumer
	 */
	void describeResources(Consumer<ResourcesDescriptor> consumer);

}
