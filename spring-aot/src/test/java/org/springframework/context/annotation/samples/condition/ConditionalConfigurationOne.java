package org.springframework.context.annotation.samples.condition;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.samples.simple.ConfigurationOne;
import org.springframework.context.annotation.samples.simple.ConfigurationTwo;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("test.one.enabled")
@Import({ ConfigurationOne.class, ConfigurationTwo.class })
public class ConditionalConfigurationOne {


}
