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

package org.springframework.nativex.substitutions.logback;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.conditional.Condition;
import ch.qos.logback.core.joran.conditional.PropertyWrapperForScripts;
import ch.qos.logback.core.spi.PropertyContainer;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.springframework.nativex.substitutions.LogbackIsAround;
import org.springframework.nativex.substitutions.OnlyIfPresent;
import org.springframework.nativex.substitutions.RemoveXmlSupport;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@TargetClass(className = "ch.qos.logback.core.joran.conditional.PropertyEvalScriptBuilder", onlyWith = { OnlyIfPresent.class, LogbackIsAround.class, RemoveXmlSupport.class })
final class Target_PropertyEvalScriptBuilder {

	@Alias
	PropertyContainer localPropContainer;

	@Alias
	Context context;

	private static class AllowedExpressionWrapper {
		static final Pattern ALLOWED_EXPRESSION_PATTERN =
				Pattern.compile("\\s*(isDefined|isNull)\\s*\\(\\s*\"(([^\"]|\\\\)*)\"\\s*\\)\\s*");
	}

	@Substitute
	public Condition build(String script) throws IllegalArgumentException {
		Matcher m = AllowedExpressionWrapper.ALLOWED_EXPRESSION_PATTERN.matcher(script);
		if (m.matches()) {
			String function = m.group(1);
			String field = m.group(2);
			switch (function) {
				case "isDefined":
					IsDefinedCondition definedCondition = new IsDefinedCondition(field);
					definedCondition.setPropertyContainers(localPropContainer, context);
					return definedCondition;
				case "isNull":
					IsNullCondition nullCondition = new IsNullCondition(field);
					nullCondition.setPropertyContainers(localPropContainer, context);
					return nullCondition;
				default:
					throw new IllegalArgumentException(script);
			}
		} else {
			throw new IllegalArgumentException(
					"Only simple 'isDefined' and 'isNull' conditions are supported in native image: " + script);
		}
	}

	private static class IsDefinedCondition extends PropertyWrapperForScripts implements Condition {

		private final String field;

		public IsDefinedCondition(String field) {
			this.field = field;
		}

		public boolean evaluate() {
			return this.isDefined(field);
		}
	}

	private static class IsNullCondition extends PropertyWrapperForScripts implements Condition {

		private final String field;

		public IsNullCondition(String field) {
			this.field = field;
		}

		public boolean evaluate() {
			return this.isNull(field);
		}
	}
}
