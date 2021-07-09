package org.springframework.context.bootstrap.generator.test;

import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.ListAssert;

/**
 * Assertions for a {@link CodeSnippet code snippet}.
 *
 * @author Stephane Nicoll
 */
public class CodeSnippetAssert extends AbstractStringAssert<CodeSnippetAssert> {

	public CodeSnippetAssert(CodeSnippet codeSnippet) {
		super(codeSnippet.getSnippet(), CodeSnippetAssert.class);
	}

	/**
	 * Return an {@link ListAssert assert} for the lines that constitute this text, to
	 * allow chaining of lines-specific assertions from this call.
	 * @return a {@link ListAssert} for the lines that constitutes this text
	 */
	public ListAssert<String> lines() {
		return new ListAssert<>(readAllLines(this.actual));
	}

	private static List<String> readAllLines(String source) {
		String[] lines = source.split("\\r?\\n");
		return Arrays.asList(lines);
	}

}
