package org.springframework.context.bootstrap.infrastructure;

import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public class AotApplicationContextFactory implements ApplicationContextFactory {

	@Override
	public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
		try {
			switch (webApplicationType) {
				case SERVLET:
					return new ServletWebServerApplicationContext();
				case REACTIVE:
					return new ReactiveWebServerApplicationContext();
				default:
					return new GenericApplicationContext();
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable create an AOT ApplicationContext instance, "
					+ "you may need a custom ApplicationContextFactory", ex);
		}
	}
}
