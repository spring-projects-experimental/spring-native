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

package org.springframework.context.origin;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.function.Predicate;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.origin.BeanDefinitionDescriptor.Type;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;

/**
 * Useful stream operators on {@link BeanDefinitionDescriptor}.
 *
 * @author Stephane Nicoll
 */
public class BeanDefinitionDescriptorPredicates {

	private final MetadataReaderFactory metadataReaderFactory;

	public BeanDefinitionDescriptorPredicates(ClassLoader classLoader) {
		this.metadataReaderFactory = new CachingMetadataReaderFactory(classLoader);
	}

	/**
	 * Filter {@link BeanDefinitionDescriptor descriptors} matching the specified {@link Type}.
	 * @param type the type to match
	 * @return a predicate to filter based on type
	 */
	public Predicate<BeanDefinitionDescriptor> ofType(Type type) {
		return (candidate) -> candidate.getType() == type;
	}

	/**
	 * Filter {@link BeanDefinitionDescriptor descriptors} for beans of the specified {@code className}.
	 * @param className the class name of the bean to match
	 * @return a predicate to filter based on bean class name
	 */
	public Predicate<BeanDefinitionDescriptor> ofBeanClassName(String className) {
		return (candidate) -> className.equals(candidate.getBeanDefinition().getBeanClassName());
	}

	/**
	 * Filter {@link BeanDefinitionDescriptor descriptors} for beans of the specified {@code type}.
	 * @param type the class of the bean to match
	 * @return a predicate to filter based on bean class
	 */
	public Predicate<BeanDefinitionDescriptor> ofBeanClassName(Class<?> type) {
		return ofBeanClassName(type.getName());
	}

	/**
	 * Filter {@link BeanDefinitionDescriptor descriptors} for beans matching the specified
	 * predicate against their {@link AnnotationMetadata}.
	 * @param annotationState the predicate to test against the bean's annotations
	 * @return a predicate to filter based on bean annotations
	 */
	public Predicate<BeanDefinitionDescriptor> annotationMatching(Predicate<AnnotationMetadata> annotationState) {
		return (candidate) -> {
			AnnotationMetadata metadata = getAnnotationMetadata(candidate.getBeanDefinition());
			return (metadata != null && annotationState.test(metadata));
		};
	}

	/**
	 * Filter {@link BeanDefinitionDescriptor descriptors} for beans annotated with the
	 * specified {@code annotationName}.
	 * @param annotationName the annotation name to test against
	 * @return a predicate to filter based on an annotation name
	 */
	public Predicate<BeanDefinitionDescriptor> annotatedWith(String annotationName) {
		return annotationMatching((metadata) -> metadata.isAnnotated(annotationName));
	}

	/**
	 * Filter {@link BeanDefinitionDescriptor descriptors} for beans annotated with the
	 * specified {@code annotationType}.
	 * @param annotationType the annotation type to test against
	 * @return a predicate to filter based on an annotation type
	 */
	public Predicate<BeanDefinitionDescriptor> annotatedWith(Class<? extends Annotation> annotationType) {
		return annotatedWith(annotationType.getName());
	}

	/**
	 * Return the {@link AnnotationMetadata} for the specified {@link BeanDefinition}.
	 * @param beanDefinition the bean definition
	 * @return the annotations of the specified bean definition
	 */
	public AnnotationMetadata getAnnotationMetadata(BeanDefinition beanDefinition) {
		if (beanDefinition instanceof AnnotatedBeanDefinition) {
			((AnnotatedBeanDefinition) beanDefinition).getMetadata();
		}
		if (beanDefinition.getBeanClassName() != null) {
			return getAnnotationMetadata(beanDefinition.getBeanClassName());
		}
		return null;
	}

	@Nullable
	private AnnotationMetadata getAnnotationMetadata(String type) {
		try {
			return this.metadataReaderFactory.getMetadataReader(type).getAnnotationMetadata();
		}
		catch (IOException ex) {
			return null;
		}
	}
}
