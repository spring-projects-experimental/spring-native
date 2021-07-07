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

package org.springframework.context.bootstrap.generator;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import org.springframework.context.bootstrap.generator.bean.BeanRegistrationGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StringUtils;

/**
 * Represent a class that provide registration for beans that are not available outside of
 * a particular package.
 *
 * @author Stephane Nicoll
 */
public class ProtectedBootstrapClass {

	private final String packageName;

	private final List<MethodSpec> methods;

	private final TypeSpec.Builder type;

	public ProtectedBootstrapClass(String packageName) {
		this.packageName = packageName;
		this.methods = new ArrayList<>();
		this.type = TypeSpec.classBuilder("ContextBootstrap").addModifiers(Modifier.PUBLIC, Modifier.FINAL);
	}

	public void addBeanRegistrationMethod(String beanName, Class<?> type,
			BeanRegistrationGenerator beanRegistrationGenerator) {
		MethodSpec.Builder method = MethodSpec.methodBuilder(registerBeanMethodName(beanName, type))
				.addModifiers(Modifier.PUBLIC, Modifier.STATIC)
				.addParameter(GenericApplicationContext.class, "context");
		beanRegistrationGenerator.writeBeanRegistration(method);
		this.methods.add(method.build());
	}

	public JavaFile build() {
		return JavaFile.builder(this.packageName, this.type.addMethods(this.methods).build()).build();
	}

	static String registerBeanMethodName(String beanName, Class<?> type) {
		String target = (isValidName(beanName)) ? beanName : type.getSimpleName();
		return "register" + StringUtils.capitalize(target);
	}

	private static boolean isValidName(String className) {
		return SourceVersion.isIdentifier(className) && !SourceVersion.isKeyword(className);
	}

}
