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

package org.springframework.context.origin;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;

/**
 * A hierarchical structure of a {@link BeanFactory}.
 *
 * @author Stephane Nicoll
 * @see BeanDefinitionDescriptor
 */
public final class BeanFactoryStructure {

	private final Map<String, BeanDefinitionDescriptor> descriptors;

	BeanFactoryStructure(Map<String, BeanDefinitionDescriptor> descriptors) {
		this.descriptors = new LinkedHashMap<>(descriptors);
	}

	public void writeReport(PrintWriter writer) {
		writer.println(String.format("%s configuration classes found", this.descriptors.values().stream().filter((descriptor) ->
				descriptor.getType().equals(Type.CONFIGURATION)).count()));
		writer.println(String.format("%s components found", this.descriptors.values().stream().filter((descriptor) ->
				descriptor.getType().equals(Type.COMPONENT)).count()));
		writer.println(String.format("%s infrastructure found", this.descriptors.values().stream().filter((descriptor) ->
				descriptor.getType().equals(Type.INFRASTRUCTURE)).count()));
		List<BeanDefinitionDescriptor> others = this.descriptors.values().stream().filter((descriptor) ->
				descriptor.getType().equals(Type.UNKNOWN)).collect(Collectors.toList());
		writer.println(String.format("%s bean definitions that have not been identified", others.size()));
		for (BeanDefinitionDescriptor descriptor : others) {
			writer.println("\t" + descriptor.getBeanDefinition());
		}
	}

}
