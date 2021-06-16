package org.springframework.context.build.samples.scan;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.build.samples.simple.SimpleComponent;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = SimpleComponent.class)
public class ScanConfiguration {
}
