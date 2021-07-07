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

import java.util.Arrays;

import com.squareup.javapoet.CodeBlock;

import org.springframework.core.ResolvableType;

/**
 * Shared generation helper.
 *
 * @author Stephane Nicoll
 */
abstract class TypeHelper {

	static void generateResolvableTypeFor(CodeBlock.Builder code, ResolvableType target) {
		generate(code, target, false);
	}

	private static void generate(CodeBlock.Builder code, ResolvableType target, boolean forceResolvableType) {
		Class<?> type = target.toClass();
		if (!target.hasGenerics()) {
			if (forceResolvableType) {
				code.add("$T.forClass($T.class)", ResolvableType.class, type);
			}
			else {
				code.add("$T.class", type);
			}
		}
		else {
			code.add("$T.forClassWithGenerics($T.class, ", ResolvableType.class, type);
			ResolvableType[] generics = target.getGenerics();
			boolean hasGenericParameter = Arrays.stream(generics).anyMatch(ResolvableType::hasGenerics);
			for (int i = 0; i < generics.length; i++) {
				ResolvableType parameter = target.getGeneric(i);
				generate(code, parameter, hasGenericParameter);
				if (i < generics.length - 1) {
					code.add(", ");
				}
			}
			code.add(")");
		}
	}

}
