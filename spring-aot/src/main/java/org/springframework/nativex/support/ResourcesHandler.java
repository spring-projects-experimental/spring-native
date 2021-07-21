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

package org.springframework.nativex.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.nativex.AotOptions;
import org.springframework.nativex.domain.init.InitializationDescriptor;
import org.springframework.nativex.domain.proxies.AotProxyDescriptor;
import org.springframework.nativex.domain.proxies.JdkProxyDescriptor;
import org.springframework.nativex.domain.reflect.FieldDescriptor;
import org.springframework.nativex.domain.reflect.MethodDescriptor;
import org.springframework.nativex.domain.reflect.ReflectionDescriptor;
import org.springframework.nativex.domain.resources.ResourcesDescriptor;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.Flag;
import org.springframework.nativex.type.AccessDescriptor;
import org.springframework.nativex.type.ComponentProcessor;
import org.springframework.nativex.type.HintApplication;
import org.springframework.nativex.type.HintDeclaration;
import org.springframework.nativex.type.Method;
import org.springframework.nativex.type.MissingTypeException;
import org.springframework.nativex.type.NativeContext;
import org.springframework.nativex.type.SpringFactoriesProcessor;
import org.springframework.nativex.type.Type;
import org.springframework.nativex.type.TypeSystem;
import org.springframework.nativex.type.TypeUtils;


public class ResourcesHandler extends Handler {

	private static Log logger = LogFactory.getLog(ResourcesHandler.class);	

	private final static String enableAutoconfigurationKey = "org.springframework.boot.autoconfigure.EnableAutoConfiguration";

	private final static String propertySourceLoaderKey = "org.springframework.boot.env.PropertySourceLoader";

	private final static String managementContextConfigurationKey = "org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration";

	private final ReflectionHandler reflectionHandler;

	private final DynamicProxiesHandler dynamicProxiesHandler;

	private final InitializationHandler initializationHandler;
	
	private SerializationHandler serializationHandler;

	private JNIReflectionHandler jniReflectionHandler;

	private final OptionHandler optionHandler;

	private final AotOptions aotOptions;
	
	private final Set<String> followed = new HashSet<>();

	public ResourcesHandler(ConfigurationCollector collector, ReflectionHandler reflectionHandler, 
			DynamicProxiesHandler dynamicProxiesHandler, InitializationHandler initializationHandler,
			SerializationHandler serializationHandler, JNIReflectionHandler jniReflectionHandler,
			OptionHandler optionHandler, AotOptions aotOptions) {
		super(collector);
		this.reflectionHandler = reflectionHandler;
		this.dynamicProxiesHandler = dynamicProxiesHandler;
		this.initializationHandler = initializationHandler;
		this.serializationHandler = serializationHandler;
		this.jniReflectionHandler = jniReflectionHandler;
		this.optionHandler = optionHandler;
		this.aotOptions = aotOptions;
	}

	/**
	 * Callback from native-image. Determine resources related to Spring applications that need to be added to the image.
	 */
	public void register() {
		if (aotOptions.toMode() == Mode.NATIVE ||
				aotOptions.toMode() == Mode.NATIVE_AGENT ||
				aotOptions.toMode() == Mode.NATIVE_NEXT) {
			processSpringFactories();
		}
		handleConstantHints(aotOptions.toMode() == Mode.NATIVE_INIT);
		if (aotOptions.toMode() == Mode.NATIVE ||
				aotOptions.toMode() == Mode.NATIVE_AGENT ||
				aotOptions.toMode() == Mode.NATIVE_NEXT) {
			handleSpringComponents();
		}
	}

	private void registerPatterns(ResourcesDescriptor rd) {
		for (String pattern : rd.getPatterns()) {
			if (pattern.equals("META-INF/spring.factories")) {
				continue; // leave to special handling which may trim these files...
			}
			collector.addResource(pattern, false);
		}
	}

	private void registerResourceBundles(ResourcesDescriptor rd) {
		logger.debug("Registering resources - #" + rd.getBundles().size() + " bundles");
		for (String bundle : rd.getBundles()) {
			try {
				ResourceBundle.getBundle(bundle);
				collector.addResource(bundle, true);
			} catch (MissingResourceException e) {
				//bundle not available. don't load it
			}
		}
	}

	/**
	 * Some types need reflective access in every Spring Boot application. When hints are scanned
	 * these 'constants' are registered against the java.lang.Object key. Because they won't have been 
	 * registered in regular analysis, here we explicitly register those. Initialization hints are handled
	 * separately.
	 */
	private void handleConstantHints(boolean isInitMode) {
		List<HintDeclaration> constantHints = ts.findActiveDefaultHints();
		logger.debug("> Registering fixed hints: " + constantHints);
		for (HintDeclaration ch : constantHints) {
			if (!isInitMode) {
				Map<String, AccessDescriptor> dependantTypes = ch.getDependantTypes();
				for (Map.Entry<String, AccessDescriptor> dependantType : dependantTypes.entrySet()) {
					String typename = dependantType.getKey();
					AccessDescriptor ad = dependantType.getValue();
					logger.debug("  fixed type registered " + typename + " with " + ad);
					if (AccessBits.isResourceAccessRequired(ad.getAccessBits()) && !typename.contains("[]")) {
						org.springframework.nativex.type.ResourcesDescriptor resourcesDescriptor = org.springframework.nativex.type.ResourcesDescriptor.ofType(typename);
						registerResourcesDescriptor(resourcesDescriptor);
					}
					List<org.springframework.nativex.type.MethodDescriptor> mds = ad.getMethodDescriptors();
					Flag[] accessFlags = AccessBits.getFlags(ad.getAccessBits());
					if (mds != null && mds.size() != 0 && AccessBits.isSet(ad.getAccessBits(),
							AccessBits.DECLARED_METHODS | AccessBits.PUBLIC_METHODS)) {
						logger.debug("  type has #" + mds.size()
								+ " members specified, removing typewide method access flags");
						accessFlags = filterFlags(accessFlags, Flag.allDeclaredMethods, Flag.allPublicMethods);
					}
					List<FieldDescriptor> fds = ad.getFieldDescriptors();
					reflectionHandler.addAccess(typename, MethodDescriptor.toStringArray(mds),
							FieldDescriptor.toStringArray(fds), true, accessFlags);
				}
				for (Map.Entry<String, AccessDescriptor> dependantType : ch.getJNITypes().entrySet()) {
					String typename = dependantType.getKey();
					AccessDescriptor ad = dependantType.getValue();
					logger.debug("  fixed JNI access type registered " + typename + " with " + ad);
					List<org.springframework.nativex.type.MethodDescriptor> mds = ad.getMethodDescriptors();
					Flag[] accessFlags = AccessBits.getFlags(ad.getAccessBits());
					if (mds != null && mds.size() != 0 && AccessBits.isSet(ad.getAccessBits(),
							AccessBits.DECLARED_METHODS | AccessBits.PUBLIC_METHODS)) {
						logger.debug("  type has #" + mds.size()
								+ " members specified, removing typewide method access flags");
						accessFlags = filterFlags(accessFlags, Flag.allDeclaredMethods, Flag.allPublicMethods);
					}
					List<FieldDescriptor> fds = ad.getFieldDescriptors();
					jniReflectionHandler.addAccess(typename, MethodDescriptor.toStringArray(mds),
							FieldDescriptor.toStringArray(fds), true, accessFlags);
				}
				List<JdkProxyDescriptor> proxyDescriptors = ch.getProxyDescriptors();
				for (JdkProxyDescriptor pd : proxyDescriptors) {
					logger.debug("Registering proxy descriptor: " + pd);
					dynamicProxiesHandler.addProxy(pd);
				}
				Set<String> serializationTypes = ch.getSerializationTypes();
				if (!serializationTypes.isEmpty()) {
					logger.debug("Registering types as serializable: "+serializationTypes);
					for (String st: serializationTypes) {
						serializationHandler.addType(st);
					}
				}
				List<org.springframework.nativex.type.ResourcesDescriptor> resourcesDescriptors = ch
						.getResourcesDescriptors();
				for (org.springframework.nativex.type.ResourcesDescriptor rd : resourcesDescriptors) {
					logger.debug("Registering resource descriptor: " + rd);
					registerResourcesDescriptor(rd);
				}
			}
			for (InitializationDescriptor initializationDescriptor : ch.getInitializationDescriptors()) {
				logger.debug("Registering initialization descriptor: " + initializationDescriptor);
				initializationHandler.registerInitializationDescriptor(initializationDescriptor);
			}
			if (!ch.getOptions().isEmpty()) {
				logger.debug("Registering options: "+ch.getOptions());
				optionHandler.addOptions(ch.getOptions());
			}
		}
		logger.debug("< Registering fixed hints");
	}
	
	public void registerResourcesDescriptor(org.springframework.nativex.type.ResourcesDescriptor rd) {
		String[] patterns = rd.getPatterns();
		for (String pattern: patterns) {
			collector.addResource(pattern,rd.isBundle());
			
		}	
	}

	/**
	 * Discover existing spring.components or synthesize one if none are found. If not running
	 * in hybrid mode then process the spring.components entries.
	 */
	public void handleSpringComponents() {
		NativeContext context = new NativeContextImpl();
//		Enumeration<URL> springComponents = fetchResources("META-INF/spring.components");
		Collection<byte[]> springComponents = ts.getResources("META-INF/spring.components");
		List<String> alreadyProcessed = new ArrayList<>();
		if (springComponents.size()!=0) {
//		if (springComponents.hasMoreElements()) {
			logger.debug("Processing existing META-INF/spring.components files...");
			for (byte[] springComponentsFile: springComponents) {
//			while (springComponents.hasMoreElements()) {
//				URL springFactory = springComponents.nextElement();
				Properties p = new Properties();
				try (ByteArrayInputStream bais = new ByteArrayInputStream(springComponentsFile)) {
					p.load(bais);
				} catch (IOException e) {
					throw new IllegalStateException("Unable to load spring.factories", e);
				}
//				loadSpringFactoryFile(springFactory, p);
				if (aotOptions.toMode() == Mode.NATIVE_AGENT) {
					processSpringComponentsAgent(p, context);
				} else {
					processSpringComponents(p, context, alreadyProcessed);
				}
			}
		} else {
			logger.debug("Found no META-INF/spring.components -> synthesizing one...");
			Properties p = synthesizeSpringComponents();
			if (aotOptions.toMode() == Mode.NATIVE_AGENT) {
				processSpringComponentsAgent(p, context);
			} else {
				processSpringComponents(p, context, alreadyProcessed);
			}
		}
	}

	private Properties synthesizeSpringComponents() {
		Properties p = new Properties();
		List<Entry<Type, List<Type>>> components = ts.scanForSpringComponents();
		List<Entry<Type, List<Type>>> filteredComponents = filterOutNestedConfigurationTypes(components);
		for (Entry<Type, List<Type>> filteredComponent : filteredComponents) {
			String k = filteredComponent.getKey().getDottedName();
			p.put(k, filteredComponent.getValue().stream().map(t -> t.getDottedName())
					.collect(Collectors.joining(",")));
		}
		logger.debug("Computed spring.components is ");
		logger.debug("vvv");
		for (Object k : p.keySet()) {
			logger.debug(k + "=" + p.getProperty((String) k));
		}
		logger.debug("^^^");
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			p.store(baos, "");
			baos.close();
			byte[] bs = baos.toByteArray();
			collector.registerResource("META-INF/spring.components", bs);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}
	
