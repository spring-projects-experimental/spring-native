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

package org.springframework.nativex.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.springframework.nativex.domain.init.InitializationDescriptor;
import org.springframework.nativex.domain.proxies.AotProxyDescriptor;
import org.springframework.nativex.domain.proxies.JdkProxyDescriptor;
import org.springframework.nativex.domain.reflect.FieldDescriptor;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.AotProxyHint;
import org.springframework.nativex.hint.AotProxyHints;
import org.springframework.nativex.hint.InitializationHint;
import org.springframework.nativex.hint.InitializationHints;
import org.springframework.nativex.hint.InitializationTime;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.NativeHints;
import org.springframework.nativex.hint.ProxyBits;
import org.springframework.nativex.hint.JdkProxyHint;
import org.springframework.nativex.hint.JdkProxyHints;
import org.springframework.nativex.hint.ResourceHint;
import org.springframework.nativex.hint.ResourcesHints;
import org.springframework.nativex.hint.SerializationHint;
import org.springframework.nativex.hint.SerializationHints;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.hint.TypeHints;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Andy Clement
 * @author Christoph Strobl
 */
@SuppressWarnings("unchecked")
public class Type {

	private static Log logger = LogFactory.getLog(Type.class);	

	public final static String AtResponseBody = "Lorg/springframework/web/bind/annotation/ResponseBody;";
	public final static String AtMapping = "Lorg/springframework/web/bind/annotation/Mapping;";
	public final static String AtMessageMapping = "Lorg/springframework/messaging/handler/annotation/MessageMapping;";
	public final static String AtTransactional = "Lorg/springframework/transaction/annotation/Transactional;";
	public final static String AtEndpoint = "Lorg/springframework/boot/actuate/endpoint/annotation/Endpoint;";
	public final static String AtJavaxTransactional = "Ljavax/transaction/Transactional;";
	public final static String AtBean = "Lorg/springframework/context/annotation/Bean;";
	public final static String AtConditionalOnClass = "Lorg/springframework/boot/autoconfigure/condition/ConditionalOnClass;";
	public final static String AtConditionalOnCloudPlatform = "Lorg/springframework/boot/autoconfigure/condition/ConditionalOnCloudPlatform;";

	public final static String AtConditionalOnProperty = "Lorg/springframework/boot/autoconfigure/condition/ConditionalOnProperty;";
//	public final static String AtConditionalOnAvailableEndpoint = "Lorg/springframework/boot/actuate/autoconfigure/endpoint/condition/ConditionalOnAvailableEndpoint;";
//	public final static String AtConditionalOnEnabledHealthIndicator = "Lorg/springframework/boot/actuate/autoconfigure/health/ConditionalOnEnabledHealthIndicator;";
//	public final static String AtConditionalOnEnabledMetricsExport = "Lorg/springframework/boot/actuate/autoconfigure/metrics/export/ConditionalOnEnabledMetricsExport;";

	public final static String AtConditionalOnMissingBean = "Lorg/springframework/boot/autoconfigure/condition/ConditionalOnMissingBean;";
	public final static String AtConditionalOnSingleCandidate = "Lorg/springframework/boot/autoconfigure/condition/ConditionalOnSingleCandidate;";
	public final static String AtConditionalOnBean = "Lorg/springframework/boot/autoconfigure/condition/ConditionalOnBean;";
	public final static String AtConfiguration = "Lorg/springframework/context/annotation/Configuration;";
	public final static String AtImportAutoConfiguration = "Lorg/springframework/boot/autoconfigure/ImportAutoConfiguration;";
	public final static String AtConfigurationProperties = "Lorg/springframework/boot/context/properties/ConfigurationProperties;";
	public final static String AtSpringBootApplication = "Lorg/springframework/boot/autoconfigure/SpringBootApplication;";
	public final static String AtController = "Lorg/springframework/stereotype/Controller;";
	public final static String AtRepository = "Lorg/springframework/stereotype/Repository;";
	public final static String AtEnableConfigurationProperties = "Lorg/springframework/boot/context/properties/EnableConfigurationProperties;";
	public final static String AtImports = "Lorg/springframework/context/annotation/Import;";
	public final static String AtValidated = "Lorg/springframework/validation/annotation/Validated;";
	public final static String AtConstructorBinding = "Lorg/springframework/boot/context/properties/ConstructorBinding;";
	public final static String AtComponent = "Lorg/springframework/stereotype/Component;";
	public final static String BeanFactoryPostProcessor = "Lorg/springframework/beans/factory/config/BeanFactoryPostProcessor;";
	public final static String BeanPostProcessor = "Lorg/springframework/beans/factory/config/BeanPostProcessor;";
	public final static String ImportBeanDefinitionRegistrar = "Lorg/springframework/context/annotation/ImportBeanDefinitionRegistrar;";
	public final static String ImportSelector = "Lorg/springframework/context/annotation/ImportSelector;";
	public final static String ApplicationListener = "Lorg/springframework/context/ApplicationListener;";
	public final static String AtAliasFor = "Lorg/springframework/core/annotation/AliasFor;";
	public final static String Condition = "Lorg/springframework/context/annotation/Condition;";
	public final static String EnvironmentPostProcessor = "Lorg/springframework/boot/env/EnvironmentPostProcessor";
	public final static String AtPostAuthorize = "Lorg/springframework/security/access/prepost/PostAuthorize;";
	public final static String AtPostFilter = "Lorg/springframework/security/access/prepost/PostFilter;";
	public final static String AtPreAuthorize = "Lorg/springframework/security/access/prepost/PreAuthorize;";
	public final static String AtPreFilter = "Lorg/springframework/security/access/prepost/PreFilter;";

	public final static Type MISSING = new Type(null, null, 0);

	public final static Type[] NO_INTERFACES = new Type[0];

	protected static Set<String> validBoxing = new HashSet<String>();

	private TypeSystem typeSystem;

	private ClassNode node;

	private Type[] interfaces;

	private String name;
	private String dottedName;

	private int dimensions = 0; // >0 for array types

	private final Lazy<List<Field>> fields;
	private final Lazy<List<Method>> methods;
	private final Lazy<List<Type>> annotations;
	
	private boolean isPrimitive;

	private Type(TypeSystem typeSystem, ClassNode node, int dimensions) {
		this.typeSystem = typeSystem;
		this.node = node;
		this.dimensions = dimensions;
		if (node != null) {
			this.name = node.name;
			for (int i = 0; i < dimensions; i++) {
				this.name += "[]";
			}
			this.dottedName = name.replace("/", ".");
			this.fields = Lazy.of(this::resolveFields);
			this.methods = Lazy.of(this::resolveMethods);
			this.annotations = Lazy.of(this::resolveAnnotations);
		} else {

			this.fields = Lazy.empty();
			this.methods = Lazy.empty();
			this.annotations = Lazy.empty();
		}
	}

	public static Type forClassNode(TypeSystem typeSystem, ClassNode node, int dimensions) {
		return new Type(typeSystem, node, dimensions);
	}

	/**
	 * @return typename in slashed form (aaa/bbb/ccc/Ddd$Eee)
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * @return short version of the typename (with packages collapsed) to add with readability
	 */
	public String getShortName() {
		String dname = getDottedName();
		StringBuilder s = new StringBuilder();
		boolean hasDot = dname.contains(".");
		while (dname.contains(".")) {
			s.append(dname.charAt(0));
			dname = dname.substring(dname.indexOf(".")+1);
		}
		if (hasDot) {
			s.append(".");
		}
		s.append(dname);
		return s.toString();
	}

	public String getDottedName() {
		return dottedName;
	}

	public String getSimpleName() {
		return dottedName.substring(dottedName.lastIndexOf('.') + 1);
	}

	public Type getSuperclass() {
		if (dimensions > 0) {
			return typeSystem.resolveSlashed("java/lang/Object");
		}
		if (node.superName == null) {
			return null;
		}
		return typeSystem.resolveSlashed(node.superName);
	}

	@Override
	public String toString() {
		return "Type:" + getName();
	}

	public Type[] getInterfaces() {
		if (interfaces == null) {
			if (dimensions != 0) {
				interfaces = new Type[] { typeSystem.resolveSlashed("java/lang/Cloneable"),
						typeSystem.resolveSlashed("java/io/Serializable") };
			} else {
				List<String> itfs = node.interfaces;
				if (itfs.size() == 0) {
					interfaces = NO_INTERFACES;
				} else {
					List<Type> interfacesOnClasspath = new ArrayList<>();
					for (int i = 0; i < itfs.size(); i++) {
						Type t = typeSystem.resolveSlashed(itfs.get(i));
						interfacesOnClasspath.add(t);
					}
					interfaces = interfacesOnClasspath.toArray(new Type[interfacesOnClasspath.size()]);
				}
			}
		}
		return interfaces;
	}

	public boolean isPartOfDomain(String packageName) {
		return belongsToPackage(packageName, true);
	}

	public boolean belongsToPackage(String packageName, boolean orPartOfSubPackage) {
		return orPartOfSubPackage ? dottedName.startsWith(packageName) : getPackageName().equals(packageName);
	}

	public String getPackageName() {
		return dottedName.substring(0, dottedName.lastIndexOf('.'));
	}

	/**
	 * @return List of slashed interface types
	 */
	public List<String> getInterfacesStrings() {
		if (dimensions != 0) {
			List<String> itfs = new ArrayList<>();
			itfs.add("java/lang/Cloneable");
			itfs.add("java/io/Serializable");
			return itfs;
		} else {
			return node.interfaces;
		}
	}

	/**
	 * @return slashed supertype name
	 */
	public String getSuperclassString() {
		return dimensions > 0 ? "java/lang/Object" : node.superName;
	}

	/**
	 * Compute all the types referenced in the signature of this type.
	 * @return the types
	 */
	public Set<String> getTypesInSignature() {
		if (dimensions > 0) {
			return Collections.emptySet();
		} else if (node.signature == null) {
			// With no generic signature it is just superclass and interfaces
			Set<String> ls = new TreeSet<>();
			if (node.superName != null) {
				ls.add(node.superName);
			}
			if (node.interfaces != null) {
				ls.addAll(node.interfaces);
			}
			return ls;
		} else {
			// Pull out all the types from the generic signature
			SignatureReader reader = new SignatureReader(node.signature);
			TypeCollector tc = new TypeCollector();
			reader.accept(tc);
			return tc.getTypes();
		}
	}

	static class TypeCollector extends SignatureVisitor {

		Set<String> types = null;

		public TypeCollector() {
			super(Opcodes.ASM9);
		}

		@Override
		public void visitClassType(String name) {
			if (types == null) {
				types = new TreeSet<>();
			}
			types.add(name);
		}

		public Set<String> getTypes() {
			if (types == null) {
				return Collections.emptySet();
			} else {
				return types;
			}
		}
	}

	// TODO fix inconsistency between extendsClass working with descriptors and implementsInterface working with slashed names
	public boolean extendsClass(String clazzname) {
		Type superclass = null;
		if (dimensions > 0) {
			superclass = typeSystem.resolveSlashed("java/lang/Object");
		} else {
			superclass = node.superName==null ? null : typeSystem.resolveSlashed(node.superName, true);
		}
		while (superclass != null) {
			if (superclass.getDescriptor().equals(clazzname)) {
				return true;
			}
			String nextSupername = superclass.node.superName;
			superclass = nextSupername==null ? null : typeSystem.resolveSlashed(nextSupername, true);
		}
		return false;
	}

	public boolean implementsInterface(String interfaceName) {
		if (this.getName().equals(interfaceName)) {
			return true;
		}
		Type[] interfacesToCheck = getInterfaces();
		for (Type interfaceToCheck : interfacesToCheck) {
			if (interfaceToCheck != null) {
				if (interfaceToCheck.getName().equals(interfaceName)) {
					return true;
				}
				if (interfaceToCheck.implementsInterface(interfaceName)) {
					return true;
				}
			}
		}
		Type superclass = getSuperclass();
		while (superclass != null) {
			if (superclass.implementsInterface(interfaceName)) {
				return true;
			}
			superclass = superclass.getSuperclass();
		}
		return false;
	}

	public List<Method> getMethodsWithAnnotation(String string) {
		// logger.debug("looking through methods "+node.methods+" for "+string);
		return dimensions > 0 ? Collections.emptyList()
				: node.methods.stream().filter(m -> hasAnnotation(m, string)).map(m -> wrap(m))
						.collect(Collectors.toList());
	}

	public List<Method> getMethodsWithAnnotationName(String string, boolean checkMetaUsage) {
		return getMethodsWithAnnotation("L" + string.replace(".", "/") + ";", checkMetaUsage);
	}

	public List<Method> getMethodsWithAnnotation(String string, boolean checkMetaUsage) {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<Method> results = new ArrayList<>();
		if (node.methods != null) {
			for (MethodNode mn : node.methods) {
				if (hasAnnotation(mn, string, checkMetaUsage)) {
					if (results == null) {
						results = new ArrayList<>();
					}
					results.add(wrap(mn));
				}
			}
		}
		return results == null ? Collections.emptyList() : results;
	}

	public List<Method> getMethods() {
		return methods.get();
	}

	private List<Method> resolveMethods() {
		return dimensions > 0 ? Collections.emptyList()
				: node.methods.stream().map(m -> wrap(m)).collect(Collectors.toList());
	}

	public List<Field> getFields() {
		return fields.get();
	}

