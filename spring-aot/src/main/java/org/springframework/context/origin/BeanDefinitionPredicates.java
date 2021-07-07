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
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.lang.Nullable;

/**
 * Useful stream operators on {@link BeanDefinition}.
 *
 * @author Stephane Nicoll
 */
public class BeanDefinitionPredicates {

	private final MetadataReaderFactory metadataReaderFactory;

	public BeanDefinitionPredicates(ClassLoader classLoader) {
		this.metadataReaderFactory = new CachingMetadataReaderFactory(classLoader);
	}

	public Predicate<BeanDefinition> ofBeanClassName(String className) {
		return (candidate) -> className.equals(candidate.getBeanClassName());
	}

	public Predicate<BeanDefinition> ofBeanClassName(Class<?> type) {
		return ofBeanClassName(type.getName());
	}

	public Predicate<BeanDefinition> annotationMatching(Predicate<AnnotationMetadata> annotationState) {
		return (candidate) -> {
			AnnotationMetadata metadata = getAnnotationMetadata(candidate);
			return (metadata != null && annotationState.test(metadata));
		};
	}

	public Predicate<BeanDefinition> annotatedWith(String annotationName) {
		return annotationMatching((metadata) -> metadata.isAnnotated(annotationName));
	}

	public Predicate<BeanDefinition> annotatedWith(Class<? extends Annotation> annotationType) {
		return annotatedWith(annotationType.getName());
	}

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
