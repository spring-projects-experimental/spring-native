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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import com.squareup.javapoet.CodeBlock;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.bootstrap.infrastructure.ExceptionHandler;
import org.springframework.core.ResolvableType;

/**
 * A factory method-based {@link BeanValueWriter}.
 *
 * @author Stephane Nicoll
 */
public class MethodBeanValueWriter extends AbstractBeanValueWriter {

	private final Method method;

	public MethodBeanValueWriter(BeanDefinition beanDefinition, ClassLoader classLoader, Method method) {
		super(beanDefinition, classLoader);
		this.method = method;
	}

	@Override
	public Class<?> getDeclaringType() {
		return this.method.getDeclaringClass();
	}

	@Override
	public boolean isAccessibleFrom(String packageName) {
		return super.isAccessibleFrom(packageName) && Modifier.isPublic(this.method.getModifiers())
				&& !hasMethodParameterInaccessible();
	}

	private boolean hasMethodParameterInaccessible() {
		for (int i = 0; i < this.method.getParameterCount(); i++) {
			if (!isAccessible(ResolvableType.forMethodParameter(this.method, i))) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void writeValueSupplier(CodeBlock.Builder code) {
		boolean wrapException = hasCheckedException(this.method.getExceptionTypes());
		if (wrapException) {
			code.add("$T.wrapException(", ExceptionHandler.class);
		}
		code.add("() ->");
		// We need to process any parameters that might hold generic to manage them upfront.
		List<ParameterResolution> parameters = resolveParameters(this.method.getParameters(), (i) -> ResolvableType.forMethodParameter(this.method, i));
		boolean hasAssignment = parameters.stream().anyMatch(ParameterResolution::hasAssignment);
		if (hasAssignment) {
			code.beginControlFlow("");
			parameters.stream().filter(ParameterResolution::hasAssignment).forEach((parameter) -> parameter.applyAssignment(code));
			code.add("return ");
		}
		else {
			code.add(" ");
		}
		if (Modifier.isStatic(this.method.getModifiers())) {
			code.add("$T", getDeclaringType());
		}
		else {
			code.add("context.getBean($T.class)", this.method.getDeclaringClass());
		}
		code.add(".$L(", this.method.getName());
		for (int i = 0; i < parameters.size(); i++) {
			parameters.get(i).applyParameter(code);
			if (i < parameters.size() - 1) {
				code.add(", ");
			}
		}
		code.add(")");
		if (wrapException) {
			code.add(")");
		}
		if (hasAssignment) {
			code.add(";\n").unindent().add("}");
		}
	}

}
