package org.springframework.aot;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.logging.ConditionEvaluationReportLoggingListener;
import org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.NativeDetector;
import org.springframework.core.io.ResourceLoader;

/**
 * An extension of {@link SpringApplication} designed to take advantage of AOT pre-processing.
 *
 * @author Sebastien Deleuze
 */
public class SpringAotApplication extends SpringApplication {

	private static final Log logger = LogFactory.getLog(SpringAotApplication.class);

	private static final boolean springAot = "true".equals(System.getProperty("springAot"))
			|| NativeDetector.inNativeImage();

	ApplicationContextFactory AOT_FACTORY = (webApplicationType) -> {
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
	};

	// TODO support multiple sources (not done yet because casting errors with varargs)
	public SpringAotApplication(Class<?> primarySource) {
		super((ResourceLoader) null, springAot ? Object.class : primarySource);
		if (springAot) {
			logger.info("AOT mode enabled");
			setApplicationContextFactory(AOT_FACTORY);
			setInitializers(Arrays.asList(getBootstrapper(), new ConditionEvaluationReportLoggingListener()));
		}
		else {
			logger.info("AOT mode disabled");
		}
	}

	private ApplicationContextInitializer<?> getBootstrapper() {
		try {
			return (ApplicationContextInitializer<?>) Class.forName("org.springframework.aot.ContextBootstrap").getDeclaredConstructor().newInstance();
		}
		catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
		if (springAot) {
			throw new UnsupportedOperationException("Sources can't be set.");
		}
		else {
			super.addPrimarySources(additionalPrimarySources);
		}
	}

	@Override
	public void setSources(Set<String> sources) {
		if (springAot) {
			throw new UnsupportedOperationException("Sources can't be set.");
		}
		else {
			super.setSources(sources);
		}
	}

	@Override
	protected void load(ApplicationContext context, Object[] sources) {
		if (!springAot) {
			super.load(context, sources);
		}
	}

	public static ConfigurableApplicationContext run(Class<?> primarySource, String[] args) {
		return new SpringAotApplication(primarySource).run(args);
	}

	public static void main(String[] args) throws Exception {
		SpringAotApplication.run(new Class<?>[0], args);
	}

}
