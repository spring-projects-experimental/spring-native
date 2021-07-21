/*
 * Copyright 2021 the original author or authors.
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

package org.springframework.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.springframework.integration.jdbc.store.JdbcMessageStore;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.InitializationHint;
import org.springframework.nativex.hint.InitializationTime;
import org.springframework.nativex.hint.JdkProxyHint;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.nativex.hint.SerializationHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.type.AccessDescriptor;
import org.springframework.nativex.type.HintDeclaration;
import org.springframework.nativex.type.NativeConfiguration;
import org.springframework.nativex.type.TypeProcessor;
import org.springframework.nativex.type.TypeSystem;

@NativeHint(trigger = JdbcMessageStore.class,
		resources = @ResourceHint(patterns = "org/springframework/integration/jdbc/schema-.*.sql"))
@NativeHint(trigger = org.springframework.integration.config.EnableIntegration.class,
		initialization =
		@InitializationHint(initTime = InitializationTime.BUILD,
				types = {
						org.springframework.integration.config.IntegrationRegistrar.class,
						org.springframework.integration.support.json.JacksonPresent.class,
						org.springframework.integration.support.management.micrometer.MicrometerMetricsCaptorRegistrar.class,
						org.springframework.integration.http.config.HttpContextUtils.class,
						org.springframework.integration.websocket.config.WebSocketIntegrationConfigurationInitializer.class
				}),
		resources = @ResourceHint(patterns = "META-INF/spring.integration.properties"),
		types = {
				@TypeHint(access = AccessBits.FULL_REFLECTION,
						types = {
								org.springframework.integration.dsl.IntegrationFlow.class,
								org.springframework.integration.gateway.RequestReplyExchanger.class,
								org.springframework.integration.graph.Graph.class,
								org.springframework.integration.graph.LinkNode.class,
								org.springframework.integration.graph.SendTimers.class,
								org.springframework.integration.graph.TimerStats.class,
								org.springframework.integration.graph.ReceiveCounters.class,
								org.springframework.integration.http.management.IntegrationGraphController.class,
								org.springframework.integration.handler.AbstractReplyProducingMessageHandler.RequestHandler.class
						}),
				@TypeHint(access = AccessBits.CLASS | AccessBits.PUBLIC_METHODS,
						types = {
								org.springframework.integration.xml.xpath.XPathUtils.class,
								org.springframework.integration.json.JsonPathUtils.class,
								com.jayway.jsonpath.JsonPath.class,
								org.springframework.integration.gateway.MethodArgsHolder.class,
								org.springframework.integration.routingslip.ExpressionEvaluatingRoutingSlipRouteStrategy.RequestAndReply.class,
								org.springframework.integration.core.Pausable.class
						})
		},
		serializables = {
				@SerializationHint(
						types = {
								Number.class,
								ArrayList.class,
								HashMap.class,
								Properties.class,
								Hashtable.class,
								Exception.class,
								UUID.class,
								org.springframework.messaging.support.GenericMessage.class,
								org.springframework.messaging.support.ErrorMessage.class,
								org.springframework.messaging.MessageHeaders.class,
								org.springframework.integration.message.AdviceMessage.class,
								org.springframework.integration.support.MutableMessage.class,
								org.springframework.integration.support.MutableMessageHeaders.class,
								org.springframework.integration.store.MessageGroupMetadata.class,
								org.springframework.integration.store.MessageHolder.class,
								org.springframework.integration.store.MessageMetadata.class,
								org.springframework.integration.history.MessageHistory.class,
								org.springframework.integration.history.MessageHistory.Entry.class,
								org.springframework.integration.handler.DelayHandler.DelayedMessageWrapper.class
						})
		},
		jdkProxies = {
				@JdkProxyHint(
						types = {
								org.springframework.integration.gateway.RequestReplyExchanger.class,
								org.springframework.aop.SpringProxy.class,
								org.springframework.aop.framework.Advised.class,
								org.springframework.core.DecoratingProxy.class
						}),
				@JdkProxyHint(
						types = {
								org.springframework.integration.handler.AbstractReplyProducingMessageHandler.RequestHandler.class,
								org.springframework.aop.SpringProxy.class,
								org.springframework.aop.framework.Advised.class,
								org.springframework.core.DecoratingProxy.class
						}),
				@JdkProxyHint(
						types = {
								org.springframework.integration.dsl.IntegrationFlow.class,
								org.springframework.context.SmartLifecycle.class,
								org.springframework.aop.SpringProxy.class,
								org.springframework.aop.framework.Advised.class,
								org.springframework.core.DecoratingProxy.class
						})
		})
@NativeHint(trigger = org.springframework.integration.util.ClassUtils.class,
		types =
		@TypeHint(
				types = {
						org.springframework.integration.core.GenericSelector.class,
						org.springframework.messaging.support.GenericMessage.class,
						org.springframework.integration.transformer.GenericTransformer.class,
						org.springframework.integration.handler.GenericHandler.class,
						java.util.function.Function.class,
						java.util.function.Supplier.class,
						kotlin.jvm.functions.Function0.class,
						kotlin.jvm.functions.Function1.class,
						kotlin.Unit.class
				},
				access = AccessBits.CLASS | AccessBits.PUBLIC_METHODS))
@NativeHint(trigger = org.springframework.integration.file.splitter.FileSplitter.class,
		serializables =
		@SerializationHint(types = {
				org.springframework.integration.file.splitter.FileSplitter.FileMarker.class,
				org.springframework.integration.file.splitter.FileSplitter.FileMarker.Mark.class,
		}))
@NativeHint(trigger = org.springframework.integration.xml.transformer.XsltPayloadTransformer.class,
		types =
		@TypeHint(types = org.springframework.web.context.support.ServletContextResource.class,
				access = AccessBits.CLASS))
@NativeHint(trigger = org.springframework.integration.xml.transformer.UnmarshallingTransformer.class,
		types =
		@TypeHint(types = org.springframework.ws.mime.MimeMessage.class,
				access = AccessBits.CLASS))
@NativeHint(trigger = org.springframework.integration.http.inbound.BaseHttpInboundEndpoint.class,
		types =
		@TypeHint(types = javax.xml.bind.Binder.class,
				typeNames = "com.rometools.rome.feed.atom.Feed",
				access = AccessBits.CLASS))
@NativeHint(trigger = org.springframework.integration.http.inbound.IntegrationRequestMappingHandlerMapping.class,
		types =
		@TypeHint(
				types = org.springframework.web.HttpRequestHandler.class,
				access = AccessBits.CLASS | AccessBits.PUBLIC_METHODS))
@NativeHint(trigger = org.springframework.integration.webflux.inbound.WebFluxIntegrationRequestMappingHandlerMapping.class,
		types =
		@TypeHint(
				types = org.springframework.web.server.WebHandler.class,
				access = AccessBits.CLASS | AccessBits.PUBLIC_METHODS))
@NativeHint(trigger = com.fasterxml.jackson.databind.ObjectMapper.class,
		initialization =
		@InitializationHint(initTime = InitializationTime.BUILD,
				types = org.springframework.integration.support.json.Jackson2JsonObjectMapper.class))
public class IntegrationHints implements NativeConfiguration {

	private static final String MESSAGING_GATEWAY_ANNOTATION =
			"Lorg/springframework/integration/annotation/MessagingGateway;";

	private static final String ABSTRACT_ENDPOINT_TYPE = "Lorg/springframework/integration/endpoint/AbstractEndpoint;";

	private static final String INTEGRATION_NODE_TYPE = "Lorg/springframework/integration/graph/IntegrationNode;";

	private static final String MESSAGE_TYPE = "org/springframework/messaging/Message";

	@Override
	public List<HintDeclaration> computeHints(TypeSystem typeSystem) {
		List<HintDeclaration> hints = new ArrayList<>();
		hints.addAll(computeMessagingGatewayHints(typeSystem));
		hints.addAll(computeAbstractEndpointHints(typeSystem));
		hints.addAll(computeIntegrationNodeHints(typeSystem));
		//		TODO Fails with 'Unable to find class file for org/springframework/web/server/WebFilter' on 'spring-aot-maven-plugin:test-generate'
		//		hints.addAll(computeMessageHints(typeSystem));
		return hints;
	}

	private static List<HintDeclaration> computeMessagingGatewayHints(TypeSystem typeSystem) {
		return TypeProcessor.namedProcessor("IntegrationHints - MessagingGateway")
				.skipMethodInspection()
				.skipFieldInspection()
				.skipConstructorInspection()
				.filter(type ->
						type.hasAnnotationInHierarchy(MESSAGING_GATEWAY_ANNOTATION) &&
								type.isInterface() &&
								!type.isAnnotation())
				.limitInspectionDepth(0)
				.onTypeDiscovered((type, context) -> {
					context.addProxy(type.getDottedName(), "org.springframework.aop.SpringProxy",
							"org.springframework.aop.framework.Advised", "org.springframework.core.DecoratingProxy");
				})
				.use(typeSystem)
				.processTypes();
	}

	private static List<HintDeclaration> computeAbstractEndpointHints(TypeSystem typeSystem) {
		return TypeProcessor.namedProcessor("IntegrationHints - AbstractEndpoint")
				.skipAnnotationInspection()
				.skipMethodInspection()
				.skipFieldInspection()
				.skipConstructorInspection()
				.filter(type -> type.extendsClass(ABSTRACT_ENDPOINT_TYPE))
				.onTypeDiscovered((type, context) ->
						context.addReflectiveAccess(type,
								new AccessDescriptor(AccessBits.CLASS | AccessBits.PUBLIC_METHODS)))
				.use(typeSystem)
				.processTypes();
	}

	private static List<HintDeclaration> computeIntegrationNodeHints(TypeSystem typeSystem) {
		return TypeProcessor.namedProcessor("IntegrationHints - IntegrationNode")
				.skipAnnotationInspection()
				.skipMethodInspection()
				.skipFieldInspection()
				.skipConstructorInspection()
				.filter(type -> type.extendsClass(INTEGRATION_NODE_TYPE))
				.onTypeDiscovered((type, context) ->
						context.addReflectiveAccess(type,
								new AccessDescriptor(AccessBits.FULL_REFLECTION)))
				.use(typeSystem)
				.processTypes();
	}

	private static List<HintDeclaration> computeMessageHints(TypeSystem typeSystem) {
		return TypeProcessor.namedProcessor("IntegrationHints - Message")
				.skipAnnotationInspection()
				.skipMethodInspection()
				.skipFieldInspection()
				.skipConstructorInspection()
				.filter(type -> type.implementsInterface(MESSAGE_TYPE))
				.onTypeDiscovered((type, context) ->
						context.addReflectiveAccess(type,
								new AccessDescriptor(AccessBits.CLASS | AccessBits.PUBLIC_METHODS)))
				.use(typeSystem)
				.processTypes();
	}

}
