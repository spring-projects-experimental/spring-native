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

package org.springframework.boot.context.config;

import org.springframework.boot.context.properties.bind.Name;
import org.springframework.nativex.type.NativeConfiguration;
import org.springframework.nativex.type.Type;
import org.springframework.nativex.type.TypeSystem;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.hint.AccessBits;

@NativeHint(types = {
	@TypeHint(types = ConfigDataLocation.class, access = AccessBits.DECLARED_METHODS),
	@TypeHint(types = ConfigDataLocation[].class, access=AccessBits.CLASS),
	@TypeHint(types = {
			ConfigDataEnvironmentPostProcessor.class,
			ConfigTreeConfigDataLoader.class,
			ConfigTreeConfigDataLocationResolver.class,
			DelegatingApplicationContextInitializer.class,
			DelegatingApplicationListener.class,
			StandardConfigDataLoader.class,
			StandardConfigDataLocationResolver.class
	}, access = AccessBits.DECLARED_CONSTRUCTORS),
	@TypeHint(types= {
		ConfigDataProperties.class,
		ConfigDataProperties.Activate.class,
		Name.class
	})
})
public class ConfigDataPropertiesHints implements NativeConfiguration {
}