	public List<Field> getFieldsWithAnnotationName(String string, boolean checkMetaUsage) {
		return getFieldsWithAnnotation("L" + string.replace(".", "/") + ";", checkMetaUsage);
	}

	public List<Field> getFieldsWithAnnotation(String string, boolean checkMetaUsage) {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<Field> results = new ArrayList<>();
		if (node.methods != null) {
			for (FieldNode fn : node.fields) {
				if (hasAnnotation(fn, string, checkMetaUsage)) {
					if (results == null) {
						results = new ArrayList<>();
					}
					results.add(wrap(fn));
				}
			}
		}
		return results == null ? Collections.emptyList() : results;
	}

	private List<Field> resolveFields() {
		return dimensions > 0 ? Collections.emptyList()
				: node.fields.stream().map(it -> new Field(it, typeSystem)).collect(Collectors.toList());
	}

	public List<Method> getMethodsWithAtBean() {
		return getMethodsWithAnnotation(AtBean);
	}

	private Method wrap(MethodNode mn) {
		return new Method(mn, typeSystem);
	}

	private Field wrap(FieldNode fn) {
		return new Field(fn, typeSystem);
	}

	private boolean hasAnnotation(MethodNode m, String string) {
		List<AnnotationNode> visibleAnnotations = m.visibleAnnotations;
		Optional<AnnotationNode> findAny = visibleAnnotations == null ? Optional.empty()
				: visibleAnnotations.stream().filter(a -> a.desc.equals(string)).findFirst();
		return findAny.isPresent();
	}

	public boolean hasAnnotation(String lAnnotationDescriptor, boolean checkMetaUsage) {
		Set<String> seen = new HashSet<>();
		return hasAnnotationHelper(lAnnotationDescriptor, checkMetaUsage, seen);
	}

	private boolean hasAnnotationHelper(String lAnnotationDescriptor, boolean checkMetaUsage, Set<String> seen) {
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals(lAnnotationDescriptor)) {
					return true;
				}
				if (checkMetaUsage && seen.add(lAnnotationDescriptor)) {
					Type t = typeSystem.Lresolve(an.desc);
					if (t != null) {
						boolean b = t.hasAnnotationHelper(lAnnotationDescriptor, checkMetaUsage, seen);
						if (b) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private boolean hasAnnotation(MethodNode mn, String lAnnotationDescriptor, boolean checkMetaUsage) {
		if (checkMetaUsage) {
			return findMetaAnnotationUsage(toAnnotations(mn.visibleAnnotations), lAnnotationDescriptor);
		} else {
			List<AnnotationNode> vAnnotations = mn.visibleAnnotations;
			if (vAnnotations != null) {
				for (AnnotationNode an : vAnnotations) {
					if (an.desc.equals(lAnnotationDescriptor)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean hasAnnotation(FieldNode fn, String lAnnotationDescriptor, boolean checkMetaUsage) {
		if (checkMetaUsage) {
			return findMetaAnnotationUsage(toAnnotations(fn.visibleAnnotations), lAnnotationDescriptor);
		} else {
			List<AnnotationNode> vAnnotations = fn.visibleAnnotations;
			if (vAnnotations != null) {
				for (AnnotationNode an : vAnnotations) {
					if (an.desc.equals(lAnnotationDescriptor)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private List<Type> toAnnotations(List<AnnotationNode> visibleAnnotations) {
		if (visibleAnnotations == null) {
			return Collections.emptyList();
		}
		List<Type> result = new ArrayList<>();
		for (AnnotationNode an : visibleAnnotations) {
			result.add(typeSystem.Lresolve(an.desc));
		}
		return result;
	}

	private boolean findMetaAnnotationUsage(List<Type> toSearch, String lAnnotationDescriptor) {
		return findMetaAnnotationUsageHelper(toSearch, lAnnotationDescriptor, new HashSet<>());
	}

	private boolean findMetaAnnotationUsageHelper(List<Type> toSearch, String lAnnotationDescriptor, Set<String> seen) {
		if (toSearch == null) {
			return false;
		}
		for (Type an : toSearch) {
			if (an.getDescriptor().equals(lAnnotationDescriptor)) {
				return true;
			}
			if (seen.add(an.getName())) {
				boolean isMetaAnnotated = findMetaAnnotationUsageHelper(an.getAnnotations(), lAnnotationDescriptor,
						seen);
				if (isMetaAnnotated) {
					return true;
				}
			}
		}
		return false;
	}

	static {
		validBoxing.add("Ljava/lang/Byte;B");
		validBoxing.add("Ljava/lang/Character;C");
		validBoxing.add("Ljava/lang/Double;D");
		validBoxing.add("Ljava/lang/Float;F");
		validBoxing.add("Ljava/lang/Integer;I");
		validBoxing.add("Ljava/lang/Long;J");
		validBoxing.add("Ljava/lang/Short;S");
		validBoxing.add("Ljava/lang/Boolean;Z");
		validBoxing.add("BLjava/lang/Byte;");
		validBoxing.add("CLjava/lang/Character;");
		validBoxing.add("DLjava/lang/Double;");
		validBoxing.add("FLjava/lang/Float;");
		validBoxing.add("ILjava/lang/Integer;");
		validBoxing.add("JLjava/lang/Long;");
		validBoxing.add("SLjava/lang/Short;");
		validBoxing.add("ZLjava/lang/Boolean;");
	}

	public boolean isAssignableFrom(Type other) {
		if (other.isPrimitiveType()) {
			if (validBoxing.contains(this.getName() + other.getName())) {
				return true;
			}
		}
		if (this == other) {
			return true;
		}

		if (this.getName().equals("Ljava/lang/Object;")) {
			return true;
		}

		Type[] interfaces = other.getInterfaces();
		for (Type intface : interfaces) {
			boolean b = this.isAssignableFrom(intface);
			if (b) {
				return true;
			}
		}
		Type superclass = other.getSuperclass();
		if (superclass != null) {
			boolean b = this.isAssignableFrom(superclass);
			if (b) {
				return true;
			}
		}
		return false;
	}

	private boolean isPrimitiveType() {
		return false;
	}

	public boolean isInterface() {
		return dimensions > 0 ? false : Modifier.isInterface(node.access);
	}

	public boolean isPublic() {
		return dimensions > 0 ? false : Modifier.isPublic(node.access);
	}

	public Map<String, String> getAnnotationValuesInHierarchy(String LdescriptorLookingFor) {
		if (dimensions > 0) {
			return Collections.emptyMap();
		}
		Map<String, String> collector = new HashMap<>();
		getAnnotationValuesInHierarchy(LdescriptorLookingFor, new ArrayList<>(), collector);
		return collector;
	}

	public void getAnnotationValuesInHierarchy(String lookingFor, List<String> seen, Map<String, String> collector) {
		if (dimensions > 0) {
			return;
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode anno : node.visibleAnnotations) {
				if (seen.contains(anno.desc))
					continue;
				seen.add(anno.desc);
				// logger.debug("Comparing "+anno.desc+" with "+lookingFor);
				if (anno.desc.equals(lookingFor)) {
					List<Object> os = anno.values;
					for (int i = 0; i < os.size(); i += 2) {
						if(os.get(i+1) instanceof List) {

							List<String> resolvedAttributeValues = new ArrayList<>();

							for(Object attributeValue : (List)os.get(i+1)) {

								if(!ObjectUtils.isArray(attributeValue)) {
									resolvedAttributeValues.add(attributeValue.toString());
									continue;
								}

								String attributeValueStringRepresentation = "";
								String[] args = (String[]) attributeValue;
								if(args[0].startsWith("L") && args[0].endsWith(";")) {
									Type resolve = typeSystem.Lresolve(args[0], true);
									if(resolve != null) {
										attributeValueStringRepresentation = resolve.getDottedName();
									}
								}
								if(args.length > 1) {
									attributeValueStringRepresentation += ("." + args[1]);
								}
								resolvedAttributeValues.add(attributeValueStringRepresentation);
							}
							collector.put(os.get(i).toString(), StringUtils.collectionToDelimitedString(resolvedAttributeValues, ";"));
						} else {
							collector.put(os.get(i).toString(), os.get(i + 1).toString());
						}
					}
				}
				try {
					Type resolve = typeSystem.Lresolve(anno.desc);
					resolve.getAnnotationValuesInHierarchy(lookingFor, seen, collector);
				} catch (MissingTypeException mte) {
					// not on classpath, that's ok
				}
			}
		}
	}

	public boolean hasAnnotationInHierarchy(String lookingFor) {
		if (dimensions > 0) {
			return false;
		}
		return hasAnnotationInHierarchy(lookingFor, new ArrayList<String>());
	}

	public boolean hasAnnotationInHierarchy(String lookingFor, List<String> seen) {
		if (dimensions > 0) {
			return false;
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode anno : node.visibleAnnotations) {
				if (seen.contains(anno.desc))
					continue;
				seen.add(anno.desc);
				// logger.debug("Comparing "+anno.desc+" with "+lookingFor);
				if (anno.desc.equals(lookingFor)) {
					return true;
				}
				try {
					Type resolve = typeSystem.Lresolve(anno.desc);
					if (resolve.hasAnnotationInHierarchy(lookingFor, seen)) {
						return true;
					}
				} catch (MissingTypeException mte) {
					// not on classpath, that's ok
				}
			}
		}
		return false;
	}

	public boolean isCondition() {
		if (dimensions > 0) {
			return false;
		}
		try {
			return implementsInterface(fromLdescriptorToSlashed(Condition));
		} catch (MissingTypeException mte) {
			return false;
		}
	}

	private boolean isEventListener() {
		try {
			return implementsInterface("org/springframework/context/event/EventListenerFactory");
		} catch (MissingTypeException mte) {
			return false;
		}
	}

	public boolean isAtImport() {
		return (dimensions > 0) ? false : isMetaAnnotated(fromLdescriptorToSlashed(AtImports));
	}

	public boolean isAtConfiguration() {
		if (dimensions > 0) {
			return false;
		}
		boolean b = isMetaAnnotated(fromLdescriptorToSlashed(AtConfiguration), new HashSet<>());
		if (b) {
			return b;
		}
		// Allow for classes that have @Bean methods but no outer @Configuration indicator
		// More examples: WebMvcSecurityConfiguration, SpringDataJacksonConfiguration
		return hasMethodsWithAtBean();
	}

	public boolean hasMethodsWithAtBean() {
		return !getMethodsWithAtBean().isEmpty();
	}
	
	public boolean isAtComponent() {
		return dimensions>0?false:isMetaAnnotated(fromLdescriptorToSlashed(AtComponent), new HashSet<>());
	}

	public boolean isAtSpringBootApplication() {
		return (dimensions > 0) ? false
				: isMetaAnnotated(fromLdescriptorToSlashed(AtSpringBootApplication), new HashSet<>());
	}

	public boolean isAtController() {
		return (dimensions > 0) ? false : isMetaAnnotated(fromLdescriptorToSlashed(AtController), new HashSet<>());
	}

	public boolean isAbstractNestedCondition() {
		if (dimensions > 0) {
			return false;
		}
		return extendsClass("Lorg/springframework/boot/autoconfigure/condition/AbstractNestedCondition;");
	}

	public boolean isMetaAnnotated(String slashedTypeDescriptor) {
		if (dimensions > 0) {
			return false;
		}
		return isMetaAnnotated(slashedTypeDescriptor, new HashSet<>());
	}

	private boolean isMetaAnnotated(String slashedTypeDescriptor, Set<String> seen) {
		if (dimensions > 0) {
			return false;
		}
		for (Type t : this.getAnnotations()) {
			if (t == null) {
				continue;
			}
			String tname = t.getName();
			if (tname.equals(slashedTypeDescriptor)) {
				return true;
			}
			if (!seen.contains(tname)) {
				seen.add(tname);
				if (t.isMetaAnnotated(slashedTypeDescriptor, seen)) {
					return true;
				}
			}
		}
		return false;
	}

	public static final List<Type> NO_ANNOTATIONS = Collections.emptyList();


	public List<String> findConditionalOnBeanValue() {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<String> findAnnotationValue = findAnnotationValue(AtConditionalOnBean, false, false);
		if (findAnnotationValue == null) {
			if (node.visibleAnnotations != null) {
				for (AnnotationNode an : node.visibleAnnotations) {
					if (an.desc.equals(AtConditionalOnBean)) {
						logger.debug("??? found nothing on this @COB annotated thing " + this.getName());
					}
				}
			}
		}
		return findAnnotationValue;
	}

	public List<String> findConditionalOnMissingBeanValue() {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<String> findAnnotationValue = findAnnotationValue(AtConditionalOnMissingBean, false, false);
		if (findAnnotationValue == null) {
			if (node.visibleAnnotations != null) {
				for (AnnotationNode an : node.visibleAnnotations) {
					if (an.desc.equals(AtConditionalOnMissingBean)) {
						logger.debug("??? found nothing on this @COMB annotated thing " + this.getName());
					}
				}
			}
		}
		return findAnnotationValue;
	}
	
	public String findConditionalOnSingleCandidateValue() {
		if (dimensions > 0) {
			return null;
		}
		String findAnnotationValue = findAnnotationSingleValue(AtConditionalOnSingleCandidate, false);
		return findAnnotationValue;
	}

	public List<String> findConditionalOnClassValue() {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<String> findAnnotationValue = findAnnotationValue(AtConditionalOnClass, true, false);
		if (findAnnotationValue == null) {
			if (node.visibleAnnotations != null) {
				for (AnnotationNode an : node.visibleAnnotations) {
					if (an.desc.equals(AtConditionalOnClass)) {
						logger.debug("??? found nothing on this @COC annotated thing " + this.getName());
					}
				}
			}
		}
		return findAnnotationValue;
	}

	public Map<String, List<String>> findImports() {
		if (dimensions > 0) {
			return Collections.emptyMap();
		}
		return findAnnotationValueWithHostAnnotation(AtImports, true, new HashSet<>());
	}

	public List<String> findAnnotationValue(String annotationType, boolean treatNameAsAliasForValue, boolean searchMeta) {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		return findAnnotationValue(annotationType, searchMeta, treatNameAsAliasForValue, new HashSet<>());
	}

	public String findAnnotationSingleValue(String annotationType, boolean searchMeta) {
		if (dimensions > 0) {
			return null;
		}
		return findAnnotationSingleValue(annotationType, searchMeta, new HashSet<>());
	}

	public Map<String, List<String>> findAnnotationValueWithHostAnnotation(String annotationType, boolean searchMeta,
			Set<String> visited) {
		if (dimensions > 0) {
			return Collections.emptyMap();
		}
		if (!visited.add(this.getName())) {
			return Collections.emptyMap();
		}
		Map<String, List<String>> collectedResults = new LinkedHashMap<>();
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals(annotationType)) {
					List<Object> values = an.values;
					if (values != null) {
						for (int i = 0; i < values.size(); i += 2) {
							if (values.get(i).equals("value")) {
								List<String> importedReferences = ((List<org.objectweb.asm.Type>) values.get(i + 1))
										.stream().map(t -> t.getDescriptor()).collect(Collectors.toList());
								collectedResults.put(this.getName().replace("/", "."), importedReferences);
							}
						}
					}
				}
			}
			if (searchMeta) {
				for (AnnotationNode an : node.visibleAnnotations) {
					// For example @EnableSomething might have @Import on it
					Type annoType = null;
					try {
						annoType = typeSystem.Lresolve(an.desc);
					} catch (MissingTypeException mte) {
						logger.debug("SBG: WARNING: Unable to find " + an.desc + " skipping...");
						continue;
					}
					collectedResults.putAll(
							annoType.findAnnotationValueWithHostAnnotation(annotationType, searchMeta, visited));
				}
			}
		}
		return collectedResults;
	}

	public List<String> findAnnotationValue(String annotationType, boolean searchMeta, boolean treatNameAsAliasForValue, Set<String> visited) {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		if (!visited.add(this.getName())) {
			return Collections.emptyList();
		}
		List<String> collectedResults = new ArrayList<>();
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals(annotationType)) {
					List<Object> values = an.values;
					if (values != null) {
						for (int i = 0; i < values.size(); i += 2) {
							if (values.get(i).equals("value")) {
								((List<org.objectweb.asm.Type>) values.get(i + 1)).stream()
									.map(t -> t.getDescriptor())
									.collect(Collectors.toCollection(() -> collectedResults));
							} else if (values.get(i).equals("name") && treatNameAsAliasForValue) {
								List<String> names = (List<String>) values.get(i+1);
								for (String name: names) {
									collectedResults.add(fromDottedToLDescriptor(name));
								}
							}
						}
					}
				}
			}
			if (searchMeta) {
				for (AnnotationNode an : node.visibleAnnotations) {
					// For example @EnableSomething might have @Import on it
					Type annoType = typeSystem.Lresolve(an.desc);
					collectedResults.addAll(annoType.findAnnotationValue(annotationType, searchMeta, false, visited));
				}
			}
		}
		return collectedResults;
	}
	

	public String findAnnotationSingleValue(String annotationType, boolean searchMeta, Set<String> visited) {
		if (dimensions > 0) {
			return null;
		}
		if (!visited.add(this.getName())) {
			return null;
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals(annotationType)) {
					List<Object> values = an.values;
					if (values != null) {
						for (int i = 0; i < values.size(); i += 2) {
							if (values.get(i).equals("value")) {
								org.objectweb.asm.Type t = (org.objectweb.asm.Type) values.get(i+1);
								return t.getDescriptor();
							}
						}
					}
				}
			}
			if (searchMeta) {
				for (AnnotationNode an : node.visibleAnnotations) {
					// For example @EnableSomething might have @Import on it
					Type annoType = typeSystem.Lresolve(an.desc);
					String value = annoType.findAnnotationSingleValue(annotationType, searchMeta, visited);
					if (value != null) {
						return value;
					}
				}
			}
		}
		return null;
	}

	public List<Type> getAnnotations() {
		return annotations.get();
	}

	private List<Type> resolveAnnotations() {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<Type> annotations = new ArrayList<>();
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				try {
					annotations.add(this.typeSystem.Lresolve(an.desc));
				} catch (MissingTypeException mte) {
					// that's ok you weren't relying on it anyway!
				}
			}
		}
		if (annotations.size() == 0) {
			annotations = NO_ANNOTATIONS;
		}
		return annotations;
	}

