package org.springframework.context.origin;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionOrigin.Type;

/**
 * A hierarchical structure of a {@link BeanFactory}.
 *
 * @author Stephane Nicoll
 * @see BeanDefinitionOrigin
 */
public final class BeanFactoryStructure {

	private final List<BeanDefinitionOrigin> beanDefinitions;

	private final List<BeanDefinition> other;

	BeanFactoryStructure(List<BeanDefinitionOrigin> beanDefinitions,
			List<BeanDefinition> other) {
		this.beanDefinitions = beanDefinitions;
		this.other = new ArrayList<>(other);
	}

	public void writeReport(PrintWriter writer) {
		writer.println(String.format("%s configuration classes found", this.beanDefinitions.stream().filter((beanDefinition) ->
				beanDefinition.getType().equals(Type.CONFIGURATION)).count()));
		writer.println(String.format("%s components found", this.beanDefinitions.stream().filter((beanDefinition) ->
				beanDefinition.getType().equals(Type.COMPONENT)).count()));
		writer.println(String.format("%s bean definitions that have not been identified", this.other.size()));
		for (BeanDefinition otherBeanDefinition : other) {
			writer.println("\t" + otherBeanDefinition);
		}
	}

}
