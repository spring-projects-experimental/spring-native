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

package org.springframework.context.annotation;

import java.lang.reflect.Method;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.NonNull;

/**
 * {@link RootBeanDefinition} for a bean contributed via a {@link BeanMethod bean factory
 * method} of a {@link ConfigurationClass}. marker subclass used to signify that a bean
 * definition was created from a configuration class as opposed to any other configuration
 * source. Used in bean overriding cases where it's necessary to determine whether the
 * bean definition was created externally.
 */
final class BeanMethodBeanDefinition extends RootBeanDefinition implements AnnotatedBeanDefinition {

	private final BeanMethod beanMethod;

	private final String derivedBeanName;

	BeanMethodBeanDefinition(BeanMethod beanMethod, String derivedBeanName) {
		this.beanMethod = beanMethod;
		this.derivedBeanName = derivedBeanName;
		setResource(beanMethod.getConfigurationClass().getResource());
		setLenientConstructorResolution(false);
	}

	BeanMethodBeanDefinition(RootBeanDefinition original,
			BeanMethod beanMethod, String derivedBeanName) {
		super(original);
		this.beanMethod = beanMethod;
		this.derivedBeanName = derivedBeanName;
	}

	private BeanMethodBeanDefinition(BeanMethodBeanDefinition original) {
		super(original);
		this.beanMethod = original.beanMethod;
		this.derivedBeanName = original.derivedBeanName;
	}

	public BeanMethod getBeanMethod() {
		return this.beanMethod;
	}

	@Override
	public AnnotationMetadata getMetadata() {
		return this.beanMethod.getConfigurationClass().getMetadata();
	}

	@Override
	@NonNull
	public MethodMetadata getFactoryMethodMetadata() {
		return this.beanMethod.getMetadata();
	}

	@Override
	public boolean isFactoryMethod(Method candidate) {
		return (super.isFactoryMethod(candidate) && BeanAnnotationHelper.isBeanAnnotated(candidate) &&
				BeanAnnotationHelper.determineBeanNameFor(candidate).equals(this.derivedBeanName));
	}

	@Override
	public BeanMethodBeanDefinition cloneBeanDefinition() {
		return new BeanMethodBeanDefinition(this);
	}
}
