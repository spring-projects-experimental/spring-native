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

package org.springframework.core.annotation;

import org.springframework.core.DecoratingProxy;
import org.springframework.core.PriorityOrdered;
import org.springframework.nativex.type.NativeConfiguration;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.hint.AccessBits;

@NativeHint( types =
@TypeHint(types= {
		AliasFor.class
}, access = AccessBits.CLASS | AccessBits.DECLARED_METHODS))
@NativeHint(types = { 
		@TypeHint(types= {
			AnnotationAttributes.class,
			AnnotationAttributes[].class,
			DecoratingProxy.class,
			PriorityOrdered.class,
			TypeMappedAnnotation[].class
		}, access = AccessBits.CLASS | AccessBits.SKIP_FOR_NATIVE_NEXT),
		@TypeHint(types = Order.class, access=AccessBits.CLASS | AccessBits.DECLARED_METHODS|AccessBits.SKIP_FOR_NATIVE_NEXT)
})
public class CoreAnnotationHints implements NativeConfiguration {
}
