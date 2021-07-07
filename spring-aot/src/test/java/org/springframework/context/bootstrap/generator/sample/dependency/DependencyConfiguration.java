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

package org.springframework.context.bootstrap.generator.sample.dependency;

import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.bootstrap.generator.sample.generic.Repository;
import org.springframework.core.env.Environment;

@Configuration
public class DependencyConfiguration {

	@Bean
	public String injectEnvironment(Environment environment) {
		return "environment";
	}

	@Bean
	public String injectContext(ConfigurableApplicationContext context) {
		return "context";
	}

	@Bean
	public String injectBeanFactory(AutowireCapableBeanFactory beanFactory) {
		return "beanFactory";
	}

	@Bean
	public String injectObjectProvider(ObjectProvider<Repository<Integer>> object) {
		return "objectProvider";
	}

	@Bean
	public Integer injectList(List<String> strings) {
		return strings.size();
	}

	@Bean
	public Integer injectSet(Set<String> strings) {
		return strings.size();
	}

}
