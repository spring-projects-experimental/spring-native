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

package org.springframework.context.annotation;

import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.context.event.EventPublishingRunListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.nativex.hint.MethodHint;
import org.springframework.nativex.type.NativeConfiguration;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.hint.AccessBits;

@NativeHint(trigger = AdviceModeImportSelector.class, abortIfTypesMissing = true, follow = true)
@NativeHint(trigger = Import.class, follow = true) // Whatever is @Imported should be followed
@NativeHint(trigger = Conditional.class, extractTypesFromAttributes = "value" ) // TODO need extract?
@NativeHint(types = @TypeHint(types = { ComponentScan.class, Configuration.class }, access = AccessBits.CLASS | AccessBits.DECLARED_METHODS))
// TODO Check required access for enums like this FilterType
@NativeHint(types = { @TypeHint(types = FilterType.class, access = AccessBits.CLASS | AccessBits.DECLARED_METHODS | AccessBits.DECLARED_FIELDS) })
@NativeHint(types =
	@TypeHint(typeNames = "org.springframework.context.annotation.ConfigurationClassParser$DefaultDeferredImportSelectorGroup",
	access=AccessBits.SKIP_FOR_NATIVE_NEXT))
@NativeHint(types = {
		@TypeHint(types = {
				AutowireCapableBeanFactory.class, // security sample shows errors on startup without this
				AutoConfigurationExcludeFilter.class,
				AutowiredAnnotationBeanPostProcessor.class				
		},access=AccessBits.SKIP_FOR_NATIVE_NEXT)
})
@NativeHint(types = {
		@TypeHint(types = {
				EmbeddedValueResolverAware.class,
				EnvironmentAware.class,
				AnnotationConfigApplicationContext.class,
				CommonAnnotationBeanPostProcessor.class,
				AnnotationScopeMetadataResolver.class,
				EventListenerMethodProcessor.class,
				DefaultEventListenerFactory.class,
				}),
		@TypeHint(types= ComponentScan.Filter.class, access = AccessBits.CLASS | AccessBits.DECLARED_METHODS),
		@TypeHint(types = { ConfigurationClassPostProcessor.class },
				methods = @MethodHint(name="setMetadataReaderFactory", parameterTypes = MetadataReaderFactory.class)
		),
		@TypeHint(types= ApplicationContext.class, access = AccessBits.LOAD_AND_CONSTRUCT | AccessBits.RESOURCE)
})
public class ContextAnnotationHints implements NativeConfiguration {
}