	private void processSpringComponentsAgent(Properties p, NativeContext context) {
		Enumeration<Object> keys = p.keys();
		while (keys.hasMoreElements()) {
			String key = (String)keys.nextElement();
			String valueString = (String)p.get(key);
			if (valueString.equals("package-info")) {
				continue;
			}
			Type keyType = ts.resolveDotted(key);
			// The context start/stop test may not exercise the @SpringBootApplication class
			if (keyType.isAtSpringBootApplication()) {
				logger.debug("hybrid: adding access to "+keyType+" since @SpringBootApplication");
				reflectionHandler.addAccess(key,  Flag.allDeclaredMethods, Flag.allDeclaredFields, Flag.allDeclaredConstructors);
//				resourcesRegistry.addResources(key.replace(".", "/")+".class");
				collector.addResource(key.replace(".", "/")+".class", false);
			}
			if (keyType.isAtController()) {
				logger.debug("hybrid: Processing controller "+key);
				List<Method> mappings = keyType.getMethods(m -> m.isAtMapping());
				// Example:
				// @GetMapping("/greeting")
				// public String greeting( @RequestParam(name = "name", required = false, defaultValue = "World") String name, Model model) {
				for (Method mapping: mappings) {
					for (int pi=0;pi<mapping.getParameterCount();pi++) {
						List<Type> parameterAnnotationTypes = mapping.getParameterAnnotationTypes(pi);
						for (Type parameterAnnotationType: parameterAnnotationTypes) {
							if (parameterAnnotationType.hasAliasForMarkedMembers()) {
								List<String> interfaces = new ArrayList<>();
								interfaces.add(parameterAnnotationType.getDottedName());
								interfaces.add("org.springframework.core.annotation.SynthesizedAnnotation");
								logger.debug("Adding dynamic proxy for "+interfaces);
								dynamicProxiesHandler.addProxy(interfaces);
							}
						}
					}
				}
			}
		}
	}

	/**
	 * Process a spring components properties object. The data within will look like:
	 * <pre><code>
	 * app.main.SampleApplication=org.springframework.stereotype.Component
	 * app.main.model.Foo=javax.persistence.Entity,something.Else
	 * app.main.model.FooRepository=org.springframework.data.repository.Repository
	 * </code></pre>
	 * @param p the properties object containing spring components
	 */
	private void processSpringComponents(Properties p, NativeContext context, List<String> alreadyProcessed) {
		int registeredComponents = 0;
		RequestedConfigurationManager requestor = new RequestedConfigurationManager();
		for (Entry<Object, Object> entry : p.entrySet()) {
			boolean processedOK = processSpringComponent((String)entry.getKey(), (String)entry.getValue(), context, requestor, alreadyProcessed);
			if (processedOK) {
				registeredComponents++;
			}
		}
		registerAllRequested(requestor);
		ts.getComponentProcessors().forEach(ComponentProcessor::printSummary);
		logger.debug("Registered " + registeredComponents + " entries");
	}
	
