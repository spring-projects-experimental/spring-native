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
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;

/**
 * A hierarchical structure of a {@link BeanFactory}.
 *
 * @author Stephane Nicoll
 * @see BeanDefinitionOrigin
 */
public final class BeanFactoryStructure {

	private final List<BeanDefinitionOrigin> beanDefinitions;

	private final List<BeanDefinition> other;

	BeanFactoryStructure(List<BeanDefinitionOrigin> beanDefinitions,
			List<BeanDefinition> other) {
		this.beanDefinitions = beanDefinitions;
		this.other = new ArrayList<>(other);
	}

	public void writeReport(PrintWriter writer) {
		writer.println(String.format("%s configuration classes found", this.beanDefinitions.stream().filter((beanDefinition) ->
				beanDefinition.getType().equals(Type.CONFIGURATION)).count()));
		writer.println(String.format("%s components found", this.beanDefinitions.stream().filter((beanDefinition) ->
				beanDefinition.getType().equals(Type.COMPONENT)).count()));
		writer.println(String.format("%s bean definitions that have not been identified", this.other.size()));
		for (BeanDefinition otherBeanDefinition : other) {
			writer.println("\t" + otherBeanDefinition);
		}
	}

}
