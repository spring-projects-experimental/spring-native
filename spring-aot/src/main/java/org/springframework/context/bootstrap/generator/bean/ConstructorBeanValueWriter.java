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

package org.springframework.context.bootstrap.generator.bean;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;

import com.squareup.javapoet.CodeBlock;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.bootstrap.infrastructure.ExceptionHandler;
import org.springframework.core.ResolvableType;

/**
 * A constructor-based {@link BeanValueWriter}.
 *
 * @author Stephane Nicoll
 */
public class ConstructorBeanValueWriter extends AbstractBeanValueWriter {

	private final Constructor<?> constructor;

	public ConstructorBeanValueWriter(BeanDefinition beanDefinition, ClassLoader classLoader,
			Constructor<?> constructor) {
		super(beanDefinition, classLoader);
		this.constructor = constructor;
	}

	@Override
	public Class<?> getDeclaringType() {
		return this.constructor.getDeclaringClass();
	}

	@Override
	public boolean isAccessibleFrom(String packageName) {
		return super.isAccessibleFrom(packageName) && Modifier.isPublic(this.constructor.getModifiers())
				&& !hasConstructorParameterInaccessible();
	}

	private boolean hasConstructorParameterInaccessible() {
		for (int i = 0; i < this.constructor.getParameterCount(); i++) {
			if (!isAccessible(ResolvableType.forConstructorParameter(this.constructor, i))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void writeValueSupplier(CodeBlock.Builder code) {
		boolean wrapException = hasCheckedException(this.constructor.getExceptionTypes());
		if (wrapException) {
			code.add("$T.wrapException(", ExceptionHandler.class);
		}
		// We need to process any parameters that might hold generic to manage them upfront.
		List<ParameterResolution> parameters = resolveParameters(this.constructor.getParameters(), (i) -> ResolvableType.forConstructorParameter(this.constructor, i));
		boolean hasAssignment = parameters.stream().anyMatch(ParameterResolution::hasAssignment);
		if (parameters.isEmpty()) {
			code.add("$T::new", getType());
		}
		else {
			code.add("() ->");
			if (hasAssignment) {
				code.beginControlFlow("");
				parameters.stream().filter(ParameterResolution::hasAssignment).forEach((parameter) -> parameter.applyAssignment(code));
				code.add("return ");
			}
			else {
				code.add(" ");
			}
			code.add("new $T(", getDeclaringType());
			for (int i = 0; i < parameters.size(); i++) {
				parameters.get(i).applyParameter(code);
				if (i < parameters.size() - 1) {
					code.add(", ");
				}
			}
			code.add(")");
		}
		if (wrapException) {
			code.add(")");
		}
		if (hasAssignment) {
			code.add(";\n").unindent().add("}");
		}
	}

}