	private boolean processSpringComponent(String componentTypename, String classifiers, NativeContext context, RequestedConfigurationManager requestor, List<String> alreadyProcessed) {
		ProcessingContext pc = ProcessingContext.of(componentTypename, ReachedBy.FromSpringComponent);
		List<ComponentProcessor> componentProcessors = ts.getComponentProcessors();
		boolean isComponent = false;
		if (classifiers.equals("package-info")) {
			return false;
		}
		if (alreadyProcessed.contains(componentTypename+":"+classifiers)) {
			return false;
		}
		alreadyProcessed.add(componentTypename+":"+classifiers);
		Type kType = ts.resolveDotted(componentTypename);
		logger.debug("Registering Spring Component: " + componentTypename);

		// Ensure if usage of @Component is meta-usage, the annotations that are meta-annotated are
		// exposed
		Entry<Type, List<Type>> metaAnnotated = kType.getMetaComponentTaggedAnnotations();
		if (metaAnnotated != null) {
			for (Type t: metaAnnotated.getValue()) {
				String name = t.getDottedName();
				reflectionHandler.addAccess(name, Flag.allDeclaredMethods);
				collector.addResource(name.replace(".", "/")+".class", false);
//				resourcesRegistry.addResources(name.replace(".", "/")+".class");
			}
		}

		if (kType.isAtConfiguration()) {
			// Treat user configuration (from spring.components) the same as configuration
			// discovered via spring.factories
			checkAndRegisterConfigurationType(componentTypename,ReachedBy.FromSpringComponent);
		} else {
			try {
				// TODO assess which kinds of thing requiring what kind of access - here we see
				// an Entity might require field reflective access where others don't
				// I think as a component may have autowired fields (and an entity may have
				// interesting fields) - you kind of always need to expose fields
				// There is a type in vanilla-orm called Bootstrap that shows this need
				reflectionHandler.addAccess(componentTypename, Flag.allDeclaredConstructors, Flag.allDeclaredMethods,
					Flag.allDeclaredClasses, Flag.allDeclaredFields);
//				resourcesRegistry.addResources(componentTypename.replace(".", "/") + ".class");
				collector.addResource(componentTypename.replace(".", "/")+".class", false);
				// Register nested types of the component
				for (Type t : kType.getNestedTypes()) {
					reflectionHandler.addAccess(t.getDottedName(), Flag.allDeclaredConstructors, Flag.allDeclaredMethods,
							Flag.allDeclaredClasses);
//					resourcesRegistry.addResources(t.getName() + ".class");
					collector.addResource(t.getName()+".class", false);
				}
				registerHierarchy(pc, kType, requestor);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		if (kType != null && kType.isAtResponseBody()) {
			// TODO [0.9.0] move into WebComponentProcessor?
			processResponseBodyComponent(kType);
		}
		List<String> values = new ArrayList<>();
		StringTokenizer st = new StringTokenizer(classifiers, ",");
		// org.springframework.samples.petclinic.visit.JpaVisitRepositoryImpl=org.springframework.stereotype.Component,javax.transaction.Transactional
		while (st.hasMoreElements()) {
			String tt = st.nextToken();
			values.add(tt);
			if (tt.equals("org.springframework.stereotype.Component")) {
				isComponent = true;
			}
			try {
				Type baseType = ts.resolveDotted(tt);

				// reflectionHandler.addAccess(tt,Flag.allDeclaredConstructors,
				// Flag.allDeclaredMethods, Flag.allDeclaredClasses);
				// reflectionHandler.addAccess(tt,Flag.allPublicConstructors,
				// Flag.allPublicMethods, Flag.allDeclaredClasses);
				reflectionHandler.addAccess(tt, Flag.allDeclaredMethods);
//				resourcesRegistry.addResources(tt.replace(".", "/") + ".class");
				collector.addResource(tt.replace(".", "/")+".class", false);
				// Register nested types of the component
				for (Type t : baseType.getNestedTypes()) {
					String n = t.getName().replace("/", ".");
					reflectionHandler.addAccess(n, Flag.allDeclaredMethods);
//					reflectionHandler.addAccess(n, Flag.allDeclaredConstructors, Flag.allDeclaredMethods, Flag.allDeclaredClasses);
//					resourcesRegistry.addResources(t.getName() + ".class");
					collector.addResource(t.getName() + ".class", false);
				}
				registerHierarchy(pc, baseType, requestor);
			} catch (Throwable t) {
				t.printStackTrace();
				logger.debug("Problems with value " + tt);
			}
		}
		if (isComponent && aotOptions.isVerify()) {
			kType.verifyComponent();
		}
		for (Type type : kType.getNestedTypes()) {
			if (type.isComponent()) {
				// TODO do we need to fill in the classifiers list here (second param) correctly?
				// (We could do it, inferring like we infer spring.components in general)
				processSpringComponent(type.getDottedName(),"",context,requestor,alreadyProcessed);
			}
		}
		for (ComponentProcessor componentProcessor: componentProcessors) {
			if (componentProcessor.handle(context, componentTypename, values)) {
				componentProcessor.process(context, componentTypename, values);
			}
		}	
		return true;
	}

	/**
	 * This is the type passed to the 'plugins' that process spring components or spring factories entries.
	 */
	class NativeContextImpl implements NativeContext {

		@Override
		public boolean addProxy(List<String> interfaces) {
			dynamicProxiesHandler.addProxy(interfaces);
			return true;
		}

		@Override
		public boolean addProxy(String... interfaces) {
			if (interfaces != null) {
				dynamicProxiesHandler.addProxy(Arrays.asList(interfaces));
			}
			return true;
		}

		@Override
		public void addAotProxy(AotProxyDescriptor proxyDescriptor) {
			dynamicProxiesHandler.addClassProxy(proxyDescriptor.getTargetClassType(), proxyDescriptor.getInterfaceTypes(), proxyDescriptor.getProxyFeatures());
		}

		@Override
		public TypeSystem getTypeSystem() {
			return ts;
		}

		@Override
		public void addReflectiveAccess(String key, Flag... flags) {
			reflectionHandler.addAccess(key, flags);
		}

		@Override
		public void addReflectiveAccess(String typeName, AccessDescriptor descriptor) {
			reflectionHandler.addAccess(typeName, true, descriptor);
		}

		@Override
		public boolean hasReflectionConfigFor(String typename) {
			return collector.getClassDescriptorFor(typename)!=null;
		}

		@Override
		public void initializeAtBuildTime(Type type) {
			initializationHandler.initializeClassesAtBuildTime(type.getDottedName());
		}

		@Override
		public Set<String> addReflectiveAccessHierarchy(String typename, int accessBits) {
			Type type = ts.resolveDotted(typename, true);
			Set<String> added = new TreeSet<>();
			registerHierarchy(type, added, accessBits);
			return added;
		}
		
		private void registerHierarchy(Type type, Set<String> visited, int accessBits) {
			String typename = type.getDottedName();
			if (visited.add(typename)) {
				addReflectiveAccess(typename, AccessBits.getFlags(accessBits));
				Set<String> relatedTypes = type.getTypesInSignature();
				for (String relatedType: relatedTypes) {
					Type t = ts.resolveSlashed(relatedType, true);
					if (t!=null) {
						registerHierarchy(t, visited, accessBits);
					}
				}
			}
		}

		@Override
		public void log(String message) {
			logger.debug(message);
		}

		@Override
		public void addResourceBundle(String bundleName) {
			registerResourceBundles(ResourcesDescriptor.ofBundle(bundleName));
		}
		
	}
	
	private void processResponseBodyComponent(Type t) {
	  // If a controller is marked up @ResponseBody (possibly via @RestController), need to register reflective access to
	  // the return types of the methods marked @Mapping (meta marked) 
	  Collection<Type> returnTypes = t.collectAtMappingMarkedReturnTypes();
	  logger.debug("Found these return types from Mapped methods in "+t.getName()+" > "+returnTypes);
	  for (Type returnType: returnTypes ) {
		  if (returnType==null) {
			  continue;
		  }
		  if (returnType.getDottedName().startsWith("java.lang")) {
			  continue;
		  }
		  reflectionHandler.addAccess(returnType.getDottedName(), Flag.allDeclaredMethods, Flag.allDeclaredConstructors,Flag.allDeclaredFields);
	  }
	}

	// Code from petclinic that ends us up in here:
	// public interface VisitRepository { ... }
	// @org.springframework.stereotype.Repository @Transactional public class JpaVisitRepositoryImpl implements VisitRepository { ... }
	// Need proxy: [org.springframework.samples.petclinic.visit.VisitRepository,org.springframework.aop.SpringProxy,
	//              org.springframework.aop.framework.Advised,org.springframework.core.DecoratingProxy] 
	// And entering here with r = JpaVisitRepositoryImpl
	private void processRepository2(Type r) {
		logger.debug("Processing @oss.Repository annotated "+r.getDottedName());
		List<String> repositoryInterfaces = new ArrayList<>();
		for (String s: r.getInterfacesStrings()) {
			repositoryInterfaces.add(s.replace("/", "."));
		}
		repositoryInterfaces.add("org.springframework.aop.SpringProxy");
		repositoryInterfaces.add("org.springframework.aop.framework.Advised");
		repositoryInterfaces.add("org.springframework.core.DecoratingProxy");
		dynamicProxiesHandler.addProxy(repositoryInterfaces);
	}

	/**
	 * Walk a type hierarchy and register them all for reflective access.
	 * @param pc Processing context
	 * @param type the type whose hierarchy to register
	 * @param typesToMakeAccessible if non null required accesses are collected here rather than recorded directly on the runtime
	 */
	public void registerHierarchy(ProcessingContext pc, Type type, RequestedConfigurationManager typesToMakeAccessible) {
		AccessBits accessRequired = AccessBits.forValue(Type.inferAccessRequired(type));
		boolean isConfiguration = type.isAtConfiguration();
		if (!isConfiguration) {
			// Double check are we here because we are a parent of some configuration being processed
			// For example: 
			// Analyzing org.springframework.web.reactive.config.WebFluxConfigurationSupport 
			//   reached by 
			// [[Ctx:org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration-FromSpringFactoriesKey], 
			// [Ctx:org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration$EnableWebFluxConfiguration-NestedReference], 
			// [Ctx:org.springframework.web.reactive.config.DelegatingWebFluxConfiguration-HierarchyProcessing], 
			// [Ctx:org.springframework.web.reactive.config.WebFluxConfigurationSupport-HierarchyProcessing]]
			// TODO [0.9.0] tidyup
			String s2 = pc.getHierarchyProcessingTopMostTypename();
			TypeSystem typeSystem = type.getTypeSystem();
			Type resolve = typeSystem.resolveDotted(s2,true);
			isConfiguration = resolve.isAtConfiguration();
		}
		registerHierarchyHelper(type, new HashSet<>(), typesToMakeAccessible, accessRequired, isConfiguration);
	}
	
	private void registerHierarchyHelper(Type type, Set<Type> visited, RequestedConfigurationManager typesToMakeAccessible,
			AccessBits inferredRequiredAccess, boolean rootTypeWasConfiguration) {
		if (typesToMakeAccessible == null) {
			throw new IllegalStateException();
		}
		if (type == null || !visited.add(type)) {
			return;
		}
		if (type.isCondition()) {
			if (type.hasOnlySimpleConstructor()) {
				typesToMakeAccessible.requestTypeAccess(type.getDottedName(),inferredRequiredAccess.getValue());
			} else {
				typesToMakeAccessible.requestTypeAccess(type.getDottedName(),inferredRequiredAccess.getValue());
			}
		} else {
			// TODO we can do better here, why can we not use the inferredRequiredAccess -
			// it looks like we aren't adding RESOURCE to something when inferring.
			typesToMakeAccessible.requestTypeAccess(type.getDottedName(),
					AccessBits.DECLARED_CONSTRUCTORS|
					AccessBits.RESOURCE|(rootTypeWasConfiguration?AccessBits.DECLARED_METHODS:AccessBits.PUBLIC_METHODS));
//					inferredRequiredAccess.getValue());
			// reflectionHandler.addAccess(configNameDotted, Flag.allDeclaredConstructors,
			// Flag.allDeclaredMethods);
		}
		
		if (rootTypeWasConfiguration && !type.isAtConfiguration()) {
			// Processing a superclass of a configuration (so may contain @Bean methods)
		}
		// Rather than just looking at superclass and interfaces, this will dig into everything including
		// parameterized type references so nothing is missed
//		if (type.getSuperclass()!=null) {
//			logger.debug("RH>SC "+type.getSuperclass());
//		registerHierarchyHelper(type.getSuperclass(),visited, typesToMakeAccessible,inferredRequiredAccess);
//		}
//		Type[] intfaces = type.getInterfaces();
//		for (Type intface: intfaces) {
//			logger.debug("RH>IF "+intface);
//			registerHierarchyHelper(intface,visited, typesToMakeAccessible,inferredRequiredAccess);
//		}
/*		
		List<String> supers = new ArrayList<>();
		if (type.getSuperclass()!=null) {
			supers.add(type.getSuperclass().getDottedName());
		}
		Type[] intfaces = type.getInterfaces();
		for (Type intface: intfaces) {
			supers.add(intface.getDottedName());
		}
		List<String> lst = new ArrayList<>();
*/	
		
		Type superclass = type.getSuperclass();
		registerHierarchyHelper(superclass, visited, typesToMakeAccessible, inferredRequiredAccess, true);
		
		Set<String> relatedTypes = type.getTypesInSignature();
		for (String relatedType: relatedTypes) {
			Type t = ts.resolveSlashed(relatedType,true);
			if (t!=null) {
//				lst.add(t.getDottedName());
				registerHierarchyHelper(t, visited, typesToMakeAccessible, inferredRequiredAccess, false);
			}
		}
//		lst.removeAll(supers);
//		if (lst.size()!=0) {
//		logger.debug("MISSED THESE ("+type.getDottedName()+"): "+lst);
//		}
	}

	/**
	 * Find all META-INF/spring.factories - for any configurations listed in each,
	 * check if those configurations use ConditionalOnClass. If the classes listed
	 * in ConditionalOnClass can't be found, discard the configuration from
	 * spring.factories. Register either the unchanged or modified spring.factories
	 * files with the system.
	 */
	public void processSpringFactories() {
		logger.debug("Processing META-INF/spring.factories files...");
		for (byte[] springFactory: ts.getResources("META-INF/spring.factories")) {
			Properties p = new Properties();
			try (ByteArrayInputStream bais = new ByteArrayInputStream(springFactory)) {
				p.load(bais);
			} catch (IOException e) {
				throw new IllegalStateException("Unable to load bytes from spring factory file", e);
			}
//			loadSpringFactoryFile(springFactory, p);
			processSpringFactory(ts, p);
		}
//		Enumeration<URL> springFactories = fetchResources("META-INF/spring.factories");
//		while (springFactories.hasMoreElements()) {
//			URL springFactory = springFactories.nextElement();
//			processSpringFactory(ts, springFactory);
//		}
	}

	private List<Entry<Type, List<Type>>> filterOutNestedConfigurationTypes(List<Entry<Type, List<Type>>> indexedComponents) {
		List<Entry<Type, List<Type>>> filtered = new ArrayList<>();
		List<Entry<Type, List<Type>>> subtypesToRemove = new ArrayList<>();
		for (Entry<Type, List<Type>> indexedComponent : indexedComponents) {
			Type componentKey = indexedComponent.getKey();
			String type = componentKey.getDottedName();
			if (componentKey.isAtConfiguration()) {
				subtypesToRemove.addAll(indexedComponents.stream()
						.filter(e -> e.getKey().getDottedName().startsWith(type + "$")).collect(Collectors.toList()));
			}
		}
		filtered.addAll(indexedComponents);
		filtered.removeAll(subtypesToRemove);
		return filtered;
	}

	/**
	 * A key in a spring.factories file has a value that is a list of types. These
	 * will be accessed at runtime through an interface but must be reflectively
	 * instantiated. Hence reflective access to constructors but not to methods.
	 */
	private void registerTypeReferencedBySpringFactoriesKey(String s) {
		try {
			Type t = ts.resolveDotted(s, true);
			if (t != null) {
				// This 'name' may not be the same as 's' if 's' referred to an inner type -
				// 'name' will include the right '$' characters.
				String name = t.getDottedName();
				if (t.hasOnlySimpleConstructor() && t.isPublic()) {
					if (!t.getTypeSystem().isNativeNextMode()) {
						reflectionHandler.addAccess(name, new String[][] { { "<init>" } },null, false);
					}
				} else {
					reflectionHandler.addAccess(name, Flag.allDeclaredConstructors);
				}
			}
		} catch (NoClassDefFoundError ncdfe) {
			logger.debug(
					"spring.factories processing, problem adding access for key " + s + ": " + ncdfe.getMessage());
		}
	}

	private void processSpringFactory(TypeSystem ts, URL springFactory) {
		logger.debug("processing spring factory file "+springFactory);
		Properties p = new Properties();
		loadSpringFactoryFile(springFactory, p);
		processSpringFactory(ts, p);
	}
		
		
	private void processSpringFactory(TypeSystem ts, Properties p) {
		List<SpringFactoriesProcessor> springFactoriesProcessors = ts.getSpringFactoryProcessors();
		List<String> forRemoval = new ArrayList<>();
		Enumeration<Object> factoryKeys = p.keys();
		boolean modified = false;
		List<String> otherAutoConfigurationKeys = new ArrayList<>(); // e.g. org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
		
		if (!(aotOptions.toMode() == Mode.NATIVE_AGENT)) {
			Properties filteredProperties = new Properties();
			for (Map.Entry<Object, Object> factoriesEntry : p.entrySet()) {
				String key = (String) factoriesEntry.getKey();
				String valueString = (String) factoriesEntry.getValue();
				List<String> values = new ArrayList<>();
				for (String value : valueString.split(",")) {
					values.add(value);
				}
				if (aotOptions.isRemoveUnusedConfig()) {
					for (SpringFactoriesProcessor springFactoriesProcessor : springFactoriesProcessors) {
						int len = values.size();
						if (springFactoriesProcessor.filter(key, values)) {
							logger.debug("Spring factory filtered by "+springFactoriesProcessor.getClass().getName()+" removing "+(len-values.size())+" entries");
							modified = true;
						}
					}
				}
				if (modified) {
					filteredProperties.put(key, String.join(",", values));
				} else {
					filteredProperties.put(key, valueString);
				}
			}
			p = filteredProperties;
		}

		factoryKeys = p.keys();
		// Handle all keys other than EnableAutoConfiguration and PropertySourceLoader
		if (!(aotOptions.toMode() == Mode.NATIVE_AGENT)) {
			while (factoryKeys.hasMoreElements()) {
				String k = (String) factoryKeys.nextElement();
				logger.debug("Adding all the classes for this key: " + k);
				if (!k.equals(enableAutoconfigurationKey) 
						&& !k.equals(propertySourceLoaderKey)
						&& !k.equals(managementContextConfigurationKey) 
						) {
					if (k.equals("org.springframework.boot.diagnostics.FailureAnalyzer") ||
						k.equals("org.springframework.context.ApplicationListener") ||
						k.equals("org.springframework.context.ApplicationContextInitializer") ||
						((k.equals("org.springframework.context.origin.BeanDefinitionOriginAnalyzer") || 
						  k.equals("org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitializationDetector") || 
						  k.equals("org.springframework.context.bootstrap.generator.bean.BeanValueWriterSupplier")) && aotOptions.toMode()==Mode.NATIVE_NEXT)
						) {
						// TODO really needs to register all those values without a no arg ctor (for now)
						// something like this:
						/*
						for (String v: p.getProperty(k).split(",")) {
							Type t = ts.resolveDotted(v, true);
							if (t != null) {
								// This 'name' may not be the same as 'v' if 'v' referred to an inner type -
								// 'name' will include the right '$' characters.
								String name = t.getDottedName();
								if (!t.hasNoArgConstructor()) {
									reflectionHandler.addAccess(name, Flag.allDeclaredConstructors);
								}
							}	
						}
						*/
						continue;
					}
					if (k.equals("org.springframework.boot.env.EnvironmentPostProcessor")
						|| k.equals("org.springframework.boot.context.config.ConfigDataLoader")
						|| k.equals("org.springframework.boot.logging.LoggingSystemFactory")
//						 || k.equals("org.springframework.boot.env.PropertySourceLoader")
						) {
						for (String v: p.getProperty(k).split(",")) {
							Type t = ts.resolveDotted(v, true);
							if (t != null) {
								// This 'name' may not be the same as 'v' if 'v' referred to an inner type -
								// 'name' will include the right '$' characters.
								String name = t.getDottedName();
								Method defaultConstructor = t.getDefaultConstructor();
								if (defaultConstructor == null || defaultConstructor.hasAnnotation("Ljava/lang/Deprecated;", false)) {
									reflectionHandler.addAccess(name, Flag.allDeclaredConstructors);
								}
							}	
						}
						continue;
					} else if (k.startsWith("org.springframework.boot.test.autoconfigure.")) {
						// TODO [issue839] smarter test, check what kind of thing it points at rather than that prefix
						otherAutoConfigurationKeys.add(k);
						continue;
					}
					if (ts.shouldBeProcessed(k)) {
						//org.springframework.boot.autoconfigure.AutoConfigurationImportFilter=org.springframework.boot.autoconfigure.condition.OnBeanCondition,org.springframework.boot.autoconfigure.condition.OnClassCondition,org.springframework.boot.autoconfigure.condition.OnWebApplicationCondition	
						if (!k.equals("org.springframework.boot.autoconfigure.AutoConfigurationImportFilter") || !ts.isNativeNextMode()) {
							for (String v : p.getProperty(k).split(",")) {
								registerTypeReferencedBySpringFactoriesKey(v);
							}
						}
					} else {
						logger.debug("Skipping processing spring.factories key " + k + " due to missing guard types");
					}
				}
			}
		}

		if (!(aotOptions.toMode() == Mode.NATIVE_AGENT)) {
			// Handle PropertySourceLoader
			String propertySourceLoaderValues = (String) p.get(propertySourceLoaderKey);
			if (propertySourceLoaderValues != null) {
				for (String v: propertySourceLoaderValues.split(",")) {
					Type t = ts.resolveDotted(v, true);
					if (t != null) {
						String name = t.getDottedName();
						Method defaultConstructor = t.getDefaultConstructor();
						if (defaultConstructor == null || defaultConstructor.hasAnnotation("Ljava/lang/Deprecated;", false)) {
							reflectionHandler.addAccess(name, Flag.allDeclaredConstructors);
						}
					}
				}
			}
		}

		modified = processConfigurationsWithKey(p, managementContextConfigurationKey) || modified;
		
		// Handle EnableAutoConfiguration
		// TODO [issue839] sort out method signature, forRemoval not needed, use retval for whether it did anything
		processFactoriesKey(p, enableAutoconfigurationKey, forRemoval);
		for (String key: otherAutoConfigurationKeys) {
			processFactoriesKey(p, key, forRemoval);
		}

		if (forRemoval.size() > 0) {
			String existingRC = ts.findAnyResourceConfigIncludingSpringFactoriesPattern();
			if (existingRC != null) {
				logger.debug("WARNING: unable to trim META-INF/spring.factories (for example to disable unused auto configurations)"+
					" because an existing resource-config is directly including it: "+existingRC);
				return;
			}
		}	

		// Filter spring.factories if necessary
		try {
			if (forRemoval.size() == 0 && !modified) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				p.store(baos, null);
				byte[] bs = baos.toByteArray();
				collector.registerResource("META-INF/spring.factories", bs);
//				Resources.registerResource("META-INF/spring.factories", springFactory.openStream());
			} else {
				logger.debug("  removed " + forRemoval.size() + " classes");
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				p.store(baos, "");
				baos.close();
				byte[] bs = baos.toByteArray();
				logger.debug("The new spring.factories is: vvvvvvvvv");
				logger.debug(new String(bs));
				logger.debug("^^^^^^^^");
//				ByteArrayInputStream bais = new ByteArrayInputStream(bs);
				collector.registerResource("META-INF/spring.factories", bs);
//				Resources.registerResource("META-INF/spring.factories", bais);
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void processFactoriesKey(Properties p, String key, List<String> forRemoval) {
		String enableAutoConfigurationValues = (String) p.get(key);
		int excludedAutoConfigCount = 0;
		if (enableAutoConfigurationValues != null) {
			List<String> configurations = new ArrayList<>();
			for (String s : enableAutoConfigurationValues.split(",")) {
				configurations.add(s);
			}
			logger.debug("Processing spring.factories - "+key+" lists #" + configurations.size()
					+ " configurations");
			for (String config : configurations) {
				if (!checkAndRegisterConfigurationType(config,ReachedBy.FromSpringFactoriesKey)) {
					if (aotOptions.isRemoveUnusedConfig()) {
						excludedAutoConfigCount++;
						logger.debug("Excluding auto-configuration " + config);
						forRemoval.add(config);
					}
				}
			}
			if (aotOptions.isRemoveUnusedConfig()) {
				logger.debug(
						"Excluding " + excludedAutoConfigCount + " auto-configurations from spring.factories file");
				configurations.removeAll(forRemoval);
				p.put(key, String.join(",", configurations));
				logger.debug("These configurations are remaining in the "+key+" key value:");
				for (int c = 0; c < configurations.size(); c++) {
					logger.debug((c + 1) + ") " + configurations.get(c));
				}
			}
		}
	}

	/**
	 * Find the configurations referred to by the specified key in the specified properties object.
	 * Process each configuration and if it fails conditional checks (e.g. @ConditionalOnClass)
	 * then it is considered inactive and removed. The key is rewritten with a new list of configurations
	 * with inactive ones removed.
	 * @param p the properties object
	 * @param configurationsKey the key into the properties object whose value is a configurations list
	 * 
	 * @return true if inactive configurations were discovered and removed
	 */
	private boolean processConfigurationsWithKey(Properties p, String configurationsKey) {
		boolean modified = false;
		List<String> inactiveConfigurations = new ArrayList<>();
		String configurationsValue = (String)p.get(configurationsKey);
		if (configurationsValue != null) {
			List<String> configurations = Stream.of(configurationsValue.split(",")).collect(Collectors.toList());
			for (String configuration: configurations) {
				if (!checkAndRegisterConfigurationType(configuration,ReachedBy.FromSpringFactoriesKey)) {
					if (aotOptions.isRemoveUnusedConfig()) {
						logger.debug("Excluding auto-configuration (key="+configurationsKey+") =" +configuration);
						inactiveConfigurations.add(configuration);
					}
				}
			}
			if (aotOptions.isRemoveUnusedConfig() && inactiveConfigurations.size()>0) {
				int totalConfigurations = configurations.size();
				configurations.removeAll(inactiveConfigurations);
				p.put(configurationsKey, String.join(",", configurations));
				logger.debug("Removed "+inactiveConfigurations.size()+" of the "+totalConfigurations+" configurations specified for the key "+configurationsKey);
				modified = true;
			}
		}
		return modified;
	}

	private void loadSpringFactoryFile(URL springFactory, Properties p) {
		try (InputStream is = springFactory.openStream()) {
			p.load(is);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load spring.factories", e);
		}
	}

	/**
	 * For the specified type (dotted name) determine which types must be
	 * reflectable at runtime. This means looking at annotations and following any
	 * type references within those. These types will be registered. If there are
	 * any issues with accessibility of required types this will return false
	 * indicating it can't be used at runtime.
	 */
	private boolean checkAndRegisterConfigurationType(String typename, ReachedBy reachedBy) {
		return processType(new ProcessingContext(), typename, reachedBy);
	}

	private boolean processType(ProcessingContext pc, String typename, ReachedBy reachedBy) {
		logger.debug("\n\nProcessing type " + typename);
		Type resolvedConfigType = ts.resolveDotted(typename,true);
		if (resolvedConfigType==null) {
			logger.debug("Configuration type " + typename + " is missing - presuming stripped out - considered failed validation");
			return false;
		} 
		boolean b = processType(pc, resolvedConfigType, reachedBy);
		logger.debug("Configuration type " + typename + " has " + (b ? "passed" : "failed") + " validation");
		return b;
	}

	/**
	 * Specific type references are used when registering types not easily identifiable from the
	 * bytecode that we are simply capturing as specific references in the Hints defined
	 * in the configuration module. Used for import selectors, import registrars, configuration.
	 * For @Configuration types here, need only bean method access (not all methods), for 
	 * other types (import selectors/etc) may not need any method reflective access at all
	 * (and no resource access in that case).
	 * @param pc 
	 */
	private boolean registerSpecific(ProcessingContext pc, String typename, AccessDescriptor ad, RequestedConfigurationManager rcm) {
		int accessBits = ad.getAccessBits();
		Type t = ts.resolveDotted(typename, true);
		if (t == null) {
			logger.debug("WARNING: Unable to resolve specific type: " + typename);
			return false;
		} else {
			boolean importRegistrarOrSelector = false;
			try {
				importRegistrarOrSelector = t.isImportRegistrar() || t.isImportSelector();
			} catch (MissingTypeException mte) {
				// something is missing, reflective access not going to work here!
				return false;
			}
			if (importRegistrarOrSelector) {
				int bits = AccessBits.CLASS | AccessBits.DECLARED_CONSTRUCTORS;
				if (t.isImportRegistrar()) { // A kafka registrar seems to need this
					bits|=AccessBits.RESOURCE;
				}
				rcm.requestTypeAccess(typename, bits, ad.getMethodDescriptors(),ad.getFieldDescriptors());
			} else {
				// e.g. RedisCacheConfiguration hint directly on ImportSelector
				if (!ts.isNativeNextMode() || !t.isAtConfiguration()) {
					if (AccessBits.isResourceAccessRequired(accessBits)) {
						rcm.requestTypeAccess(typename, AccessBits.RESOURCE);
						rcm.requestTypeAccess(typename, accessBits, ad.getMethodDescriptors(), ad.getFieldDescriptors());
					} else {
						rcm.requestTypeAccess(typename, accessBits, ad.getMethodDescriptors(), ad.getFieldDescriptors());
						// TODO worth limiting it solely to @Bean methods? Need to check how many
						// configuration classes typically have methods that are not @Bean
					}
				}
				if (t.isAtConfiguration() && !ts.isNativeNextMode()) {
					// This is because of cases like Transaction auto configuration where the
					// specific type names types like ProxyTransactionManagementConfiguration
					// are referred to from the AutoProxyRegistrar CompilationHint.
					// There is a conditional on bean later on the supertype
					// (AbstractTransactionConfiguration)
					// and so we must register proxyXXX and its supertypes as visible.
					registerHierarchy(pc, t, rcm);
				}
			}
			return true;
		}
	}
	
	/**
	 * Captures the route taken when processing a type - we can be more aggressive about
	 * discarding information depending on the route taken. For example it is easy
	 * to modify spring.factories to no longer to refer to an autoconfiguration, but
	 * you can't throw away a configuration if referenced from @Import in another type.
	 * (You can create a shell of a representation for the unnecessary config but
	 * you cannot discard entirely because @Import processing will break).
	 */
	enum ReachedBy {
		FromRoot,
		Import,
		Other,
		FromSpringFactoriesKey, // the type reference was discovered from a spring.factories entry
		FromSpringComponent, // the type reference was discovered when reviewing spring.components
		AtBeanReturnType, // the type reference is the return type of an @Bean method
		NestedReference, // This was discovered as a nested type within some type currently being processed
		HierarchyProcessing, // This was discovered whilst going up the hierarchy from some type currently being processed
		Inferred, // This type was 'inferred' whilst processing a hint (e.g. the class in a @COC usage)
		Specific, // This type was explicitly listed in a hint that was processed
		InnerOfNestedCondition // it is the inner type of a class implementing AbstractNestedCondition
	}
	
	private boolean checkJmxConstraint(Type type, ProcessingContext pc) {
		if (aotOptions.isRemoveJmxSupport()) {
			if (type.getDottedName().toLowerCase().contains("jmx") && 
					!(pc.peekReachedBy()==ReachedBy.Import || pc.peekReachedBy()==ReachedBy.NestedReference)) {
				logger.debug(type.getDottedName()+" FAILED validation - it has 'jmx' in it - returning FALSE");
				if (!aotOptions.isRemoveUnusedConfig()) {
//					resourcesRegistry.addResources(type.getDottedName().replace(".", "/")+".class");
					collector.addResource(type.getDottedName().replace(".", "/")+".class", false);
				}
				return false;
			}
		}
		return true;
	}


//	private boolean checkConditionalOnEnabledMetricsExport(Type type) {
//		boolean isOK = type.testAnyConditionalOnEnabledMetricsExport();
//		if (!isOK) {
//			logger.debug(type.getDottedName()+" FAILED ConditionalOnEnabledMetricsExport check - returning FALSE");
//			return false;
//		}
//		return true;
//	}
	
	List<String> failedPropertyChecks = new ArrayList<>();
	
	/**
	 * It is possible to ask for property checks to be done at build time - this enables chunks of code to be discarded early
	 * and not included in the image. 
	 * 
	 * @param type the type that may have property related conditions on it
	 * @return true if checks pass, false if one fails and the type should be considered inactive
	 */
	private boolean checkPropertyRelatedConditions(Type type) {
		// Problems observed discarding inner configurations due to eager property checks
		// (configserver sample). Too aggressive, hence the $ check
		if (aotOptions.isBuildTimePropertyChecking() && !type.getName().contains("$")) {
			String testResult = TypeUtils.testAnyConditionalOnProperty(type, aotOptions);
			if (testResult != null) {
				String message = type.getDottedName()+" FAILED ConditionalOnProperty property check: "+testResult;
				failedPropertyChecks.add(message);
				logger.debug(message);
				return false;
			}
			// These are like a ConditionalOnProperty check but using a special condition to check the property
			testResult = TypeUtils.testAnyConditionalOnAvailableEndpoint(type, aotOptions);
			if (testResult != null) {
				String message =  type.getDottedName()+" FAILED ConditionalOnAvailableEndpoint property check: "+testResult;
				failedPropertyChecks.add(message);
				logger.debug(message);
				return false;
			}
			testResult = TypeUtils.testAnyConditionalOnEnabledMetricsExport(type, aotOptions);
			if (testResult != null) {
				String message = type.getDottedName()+" FAILED ConditionalOnEnabledMetricsExport property check: "+testResult;
				failedPropertyChecks.add(message);
				logger.debug(message);
				return false;
			}
			testResult = TypeUtils.testAnyConditionalOnEnabledHealthIndicator(type, aotOptions);
			if (testResult != null) {
				String message = type.getDottedName()+" FAILED ConditionalOnEnabledHealthIndicator property check: "+testResult;
				failedPropertyChecks.add(message);
				logger.debug(message);
				return false;
			}
		}
		return true;
	}

	private boolean checkConstraintMissingTypesInHierarchyOfThisType(Type type) {
		// Check the hierarchy of the type, if bits are missing resolution of this
		// type at runtime will not work - that suggests that in this particular
		// run the types are not on the classpath and so this type isn't being used.
		Set<String> missingTypes = ts.findMissingTypesInHierarchyOfThisType(type);
		if (!missingTypes.isEmpty()) {
			logger.debug("for " + type.getName() + " missing types in hierarchy are " + missingTypes );
			if (aotOptions.isRemoveUnusedConfig()) {
				return false;
			}
		}
		return true;
	}

	private boolean isIgnoredConfiguration(Type type) {
		if (aotOptions.isIgnoreHintsOnExcludedConfig() && type.isAtConfiguration()) {
			if (isIgnored(type)) {
				logger.debug("skipping hint processing on "+type.getName()+" because it is explicitly excluded in this application");
				return true;
			}
		}
		return false;
	}

	private void checkMissingAnnotationsOnType(Type type) {
		Set<String> missingAnnotationTypes = ts.resolveCompleteFindMissingAnnotationTypes(type);
		if (!missingAnnotationTypes.isEmpty()) {
			// If only the annotations are missing, it is ok to reflect on the existence of
			// the type, it is just not safe to reflect on the annotations on that type.
			logger.debug("for " + type.getName() + " missing annotation types are "
					+ missingAnnotationTypes);
		}
	}
	
	static class ContextEntry {
		private String typename;
		private ReachedBy reachedBy;
		ContextEntry(String typename, ReachedBy reachedBy) {
			this.typename = typename;
			this.reachedBy = reachedBy;
		}
		public String toString() {
			return "[Ctx:"+typename+"-"+reachedBy+"]";
		}
	}

	/**
	 * The ProcessingContext keeps track of the route taken through chasing hints/configurations.
	 * At any point this means we can know why we are processing a type, processing of some types
	 * may need to know about why it is being looked at. For example the superclass of a configuration
	 * may not have @ Configuration on it and yet have @ Bean methods in it.
	 */
	@SuppressWarnings("serial")
	static class ProcessingContext extends Stack<ContextEntry> {
		
		// Keep track of everything seen during use of this ProcessingContext
		private Set<String> visited = new HashSet<>();

		public static ProcessingContext of(String typename, ReachedBy reachedBy) {
			ProcessingContext pc = new ProcessingContext();
			pc.push(new ContextEntry(typename, reachedBy));
			return pc;
		}

		public ReachedBy peekReachedBy() {
			return peek().reachedBy;
		}

		public ContextEntry push(Type type, ReachedBy reachedBy) {
			return push(new ContextEntry(type.getDottedName(), reachedBy));
		}

		public boolean recordVisit(String name) {
			return visited.add(name);
		}

		public int depth() {
			return size();
		}
		
		public String getHierarchyProcessingTopMostTypename() {
			// Double check are we here because we are a parent of some configuration being processed
						// For example: 
						// Analyzing org.springframework.web.reactive.config.WebFluxConfigurationSupport 
						//   reached by 
						// [[Ctx:org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration-FromSpringFactoriesKey], 
						// [Ctx:org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration$EnableWebFluxConfiguration-NestedReference], 
						// [Ctx:org.springframework.web.reactive.config.DelegatingWebFluxConfiguration-HierarchyProcessing], 
						// [Ctx:org.springframework.web.reactive.config.WebFluxConfigurationSupport-HierarchyProcessing]]
			int i = size()-1;
			ContextEntry entry = get(i);
			while (entry.reachedBy==ReachedBy.HierarchyProcessing) {
				if (i==0) {
					break;
				}
				entry = get(--i);
			}
			// Now entry points to the first non HierarchyProcessing case
			return entry.typename;
		}

		public boolean isFromSpringFactoriesKey() {
			ContextEntry contextEntry = get(0);
			return contextEntry.reachedBy==ReachedBy.FromSpringFactoriesKey;
		}
		
	}

	private boolean processType(ProcessingContext pc, Type type, ReachedBy reachedBy) {
		pc.push(type, reachedBy);
		String typename = type.getDottedName();
		logger.debug("Analyzing " + typename + " reached by " + pc);
		
		if (!checkJmxConstraint(type, pc)) {
			pc.pop();
			return false;
		}
		
		if (!checkPropertyRelatedConditions(type)) {
			pc.pop();
			return false;
		}
		
		if (!checkConstraintMissingTypesInHierarchyOfThisType(type)) {
			pc.pop();
			return false;
		}

//		if (!checkConditionalOnBean(type) || !checkConditionalOnMissingBean(type) || !checkConditionalOnClass(type)) {
//			pc.pop();
//			return false;
//		}
		
		checkMissingAnnotationsOnType(type);

		if (isIgnoredConfiguration(type)) {
			// You may wonder why this is not false? That is because if we return false it will be deleted from
			// spring.factories. Then later when Spring processes the spring exclude autoconfig key that contains this
			// name - it will fail with an error that it doesn't refer to a valid configuration. So here we return true,
			// which isn't optimal but we do skip all the hint processing and further chasing from this configuration.
			pc.pop();
			return true;
		}

		boolean passesTests = true;
		RequestedConfigurationManager accessManager = new RequestedConfigurationManager();
		List<HintApplication> hints = type.getApplicableHints();
		printHintSummary(type, hints);
		Map<Type,ReachedBy> toFollow = new HashMap<>();
		for (HintApplication hint : hints) {
			logger.debug("processing hint " + hint);
			RequestedConfigurationManager hintRCM = new RequestedConfigurationManager();
			passesTests = processExplicitTypeReferencesFromHint(pc, hintRCM, hint, toFollow);
			if (!passesTests && aotOptions.isRemoveUnusedConfig()) {
				break;
			}
			passesTests = processImplicitTypeReferencesFromHint(pc, hintRCM, type, hint, toFollow);
			if (!passesTests && aotOptions.isRemoveUnusedConfig()) {
				break;
			}
			if (!ts.isNativeNextMode() || !isConditionalChain(hint.getAnnotationChain())) {
				accessManager.mergeIn(hintRCM);
				registerAnnotationChain(accessManager, hint.getAnnotationChain());
				accessManager.requestProxyDescriptors(hint.getProxyDescriptors());
				accessManager.requestResourcesDescriptors(hint.getResourceDescriptors());
				accessManager.requestInitializationDescriptors(hint.getInitializationDescriptors());
				accessManager.requestOptions(hint.getOptions());
				accessManager.requestSerializationTypes(hint.getSerializationTypes());
				accessManager.requestJniTypes(hint.getJNITypes());
			}
		}

		// TODO think about pulling out into extension mechanism for condition evaluators
		// Special handling for @ConditionalOnWebApplication
		if (!type.checkConditionalOnWebApplication() && (pc.depth()==1 || isNestedConfiguration(type))) {
			passesTests = false;
		}

		pc.recordVisit(type.getName());
		if (passesTests || !aotOptions.isRemoveUnusedConfig()) {
			processHierarchy(pc, accessManager, type);
		}
		
		checkForImportedConfigurations(type, toFollow);

		if (passesTests || !aotOptions.isRemoveUnusedConfig()) {
			if (type.isAtComponent() && aotOptions.isVerify()) {
				type.verifyComponent();
			}
//			if (type.isComponent()) {
//				List<ComponentProcessor> componentProcessors = ts.getComponentProcessors();
//				Entry<Type, List<Type>> relevantStereotypes = type.getRelevantStereotypes();
//				if (relevantStereotypes != null) {
//					List<String> classifiers = relevantStereotypes.getValue().stream().map(t -> t.getDottedName())
//							.collect(Collectors.toList());
//					NativeContext context = new NativeContextImpl();
//					logger.debug("> running component processors on "+type.getDottedName()+" with classifiers "+classifiers);
//					for (ComponentProcessor componentProcessor: componentProcessors) {
//						if (componentProcessor.handle(context, type.getDottedName(), classifiers)) {
//							componentProcessor.process(context, type.getDottedName(), classifiers);
//						}
//					}	
//				}
//				logger.debug("< running component processors on "+type.getDottedName());
//			}
			
			// prevent this in native-next
			if (!ts.isNativeNextMode() && type.isAtConfiguration()) {
				checkForAutoConfigureBeforeOrAfter(type, accessManager);
				String[][] validMethodsSubset = processTypeAtBeanMethods(pc, accessManager, toFollow, type);
				if (validMethodsSubset != null) {
					printMemberSummary("These are the valid @Bean methods",validMethodsSubset);
					/*
					 * Think about activating this code - it produces member level correctness configuration for configurations
					 * but it doesn't really help the figures.
					 */
					/*
					Integer access = accessManager.getTypeAccessRequestedFor(type.getDottedName());
					System.out.println("Access was "+AccessBits.toString(access));
					if ((access & AccessBits.DECLARED_METHODS)!=0) {
						access-=AccessBits.DECLARED_METHODS;
					System.out.println("Access reduced to "+AccessBits.toString(access));
						accessManager.reduceTypeAccess(type.getDottedName(),access);
					}
					accessManager.addMethodDescriptors(type.getDottedName(), validMethodsSubset);
					*/
				}
			}
			processTypesToFollow(pc, accessManager, type, reachedBy, toFollow);
			registerAllRequested(accessManager);
		}

		// If the outer type is failing a test, we don't need to go into nested types...
		if (passesTests || !aotOptions.isRemoveUnusedConfig()) {
			processNestedTypes(pc, type);
		}
		pc.pop();
		return passesTests;
	}

	private boolean isConditionalChain(List<Type> annotationChain) {
		if (annotationChain.size()>1 && 
			(annotationChain.get(1).getDottedName().equals("org.springframework.boot.autoconfigure.condition.ConditionalOnClass") ||
			 annotationChain.get(1).getDottedName().equals("org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean") ||
			 annotationChain.get(1).getDottedName().equals("org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate"))) {
			return true;
		}
		return false;
	}

	private void checkForImportedConfigurations(Type type, Map<Type, ReachedBy> toFollow) {
		List<String> importedConfigurations = type.getImportedConfigurations();
		if (importedConfigurations.size()>0) {
			logger.debug("found these imported configurations by "+type.getDottedName()+": "+importedConfigurations);
		}
		for (String importedConfiguration: importedConfigurations) {
			toFollow.put(ts.resolveSlashed(Type.fromLdescriptorToSlashed(importedConfiguration)),ReachedBy.Import);
		}
	}


	// This type might have @AutoConfigureAfter/@AutoConfigureBefore references to
	// other configurations.
	// Those may be getting discarded in this run but need to be class accessible
	// because this configuration needs to refer to them.
	private void checkForAutoConfigureBeforeOrAfter(Type type, RequestedConfigurationManager accessManager) {
		List<Type> boaTypes = type.getAutoConfigureBeforeOrAfter();
		if (boaTypes.size() != 0) {
			logger.debug("registering " + boaTypes.size() + " @AutoConfigureBefore/After references");
			for (Type t : boaTypes) {
				List<Type> transitiveBOAs = t.getAutoConfigureBeforeOrAfter();
				// If this linked configuration also has @AutoconfigureBeforeOrAfter, we need to include it as a
				// resource so that Spring will find these transitive dependencies on further configurations.
				if (transitiveBOAs.size()!=0) {
					accessManager.requestTypeAccess(t.getDottedName(), AccessBits.CLASS|AccessBits.RESOURCE);
				} else {
					accessManager.requestTypeAccess(t.getDottedName(), AccessBits.CLASS);
				}
			}
		}
	}

	private void processTypesToFollow(ProcessingContext pc, RequestedConfigurationManager accessManager,
			Type type, ReachedBy reachedBy, Map<Type, ReachedBy> toFollow) {
		// Follow transitively included inferred types only if necessary:
		for (Map.Entry<Type,ReachedBy> entry : toFollow.entrySet()) {
			Type t = entry.getKey();
			if (aotOptions.toMode() == Mode.NATIVE_AGENT && t.isAtConfiguration()) {
				boolean existsInVisibleConfig = existingReflectionConfigContains(t.getDottedName()); // Only worth following if this config is active...
				if (!existsInVisibleConfig) {
					logger.debug("in agent mode not following "+t.getDottedName()+" from "+type.getName()+" - it is not mentioned in existing reflect configuration");
					continue;
				}
			}
			if (!followed.add(t.getDottedName())) {
				logger.debug("already followed "+t.getDottedName());
				continue;
			}
			try {
				boolean b = processType(pc, t, entry.getValue());
				if (!b) {
					logger.debug("followed " + t.getName() + " and it failed validation (whilst processing "+type.getDottedName()+" reached by "+reachedBy+")");
//						if (t.isAtConfiguration()) {
//							accessRequestor.removeTypeAccess(t.getDottedName());
//						} else {
						accessManager.reduceTypeAccess(t.getDottedName(),AccessBits.DECLARED_CONSTRUCTORS|AccessBits.CLASS|AccessBits.RESOURCE);
//						}
				}
			} catch (MissingTypeException mte) {
				// Failed to follow that type because some element involved is not on the classpath 
				// (Typically happens when not specifying discard-unused-autconfiguration)
				logger.debug("Unable to completely process followed type "+t.getName()+": "+mte.getMessage());
			}
		}
	}

	private void processHierarchy(ProcessingContext pc, RequestedConfigurationManager accessManager, Type type) {
		logger.debug(">processHierarchy "+type.getShortName());
		String typename = type.getDottedName();
		boolean isConfiguration = type.isAtConfiguration();
		if (!isConfiguration) {
			// Double check are we here because we are a parent of some configuration being processed
			// For example: 
			// Analyzing org.springframework.web.reactive.config.WebFluxConfigurationSupport 
			//   reached by 
			// [[Ctx:org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration-FromSpringFactoriesKey], 
			// [Ctx:org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration$EnableWebFluxConfiguration-NestedReference], 
			// [Ctx:org.springframework.web.reactive.config.DelegatingWebFluxConfiguration-HierarchyProcessing], 
			// [Ctx:org.springframework.web.reactive.config.WebFluxConfigurationSupport-HierarchyProcessing]]
			// TODO [0.9.0] tidyup
			String s2 = pc.getHierarchyProcessingTopMostTypename();
			TypeSystem typeSystem = type.getTypeSystem();
			Type resolve = typeSystem.resolveDotted(s2,true);
			isConfiguration = resolve.isAtConfiguration();
		}

		if (!ts.isNativeNextMode() || (!isConfiguration && !type.isCondition())) {
			accessManager.requestTypeAccess(typename, Type.inferAccessRequired(type));
			// TODO need this guard? if (isConfiguration(configType)) {
			registerHierarchy(pc, type, accessManager);
		}

		recursivelyCallProcessTypeForHierarchyOfType(pc, type);
		logger.debug("<processHierarchy "+type.getShortName());
	}

	private void processNestedTypes(ProcessingContext pc, Type type) {
		logger.debug(" processing nested types of "+type.getName());
		List<Type> nestedTypes = type.getNestedTypes();
		for (Type t : nestedTypes) {
			if (pc.recordVisit(t.getName())) {
				if (!(t.isAtConfiguration() || (t.isConditional() && !ts.isNativeNextMode()) || t.isMetaImportAnnotated() || t.isComponent())) {
					continue;
				}
				try {
					boolean b = processType(pc, t, ReachedBy.NestedReference);
					if (!b) {
						logger.debug("verification of nested type " + t.getName() + " failed");
					}
				} catch (MissingTypeException mte) {
					// Failed to process that type because some element involved is not on the classpath 
					// (Typically happens when not specifying discard-unused-autoconfiguration)
					logger.debug("Unable to completely process nested type "+t.getName()+": "+mte.getMessage());
				}
			}
		}
	}

	private boolean processImplicitTypeReferencesFromHint(ProcessingContext pc,
			RequestedConfigurationManager accessRequestor, Type type, HintApplication hint, Map<Type, ReachedBy> toFollow) {
		boolean passesTests = true;
		Map<String, Integer> inferredTypes = hint.getInferredTypes();
		if (inferredTypes.size() > 0) {
			logger.debug("attempting registration of " + inferredTypes.size() + " inferred types");
			for (Map.Entry<String, Integer> inferredType : inferredTypes.entrySet()) {
				String s = inferredType.getKey();
				Type t = ts.resolveDotted(s, true);
				boolean exists = (t != null);
				if (!exists) {
					logger.debug("inferred type " + s + " not found");
				}
				if (exists) {
					accessRequestor.requestTypeAccess(s, inferredType.getValue());
					
					if (hint.isFollow() || t.shouldFollow()) {
						logger.debug("will follow " + t);
						ReachedBy reason = isImportHint(hint)?ReachedBy.Import:ReachedBy.Inferred;
						toFollow.put(t,reason);
					} else if (t.isAbstractNestedCondition()) {
						// The inner types are hosting conditions
						logger.debug("will follow inner types inside this nested condition type " + t);
						for (Type inner : t.getNestedTypes()) {
							toFollow.put(inner, ReachedBy.InnerOfNestedCondition);
						}
					}
				} else if (hint.isSkipIfTypesMissing() && (pc.depth() == 1 || isNestedConfiguration(type) /*|| reachedBy==ReachedBy.Specific*/ || pc.peekReachedBy()==ReachedBy.Import)) {
					if (pc.depth()>1) {
						logger.debug("inferred type missing: "+s+" (processing type: "+type.getDottedName()+" reached by "+pc.peekReachedBy()+") - discarding "+type.getDottedName());
					}
					// Notes: if an inferred type is missing, we have to be careful. Although it should suggest we discard
					// the type being processed, that is not always possible depending on how the type being processed
					// was reached.  If the type being processed came from a spring.factories file, fine, we can throw it away
					// and modify spring.factories. But if we started processing this type because it was listed in an @Import
					// reference, we can't throw it away completely because Spring will fail when it can't class load something
					// listed in @Import. What we can do is create a minimal shell of a type (not allow member reflection) but
					// we can't discard it completely. So the checks here are trying to be careful about what we can throw away.
					// Because nested configuration is discovered solely by reflecting on outer configuration, we can discard
					// any types being processed that were reached by digging into nested types.
					passesTests = false;
					// Once failing, no need to process other hints
					if (aotOptions.isRemoveUnusedConfig()) {
						break;
					}
				}
			}
		}
		return passesTests;
	}

	private boolean processExplicitTypeReferencesFromHint(ProcessingContext pc, 
			RequestedConfigurationManager accessRequestor, HintApplication hint, Map<Type, ReachedBy> toFollow) {
		boolean passesTests = true;
		Map<String, AccessDescriptor> specificNames = hint.getSpecificTypes();
		if (specificNames.size() > 0) {
			logger.debug("attempting registration of " + specificNames.size() + " specific types");
			for (Map.Entry<String, AccessDescriptor> specificNameEntry : specificNames.entrySet()) {
				String specificTypeName = specificNameEntry.getKey();
				if (!registerSpecific(pc, specificTypeName, specificNameEntry.getValue(), accessRequestor)) {
					if (hint.isSkipIfTypesMissing()) {
						passesTests = false;
						if (aotOptions.isRemoveUnusedConfig()) {
							break;
						}
					}
				} else {
					Type type = ts.resolveDotted(specificTypeName,true);
					if (hint.isFollow() || (type!=null && type.shouldFollow())) {					
						logger.debug( "will follow specific type reference " + specificTypeName);
						toFollow.put(ts.resolveDotted(specificTypeName),ReachedBy.Specific);
					}
				}
			}
		}
		return passesTests;
	}

	/**
	 * This handles when some @Bean methods on type do not need runtime access (because they have conditions
	 * on them that failed when checked). In this case the full list of methods should be spelled out
	 * excluding the unnecessary ones. If validMethodsSubset is null then all @Bean methods are valid.
	 */
	private void configureMethodAccess(RequestedConfigurationManager accessRequestor, Type type,
			String[][] validMethodsSubset, boolean atBeanMethodsOnly) {
		logger.debug("computing full reflective method access list for "+type.getDottedName()+" validMethodSubset incoming = "+MethodDescriptor.toString(validMethodsSubset));
//		boolean onlyPublicMethods = (accessRequestor.getTypeAccessRequestedFor(type.getDottedName())&AccessBits.PUBLIC_METHODS)!=0;
		boolean onlyNonPrivateMethods = true;
		List<String> toMatchAgainst = new ArrayList<>();
		if (validMethodsSubset!=null) {
			for (String[] validMethod: validMethodsSubset) {
				toMatchAgainst.add(String.join("::",validMethod));
			}
		}
		List<String[]> allRelevantMethods = new ArrayList<>();
		logger.debug("There are "+allRelevantMethods.size()+" possible members");
		for (Method method : type.getMethods()) {
			if (!method.getName().equals("<init>") && !method.getName().equals("<clinit>")) { // ignore constructors
				if (onlyNonPrivateMethods && method.isPrivate()) {
					logger.debug("checking '"+method.getName()+method.getDesc()+"' -> private - skipping");
					continue;
				}
//				if (onlyPublicMethods && !method.isPublic()) {
//					continue;
//				}
				if (method.hasUnresolvableParams()) {
					logger.debug("checking '"+method.getName()+method.getDesc()+"' -> unresolvable parameters, ignoring");
					continue;
				}
				String[] candidate = method.asConfigurationArray();
				if (method.markedAtBean()) {
					if (validMethodsSubset != null && !toMatchAgainst.contains(String.join("::", candidate))) {
						continue;
					}
				} else {
					if (atBeanMethodsOnly) {
						continue;
					}
				}
				allRelevantMethods.add(candidate);
			}
		}
		String[][] methods = allRelevantMethods.toArray(new String[0][]);
		printMemberSummary("These will be granted reflective access:", methods);
		accessRequestor.addMethodDescriptors(type.getDottedName(), methods);
	}

	private void printMemberSummary(String prefix, String[][] processTypeAtBeanMethods) {
		if (processTypeAtBeanMethods != null) {
			logger.debug(prefix+" member summary: " + processTypeAtBeanMethods.length);
			for (int i = 0; i < processTypeAtBeanMethods.length; i++) {
				String[] member = processTypeAtBeanMethods[i];
				StringBuilder s = new StringBuilder();
				s.append(member[0]).append("(");
				for (int p = 1; p < member.length; p++) {
					if (p > 1) {
						s.append(",");
					}
					s.append(member[p]);
				}
				s.append(")");
				logger.debug(s.toString());
			}
		}
	}

	private boolean isImportHint(HintApplication hint) {
		List<Type> annotationChain = hint.getAnnotationChain();
		return annotationChain.get(annotationChain.size()-1).equals(ts.getType_Import());
	}

	private void printHintSummary(Type type, List<HintApplication> hints) {
		if (hints.size() != 0) {
			logger.debug("found "+ hints.size() + " hints on " + type.getDottedName()+":");
			for (int h = 0; h < hints.size(); h++) {
				logger.debug((h + 1) + ") " + hints.get(h));
			}
		} else {
			logger.debug("no hints on " + type.getName());
		}
	}

	/**
	 * Process any @Bean methods in a configuration type. For these methods we need to ensure
	 * reflective access to the return types involved as well as any annotations on the
	 * method. It is also important to check any Conditional annotations on the method as
	 * a ConditionalOnClass may fail at build time and the whole @Bean method can be ignored.
	 */
	private String[][] processTypeAtBeanMethods(ProcessingContext pc, RequestedConfigurationManager rcm,
			Map<Type,ReachedBy> toFollow, Type type) {
		List<String[]> passingMethodsSubset = new ArrayList<>();
		boolean anyMethodFailedValidation = false;
		// This is computing how many methods we are exposing unnecessarily via reflection by 
		// specifying allDeclaredMethods for this type rather than individually specifying them.
		// A high number indicates we should perhaps do more to be selective.
		int totalMethodCount = type.getMethodCount(false);
		List<Method> atBeanMethods = type.getMethodsWithAtBean();
		int rogue = (totalMethodCount - atBeanMethods.size());
		if (rogue != 0) {
			logger.debug(
					"WARNING: Methods unnecessarily being exposed by reflection on this config type "
					+ type.getName() + " = " + rogue + " (total methods including @Bean ones:" + totalMethodCount + ")");
		}

		if (atBeanMethods.size() != 0) {
			logger.debug("processing " + atBeanMethods.size() + " @Bean methods on type "+type.getDottedName());
		}

		for (Method atBeanMethod : atBeanMethods) {
			RequestedConfigurationManager methodRCM = new RequestedConfigurationManager();
			Map<Type, ReachedBy> additionalFollows = new HashMap<>();
			boolean passesTests = true;
			
			// boolean methodAnnotatedAtConfigurationProperties = atBeanMethod.hasAnnotation(Type.AtConfigurationProperties, false);
			
			// Check if resolvable...
			boolean hasUnresolvableTypesInSignature = atBeanMethod.hasUnresolvableTypesInSignature();
			if (hasUnresolvableTypesInSignature) {
				anyMethodFailedValidation = true;
				continue;
			}
			
			Type returnType = atBeanMethod.getReturnType();
			if (returnType == null) {
				// null means that type is not on the classpath so skip further analysis of this method...
				continue;
			} else {
				// Note for the future - this code is intended to catch return types of bean methods that themselves
				// include @Autowired fields/methods (but are not marked @Component). If deeper analysis is
				// required the check down below "if (returnType.isComponent()) {" should be extended so full processing
				// would happen for these return types.
				int bits = AccessBits.CLASS | AccessBits.DECLARED_CONSTRUCTORS;
				methodRCM.requestTypeAccess(returnType.getDottedName(), bits, returnType.getRequiredAccessibleMethods(), returnType.getRequiredAccessibleFields());
			}

			// Example method being handled here:
			//	@Bean
			//	@ConditionalOnClass(name = "org.springframework.security.authentication.event.AbstractAuthenticationEvent")
			//	@ConditionalOnMissingBean(AbstractAuthenticationAuditListener.class)
			//	public AuthenticationAuditListener authenticationAuditListener() throws Exception {
			//		return new AuthenticationAuditListener();
			//	}
			List<HintApplication> methodHints = atBeanMethod.getHints();
			logger.debug("@Bean method "+atBeanMethod + " hints: #"+methodHints.size());
			for (int i=0;i<methodHints.size();i++) {
				logger.debug((i+1)+") "+methodHints.get(i));
			}
			for (int h=0;h<methodHints.size() && passesTests;h++) {
				HintApplication hint = methodHints.get(h);
				logger.debug("processing hint " + hint);

				Map<String, AccessDescriptor> specificNames = hint.getSpecificTypes();
				if (specificNames.size() != 0) {
					logger.debug("handling " + specificNames.size() + " specific types");
					for (Map.Entry<String, AccessDescriptor> specificNameEntry : specificNames.entrySet()) {
						registerSpecific(pc, specificNameEntry.getKey(),
								specificNameEntry.getValue(), methodRCM);
					}
				}

				Map<String, Integer> inferredTypes = hint.getInferredTypes();
				if (inferredTypes.size()!=0) {
					logger.debug("handling " + inferredTypes.size() + " inferred types");
					for (Map.Entry<String, Integer> inferredType : inferredTypes.entrySet()) {
						String s = inferredType.getKey();
						Type t = ts.resolveDotted(s, true);
						boolean exists = (t != null);
						if (!exists) {
							logger.debug("inferred type " + s + " not found");
						} else {
							logger.debug("inferred type " + s + " found, will get accessibility " + AccessBits.toString(inferredType.getValue()));
						}
						if (exists) {
							// TODO if already there, should we merge access required values?
							methodRCM.requestTypeAccess(s, inferredType.getValue());
							if (hint.isFollow() || t.shouldFollow()) {
								additionalFollows.put(t,ReachedBy.Other);
							} else if (t.isAbstractNestedCondition()) {
								// The inner types are hosting conditions
								logger.debug("will follow inner types inside this nested condition type " + t);
								for (Type inner : t.getNestedTypes()) {
									toFollow.put(inner, ReachedBy.InnerOfNestedCondition);
								}								
							}
						} else if (hint.isSkipIfTypesMissing()) {
							passesTests = false;
							break;
						}
					}
				}
				if (passesTests) {
					List<Type> annotationChain = hint.getAnnotationChain();
					registerAnnotationChain(methodRCM, annotationChain);
				}
			}


			// Register other runtime visible annotations from the @Bean method. For example
			// this ensures @Role is visible on:
			// @Bean
			// @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
			// @ConditionalOnMissingBean(Validator.class)
			// public static LocalValidatorFactoryBean defaultValidator() {
			if (passesTests) {
				List<Type> annotationsOnMethod = atBeanMethod.getAnnotationTypes();
				for (Type annotationOnMethod : annotationsOnMethod) {
					methodRCM.requestTypeAccess(annotationOnMethod.getDottedName(), AccessBits.ANNOTATION);
				}
			} 
			if (passesTests) {
				try {
					passingMethodsSubset.add(atBeanMethod.asConfigurationArray());
					toFollow.putAll(additionalFollows);
					if (returnType.isComponent()) {
						toFollow.put(returnType, ReachedBy.AtBeanReturnType);
					}
					if (!ts.isNativeNextMode()) {
						rcm.mergeIn(methodRCM);
					}
					logger.debug("method passed checks - adding configuration for it");
				} catch (IllegalStateException ise) {
					// usually if asConfigurationArray() fails - due to an unresolvable type - it indicates
					// this method is no use
					logger.debug("method failed checks - ise on "+atBeanMethod.getName()+" so ignoring");
					anyMethodFailedValidation = true;
				}
			} else {
				anyMethodFailedValidation = true;
				logger.debug("method failed checks - not adding configuration for it");
			}
		}
		if (anyMethodFailedValidation) {
			// Return the subset that passed
			return passingMethodsSubset.toArray(new String[0][]);
		} else {
			return null;
		}
	}

	private void recursivelyCallProcessTypeForHierarchyOfType(ProcessingContext pc, Type type) {
		Type s = type.getSuperclass();
		while (s != null) {
			if (s.getName().equals("java/lang/Object")) {
				break;
			}
			if (pc.recordVisit(s.getName())) {
				boolean b = processType(pc, s, ReachedBy.HierarchyProcessing);
				if (!b) {
					logger.debug("WARNING: whilst processing type " + type.getName()
							+ " superclass " + s.getName() + " verification failed");
				}
			} else {
				break;
			}
			s = s.getSuperclass();
		}
	}

	private void registerAllRequested(RequestedConfigurationManager accessRequestor) {
		registerAllRequested(0, accessRequestor);
	}
	
	// In an attempt to reduce verbosity helps avoid reporting identical messages over and over
	private static Map<String, Integer> reflectionConfigurationAlreadyAdded = new HashMap<>();

	private void registerAllRequested(int depth, RequestedConfigurationManager accessRequestor) {
		for (InitializationDescriptor initializationDescriptor : accessRequestor.getRequestedInitializations()) {
			initializationHandler.registerInitializationDescriptor(initializationDescriptor);
		}
		optionHandler.addOptions(accessRequestor.getRequestedOptions());
		for (JdkProxyDescriptor proxyDescriptor : accessRequestor.getRequestedProxies()) {
			dynamicProxiesHandler.addProxy(proxyDescriptor);
		}
		for (org.springframework.nativex.type.ResourcesDescriptor rd : accessRequestor.getRequestedResources()) {
			registerResourcesDescriptor(rd);
		}
		for (String serializationType: accessRequestor.getRequestedSerializableTypes()) {
			serializationHandler.addType(serializationType);
		}
		for (Entry<String, AccessDescriptor> jniType : accessRequestor.getRequestedJNITypes().entrySet()) {
			jniReflectionHandler.addAccess(jniType.getKey(), jniType.getValue());
		}
		for (Map.Entry<String, Integer> accessRequest : accessRequestor.getRequestedTypeAccesses()) {
			String dname = accessRequest.getKey();
			int requestedAccess = accessRequest.getValue();
			List<org.springframework.nativex.type.MethodDescriptor> methods = accessRequestor.getMethodAccessRequestedFor(dname);
			
			// TODO promote this approach to a plugin if becomes a little more common...
			if (dname.equals("org.springframework.boot.autoconfigure.web.ServerProperties$Jetty")) { // See EmbeddedJetty @COC check
				if (!ts.canResolve("org/eclipse/jetty/webapp/WebAppContext")) {
					logger.debug("Reducing access on "+dname+" because WebAppContext not around");
					requestedAccess = AccessBits.CLASS;
				}
			}

			if (dname.equals("org.springframework.boot.autoconfigure.web.ServerProperties$Undertow")) { // See EmbeddedUndertow @COC check
				if (!ts.canResolve("io/undertow/Undertow")) {
					logger.debug("Reducing access on "+dname+" because Undertow not around");
					requestedAccess = AccessBits.CLASS;
				}
			}

			if (dname.equals("org.springframework.boot.autoconfigure.web.ServerProperties$Tomcat")) { // See EmbeddedTomcat @COC check
				if (!ts.canResolve("org/apache/catalina/startup/Tomcat")) {
					logger.debug("Reducing access on "+dname+" because Tomcat not around");
					requestedAccess = AccessBits.CLASS;
				}
			}

			if (dname.equals("org.springframework.boot.autoconfigure.web.ServerProperties$Netty")) { // See EmbeddedNetty @COC check
				if (!ts.canResolve("reactor/netty/http/server/HttpServer")) {
					logger.debug("Reducing access on "+dname+" because HttpServer not around");
					requestedAccess = AccessBits.CLASS;
				}
			}

			// Only log new info that is being added at this stage to keep logging down
			Integer access = reflectionConfigurationAlreadyAdded.get(dname);
			if (access == null) {
				logger.debug(spaces(depth) + "configuring reflective access to " + dname + "   " + AccessBits.toString(requestedAccess)+
						(methods==null?"(NO EXPLICIT METHODS)":" mds="+methods));
				reflectionConfigurationAlreadyAdded.put(dname, requestedAccess);
			} else {
				int extraAccess = AccessBits.compareAccess(access,requestedAccess);
				if (extraAccess>0) {
					logger.debug(spaces(depth) + "configuring reflective access, adding access for " + dname + " of " + 
							AccessBits.toString(extraAccess)+" (total now: "+AccessBits.toString(requestedAccess)+")");
					reflectionConfigurationAlreadyAdded.put(dname, access);
				}
			}
			Flag[] flags = AccessBits.getFlags(requestedAccess);
			Type rt = ts.resolveDotted(dname, true);

			if (methods != null) {
				// methods are explicitly specified, remove them from flags
				logger.debug(dname+" has #"+methods.size()+" methods directly specified so removing any general method access needs");
				flags = filterFlags(flags, Flag.allDeclaredMethods, Flag.allPublicMethods);
			}
//			logger.debug(spaces(depth) + "fixed flags? "+Flag.toString(flags));
//			logger.debug(depth, "ms: "+methods);

			reflectionHandler.addAccess(dname, MethodDescriptor.toStringArray(methods), FieldDescriptor.toStringArray(accessRequestor.getFieldAccessRequestedFor(dname)), true, flags);
			/*
			if (flags != null && flags.length == 1 && flags[0] == Flag.allDeclaredConstructors) {
				Type resolvedType = ts.resolveDotted(dname, true);
//				if (resolvedType != null && resolvedType.hasOnlySimpleConstructor()) {
//					reflectionHandler.addAccess(dname, new String[][] { { "<init>" } },null, true);
//				} else {
//				}
			} else {
				reflectionHandler.addAccess(dname, null, null, true, flags);
			}
			*/
			if (AccessBits.isResourceAccessRequired(requestedAccess)) {
				collector.addResource(fromTypenameToClassResource(dname), false);
			}
		}
	}
	
	private String fromTypenameToClassResource(String name) {
		return name.replace(".", "/").replace("$", ".").replace("[", "\\[").replace("]", "\\]") + ".class";
	}
	
	private Flag[] filterFlags(Flag[] flags, Flag... toFilter) {
		List<Flag> ok = new ArrayList<>();
		for (Flag flag: flags) {
			boolean skip  =false;
			for (Flag f: toFilter) {
				if (f==flag) {
					skip = true;
				}
			}
			if (!skip) {
				ok.add(flag);
			}
		}
		return ok.toArray(new Flag[0]);
	}

	private boolean isIgnored(Type configurationType) {
		List<String> excludedAutoConfig = ts.getExcludedAutoConfigurations();
		return excludedAutoConfig.contains(configurationType.getDottedName());
	}

	private boolean existingReflectionConfigContains(String s) {
		Map<String, ReflectionDescriptor> reflectionConfigurationsOnClasspath = ts.getReflectionConfigurationsOnClasspath();
		for (ReflectionDescriptor rd: reflectionConfigurationsOnClasspath.values()) {
			if (rd.hasClassDescriptor(s)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Crude guess at nested configuration.
	 */
	private boolean isNestedConfiguration(Type type) {
		boolean b = type.isAtConfiguration() && type.getEnclosingType()!=null;
		return b;
	}

	private void registerAnnotationChain(RequestedConfigurationManager tar, List<Type> annotationChain) {
		// Causes configurations to register since they have those annotations on them
		if (ts.isNativeNextMode() && annotationChain.size()>0 && annotationChain.get(0).isAtConfiguration()) {
			return;
		}
		logger.debug("attempting registration of " + annotationChain.size()
				+ " elements of annotation hint chain");
		for (int i = 0; i < annotationChain.size(); i++) {
			// i=0 is the annotated type, i>0 are all annotation types
			Type t = annotationChain.get(i);
			if (i==0 && (aotOptions.toMode() == Mode.NATIVE_AGENT)) {
				boolean beingReflectedUponInIncomingConfiguration = existingReflectionConfigContains(t.getDottedName());
				if (!beingReflectedUponInIncomingConfiguration) {
					logger.debug("In agent mode skipping "+t.getDottedName()+" because in already existing configuration");
					break;
				}
			}
			tar.requestTypeAccess(t.getDottedName(), Type.inferAccessRequired(t));
		}
	}

	private String spaces(int depth) {
		return "                                                  ".substring(0, depth * 2);
	}

}
