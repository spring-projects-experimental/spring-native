package org.springframework.context.annotation.samples.simple;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfigurationTwo {

	@Bean
	String beanTwo() {
		return "two";
	}

}
