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

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * Strategy interface to exclude infrastructure beans that should not be included when the
 * context is initialized with a bootstrap class.
 *
 * @author Stephane Nicoll
 */
public interface BeanDefinitionSelector {

	/**
	 * Determine if the specified bean should be added to the generated bootstrap class.
	 * @param beanName the bean name to consider
	 * @param beanDefinition the bean definition to consider
	 * @return {@code true} if it should be selected
	 */
	Boolean select(String beanName, BeanDefinition beanDefinition);

}
