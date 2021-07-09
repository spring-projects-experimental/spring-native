package org.springframework.context.bootstrap.generator.test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.assertj.core.api.AssertProvider;

/**
 * A code snippet that is generated as part of a {@link JavaFile} so that Javapoet can
 * perform the necessary transformations (i.e. imports).
 *
 * @author Stephane Nicoll
 */
public final class CodeSnippet implements AssertProvider<CodeSnippetAssert> {

	private static final String START_SNIPPET = "// start-snippet\n";

	private static final String END_SNIPPET = "// end-snippet";

	private final String fileContent;

	private final String snippet;

	CodeSnippet(String fileContent, String snippet) {
		this.fileContent = fileContent;
		this.snippet = snippet;
	}

	String getFileContent() {
		return this.fileContent;
	}

	String getSnippet() {
		return this.snippet;
	}

	@Override
	public CodeSnippetAssert assertThat() {
		return new CodeSnippetAssert(this);
	}

	/**
	 * Create a {@link CodeSnippet} using the specified code generator.
	 * @param code a consumer to use to generate the code snippet
	 * @return a {@link CodeSnippet} instance
	 * @see #create() to use additional options
	 */
	public static CodeSnippet of(Consumer<CodeBlock.Builder> code) {
		return create().build(code);
	}

	/**
	 * Initialize a new {@link Builder}.
	 * @return a new builder
	 */
	public static Builder create() {
		return new Builder();
	}

	public static final class Builder {

		private static final String INDENT = "  ";

		private static final String SNIPPET_INDENT = INDENT + INDENT;

		private Consumer<MethodSpec.Builder> methodCustomizer;

		private Consumer<CodeBlock.Builder> bodyCustomizer;

		public Builder customizeMethod(Consumer<MethodSpec.Builder> methodCustomizer) {
			this.methodCustomizer = methodCustomizer;
			return this;
		}

		public Builder customizeBody(Consumer<CodeBlock.Builder> bodyCustomizer) {
			this.bodyCustomizer = bodyCustomizer;
			return this;
		}

		public CodeSnippet build(Consumer<CodeBlock.Builder> code) {
			MethodSpec.Builder method = MethodSpec.methodBuilder("test").addModifiers(Modifier.PUBLIC);
			if (this.methodCustomizer != null) {
				this.methodCustomizer.accept(method);
			}
			CodeBlock.Builder body = CodeBlock.builder();
			if (this.bodyCustomizer != null) {
				this.bodyCustomizer.accept(body);
			}
			body.add(START_SNIPPET);
			code.accept(body);
			body.add(END_SNIPPET);
			method.addCode(body.build());
			String fileContent = write(createTestJavaFile(method.build()));
			String snippet = isolateGeneratedContent(fileContent);
			return new CodeSnippet(fileContent, snippet);
		}

		private String isolateGeneratedContent(String javaFile) {
			int start = javaFile.indexOf(START_SNIPPET);
			String tmp = javaFile.substring(start + START_SNIPPET.length());
			int end = tmp.indexOf(END_SNIPPET);
			tmp = tmp.substring(0, end);
			// Remove indent
			return Arrays.stream(tmp.split("\n")).map((line) -> {
				if (!line.startsWith(SNIPPET_INDENT)) {
					throw new IllegalStateException("Missing indent for " + line);
				}
				return line.substring(SNIPPET_INDENT.length());
			}).collect(Collectors.joining("\n"));
		}

		private JavaFile createTestJavaFile(MethodSpec method) {
			return JavaFile.builder("example", TypeSpec.classBuilder("Test").addModifiers(Modifier.PUBLIC)
					.addMethod(method).build()).indent(INDENT).build();
		}

		private String write(JavaFile file) {
			try {
				StringWriter out = new StringWriter();
				file.writeTo(out);
				return out.toString();
			}
			catch (IOException ex) {
				throw new IllegalStateException("Failed to write " + file, ex);
			}
		}

	}
}
