package org.springframework.context.origin;

import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.config.BeanDefinition;

/**
 * Track the origin of a {@link BeanDefinition}.
 *
 * @author Stephane Nicoll
 */
public final class BeanDefinitionOrigin {

	private final BeanDefinition beanDefinition;

	private final Type type;

	private final Set<BeanDefinition> origins;

	public BeanDefinitionOrigin(BeanDefinition beanDefinition, Type type, Set<BeanDefinition> origins) {
		this.beanDefinition = beanDefinition;
		this.type = type;
		this.origins = origins;
	}

	public BeanDefinition getBeanDefinition() {
		return this.beanDefinition;
	}

	public Type getType() {
		return this.type;
	}

	public Set<BeanDefinition> getOrigins() {
		return this.origins;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", BeanDefinitionOrigin.class.getSimpleName() + "[", "]")
				.add("beanDefinition=" + determineId(beanDefinition))
				.add("type=" + type.name().toLowerCase(Locale.ROOT))
				.add("origins=" + origins.stream().map(BeanDefinition::getBeanClassName).collect(Collectors.joining(", ")))
				.toString();
	}

	private String determineId(BeanDefinition beanDefinition) {
		String beanClassName = beanDefinition.getBeanClassName();
		if (beanClassName != null) {
			return beanClassName;
		}
		String factoryBeanName = beanDefinition.getFactoryBeanName();
		if (factoryBeanName != null) {
			return String.format("%s#%s", factoryBeanName, beanDefinition.getFactoryMethodName());
		}
		return null;
	}

	public enum Type {

		/**
		 * Contributes components and/or other configuration classes.
		 */
		CONFIGURATION,

		/**
		 * A single component.
		 */
		COMPONENT

	}
}
