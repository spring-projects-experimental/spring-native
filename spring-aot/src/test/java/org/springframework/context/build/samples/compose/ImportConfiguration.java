package org.springframework.context.build.samples.compose;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.build.samples.simple.ConfigurationOne;
import org.springframework.context.build.samples.simple.ConfigurationTwo;

@Configuration(proxyBeanMethods = false)
@Import({ ConfigurationOne.class, ConfigurationTwo.class })
public class ImportConfiguration {
}
