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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.CodeBlock.Builder;
import org.junit.jupiter.api.Test;

import org.springframework.core.ResolvableType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TypeHelper}.
 *
 * @author Stephane Nicoll
 */
class TypeHelperTests {

	@Test
	void generateResolvableTypeForTypeWithGenericParameter() {
		assertCode(
				ResolvableType.forClassWithGenerics(Function.class,
						ResolvableType.forClassWithGenerics(Supplier.class, String.class),
						ResolvableType.forClassWithGenerics(Supplier.class, Integer.class)),
				(code) -> assertThat(code).endsWith(".forClassWithGenerics(java.util.function.Function.class, "
						+ "org.springframework.core.ResolvableType.forClassWithGenerics(java.util.function.Supplier.class, java.lang.String.class), "
						+ "org.springframework.core.ResolvableType.forClassWithGenerics(java.util.function.Supplier.class, java.lang.Integer.class))"));
	}

	@Test
	void generateResolvableTypeForTypeWithMixedParameter() {
		assertCode(
				ResolvableType.forClassWithGenerics(Function.class,
						ResolvableType.forClassWithGenerics(Supplier.class, String.class),
						ResolvableType.forClass(Integer.class)),
				(code) -> assertThat(code).endsWith(".forClassWithGenerics(java.util.function.Function.class, "
						+ "org.springframework.core.ResolvableType.forClassWithGenerics(java.util.function.Supplier.class, java.lang.String.class), "
						+ "org.springframework.core.ResolvableType.forClass(java.lang.Integer.class))"));
	}

	private void assertCode(ResolvableType type, Consumer<String> code) {
		Builder builder = CodeBlock.builder();
		TypeHelper.generateResolvableTypeFor(builder, type);
		code.accept(builder.build().toString());
	}

}