	public Type findAnnotation(Type searchType) {
		if (dimensions > 0) {
			return null;
		}
		List<Type> annos = getAnnotations();
		for (Type anno : annos) {
			if (anno.equals(searchType)) {
				return anno;
			}
		}
		return null;
	}

	/**
	 * Discover any uses of @Indexed or javax annotations, via direct/meta or
	 * interface usage. Example output:
	 * 
	 * <pre>
	 * <code>
	 * org.springframework.samples.petclinic.PetClinicApplication=org.springframework.stereotype.Component
	 * org.springframework.samples.petclinic.model.Person=javax.persistence.MappedSuperclass
	 * org.springframework.samples.petclinic.owner.JpaOwnerRepositoryImpl=[org.springframework.stereotype.Component,javax.transaction.Transactional]
	 * </code>
	 * </pre>
	 * @return the stereotypes
	 */
	public Entry<Type, List<Type>> getRelevantStereotypes() {
		if (dimensions > 0) {
			return null;
		}
		List<Type> relevantAnnotations = new ArrayList<>();
		collectRelevantStereotypes(this, new HashSet<>(), relevantAnnotations);
		List<Type> types = getJavaxAnnotations();
		for (Type t : types) {
			if (!relevantAnnotations.contains(t)) {
				relevantAnnotations.add(t);
			}
		}
		if (!relevantAnnotations.isEmpty()) {
			return new AbstractMap.SimpleImmutableEntry<Type, List<Type>>(this, relevantAnnotations);
		} else {
			return null;
		}
	}

	private void collectRelevantStereotypes(Type type, Set<Type> seen, List<Type> collector) {
		if (type == null || type.dimensions > 0 || !seen.add(type)) {
			return;
		}
		List<Type> ts = type
				.getAnnotatedElementsInHierarchy(a -> a.desc.equals("Lorg/springframework/stereotype/Indexed;"));
		for (Type t : ts) {
			if (!collector.contains(t)) {
				collector.add(t);
			}
		}
		for (Type inttype : type.getInterfaces()) {
			collectRelevantStereotypes(inttype, seen, collector);
		}
		collectRelevantStereotypes(type.getSuperclass(), seen, collector);
	}

	public Entry<Type, List<Type>> getMetaComponentTaggedAnnotations() {
		if (dimensions > 0)
			return null;
		List<Type> relevantAnnotations = new ArrayList<>();
		List<Type> indexedTypesInHierachy = getAnnotatedElementsInHierarchy(
				a -> a.desc.equals("Lorg/springframework/stereotype/Component;"), true);
		for (Type t : indexedTypesInHierachy) {
			if (t.isAnnotation()) {
				relevantAnnotations.add(t);
			}
		}
		if (!relevantAnnotations.isEmpty()) {
			return new AbstractMap.SimpleImmutableEntry<Type, List<Type>>(this, relevantAnnotations);
		} else {
			return null;
		}
	}

	/**
	 * @return list of javax.* annotations on this type, directly specified or
	 *         meta-specified.
	 */
	List<Type> getJavaxAnnotations() {
		return getJavaxAnnotations(new HashSet<>());
	}

