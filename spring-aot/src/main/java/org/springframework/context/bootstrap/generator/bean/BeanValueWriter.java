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

import com.squareup.javapoet.CodeBlock;

/**
 * Abstract how to write the bean value supplier of a bean definition.
 *
 * @author Stephane Nicoll
 */
public interface BeanValueWriter {

	Class<?> getType();

	Class<?> getDeclaringType();

	boolean isAccessibleFrom(String packageName);

	void writeValueSupplier(CodeBlock.Builder code);

}
