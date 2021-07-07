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

package org.springframework.context.bootstrap.generator.test;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.ListAssert;

/**
 * Assertions for text content.
 *
 * @author Stephane Nicoll
 */
public class TextAssert extends AbstractStringAssert<TextAssert> {

	public TextAssert(String actual) {
		super(actual, TextAssert.class);
	}

	/**
	 * Return an {@link ListAssert assert} for the lines that constitute this text, to
	 * allow chaining of lines-specific assertions from this call.
	 * @return a {@link ListAssert} for the lines that constitutes this text
	 */
	public ListAssert<String> lines() {
		return new ListAssert<>(readAllLines(this.actual));
	}

	public static List<String> readAllLines(String source) {
		String[] lines = source.split("\\r?\\n");
		return Arrays.asList(lines);
	}

}