	public boolean isAnnotated(String Ldescriptor) {
		if (dimensions > 0) {
			return false;
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals(Ldescriptor)) {
					return true;
				}
			}
		}
		return false;
	}

	private List<Type> getAnnotatedElementsInHierarchy(Predicate<AnnotationNode> p) {
		return getAnnotatedElementsInHierarchy(p, new HashSet<>(), false);
	}

	private List<Type> getAnnotatedElementsInHierarchy(Predicate<AnnotationNode> p, boolean includeInterim) {
		return getAnnotatedElementsInHierarchy(p, new HashSet<>(), includeInterim);
	}

	private List<Type> getAnnotatedElementsInHierarchy(Predicate<AnnotationNode> p, Set<String> seen,
			boolean includeInterim) {
		if (dimensions > 0)
			return Collections.emptyList();
		List<Type> results = new ArrayList<>();
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				boolean match = p.test(an);
				if (match || seen.add(an.desc)) {
					if (match) {
						results.add(this);
					} else {
						Type annoType = typeSystem.Lresolve(an.desc, true);
						if (annoType != null) {
							List<Type> ts = annoType.getAnnotatedElementsInHierarchy(p, seen, includeInterim);
							if (ts.size() != 0 && includeInterim) {
								// Include interim enables us to catch intermediate annotations on the route to
								// the one that passes the predicate test.
								results.add(this);
							}
							results.addAll(ts);
						}
					}
				}
			}
		}
		return results.size() > 0 ? results : Collections.emptyList();
	}

	private List<Type> getJavaxAnnotations(Set<String> seen) {
		if (dimensions > 0)
			return Collections.emptyList();
		List<Type> result = new ArrayList<>();
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (seen.add(an.desc)) {
					Type annoType = typeSystem.Lresolve(an.desc, true);
					if (annoType != null) {
						if (annoType.getDottedName().startsWith("javax.")) {
							result.add(annoType);
						} else {
							List<Type> ts = annoType.getJavaxAnnotations(seen);
							result.addAll(ts);
						}
					}
				}
			}
		}
		return result;
	}

	public List<Type> getNestedTypes() {
		if (dimensions > 0)
			return Collections.emptyList();
		List<Type> result = null;
		List<InnerClassNode> innerClasses = node.innerClasses;
		for (InnerClassNode inner : innerClasses) {
			if (inner.outerName == null || !inner.outerName.equals(getName())) {
				// logger.debug("SKIPPING "+inner.name+" because outer is
				// "+inner.outerName+" and we are looking at "+getName());
				continue;
			}
			if (inner.name.equals(getName())) {
				continue;
			}
			Type t = typeSystem.resolve(inner.name); // aaa/bbb/ccc/Ddd$Eee
			if (result == null) {
				result = new ArrayList<>();
			}
			result.add(t);
		}
		return result == null ? Collections.emptyList() : result;
	}

	public String getDescriptor() {
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < dimensions; i++) {
			s.append("[");
		}
		s.append("L").append(node.name.replace(".", "/")).append(";");
		return s.toString();
	}

	/**
	 * Find compilation hints directly on this type or used as a meta-annotation on
	 * annotations on this type.
	 * @return the hints
	 */
	public List<HintApplication> getApplicableHints() {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<HintApplication> hints = new ArrayList<>();
		List<HintDeclaration> hintDeclarations = typeSystem.findHints(getName());
		if (hintDeclarations.size() != 0) {
			List<Type> s = new ArrayList<>();
			s.add(this);
			for (HintDeclaration hintDeclaration : hintDeclarations) {
				hints.add(new HintApplication(s, Collections.emptyMap(), hintDeclaration));
			}
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				Type annotationType = typeSystem.Lresolve(an.desc, true);
				if (annotationType == null) {
					logger.debug("Couldn't resolve " + an.desc + " annotation type whilst searching for hints on "
							+ getName());
				} else {
					Stack<Type> s = new Stack<>();
					s.push(this);
					annotationType.collectHints(an, hints, new HashSet<>(), s);
				}
			}
		}
		try {
			if (isImportSelector() && hints.size() == 0) {
				// Failing early as this will likely result in a problem later - fix is
				// typically (right now) to add a new Hint in the configuration module
//				if (typeSystem.failOnMissingSelectorHint() && !typeSystem.isNativeNextMode()) {
//					throw new IllegalStateException("No access hint found for import selector: " + getDottedName());
//				} else {
				logger.debug("WARNING: No access hint found for import selector: " + getDottedName());
//				}
			}
		} catch (MissingTypeException mte) {
			logger.debug("Unable to determine if type " + getName()
					+ " is import selector - can't fully resolve hierarchy - ignoring");
		}
		return hints.size() == 0 ? Collections.emptyList() : hints;
	}

	void collectHints(AnnotationNode an, List<HintApplication> hints, Set<AnnotationNode> visited, Stack<Type> annotationChain) {
		if (!visited.add(an)) {
			return;
		}
		try {
			annotationChain.push(this);
			List<HintDeclaration> hintsOnAnnotation = typeSystem.findHints(an.desc);
			if (hintsOnAnnotation.size() != 0) {
				List<String> extractAttributeNames = hintsOnAnnotation.stream().map(hd -> hd.getExtractAttributeNames()).filter(x->x!=null).flatMap(List::stream).collect(Collectors.toList());
				List<String> typesCollectedFromAnnotation = collectTypeReferencesInAnnotation(an, extractAttributeNames);
				if (an.desc.equals(Type.AtEnableConfigurationProperties)) {
					Map<String,Integer> propertyTypesForAccess = processConfigurationProperties(typesCollectedFromAnnotation);
					Map<String,Integer> newMap = new HashMap<>();
					for (Map.Entry<String,Integer> entry: propertyTypesForAccess.entrySet()) {
						newMap.put(fromLdescriptorToDotted(entry.getKey()),entry.getValue());
					}
					if (DEBUG_CONFIGURATION_PROPERTY_ANALYSIS) {
						logger.debug("ConfigurationPropertyAnalysis: whilst looking at type "+typesCollectedFromAnnotation+" making these accessible:"+
								newMap.entrySet().stream().map(e -> "\n"+e.getKey()+":"+AccessBits.toString(e.getValue())).collect(Collectors.toList()));
					}
					for (HintDeclaration hintOnAnnotation : hintsOnAnnotation) {
						hints.add(new HintApplication(new ArrayList<>(annotationChain), newMap, hintOnAnnotation));
					}
				} else {
					for (HintDeclaration hintOnAnnotation : hintsOnAnnotation) {
						hints.add(new HintApplication(new ArrayList<>(annotationChain),
							asMap(typesCollectedFromAnnotation, hintOnAnnotation.skipIfTypesMissing),
							hintOnAnnotation));
					}
				}
			}
			// check for hints on meta annotation
			if (node.visibleAnnotations != null) {
				for (AnnotationNode visibleAnnotation : node.visibleAnnotations) {
					Type annotationType = typeSystem.Lresolve(visibleAnnotation.desc, true);
					if (annotationType == null) {
						logger.debug("Couldn't resolve " + visibleAnnotation.desc
								+ " annotation type whilst searching for hints on " + getName());
					} else {
						annotationType.collectHints(visibleAnnotation, hints, visited, annotationChain);
					}
				}
			}
		} finally {
			annotationChain.pop();
		}
	}
	
	private final static boolean DEBUG_CONFIGURATION_PROPERTY_ANALYSIS = false;
	
	/**
	 * Treat this type as a configuration properties, and based on the needs
	 * of that behaviour compute a map for all the types (including this one)
	 * that will need reflective access (creating a map of type names to AccessBits).
	 */
	public Map<String,Integer> processAsConfigurationProperties() {
		return processConfigurationProperties(Collections.singletonList(this.getDescriptor()));
	}
	
	private Map<String,Integer> processConfigurationProperties(List<String> propertiesTypes) {
		if (disablePropertyReflection) return Collections.emptyMap();
		Map<String,Integer> collector = new HashMap<>();
		for (String propertiesType: propertiesTypes) {
			Type type = typeSystem.Lresolve(propertiesType, true);
			if (type != null) {
				boolean shouldWalk = true;
				// TODO Uncomment when we know how to pass AotConfiguration here
				if (typeSystem.isBuildTimePropertyChecking()) {
					if (DEBUG_CONFIGURATION_PROPERTY_ANALYSIS) {
						logger.debug("Build time property checking for "+type.getDottedName());
					}
					String prefix = type.getConfigurationPropertiesPrefix();
					if (prefix!=null && typeSystem.buildTimeCheckableProperty(prefix)) {
						if (DEBUG_CONFIGURATION_PROPERTY_ANALYSIS) {
							logger.debug("found "+prefix+" is checkable at build time");
						}
						boolean foundSomethingToBindInThatConfigProps = false;
						Map<String, String> activeProperties = typeSystem.getActiveProperties();
						if (DEBUG_CONFIGURATION_PROPERTY_ANALYSIS) {
							logger.debug("Comparing prefix "+prefix+" against entries: "+activeProperties);
						}
						for (Map.Entry<String,String> activeProperty: activeProperties.entrySet()) {
							if (activeProperty.getKey().startsWith(prefix)) {
								if (DEBUG_CONFIGURATION_PROPERTY_ANALYSIS) {
									logger.debug("found something binding to that prefix in specified set of active properties");
								}
								foundSomethingToBindInThatConfigProps = true;
							}
						}
						if (!foundSomethingToBindInThatConfigProps) {
							shouldWalk=false;
						}
					}
				}
				if (shouldWalk) {
					walkPropertyType(propertiesType, collector);
				} else {
					// Nothing is binding to this so we don't need to expose any fields or methods
					collector.put(propertiesType, AccessBits.CLASS|AccessBits.DECLARED_CONSTRUCTORS);
				}
			}
		}
		return collector;
	}
	
	private String getConfigurationPropertiesPrefix() {
		Map<String,String> values = getAnnotationValuesInHierarchy(AtConfigurationProperties);
		if (values != null) {
			String prefix = values.get("prefix");
			if ((prefix == null || prefix.length()==0) && values.get("value")!=null) {
				prefix = values.get("value");
			}
			return prefix;
		}
		return null;
	}

	private Integer inferPropertyAccessRequired(String propertiesType) {
		if (propertiesType.startsWith("Ljava/lang/") || // String/Integer/...
			propertiesType.startsWith("Ljava/nio/") || // Charset
			propertiesType.startsWith("Ljava/io/") || // File
			propertiesType.startsWith("Ljava/net/") // InetAddress
			) {
			return 0;
		}
		// See convertors in ApplicationConversionService.addApplicationConverters
		if (propertiesType.equals("Ljava/time/Period;") ||
			propertiesType.equals("Ljava/time/Duration;") ||
			propertiesType.equals("Lorg/springframework/util/unit/DataSize;") ||
			propertiesType.startsWith("Lorg/springframework/core/io/") || // catching Resource, anything else?
			propertiesType.startsWith("Ljava/util/") // Map/List/Set/...   issue #382
			) { 
			return AccessBits.CLASS;
		}
		Type type = typeSystem.Lresolve(propertiesType, true);
		if (type.isEnum()) {
			return AccessBits.CLASS;
		}
		int access = AccessBits.CLASS | AccessBits.DECLARED_CONSTRUCTORS;
		if (type.isAtValidated()) {
			// Need access to the annotations on the fields that define validation constraints
			access |= AccessBits.DECLARED_FIELDS;
		}
		if (!type.isAtConstructorBinding()) {
			// If not constructor binding, need access to the setters (yes, this will currently give too much access as it will include getters)
			access |= AccessBits.PUBLIC_METHODS;//DECLARED_METHODS;
		}
		return access;
	}

	/**
	 * Review the getters in a given configuration properties type, for any types in the return
	 * types of the getter, add them for later reflective access. Include any in the generic
	 * signature of the return type too.
	 */
	private void walkPropertyType(String propertiesType, Map<String,Integer> collector) {
		Type type = typeSystem.Lresolve(propertiesType, true);
		if (type != null) {
			if (!collector.containsKey(propertiesType)) {
				int inferredAccess = inferPropertyAccessRequired(propertiesType);
//				logger.debug("inferPropertyAccessRequired: "+propertiesType+" return "+AccessBits.toString(inferredAccess));
				if (inferredAccess !=0) {
					collector.put(propertiesType, inferredAccess);
					if (inferredAccess != AccessBits.CLASS) { 
						for (Method method : type.getMethods(m -> (m.getName().startsWith("get")))) {
							for (Type returnSignatureType: method.getSignatureTypes(true)) {
								String returnTypeDescriptor = returnSignatureType.getDescriptor();
								walkPropertyType(returnTypeDescriptor, collector);
							}
						}
					}
				}
			}
		}
	}

	public boolean isEnum() {
		return (node.access & Opcodes.ACC_ENUM)!=0;
	}

	private Map<String, Integer> asMap(List<String> typesCollectedFromAnnotation, boolean usingForVisibilityCheck) {
		Map<String, Integer> map = new HashMap<>();
		for (String t : typesCollectedFromAnnotation) {
			Type type = typeSystem.Lresolve(t, true);
			int ar = -1;
			if (usingForVisibilityCheck) {
				ar = AccessBits.CLASS;
			} else {
				ar = inferAccessRequired(type);
			}
			if (ar!= AccessBits.NONE) {
				map.put(fromLdescriptorToDotted(t), ar);
			}
		}
		return map;
	}

	private Type[] getMemberTypes() {
		if (dimensions > 0)
			return new Type[0];
		List<Type> result = new ArrayList<>();
		List<InnerClassNode> nestMembers = node.innerClasses;
		if (nestMembers != null) {
			for (InnerClassNode icn : nestMembers) {
				if (icn.name.startsWith(this.getName() + "$")) {
					result.add(typeSystem.resolveSlashed(icn.name));
				}
			}
			logger.debug(this.getName()
					+ " has inners " + nestMembers.stream().map(f -> "oo=" + this.getDescriptor() + "::o=" + f.outerName
							+ "::n=" + f.name + "::in=" + f.innerName).collect(Collectors.joining(","))
					+ "  >> " + result);
		}
		return result.toArray(new Type[0]);
	}
	
	public List<String> getMethodsInvokingGetBean() {
		byte[] bytes = typeSystem.find(getName());
		try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
			return GetBeanDetectionVisitor.run(bais);
		} catch (IOException e) {
			throw new IllegalStateException("Unexpected IOException processing bytes for "+this.getName());
		}
	}

	public List<String> getMethodsInvokingAtBeanMethods() {
		byte[] bytes = typeSystem.find(getName());
		try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
			return AtBeanMethodInvocationDetectionVisitor.run(typeSystem, bais);
		} catch (IOException e) {
			throw new IllegalStateException("Unexpected IOException processing bytes for "+this.getName());
		}
	}


	/*
	private List<CompilationHint> findCompilationHintHelper(HashSet<Type> visited) {
		if (!visited.add(this)) {
			return null;
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				List<CompilationHint> compilationHints = typeSystem.findHints(an.desc);// SpringConfiguration.findProposedHints(an.desc);
				if (compilationHints.size() != 0) {
					return compilationHints;
				}
				Type resolvedAnnotation = typeSystem.Lresolve(an.desc);
				compilationHints = resolvedAnnotation.findCompilationHintHelper(visited);
				if (compilationHints.size() != 0) {
					return compilationHints;
				}
			}
		}
		return null;
	}
	*/

	@SuppressWarnings("rawtypes")
	private List<String> collectTypeReferencesInAnnotation(AnnotationNode an, List<String> extractAttributeNames) {
		List<Object> values = an.values;
		List<String> importedReferences = new ArrayList<>();
		if (values != null) {
			for (int i = 0; i < values.size(); i += 2) {
				String attributeName = (String)values.get(i);
				if (attributeName.equals("value") || 
						extractAttributeNames.contains(attributeName)
					) {
					// For some annotations it is a list, for some a single class (e.g.
					// @ConditionalOnSingleCandidate)
					Object object = values.get(i + 1);
					if (object instanceof List) {
						// Adapt to whatever is in the list
						List listValue = (List)object;
						if (listValue.size()>0) {
							Object listEntry = listValue.get(0);
							if (listEntry instanceof org.objectweb.asm.Type) {
								List<String> toAdd = ((List<org.objectweb.asm.Type>) object).stream()
										.map(t -> t.getDescriptor()).collect(Collectors.toList());
								importedReferences.addAll(toAdd);
							} else if (listEntry instanceof String) {
								for (Object o: listValue) {
									importedReferences.add("L"+((String)o).replace(".", "/")+";");
								}
							}
						}
					} else {
						if (object instanceof org.objectweb.asm.Type) {
							importedReferences.add(((org.objectweb.asm.Type) object).getDescriptor());
						} else if (object instanceof String) {
							importedReferences.add("L" + ((String) object).replace(".", "/") + ";");
						}
					}
				}
			}
		}
		return importedReferences.size() == 0 ? Collections.emptyList() : importedReferences;
	}

	public boolean isImportSelector() {
		try {
			return implementsInterface(fromLdescriptorToSlashed(ImportSelector));
		} catch (MissingTypeException mte) {
			return false;
		}
	}
	
	public boolean isAtValidated() {
		return (dimensions > 0) ? false : isMetaAnnotated(fromLdescriptorToSlashed(AtValidated));	
	}
	
	public boolean isAtConstructorBinding() {
		return (dimensions > 0) ? false : isMetaAnnotated(fromLdescriptorToSlashed(AtConstructorBinding));	
	}

	public boolean isApplicationListener() {
		try {
			return implementsInterface(fromLdescriptorToSlashed(ApplicationListener));
		} catch (MissingTypeException mte) {
			return false;
		}
	}

	private boolean isEnvironmentPostProcessor() {
		return implementsInterface(fromLdescriptorToSlashed(EnvironmentPostProcessor),true);
	}

	public boolean isBeanFactoryPostProcessor() {
		try {
			return implementsInterface(fromLdescriptorToSlashed(BeanFactoryPostProcessor));
		} catch (MissingTypeException mte) {
			return false;
		}
	}
	
	public boolean isBeanPostProcessor() {
		try {
			return implementsInterface(fromLdescriptorToSlashed(BeanPostProcessor));
		} catch (MissingTypeException mte) {
			return false;
		}
	}


	public boolean isImportRegistrar() {
		try {
			return implementsInterface(fromLdescriptorToSlashed(ImportBeanDefinitionRegistrar));
		} catch (MissingTypeException mte) {
			return false;
		}
	}
	
	public static String fromDottedToLDescriptor(String dotted)  {
		if (dotted.contains("[")) 
			throw new IllegalStateException("arrays not handled yet");
		return "L"+dotted.replace(".","/")+";";
	}
	

	public static String fromLdescriptorToSlashed(String Ldescriptor) {
		int dims = 0;
		int p = 0;
		if (Ldescriptor.startsWith("[")) {
			while (Ldescriptor.charAt(p) == '[') {
				p++;
				dims++;
			}
		}
		StringBuilder r = new StringBuilder();
		r.append(Ldescriptor.substring(p + 1, Ldescriptor.length() - 1));
		for (int i = 0; i < dims; i++) {
			r.append("[]");
		}
		return r.toString();
	}

	public static String fromLdescriptorToDotted(String Ldescriptor) {
		return fromLdescriptorToSlashed(Ldescriptor).replace("/", ".");
	}

	public void collectMissingAnnotationTypesHelper(Set<String> missingAnnotationTypes, HashSet<Type> visited) {
		if (dimensions > 0)
			return;
		if (!visited.add(this)) {
			return;
		}
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				Type annotationType = typeSystem.Lresolve(an.desc, true);
				if (annotationType == null) {
					missingAnnotationTypes.add(an.desc.substring(0, an.desc.length() - 1).replace("/", "."));
				} else {
					annotationType.collectMissingAnnotationTypesHelper(missingAnnotationTypes, visited);
				}
			}
		}
	}

	public int getMethodCount(boolean includeConstructors) {
		if (includeConstructors) {
			return node.methods.size();
		} else {
			int m = 0;
			for (MethodNode methodNode : node.methods) {
				if (!methodNode.name.equals("<init>")) {
					m++;
				}
			}
			return m;
		}
	}

	public boolean isAnnotation() {
		if (dimensions > 0)
			return false;
		return (node.access & Opcodes.ACC_ANNOTATION) != 0;
	}

	public List<Type> getAutoConfigureBeforeOrAfter() {
		if (dimensions > 0)
			return Collections.emptyList();
		List<Type> result = new ArrayList<>();
		if (node.visibleAnnotations!=null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals("Lorg/springframework/boot/autoconfigure/AutoConfigureAfter;")
						|| an.desc.equals("Lorg/springframework/boot/autoconfigure/AutoConfigureBefore;")) {
					List<Object> values = an.values;
					if (values != null) {
						for (int i = 0; i < values.size(); i += 2) {
							if (values.get(i).equals("value")) {
								List<org.objectweb.asm.Type> types = (List<org.objectweb.asm.Type>) values.get(i + 1);
								for (org.objectweb.asm.Type type : types) {
									Type t = typeSystem.Lresolve(type.getDescriptor(), true);
									if (t != null) {
										result.add(t);
									}
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	// TODO this isn't quite right as it doesn't check for other constructors!
	public boolean hasOnlySimpleConstructor() {
		return getDefaultConstructor() != null;
	}
	
	public boolean hasNoArgConstructor() {
		return getDefaultConstructor() !=null;
	}

	/**
	 * Look for Hint related annotations (for example {@link NativeHint} or {@link TypeHint})
	 * on this type and unpack them, building a series of HintDeclarations. The Hint related
	 * annotations are repeatable and this code handles that.
	 * 
	 * @return the hint declarations
	 */
	public List<HintDeclaration> unpackHints() {
		if (dimensions > 0) {
			return Collections.emptyList();
		}
		List<HintDeclaration> hints = new ArrayList<>();
		HintDeclaration defaultHintDeclaration = new HintDeclaration();
		if (implementsInterface("org/springframework/nativex/type/NativeConfiguration")) {
			// It is an 'always on' hint
			defaultHintDeclaration.setTriggerTypename("java.lang.Object");
		} else {
			// the trigger is the annotation host
			defaultHintDeclaration.setTriggerTypename(getDottedName());
		}
		boolean defaultHintPopulated = false;
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				String name = fromLdescriptorToDotted(an.desc);
				if (name.equals(NativeHint.class.getName())) {
					hints.add(unpackNativeHint(an));
				} else if (name.equals(NativeHints.class.getName())) {
					hints.addAll(unpackNativeHints(an));
				} else if (name.equals(SerializationHint.class.getName())) {
					defaultHintPopulated=true;
					unpackSerializationHint(an, defaultHintDeclaration);
				} else if (name.equals(SerializationHints.class.getName())) {
					defaultHintPopulated=true;
					processRepeatableAnnotationList(an, anno -> unpackSerializationHint(anno, defaultHintDeclaration));
				} else if (name.equals(TypeHint.class.getName())) {
					defaultHintPopulated=true;
					unpackTypeHint(an, defaultHintDeclaration);
				} else if (name.equals(TypeHints.class.getName())) {
					defaultHintPopulated=true;
					processRepeatableAnnotationList(an, anno -> unpackTypeHint(anno, defaultHintDeclaration));
				} else if (name.equals(JdkProxyHint.class.getName())) {
					defaultHintPopulated=true;
					unpackProxyHint(an, defaultHintDeclaration);
				} else if (name.equals(JdkProxyHints.class.getName())) {
					defaultHintPopulated=true;
					processRepeatableAnnotationList(an, anno -> unpackProxyHint(anno, defaultHintDeclaration));
				} else if (name.equals(AotProxyHint.class.getName())) {
					defaultHintPopulated=true;
					unpackAotProxyHint(an, defaultHintDeclaration);
				} else if (name.equals(AotProxyHints.class.getName())) {
					defaultHintPopulated=true;
					processRepeatableAnnotationList(an, anno -> unpackAotProxyHint(anno, defaultHintDeclaration));
				} else if (name.equals(ResourceHint.class.getName())) {
					defaultHintPopulated=true;
					unpackResourceHint(an, defaultHintDeclaration);
				} else if (name.equals(ResourcesHints.class.getName())) {
					defaultHintPopulated=true;
					processRepeatableAnnotationList(an, anno -> unpackResourceHint(anno, defaultHintDeclaration));
				} else if (name.equals(InitializationHint.class.getName())) {
					defaultHintPopulated=true;
					unpackInitializationHint(an, defaultHintDeclaration);
				} else if (name.equals(InitializationHints.class.getName())) {
					defaultHintPopulated=true;
					processRepeatableAnnotationList(an, anno -> unpackInitializationHint(anno, defaultHintDeclaration));
				}
			}
		}
		if (defaultHintPopulated) {
			hints.add(defaultHintDeclaration);
		}
		return hints.isEmpty() ? Collections.emptyList() : hints;
	}

	private HintDeclaration unpackNativeHint(AnnotationNode an) {
		HintDeclaration ch = new HintDeclaration();
		List<Object> values = an.values;
		if (values != null) {
			for (int i = 0; i < values.size(); i += 2) {
				String key = (String) values.get(i);
				Object value = values.get(i + 1);
				if (key.equals("trigger")) {
					ch.setTriggerTypename(((org.objectweb.asm.Type) value).getClassName());
				} else if (key.equals("options")) {
					for (String option : (List<String>) value) {
						ch.addOption(option);
					}
				} else if (key.equals("types")) {
					processHintList(value, anno -> unpackTypeHint(anno,ch));
				} else if (key.equals("imports")) {
					processImportHints(ch, value);
				} else if (key.equals("jdkProxies")) {
					processHintList(value, anno -> unpackProxyHint(anno,ch));
				} else if (key.equals("aotProxies")) {
					processHintList(value, anno -> unpackAotProxyHint(anno,ch));
				} else if (key.equals("resources")) {
					processHintList(value, anno -> unpackResourceHint(anno,ch));
				} else if (key.equals("initialization")) {
					processHintList(value, anno -> unpackInitializationHint(anno,ch));
				} else if (key.equals("serializables")) {
					processHintList(value, anno -> unpackSerializationHint(anno,ch));
				} else if (key.equals("abortIfTypesMissing")) {
					Boolean b = (Boolean) value;
					ch.setAbortIfTypesMissing(b);
				} else if (key.equals("follow")) {
					Boolean b = (Boolean) value;
					ch.setFollow(b);
				} else if (key.equals("extractTypesFromAttributes")) {
					ch.setAttributesToExtract((List<String>)value);
				} else {
					logger.debug("annotation " + key + "=" + value + "(" + value.getClass() + ")");
				}
			}
		}
		if (ch.getTriggerTypename() == null) {
			ch.setTriggerTypename("java.lang.Object");
		}
		return ch;
	}

	private void processHintList(Object value, Consumer<AnnotationNode> consumer) {
		for (AnnotationNode hint : asList(value, AnnotationNode.class)) {
			consumer.accept(hint);
		}
	}

	private <T> List<T> asList(Object value, Class<T> type) {
		return (List<T>) value;
	}

	private void processImportHints(HintDeclaration ch, Object value) {
		List<org.objectweb.asm.Type> importInfos = (ArrayList<org.objectweb.asm.Type>) value;
		for (org.objectweb.asm.Type importInfo : importInfos) {
			String className = importInfo.getClassName();
			Type resolvedImportInfo = typeSystem.resolveDotted(className, true);
			if (resolvedImportInfo == null) {
				throw new IllegalStateException("Cannot find importInfos referenced type: " + className);
			}
			ClassNode node = resolvedImportInfo.getClassNode();
			if (node.visibleAnnotations != null) {
				for (AnnotationNode an : node.visibleAnnotations) {
					String annotationClassname = fromLdescriptorToDotted(an.desc);
					if (annotationClassname.equals(TypeHint.class.getName())) {
						unpackTypeHint(an, ch);
					} else if (annotationClassname.equals(TypeHints.class.getName())) {
						processRepeatableAnnotationList(an, anno -> unpackTypeHint(anno, ch));
					} else if (annotationClassname.equals(ResourceHint.class.getName())) {
						unpackResourceHint(an, ch);
					} else if (annotationClassname.equals(ResourcesHints.class.getName())) {
						processRepeatableAnnotationList(an, anno -> unpackResourceHint(anno, ch));
					} else if (annotationClassname.equals(JdkProxyHint.class.getName())) {
						unpackProxyHint(an, ch);
					} else if (annotationClassname.equals(JdkProxyHints.class.getName())) {
						processRepeatableAnnotationList(an, anno -> unpackProxyHint(anno, ch));
					} else if (annotationClassname.equals(InitializationHint.class.getName())) {
						unpackInitializationHint(an, ch);
					} else if (annotationClassname.equals(InitializationHints.class.getName())) {
						processRepeatableAnnotationList(an, anno -> unpackInitializationHint(anno, ch));
					} else if (annotationClassname.equals(SerializationHint.class.getName())) {
						unpackSerializationHint(an, ch);
					} else if (annotationClassname.equals(SerializationHints.class.getName())) {
						processRepeatableAnnotationList(an, anno -> unpackSerializationHint(anno, ch));
					}
				}
			}

		}
	}

	ClassNode getClassNode() {
		return node;
	}

	private void unpackTypeHint(AnnotationNode typeInfo, HintDeclaration ch) {
		List<Object> values = typeInfo.values;
		List<org.objectweb.asm.Type> types = new ArrayList<>();
		List<String> typeNames = new ArrayList<>();
		int accessRequired = -1;
		boolean isJniHint = false;
		List<MethodDescriptor> mds = new ArrayList<>();
		List<FieldDescriptor> fds = new ArrayList<>();
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("types")) {
				types = (ArrayList<org.objectweb.asm.Type>) value;
			} else if (key.equals("access")) {
				accessRequired = (Integer) value;
				if (accessRequired == AccessBits.JNI) {
					accessRequired = -1; // reset to allow inferencing to occur
					isJniHint = true;
				} else if ((accessRequired & AccessBits.JNI)!=0) {
					accessRequired = accessRequired - AccessBits.JNI;
					isJniHint = true;
				}
			} else if (key.equals("typeNames")) {
				typeNames = (ArrayList<String>) value;
			} else if (key.equals("methods")) {
				processHintList(value, anno -> unpackMethodInfo(anno, mds));
			} else if (key.equals("fields")) {
				processHintList(value, anno -> unpackFieldInfo(anno, fds));
			}
		}
		String mode = typeSystem.aotOptions==null?null:typeSystem.aotOptions.getMode();
		if (mode!=null && accessRequired != -1 && 
			mode.equals(org.springframework.nativex.support.Mode.NATIVE_NEXT.toString()) && 
			(accessRequired&AccessBits.SKIP_FOR_NATIVE_NEXT)!=0) {
			logger.debug("Skipping hints not useful for NATIVE_NEXT: found on "+this.getDottedName()+" (includes TypeHints for "+types+")");
			return;
		}
		if (accessRequired == AccessBits.SKIP_FOR_NATIVE_NEXT) {
			accessRequired = -1;
		} else if (accessRequired !=-1 && (accessRequired & AccessBits.SKIP_FOR_NATIVE_NEXT)!=0) {
			accessRequired = accessRequired - AccessBits.SKIP_FOR_NATIVE_NEXT;
		}
		System.out.println(accessRequired+" "+Integer.toHexString(accessRequired));
		for (org.objectweb.asm.Type type : types) {
			AccessDescriptor ad = null;
			if (accessRequired == -1) {
				ad = new AccessDescriptor(inferAccessRequired(type, mds, fds), mds, fds);
			} else {
				if ((MethodDescriptor.includesConstructors(mds) || MethodDescriptor.includesStaticInitializers(mds)) && 
						AccessBits.isSet(accessRequired, AccessBits.DECLARED_METHODS|AccessBits.PUBLIC_METHODS)) {
					throw new IllegalStateException("Do not include global method reflection access when specifying individual methods");
				}
				ad = new AccessDescriptor(accessRequired, mds, fds);
			}
			if (isJniHint) {
				ch.addJniType(type.getClassName(), ad);
			} else {
				ch.addDependantType(type.getClassName(), ad);				
			}
		}
		for (String typeName : typeNames) {
			Type resolvedType = typeSystem.resolveName(typeName, true);
			if (resolvedType != null) {
				AccessDescriptor ad = null;
				if (accessRequired == -1) {
					ad = new AccessDescriptor(inferAccessRequired(resolvedType), mds, fds);
				} else {
					if ((MethodDescriptor.includesConstructors(mds) || MethodDescriptor.includesStaticInitializers(mds)) && 
							AccessBits.isSet(accessRequired, AccessBits.DECLARED_METHODS|AccessBits.PUBLIC_METHODS)) {
						throw new IllegalStateException("Do not include global method reflection access when specifying individual methods");
					}
					ad = new AccessDescriptor(accessRequired, mds, fds);
				}
				if (isJniHint) {
					ch.addJniType(typeName, ad);
				} else {
					ch.addDependantType(typeName, ad);				
				}
			}
		}
	}

	private void unpackAotProxyHint(AnnotationNode typeInfo, HintDeclaration ch) {
		List<Object> values = typeInfo.values;
		String targetClassName = "java.lang.Object";
		List<org.objectweb.asm.Type> interfaces = new ArrayList<>();
		List<String> interfaceNames = new ArrayList<>();
		int proxyFeatures = ProxyBits.NONE;
		boolean typeMissing = false;
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("targetClass")) {
				targetClassName = ((org.objectweb.asm.Type) value).getClassName();
			} else if (key.equals("targetClassName")) {
				targetClassName = (String) value;
			} else if (key.equals("interfaces")) {
				interfaces = (ArrayList<org.objectweb.asm.Type>) value;
			} else if (key.equals("interfaceNames")) {
				interfaceNames = (ArrayList<String>) value;
			} else if (key.equals("proxyFeatures")) {
				proxyFeatures = (Integer)value;
			}
		}
		Type resolvedType = typeSystem.resolveName(targetClassName, true);
		if (resolvedType == null) {
			typeMissing = true;
		}		
		List<String> proxyInterfaceTypes = new ArrayList<>();
		for (org.objectweb.asm.Type intface : interfaces) {
			String intfaceName = intface.getClassName();
			resolvedType = typeSystem.resolveName(intfaceName, true);
			if (resolvedType != null) {
				proxyInterfaceTypes.add(intfaceName);
			} else {
				typeMissing = true;
			}
		}
		for (String intfaceName : interfaceNames) {
			resolvedType = typeSystem.resolveName(intfaceName, true);
			if (resolvedType != null) {
				proxyInterfaceTypes.add(intfaceName);
			} else {
				typeMissing = true;
			}
		}
		if (!typeMissing) {
			AotProxyDescriptor cpd = new AotProxyDescriptor(
					targetClassName,
					proxyInterfaceTypes,
					proxyFeatures
					);
			ch.addProxyDescriptor(cpd);
		}
	}

	private void unpackSerializationHint(AnnotationNode typeInfo, HintDeclaration ch) {
		List<Object> values = typeInfo.values;
		List<org.objectweb.asm.Type> types = new ArrayList<>();
		List<String> typeNames = new ArrayList<>();
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("types")) {
				types = (ArrayList<org.objectweb.asm.Type>) value;
			} else if (key.equals("typeNames")) {
				typeNames = (ArrayList<String>) value;
			}
		}
		for (org.objectweb.asm.Type type : types) {
			ch.addSerializationType(type.getClassName());
		}
		for (String typeName : typeNames) {
			Type resolvedType = typeSystem.resolveName(typeName, true);
			if (resolvedType != null) {
				ch.addSerializationType(typeName);
			}
		}
	}

	private Integer inferAccessRequired(org.objectweb.asm.Type type, List<MethodDescriptor> mds,
			List<FieldDescriptor> fds) {
		int inferredAccess = inferAccessRequired(type);
		int originalAccess = inferredAccess;
		// Adjust inferred access if explicit method/constructor/field references specified
		boolean includesConstructors = MethodDescriptor.includesConstructors(mds);
		boolean includesMethods = MethodDescriptor.includesMethods(mds);
		if (includesMethods) {
			if ((inferredAccess & AccessBits.DECLARED_METHODS)!= 0) {
				inferredAccess-=AccessBits.DECLARED_METHODS;
			}
			if ((inferredAccess & AccessBits.PUBLIC_METHODS)!= 0) {
				inferredAccess-=AccessBits.PUBLIC_METHODS;
			}
		}
		if (includesConstructors) {
			if ((inferredAccess & AccessBits.DECLARED_CONSTRUCTORS) ==0) {
				inferredAccess-=AccessBits.DECLARED_CONSTRUCTORS;
			}
		}
		if (fds!=null && !fds.isEmpty()) {
			if ((inferredAccess & AccessBits.DECLARED_FIELDS)!= 0) {
				inferredAccess-=AccessBits.DECLARED_FIELDS;
			}
		}
		if (inferredAccess != originalAccess) {
			logger.debug("Modifying default inferred access to "+type.getClassName()+" from "+
					AccessBits.toString(originalAccess)+" to "+AccessBits.toString(inferredAccess));
		}
		return inferredAccess;
	}

	private void unpackFieldInfo(AnnotationNode fieldInfo, List<FieldDescriptor> fds) {
		List<Object> values = fieldInfo.values;
		String name = null;
		boolean allowUnsafeAccess = false; // default is false
		boolean allowWrite = false; // default is false
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("allowUnsafeAccess")) {
				allowUnsafeAccess = (Boolean) value;
			} else if (key.equals("allowWrite")) {
				allowWrite = (Boolean) value;
			} else if (key.equals("name")) {
				name = (String) value;
			}
		}
		fds.add(new FieldDescriptor(name, allowWrite, allowUnsafeAccess));
	}

	private void unpackMethodInfo(AnnotationNode methodInfo, List<MethodDescriptor> mds) {
		List<Object> values = methodInfo.values;
		List<org.objectweb.asm.Type> parameterTypes = new ArrayList<>();
		String name = null;
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("parameterTypes")) {
				parameterTypes = (ArrayList<org.objectweb.asm.Type>) value;
			} else if (key.equals("name")) {
				name = (String) value;
			}
		}
		boolean unresolvable = false;
		List<String> resolvedParameterTypes = new ArrayList<>();
		for (org.objectweb.asm.Type ptype : parameterTypes) {
			String typeName = ptype.getClassName();
			Type resolvedType = typeSystem.resolveName(typeName, true);
			if (resolvedType != null) {
				resolvedParameterTypes.add(typeName);
			} else {
				unresolvable = true;
			}
		}
		if (unresolvable) {
			StringBuilder message = new StringBuilder();
			for (org.objectweb.asm.Type ptype : parameterTypes) {
				message.append(ptype.getClassName()).append(" ");
			}
			logger.debug("Unable to fully resolve method " + name + "(" + message.toString().trim() + ")");
		} else {
			mds.add(new MethodDescriptor(name, resolvedParameterTypes));
		}
	}

	private void unpackProxyHint(AnnotationNode typeInfo, HintDeclaration ch) {
		List<Object> values = typeInfo.values;
		List<org.objectweb.asm.Type> types = new ArrayList<>();
		List<String> typeNames = new ArrayList<>();
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("types")) {
				types = (ArrayList<org.objectweb.asm.Type>) value;
			} else if (key.equals("typeNames")) {
				typeNames = (ArrayList<String>) value;
			}
		}
		// Note: Proxies hints will get discarded immediately if types are not around
		List<String> proxyTypes = new ArrayList<>();
		boolean typeMissing = false;
		boolean typesSpecified = false;
		boolean typenamesSpecified = false;
		for (org.objectweb.asm.Type type : types) {
			typesSpecified = true;
			String typeName = type.getClassName();
			Type resolvedType = typeSystem.resolveName(typeName, true);
			if (resolvedType != null) {
				proxyTypes.add(typeName);
			} else {
				typeMissing = true;
			}
		}
		for (String typeName : typeNames) {
			typenamesSpecified = true;
			Type resolvedType = typeSystem.resolveName(typeName, true);
			if (resolvedType != null) {
				proxyTypes.add(typeName);
			} else {
				typeMissing = true;
			}
		}
		if (typesSpecified && typenamesSpecified) {
			throw new IllegalStateException("ERROR: [Limitation] Don't mix typenames and explicit type references in a ProxyHint on type "+getDottedName());
		}
		if (!typeMissing) {
			ch.addProxyDescriptor(new JdkProxyDescriptor(proxyTypes));
		}
	}

	private void unpackResourceHint(AnnotationNode typeInfo, HintDeclaration ch) {
		List<Object> values = typeInfo.values;
		List<String> patterns = null;
		Boolean isBundle = null;
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("patterns")) {
				patterns = (ArrayList<String>) value;
			} else if (key.equals("isBundle")) {
				isBundle = (Boolean) value;
			}
		}
		ch.addResourcesDescriptor(
				new ResourcesDescriptor(patterns.toArray(new String[0]), isBundle == null ? false : isBundle));
	}

	private void unpackInitializationHint(AnnotationNode typeInfo, HintDeclaration ch) {
		List<Object> values = typeInfo.values;
		List<org.objectweb.asm.Type> types = new ArrayList<>();
		List<String> typeNames = new ArrayList<>();
		List<String> packageNames = new ArrayList<>();
		InitializationTime initTime = null;
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("types")) {
				types = (ArrayList<org.objectweb.asm.Type>) value;
			} else if (key.equals("typeNames")) {
				typeNames = (ArrayList<String>) value;
			} else if (key.equals("packageNames")) {
				packageNames = (ArrayList<String>) value;
			} else if (key.equals("initTime")) {
				initTime = InitializationTime.valueOf(((String[]) value)[1]);
			}
		}
		for (org.objectweb.asm.Type type : types) {
			String typeName = type.getClassName();
			typeNames.add(typeName);
		}
		InitializationDescriptor id = new InitializationDescriptor();
		if (initTime == InitializationTime.BUILD) {
			for (String typeName : typeNames) {
				id.addBuildtimeClass(typeName);
			}
			for (String packageName : packageNames) {
				id.addBuildtimePackage(packageName);
			}
		} else {
			for (String typeName : typeNames) {
				id.addRuntimeClass(typeName);
			}
			for (String packageName : packageNames) {
				id.addRuntimePackage(packageName);
			}
		}
		ch.addInitializationDescriptor(id);
	}

	private int inferAccessRequired(org.objectweb.asm.Type type) {
		Type t = typeSystem.resolve(type, true);
		return inferAccessRequired(t);
	}
	
	public boolean isConfigurationProperties() {
		return (dimensions > 0) ? false
				: isMetaAnnotated(fromLdescriptorToSlashed(AtConfigurationProperties), new HashSet<>());
	}

	public static boolean disablePropertyReflection = false;
	
	static {
		if (disablePropertyReflection) {
			logger.debug("property reflection disabled!");
		}
	}
		
	public static int inferAccessRequired(Type t) {
		if (t == null) {
			return AccessBits.FULL_REFLECTION;
		}
		if (t.isConfigurationProperties() && disablePropertyReflection && t.getTypeSystem().isNativeNextMode()) {
			return AccessBits.NONE;
		} else if ((t.isBeanFactoryPostProcessor() || t.isBeanPostProcessor()) && t.getTypeSystem().isNativeNextMode()) {
			return AccessBits.NONE;
		} else if ((t.isAtConfiguration() || t.isImportSelector() || t.isImportRegistrar()/* || t.isConfigurationProperties()*/) && t.getTypeSystem().isNativeNextMode()) {
			return AccessBits.NONE;
		} else if (t.isAtConfiguration() || t.isMetaImportAnnotated()) {
			return AccessBits.ALL;
		} else if (t.isImportSelector()) {
			return AccessBits.LOAD_AND_CONSTRUCT | AccessBits.RESOURCE;
		} else if (t.isImportRegistrar()) {
			return AccessBits.LOAD_AND_CONSTRUCT | AccessBits.RESOURCE; // Including resource because of KafkaBootstrapConfiguration
		} else if (t.isBeanFactoryPostProcessor()) {
			// vanilla-jpa demos these needing accessing a a resource *sigh*
			// TODO investigate if deeper pattern can tell us why certain things need
			// RESOURCE
			return AccessBits.LOAD_AND_CONSTRUCT | AccessBits.DECLARED_METHODS | AccessBits.RESOURCE;
		} else if (t.isBeanPostProcessor()) {
			return AccessBits.CLASS | AccessBits.DECLARED_CONSTRUCTORS /*| AccessBits.DECLARED_METHODS*/ | AccessBits.RESOURCE;
		} else if (t.isArray()) {
			return AccessBits.CLASS;
		} else if (t.isConfigurationProperties()) {
			int access = AccessBits.CLASS | AccessBits.DECLARED_CONSTRUCTORS;
			if (t.isAtValidated()) {
				access |= AccessBits.DECLARED_FIELDS;
			}
			if (!t.isAtConstructorBinding()) {
				access |= AccessBits.DECLARED_METHODS;
			}
			return access;
		} else if (t.isCondition()) {
			return t.getTypeSystem().isNativeNextMode()?AccessBits.NONE:AccessBits.CLASS | AccessBits.DECLARED_CONSTRUCTORS | AccessBits.RESOURCE;
		} else if (t.isComponent() || t.isApplicationListener()) {
			return AccessBits.ALL;
		} else if (t.isEnvironmentPostProcessor()) {
			return AccessBits.LOAD_AND_CONSTRUCT;
		} else {
			return AccessBits.FULL_REFLECTION;//-AccessBits.DECLARED_FIELDS;
		}
	}

	private List<HintDeclaration> unpackNativeHints(AnnotationNode an) {
		List<HintDeclaration> chs = new ArrayList<>();
		List<Object> values = an.values;
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("value")) {
				List<AnnotationNode> annotationNodes = (List<AnnotationNode>) value;
				for (int j = 0; j < annotationNodes.size(); j++) {
					chs.add(unpackNativeHint(annotationNodes.get(j)));
				}
			}
		}
		return chs;
	}

	private void processRepeatableAnnotationList(AnnotationNode an, Consumer<AnnotationNode> c) {
		List<Object> values = an.values;
		for (int i = 0; i < values.size(); i += 2) {
			String key = (String) values.get(i);
			Object value = values.get(i + 1);
			if (key.equals("value")) {
				List<AnnotationNode> annotationNodes = (List<AnnotationNode>) value;
				for (int j = 0; j < annotationNodes.size(); j++) {
					c.accept(annotationNodes.get(j));
				}
			}
		}
	}

	public List<HintDeclaration> getCompilationHints() {
		if (dimensions > 0)
			return Collections.emptyList();
		return unpackHints();
	}

	public int getDimensions() {
		return dimensions;
	}

	public boolean isArray() {
		return dimensions > 0;
	}

	/**
	 * @return true if type or a method inside is marked @Transactional
	 */
	public boolean isTransactional() {
		// TODO are these usable as meta annotations?
		return isAnnotated(AtTransactional) || isAnnotated(AtJavaxTransactional);
	}

	public boolean hasTransactionalMethods() {
		// TODO meta annotation usage?
		List<Method> methodsWithAtTransactional = getMethodsWithAnnotation(AtTransactional);
		if (methodsWithAtTransactional.size() > 0) {
			return true;
		}
		List<Method> methodsWithAtJavaxTransactional = getMethodsWithAnnotation(AtJavaxTransactional);
		if (methodsWithAtJavaxTransactional.size() > 0) {
			return true;
		}
		return false;
	}
	
	public List<Method> getTransactionalMethods() {
		List<Method> results = new ArrayList<>();
		results.addAll(getMethodsWithAnnotation(AtTransactional));
		results.addAll(getMethodsWithAnnotation(AtJavaxTransactional));
		return results;
	}

	public boolean isAnnotatedInHierarchy(String anno) {
		if (isAnnotated(AtTransactional)) {
			return true;
		}
		List<Method> methodsWithAnnotation = getMethodsWithAnnotation(anno);
		if (methodsWithAnnotation.size() != 0) {
			return true;
		}
		Type superclass = this.getSuperclass();
		if (superclass != null) {
			if (superclass.isAnnotatedInHierarchy(anno)) {
				return true;
			}
		}
		Type[] intfaces = this.getInterfaces();
		if (intfaces != null) {
			for (Type intface : intfaces) {
				if (intface.isAnnotatedInHierarchy(anno)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isAtRepository() {
		return isAnnotated(AtRepository);
	}

	public boolean isAtResponseBody() {
		boolean b = hasAnnotation(AtResponseBody, true);
		// logger.debug("Checking if " + getName() + " is @ResponseBody meta
		// annotated: " + b);
		return b;
	}

	public Collection<Type> collectAtMappingMarkedReturnTypes() {
		Set<Type> returnTypes = new LinkedHashSet<>();
		List<Method> methodsWithAnnotation = getMethodsWithAnnotation(AtMapping, true);
		for (Method m : methodsWithAnnotation) {
			returnTypes.add(m.getReturnType());
		}
		return returnTypes;
	}

	public String findTypeParameterInSupertype(String supertype, int typeParameterNumber) {
		if (node.signature == null) {
			return null;
		}
		SignatureReader reader = new SignatureReader(node.signature);
		TypeParamFinder tpm = new TypeParamFinder(supertype);
		reader.accept(tpm);
		return tpm.getTypeParameter(typeParameterNumber);
	}

	static class TypeParamFinder extends SignatureVisitor {

		String typeparameter;

		List<String> types = null;

		private String supertype;

		private boolean nextIsBoundType = false;

		private boolean collectTypeParams = false;

		private List<String> typeParams = new ArrayList<>();

		public TypeParamFinder(String supertype) {
			super(Opcodes.ASM9);
			this.supertype = supertype.replace(".", "/");
		}

		public String getTypeParameter(int typeParameterNumber) {
			if (typeParameterNumber >= typeParams.size()) {
				return null;
			}
			return typeParams.get(typeParameterNumber).replace("/", ".");
		}

		@Override
		public SignatureVisitor visitSuperclass() {
			nextIsBoundType = true;
			collectTypeParams = false;
			return super.visitSuperclass();
		}

		@Override
		public SignatureVisitor visitInterface() {
			nextIsBoundType = true;
			collectTypeParams = false;
			return super.visitInterface();
		}

		@Override
		public void visitClassType(String name) {
			if (nextIsBoundType && name.equals(supertype)) {
				// Hit the bound type of interest, the next few types are what we should collect
				collectTypeParams = true;
			} else if (collectTypeParams) {
				typeParams.add(name);
			}
			super.visitClassType(name);
			nextIsBoundType = false;
		}
	}

	public String fetchReactiveCrudRepositoryType() {
		return findTypeParameterInSupertype("org.springframework.data.repository.reactive.ReactiveCrudRepository", 0);
	}

	public String fetchCrudRepositoryType() {
		return findTypeParameterInSupertype("org.springframework.data.repository.CrudRepository", 0);
	}

	public String fetchJpaRepositoryType() {
		return findTypeParameterInSupertype("org.springframework.data.jpa.repository.JpaRepository", 0);
	}


	/**
	 * Verify this type as a component, checking everything is set correctly for
	 * native-image construction to succeed.
	 *
	 */
	public void verifyComponent() {
		List<String> methodsInvokingAtBeanMethods = null;
		try {
			methodsInvokingAtBeanMethods = getMethodsInvokingAtBeanMethods();
		} catch (Exception e) {
			// Probably a MissingTypeException trying to resolve something not on the classpath -
			// in this case we can't correctly verify something, but it *probably* isn't getting used anyway
		}
		if (methodsInvokingAtBeanMethods != null) {
			throw new IllegalStateException("ERROR: in '"+getDottedName()+"' these methods are directly invoking methods marked @Bean: "+
					methodsInvokingAtBeanMethods+" - due to the enforced proxyBeanMethods=false for components in a native-image, please consider "+
					"refactoring to use instance injection. If you are confident this is not going to affect your application, you may turn this check "
					+ "off using -Dspring.native.verify=false.");
		}
	}

	private void verifyProxyBeanMethodsSetting() {
		List<Method> methodsWithAtBean = getMethodsWithAtBean();
		if (methodsWithAtBean.size() != 0) {
			List<AnnotationNode> annos = collectAnnotations();
			annos = filterAnnotations(annos, an -> {
				Type annotationType = typeSystem.Lresolve(an.desc);
				return annotationType.hasMethod("proxyBeanMethods");
			});
			// Rule:
			// At least one annotation in the list has to be setting proxyBeanMethods=false.
			// Some may not supply it if they are being aliased by other values.
			// TODO this does not check the default value for proxyBeanMethods
			// TODO this does not verify/follow AliasFor usage
			boolean atLeastSetFalseSomewhere = false;
			if (ClassUtils.isPresent(getDottedName().replace("$", "_") + "Cached", null)) {
				return;
			}
			for (AnnotationNode anode : annos) {
				if (hasValue(anode, "proxyBeanMethods")) {
					Boolean value = (Boolean) getValue(anode, "proxyBeanMethods");
					if (!value) {
						atLeastSetFalseSomewhere = true;
					}
				}
			}
			if (!atLeastSetFalseSomewhere) {
				logger.debug("[verification] Warning: component " + this.getDottedName()
						+ " does not specify annotation value proxyBeanMethods=false to avoid CGLIB proxies");
			}
		}
	}

	/**
	 * For an {@link AnnotationNode} retrieve the value for a particular attribute,
	 * will throw an exception if no value is set for that attribute.
	 *
	 * @param anode the annotation node whose values should be checked
	 * @param name  the annotation attribute name being searched for
	 * @return the value of that attribute if set on that annotation node
	 * @throws IllegalStateException if that attribute name is not set
	 */
	private Object getValue(AnnotationNode anode, String name) {
		List<Object> values = anode.values;
		for (int i = 0; i < values.size(); i += 2) {
			if (values.get(i).toString().equals(name)) {
				return values.get(i + 1);
			}
		}
		return new IllegalStateException("Attribute " + name
				+ " not set on the specified annotation, precede this call to getValue() with a hasValue() check");
	}

	/**
	 * For an {@link AnnotationNode} check if it specifies a value for a particular
	 * attribute.
	 *
	 * @param node the annotation node whose values should be checked
	 * @param name the annotation attribute name being searched for
	 * @return true if the annotation did specify a value for that attribute
	 */
	private boolean hasValue(AnnotationNode node, String name) {
		List<Object> values = node.values;
		if (values != null) {
			for (int i = 0; i < values.size(); i += 2) {
				if (values.get(i).toString().equals(name)) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean hasMethod(String methodName) {
		List<MethodNode> methods = node.methods;
		for (MethodNode method : methods) {
			if (method.name.equals(methodName)) {
				return true;
			}
		}
		return false;
	}

	private List<AnnotationNode> filterAnnotations(List<AnnotationNode> input, Predicate<AnnotationNode> filter) {
		return input.stream().filter(filter).collect(Collectors.toList());
	}

	private List<AnnotationNode> collectAnnotations() {
		List<AnnotationNode> resultsHolder = new ArrayList<>();
		collectAnnotationsHelper(resultsHolder, new HashSet<String>());
		return resultsHolder;
	}

	private void collectAnnotationsHelper(List<AnnotationNode> collector, Set<String> seen) {
		if (node.visibleAnnotations != null) {
			for (AnnotationNode anno : node.visibleAnnotations) {
				if (!seen.add(anno.desc)) {
					continue;
				}
				collector.add(anno);
				Type annotationType = typeSystem.Lresolve(anno.desc);
				annotationType.collectAnnotationsHelper(collector, seen);
			}
		}
	}

	public Method getDefaultConstructor() {
		if (dimensions > 0)
			return null;
		List<MethodNode> methods = node.methods;
		for (MethodNode mn : methods) {
			if (mn.name.equals("<init>")) {
				if (mn.desc.equals("()V")) {
					return wrap(mn);
				}
			}
		}
		return null;
	}

	public List<Method> getMethods(Predicate<Method> predicate) {
		return dimensions > 0 ? Collections.emptyList()
				: node.methods.stream().map(m -> wrap(m)).filter(m -> predicate.test(m)).collect(Collectors.toList());
	}
	
	public List<Method> getMethod(String name) {
		List<Method> results = new ArrayList<>();
		for (int i = 0; i < node.methods.size(); i++) {
			MethodNode methodNode = node.methods.get(i);
			if (methodNode.name.equals(name)) {
				results.add(wrap(methodNode));
			}
		}
		return results;
	}

	public boolean equals(Object that) {
		return (that instanceof Type) && (((Type) that).name.equals(this.name))
				&& (((Type) that).dimensions == this.dimensions) && (((Type) that).node.equals(this.node));
	}

	public int hashCode() {
		return node.hashCode() * 37;
	}

	/**
	 * TODO: This is a little crude, relying on patterns rather than classfile
	 * encoded data.
	 * 
	 * @return the guessed enclosing type, if resolvable
	 */
	public Type getEnclosingType() {
		String n = this.getDottedName();
		int idx = n.lastIndexOf("$");
		if (idx == -1) {
			return null;
		}
		Type t = typeSystem.resolveDotted(n.substring(0, idx), true);
		if (t == null) {
			return null;
		} else {
			return t;
		}
	}

	/**
	 * Check if there is a @ConditionalOnWebApplication annotation on this type. If
	 * there is, check that web application condition immediately (which is related
	 * to whether a certain type is on the classpath).
	 * 
	 * @return false if there is an @COWA and the specified web application type
	 *         requirement cannot be met, otherwise true
	 */
	public boolean checkConditionalOnWebApplication() {
		if (node.visibleAnnotations != null) {
			for (AnnotationNode an : node.visibleAnnotations) {
				if (an.desc.equals("Lorg/springframework/boot/autoconfigure/condition/ConditionalOnWebApplication;")) {
					boolean checkHappened = false;
					List<Object> values = an.values;
					if (values != null) {
						for (int i = 0; i < values.size(); i += 2) {
							if (values.get(i).equals("type")) {
								// [Lorg/springframework/boot/autoconfigure/condition/ConditionalOnWebApplication$Type;, SERVLET]
								String webApplicationType = ((String[]) values.get(i + 1))[1];
								// SERVLET, REACTIVE, ANY
								if (webApplicationType.equals("SERVLET")) {
									// If GenericWebApplicationContext not around this check on SERVLET cannot pass
									checkHappened=true;
									return typeSystem.resolveDotted(
											"org.springframework.web.context.support.GenericWebApplicationContext",
											true) != null;
								}
								if (webApplicationType.equals("REACTIVE")) {
									// If HandlerResult not around this check on REACTIVE cannot pass
									checkHappened=true;
									return typeSystem.resolveDotted("org.springframework.web.reactive.HandlerResult",
											true) != null;
								}
							}
						}
					}
					if (!checkHappened) {
						// It was set to ANY (the default), let's check for both
						return  typeSystem.resolveDotted( "org.springframework.web.context.support.GenericWebApplicationContext", true) != null || 
								typeSystem.resolveDotted("org.springframework.web.reactive.HandlerResult", true) != null;
					}
				}
			}
		}
		return true;
	}

	/**
	 * For annotation types this will return true if any of the members of the
	 * annotation are using @AliasFor (implying they need a proxy at runtime) For
	 * example:
	 * 
	 * <pre>
	 * <code>
	 * &#64;AliasFor(annotation = RequestMapping.class)
	 * String name() default "";
	 * </code>
	 * </pre>
	 * @return {@code true} if has alias
	 */
	public boolean hasAliasForMarkedMembers() {
		for (Method m : getMethods()) {
			if (m.hasAliasForAnnotation()) {
				return true;
			}
		}
		return false;
	}

	public void collectAliasReferencedMetas(Set<String> collector) {
		// If this gets set the type is using @AliasFor amongst its own
		// attributes, not specifying one of them is an alias for
		// an attribute in a seperate meta annotation.
		boolean usesLocalAliases = false;
		for (Method m : getMethods()) {
			Pair<String, Boolean> aliasForInfo = m.getAliasForSummary();
			if (aliasForInfo != null) {
				String relatedMetaAnnotation = aliasForInfo.getA();
				if (relatedMetaAnnotation == null) {
					usesLocalAliases |= aliasForInfo.getB();
				} else {
					collector.add(relatedMetaAnnotation);
				}
			}
		}
		if (usesLocalAliases) {
			collector.add(getDottedName());
		}
	}

	/**
	 * Example: see @EnableR2dbcRepositories in
	 * R2dbcRepositoresAutoConfigureRegistrar:
	 * 
	 * <pre>
	 * <code>
	 * &#64;EnableR2dbcRepositories 
	 * private static class EnableR2dbcRepositoriesConfiguration { }
	 * </code>
	 * </pre>
	 * 
	 * The @Enable... thing looks like this:
	 * 
	 * <pre>
	 * <code>
	 * &#64;Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME) @Documented @Inherited
	 * &#64;Import(R2dbcRepositoriesRegistrar.class) 
	 * public @interface EnableR2dbcRepositories {
	 * </code>
	 * </pre>
	 * 
	 * So this method is checking if a class is meta annotated with @Import
	 * @return {@code true} if meta import annotated
	 */
	public boolean isMetaImportAnnotated() {
		return isMetaAnnotated(fromLdescriptorToSlashed(AtImports));
	}

	public boolean isComponent() {
		return isMetaAnnotated(fromLdescriptorToSlashed(AtComponent));
	}
	
	public boolean implementsInterface(String interfaceDescriptor, boolean silent) {
		try {
			return implementsInterface(interfaceDescriptor);
		} catch (MissingTypeException mte) {
			if (silent) {
				return false;
			} else {
				throw mte;
			}
		}
	}

	public boolean isConditional() {
		// Extends Condition or has @Conditional related annotation on it
		if (implementsInterface("org/springframework/context/annotation/Condition",true)
				|| isMetaAnnotated("org/springframework/context/annotation/Conditional")) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * @return true if type or a method inside is marked @PostAuthorize, @PostFilter, @PreAuthorize or @PreFilter
	 */
	public boolean isAtPrePostSecured() {
		return isAnnotatedInHierarchy(AtPostAuthorize) || isAnnotatedInHierarchy(AtPostFilter)
				|| isAnnotatedInHierarchy(AtPreAuthorize) || isAnnotatedInHierarchy(AtPreFilter);
	}

	public void collectAnnotations(Set<Type> collector, Predicate<Type> filter) {
		if (!this.isAnnotation()) {
			throw new IllegalStateException(this.getDottedName() + " is not an annotation");
		}
		walkAnnotation(collector, filter, new HashSet<>());
	}

	private void walkAnnotation(Set<Type> collector, Predicate<Type> filter, Set<Type> seen) {
		if (!seen.add(this)) {
			return;
		}
		if (filter.test(this)) {
			collector.add(this);
		}
		List<Type> annotations = getAnnotations();
		for (Type annotation : annotations) {
			annotation.walkAnnotation(collector, filter, seen);
		}
	}

	public Field getField(String name) {
		for (Field field : fields.get()) {
			if (field.getName().equals(name)) {
				return field;
			}
		}
		return null;
	}

	public List<String> getImportedConfigurations() {
		List<String> importedConfigurations = findAnnotationValueWithHostAnnotation(AtImportAutoConfiguration, false,
				new HashSet<>()).get(this.getDottedName());
		return (importedConfigurations == null ? Collections.emptyList() : importedConfigurations);
	}

	public AnnotationNode getAnnotation(String Ldescriptor) {
		if (node.visibleAnnotations !=null) {
			for (AnnotationNode annotationNode : node.visibleAnnotations) {
				if (annotationNode.desc.equals(Ldescriptor)) {
					return annotationNode;
				}
			}
		}
		return null;
	}

	AnnotationNode getAnnotationMetaAnnotatedWith(String Ldescriptor) {
		if (node.visibleAnnotations !=null) {
			for (AnnotationNode annotationNode : node.visibleAnnotations) {
				if (annotationNode.desc.equals(Ldescriptor)) {
					return annotationNode;
				}
				Type resolvedAnnotationType = typeSystem.Lresolve(annotationNode.desc,true);
				if (resolvedAnnotationType != null) {
					String slashedDesc = Ldescriptor.substring(1,Ldescriptor.length()-1);
					if (resolvedAnnotationType.isMetaAnnotated(slashedDesc)) {
						return annotationNode;
					}
				}
			}
		}
		return null;
	}


	public TypeSystem getTypeSystem() {
		return this.typeSystem;
	}

	public boolean hasAnnotatedField(Predicate<String> annotationCheck) {
		if (node.fields != null) {
			for (FieldNode fieldNode : node.fields) {
				List<AnnotationNode> vAnnotations = fieldNode.visibleAnnotations;
				if (vAnnotations!=null) {
					if (annotationCheck == null) {
						return true;
					}
					for (AnnotationNode vAnnotation : vAnnotations) {
						String d = vAnnotation.desc;
						if (annotationCheck.test(d.substring(1,d.length()-1).replace("/", "."))) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * @param methodName the method name
	 * @param descriptor the descriptor
	 * @return if the specified method on this type have the {@code @Bean} annotation on it
	 */
	public boolean isAtBeanMethod(String methodName, String descriptor) {
		List<MethodNode> ms = this.node.methods;
		for (MethodNode m : ms) {
			if (m.name.equals(methodName) && m.desc.equals(descriptor)) {
				List<AnnotationNode> vas = m.visibleAnnotations;
				if (vas != null) {
					for (AnnotationNode va : vas) {
						if (va.desc.equals(AtBean)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	
	public boolean verifyType(boolean debugVerification) {
		List<String> verificationProblems = new ArrayList<>();
		// Type
		Set<String> typesInSignature = getTypesInSignature();
		for (String type: typesInSignature) {
			Type resolved = typeSystem.resolveSlashed(type,true);
			if (resolved == null) {
				verificationProblems.add("Cannot resolve "+type+" in type signature");
			}
		}
		if (verificationProblems.size()!=0) {
			if (debugVerification) {
				logger.debug("FAILED TYPE VERIFICATION OF "+getDottedName()+"\n"+verificationProblems);
			}
		}
		return verificationProblems.isEmpty();
	}
	/**
	 * Check all the type references from the types signature and the signatures of its members can be resolved.
	 * @return true if verification is OK, false if there is a problem
	 */
	public boolean verifyMembers(boolean debugVerification) {
		List<String> verificationProblems = new ArrayList<>();
		// Fields
		Set<String> typesInSignature = null;
		List<Field> fields = getFields();
		for (Field field: fields) {
			typesInSignature = field.getTypesInSignature();
			for (String type: typesInSignature) {
				while (type.endsWith("[]")) {
					type = type.substring(0,type.length()-2);
				}
				if (typeSystem.isVoidOrPrimitive(type)) continue;
				Type resolved = typeSystem.resolveSlashed(type, true);
				if (resolved == null) {
					verificationProblems.add("Cannot resolve "+type+" in signature of field "+field.getName());
				}
			}
		}
		// Methods
		List<Method> methods = getMethods();
		for (Method method: methods) {
			typesInSignature = method.getTypesInSignature();
			for (String type: typesInSignature) {
				while (type.endsWith("[]")) {
					type = type.substring(0,type.length()-2);
				}
				if (typeSystem.isVoidOrPrimitive(type)) continue;
				Type resolved = typeSystem.resolveSlashed(type, true);
				if (resolved == null) {
					verificationProblems.add("Cannot resolve "+type+" in signature of method "+method.getName());
				}
			}
		}
		if (verificationProblems.size()!=0) {
			if (debugVerification) {
				logger.warn("Failed verification check: problems with members of "+getDottedName()+"\n"+verificationProblems);
			}
		}
		return verificationProblems.isEmpty();
	}

	public boolean shouldFollow() {
		// Example: For isApplicationListener(): DataSourceInitializerInvoker imported from DataSourceInitializationConfiguration
		return isAtConfiguration() || isImportSelector() || isImportRegistrar() || isBeanFactoryPostProcessor() || isApplicationListener();
	}

	public boolean hasAutowiredMethods() {
		List<Method> ms = getMethodsWithAnnotationName("org.springframework.beans.factory.annotation.Autowired", false);
		return !ms.isEmpty();
	}
	
	public boolean hasAutowiredFields() {
		List<Field> fs = getFieldsWithAnnotationName("org.springframework.beans.factory.annotation.Autowired", false);
		return !fs.isEmpty();
	}

	/**
	 * @return subtypes of the current type
	 */
	public List<Type> getSubtypes() {
		long stime = System.currentTimeMillis();
		String n = this.isInterface()?this.getName():this.getDescriptor();
		List<Type> subtypes = typeSystem.scan(t -> {
			if (t == this) {
				return false;
			}
			return this.isInterface()?t.implementsInterface(n):t.extendsClass(n);
		});
		long etime = System.currentTimeMillis();
		logger.debug("TIMER: Time taken to scan for subtypes of "+getDottedName()+" was "+(etime-stime)+"ms and found "+subtypes.size()+" subtypes");
		return subtypes;
	}
	
	/**
	 * Determine if any methods on this type look like they need to be reflectively accessible and if so return
	 * the descriptors for them. Managing the list of annotations that should be accessible is painful, so this
	 * code will assume if the annotation starts with org.springframework it is a candidate of interest (it will
	 * ignore those containing Null to skip Nullable/etc).
	 * 
	 * @return list of {@link MethodDescriptor} for those methods of interest
	 */
	public List<MethodDescriptor> getRequiredAccessibleMethods() {
		List<Method> methods = new ArrayList<>();
		for (Method method: getMethods()) {
			boolean ignore = true;
			for (Type t: method.getAnnotationTypes()) {
				String name = t.getDottedName();
				if (name.startsWith("org.springframework.") && !name.contains("Null")) {
					ignore = false;
					break;
				}
			}
			if (!ignore) {
				methods.add(method);
			}
		}
		if (methods.isEmpty()) {
			return null;
		}
		List<MethodDescriptor> methodDescriptors = new ArrayList<>();
		for (Method method: methods) {
			String[] array = method.asConfigurationArray();
			methodDescriptors.add(MethodDescriptor.of(array));
		}
		return methodDescriptors;
	}

	/**
	 * Determine if any fields on this type look like they need to be reflectively accessible and if so return
	 * the descriptors for them. Managing the list of annotations that should be accessible is painful, so this
	 * code will assume if the annotation starts with org.springframework it is a candidate of interest (it will
	 * ignore those containing Null to skip Nullable/etc).
	 * 
	 * @return list of {@link MethodDescriptor} for those methods of interest
	 */
	public List<FieldDescriptor> getRequiredAccessibleFields() {
		List<Field> fields = new ArrayList<>();
		for (Field field: getFields()) {
			boolean ignore = true;
			for (Type t: field.getAnnotationTypes()) {
				String name = t.getDottedName();
				if (name.startsWith("org.springframework.") && !name.contains("Null")) {
					ignore = false;
					break;
				}
			}
			if (!ignore) {
				fields.add(field);
			}
		}
		if (fields.isEmpty()) {
			return null;
		}
		List<FieldDescriptor> fieldDescriptors = new ArrayList<>();
		for (Field field: fields) {
			fieldDescriptors.add(FieldDescriptor.of(field.getName(),false,false));
		}
		return fieldDescriptors;
	}

}
