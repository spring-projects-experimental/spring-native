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

package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.type.HintDeclaration;
import org.springframework.nativex.type.NativeConfiguration;
import org.springframework.nativex.type.Type;
import org.springframework.nativex.type.TypeProcessor;
import org.springframework.nativex.type.TypeSystem;

@NativeHint(trigger = EnableConfigurationPropertiesRegistrar.class, types = {
		@TypeHint(types = {
				BoundConfigurationProperties.class,
				ConfigurationPropertiesBindingPostProcessor.class
		}),
		@TypeHint(types = {
				DeprecatedConfigurationProperty.class,
				NestedConfigurationProperty.class
				}, typeNames = {
				"java.lang.CharSequence[]",
				"java.lang.String[]"
				})
})
@NativeHint(trigger = EnableConfigurationProperties.class,
	types = @TypeHint(types= ConstructorBinding.class)
)
@NativeHint(types = {
		@TypeHint(types= {
				ConfigurationPropertiesScan.class,
				ConfigurationPropertiesScanRegistrar.class
		},
		typeNames= {
				"java.io.Serializable[]",
				"java.lang.Comparable[]"
		}, access= AccessBits.SKIP_FOR_NATIVE_NEXT),
		@TypeHint(types= ArrayList.class, access=AccessBits.LOAD_AND_CONSTRUCT )
})
public class ConfigurationPropertiesHints implements NativeConfiguration {

	private static final String CONFIGURATION_PROPERTIES_ANNOTATION = "Lorg/springframework/boot/context/properties/ConfigurationProperties;";

	@Override
	public List<HintDeclaration> computeHints(TypeSystem typeSystem) {
		List<HintDeclaration> hints = new ArrayList<>();
		hints.addAll(computeConfigurationPropertiesHints(typeSystem));
		return hints;
	}

	private List<HintDeclaration> computeConfigurationPropertiesHints(TypeSystem typeSystem) {
		return TypeProcessor.namedProcessor("ConfigurationPropertiesHints - ConfigurationProperties")
				.filter(type -> type.hasAnnotationInHierarchy(CONFIGURATION_PROPERTIES_ANNOTATION) && !type.isPartOfDomain("org.springframework.boot"))
				.limitInspectionDepth(0)
				.onTypeDiscovered((type, context) -> {
					Map<String, Integer> propertyTypesForAccess = type.processAsConfigurationProperties();
					for (Map.Entry<String,Integer> entry: propertyTypesForAccess.entrySet()) {
						context.addReflectiveAccess(Type.fromLdescriptorToDotted(entry.getKey()), entry.getValue());
					}
				})
				.use(typeSystem)
				.processTypes();
	}
}
