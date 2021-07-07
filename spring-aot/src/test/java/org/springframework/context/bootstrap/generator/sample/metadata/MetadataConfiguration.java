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

package org.springframework.context.bootstrap.generator.sample.metadata;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.context.bootstrap.generator.sample.generic.Repository;

@Configuration
public class MetadataConfiguration {

	@Bean
	@Primary
	public String primaryBean() {
		return "primary";
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public String infrastructureBean() {
		return "infrastructure";
	}

	@Bean
	@Primary
	public Repository<String> primaryGenericBean() {
		return new Repository<>();
	}

	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public Repository<String> infrastructureGenericBean() {
		return new Repository<>();
	}

}
