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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import org.springframework.boot.loader.tools.MainClassFinder;
import org.springframework.core.type.classreading.ClassDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.nativex.AotOptions;
import org.springframework.nativex.domain.reflect.JsonMarshaller;
import org.springframework.nativex.domain.reflect.ReflectionDescriptor;
import org.springframework.nativex.domain.resources.ResourcesDescriptor;
import org.springframework.nativex.domain.resources.ResourcesJsonMarshaller;
import org.springframework.nativex.support.Utils;


/**
 * Simple type system with some rudimentary caching.
 * 
 * @author Andy Clement
 */
public class TypeSystem {

	private static Log logger = LogFactory.getLog(TypeSystem.class);

	public static String SPRING_AT_CONFIGURATION = "Lorg/springframework/context/annotation/Configuration;";

	private JavaModuleLookupSystem javaModuleLookupSystem = JavaModuleLookupSystem.get();

	public Map<TypeId, Type> primitives = new HashMap<>();

	// Map of all types on the classpath that have some kind of annotations on them
	Map<String, AnnotationInfo> annotatedTypes;

	private SpringConfiguration hintLocator = null;

	// Classpath from which this type system will resolve types
	private List<String> classpath;

	// Cache of resolved types TODO time out entries?
	private Map<String, Type> typeCache = new HashMap<>();

	// Map of which zip files contain which packages
	private Map<String, Set<File>> packageCache = new HashMap<>();

	// Map of which application files contain particular packages
	private Map<String, List<File>> appPackages = new HashMap<>();

	private Map<String, ResourcesDescriptor> resourceConfigurations;
	
	private List<String> excludedAutoConfigurations;

	private Map<String, ReflectionDescriptor> reflectionConfigurations;
	
	// A map from the types whose clinits make isPresent checks to the types that they are checking the presence
	// of (the parameters to the isPresent calls)
	private Map<String,List<String>> typesMakingIsPresentChecksInStaticInitializers;
	
	public AotOptions aotOptions;

	public TypeSystem(List<String> classpath) {
		this.classpath = classpath;
		index();
	}

	public List<String> getClasspath() {
		return classpath;
	}

	/**
	 * Resolve the {@link Type} from this {@code TypeSystem} classpath,
	 * returning {@code null} if not found.
	 * @param typeName the name of the type to resolve
	 * @return the resolved type, or {@code null}.
	 */
	@Nullable
	public Type resolve(TypeName typeName) {
		return resolveSlashed(typeName.toSlashName(), true);
	}

	public Type resolveName(String dottedTypeName) {
		return resolveDotted(dottedTypeName);
	}

	public Type resolveDotted(String dottedTypeName) {
		String slashedTypeName = toSlashedName(dottedTypeName);
		return resolveSlashed(slashedTypeName);
	}

	public Type resolveName(String desc, boolean silent) {
		return resolveDotted(desc, silent);
	}

	public Type resolveDotted(String desc, boolean silent) {
		try {
			return resolveDotted(desc);
		} catch (MissingTypeException mte) {
			if (silent)
				return null;
			else
				throw mte;
		}
	}

	public boolean canResolveSlashed(String slashedTypeName) {
		try {
			return resolveSlashed(slashedTypeName) != null;
		} catch (RuntimeException re) {
			if (re.getMessage().startsWith("Unable to find class file for")) {
				return false;
			}
			throw re;
		}
	}

	public Type resolveSlashed(String slashedTypeName) {
		return resolveSlashed(slashedTypeName, false);
	}

	public Type resolveSlashed(String slashedTypeName, boolean allowNotFound) {
		Type resolvedType = typeCache.get(slashedTypeName);
		if (resolvedType == Type.MISSING) {
			if (allowNotFound) {
				return null;
			} else {
				throw new MissingTypeException(slashedTypeName);
			}
		}
		if (resolvedType != null) {
			return resolvedType;
		}
		resolvedType = findType(slashedTypeName);
		if (resolvedType == null) {
			// It may be an inner type but slashedTypeName is com/foo/example/Outer/Inner
			String current = slashedTypeName;
			int lastSlash = current.lastIndexOf("/");
			while (lastSlash != -1 && (lastSlash+1)<current.length()) {
				String attempt = current.substring(0,lastSlash)+"$"+current.substring(lastSlash+1);
				resolvedType = findType(attempt);
				if (resolvedType != null) {
					break;
				}
				current = attempt;
				lastSlash = current.lastIndexOf("/");
			}
		}
		if (resolvedType != null) {
			typeCache.put(slashedTypeName, resolvedType);
			return resolvedType;
		} else {
			// cache a missingtype so we don't go looking again!
			typeCache.put(slashedTypeName, Type.MISSING);
			if (allowNotFound) {
				return null;
			} else {
				throw new MissingTypeException(slashedTypeName);
			}
		}
	}
	
	private Type resolve(Path pathToClassfile) {
		try (InputStream is = Files.newInputStream(pathToClassfile)) {
			ClassNode node = new ClassNode();
			ClassReader reader = new ClassReader(is);
			reader.accept(node, ClassReader.SKIP_DEBUG);
			Type type = typeCache.get(node.name);
			if (type == null) {
				type = Type.forClassNode(this, node, 0);
				typeCache.put(node.name, type);
			}
			return type;
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load from path "+pathToClassfile,e);
		}
	}
	
	private Type findType(String slashedTypeName) {
		int dimensions = 0;
		String typeToLocate = slashedTypeName;
		if (slashedTypeName.endsWith("[]")) {
			String n = slashedTypeName;
			while (n.endsWith("[]")) {
				dimensions++;
				n = n.substring(0,n.length()-2);
			}
			typeToLocate = n;
		}
		byte[] bytes = find(typeToLocate);
		if (bytes == null) {
			// System class?
			InputStream resourceAsStream = Thread.currentThread().getContextClassLoader()
					.getResourceAsStream(typeToLocate + ".class");
			if (resourceAsStream == null) {
				// One more try, are we on Java9+ and modules are hiding it from us...
				if (javaModuleLookupSystem != null) {
					resourceAsStream = javaModuleLookupSystem.findClassfile(typeToLocate+".class");
				}
			}
			if (resourceAsStream == null) {
				return null;
			}
			try {
				bytes = loadFromStream(resourceAsStream);
			} catch (RuntimeException e) {
				throw new RuntimeException("Problems loading class from resource stream: " + slashedTypeName, e);
			}
		}
		ClassNode node = new ClassNode();
		ClassReader reader = new ClassReader(bytes);
		reader.accept(node, ClassReader.SKIP_DEBUG);
		return Type.forClassNode(this, node, dimensions);
	}

	private String toSlashedName(String dottedTypeName) {
		return dottedTypeName.replace(".", "/");
	}

	public boolean canResolve(String classname) {
		if (classname.contains(".")) {
			throw new RuntimeException("Dont pass dotted names to resolve() :" + classname);
		}
		return canResolveSlashed(classname);
	}

	public Type resolve(String classname, boolean allowNotFound) {
		if (classname.contains(".")) {
			throw new RuntimeException("Dont pass dotted names to resolve() :" + classname);
		}
		return resolveSlashed(classname, allowNotFound);
	}

	public Type resolve(String classname) {
		return resolve(classname, false);
	}

	public Type Lresolve(String desc) {
		return resolve(desc.substring(1, desc.length() - 1));
	}

	public Type resolve(org.objectweb.asm.Type type, boolean silent) {
		try {
			String desc = type.getDescriptor(); //[[Lorg/springframework/amqp/rabbit/annotation/RabbitBootstrapConfiguration;
			if (!desc.endsWith(";")) {
				// primitive/void
				return null;
			}
			int dims = 0;
			while (desc.charAt(dims)=='[') { dims++; }
			if (dims>0) {
				StringBuilder s = new StringBuilder();
				s.append(desc.substring(1+dims,desc.length()-1));
				for (int i=0;i<dims;i++) {
					s.append("[]");
				}
				Type tt = resolveSlashed(s.toString(),silent);
				return tt;
			} else {
				return resolve(desc.substring(1, desc.length() - 1));
			}
		} catch (MissingTypeException mte) {
			if (silent) {
				return null;
			} else {
				throw mte;
			}
		}
	}

	public Type Lresolve(String desc, boolean silent) {
		try {
			return resolve(desc.substring(1, desc.length() - 1));
		} catch (MissingTypeException mte) {
			if (silent)
				return null;
			else
				throw mte;
		}
	}

	public Set<String> findMissingTypesInHierarchyOfThisType(Type type) {
		return resolveComplete(type.getDescriptor());
	}

	public Set<String> resolveCompleteFindMissingAnnotationTypes(Type type) {
		Set<String> missingAnnotationTypes = new LinkedHashSet<>();
		type.collectMissingAnnotationTypesHelper(missingAnnotationTypes, new HashSet<>());
		return missingAnnotationTypes;
	}

	/**
	 * Verifies the type plus all its super types, interfaces and any type
	 * references in generic specifications exist.
	 * 
	 * @return List of missing types, empty if all good!
	 */
	public Set<String> resolveComplete(String desc) {
		Set<String> missingTypes = new LinkedHashSet<>();
		resolveComplete(desc.substring(1, desc.length() - 1), missingTypes, new HashSet<>());
		return missingTypes;
	}

	private void resolveComplete(String slashedDescriptor, Set<String> missingTypes, Set<String> visited) {
		if (visited.add(slashedDescriptor)) {
			Type baseType = resolve(slashedDescriptor, true);
			if (baseType == null) {
				missingTypes.add(slashedDescriptor);
			} else {
				// Check generics
				Set<String> typesInSignature = baseType.getTypesInSignature();
//				for (String t: typesInSignature) {
//					logger.debug("Found this "+t+" in signature of "+baseType.getName());
//				}
				String superclassString = baseType.getSuperclassString();
				if (superclassString != null) {
					resolveComplete(superclassString, missingTypes, visited);
				}
				List<String> interfaces = baseType.getInterfacesStrings();
				if (interfaces != null) {
					for (String interfce : interfaces) {
						resolveComplete(interfce, missingTypes, visited);
					}
				}
			}
		}
	}

	public void index() {
		for (String s : classpath) {
			File f = new File(s);
			if (f.isDirectory()) {
				indexDir(f);
			} else {
				indexJar(f);
			}
		}
	}

	public void indexDir(File dir) {
		Path root = Paths.get(dir.toURI());
		try {
			Files.walk(root).filter(f -> f.toString().endsWith(".class")).map(f -> {
				String name = f.toString().substring(root.toString().length() + 1);
				int lastSlash = name.lastIndexOf(File.separatorChar);
				if (lastSlash != -1 && name.endsWith(".class")) {
					return name.substring(0, lastSlash);
				}
				return null;
			}).forEach(n -> {
				if (n != null) {
					n = n.replace("\\", "/");
					List<File> dirs = appPackages.get(n);
					if (dirs == null) {
						dirs = new ArrayList<>();
						appPackages.put(n, dirs);
					}
					dirs.add(dir);
				}
			});
		} catch (IOException ioe) {
			throw new IllegalStateException("Unable to walk " + dir, ioe);
		}
	}

	public void indexJar(File jar) {
		// Walk the jar, index entries and cache package > this jar
		try {
			try (ZipFile zf = new ZipFile(jar)) {
				Enumeration<? extends ZipEntry> entries = zf.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName();
					if (name.endsWith(".class")) {
						int lastSlash = name.lastIndexOf("/");
						if (lastSlash != -1 && name.endsWith(".class")) {
							String packageName = name.substring(0, lastSlash);
							Set<File> jars = packageCache.getOrDefault(packageName, new HashSet<>());
							jars.add(jar);
							packageCache.put(packageName, jars);
						}
					}
				}
			}
		} catch (FileNotFoundException | NoSuchFileException fileIsntThere) {
			System.err.println("WARNING: Unable to find jar '" + jar + "' whilst scanning filesystem");
		} catch (IOException ioe) {
			throw new RuntimeException("Problem during scan of " + jar, ioe);
		}
	}

	public byte[] find(String slashedTypeName) {
		String search = slashedTypeName + ".class";
		try {
			int index = slashedTypeName.lastIndexOf("/");
			String packageName = index == -1 ? "" : slashedTypeName.substring(0, index);
			if (appPackages.containsKey(packageName)) {
				List<File> list = appPackages.get(packageName);
				for (File f : list) {
					File toTry = new File(f, search);
					if (toTry.exists()) {
						try (FileInputStream fis = new FileInputStream(toTry)) {
							return loadFromStream(fis);
						}
//						return loadFromStream(new FileInputStream(toTry));
					}
				}
			}
			Set<File> jarfiles = packageCache.get(packageName);
			if (jarfiles!=null) {
				for (File jarfile: jarfiles) {
					try (ZipFile zf = new ZipFile(jarfile)) {
						Enumeration<? extends ZipEntry> entries = zf.entries();
						while (entries.hasMoreElements()) {
							ZipEntry entry = entries.nextElement();
							String name = entry.getName();
							if (name.equals(search)) {
								return loadFromStream(zf.getInputStream(entry));
							}
						}
					}
				}
			}
			return null;
		} catch (IOException ioe) {
			throw new RuntimeException("Problem finding " + slashedTypeName, ioe);
		}
	}

	public static byte[] loadFromStream(InputStream stream) {
		try {
			BufferedInputStream bis = new BufferedInputStream(stream);
			int size = 2048;
			byte[] theData = new byte[size];
			int dataReadSoFar = 0;
			byte[] buffer = new byte[size / 2];
			int read = 0;
			while ((read = bis.read(buffer)) != -1) {
				if ((read + dataReadSoFar) > theData.length) {
					// need to make more room
					byte[] newTheData = new byte[theData.length * 2];
					// logger.debug("doubled to " + newTheData.length);
					System.arraycopy(theData, 0, newTheData, 0, dataReadSoFar);
					theData = newTheData;
				}
				System.arraycopy(buffer, 0, theData, dataReadSoFar, read);
				dataReadSoFar += read;
			}
			bis.close();
			// Resize to actual data read
			byte[] returnData = new byte[dataReadSoFar];
			System.arraycopy(theData, 0, returnData, 0, dataReadSoFar);
			return returnData;
		} catch (IOException e) {
			throw new RuntimeException("Unexpectedly unable to load bytedata from input stream", e);
		} finally {
			try {
				stream.close();
			} catch (IOException ioe) {
			}
		}
	}

	public String toString() {
		return "TypeSystem for cp(" + classpath + ")  jarPackages=#" + packageCache.size() + " appPackages="
				+ appPackages;
	}

	public void scan() {
		// Scan the classpath for things of interest, do this only once!
		for (String classpathEntry : classpath) {
			File f = new File(classpathEntry);
			if (f.exists()) {
				if (f.isDirectory()) {
					scanFiles(f, f);
				} else {
					scanArchive(f);
				}
			}
		}
	}

	private void scanArchive(File f) {
		try (ZipFile zf = new ZipFile(f)) {
			Enumeration<? extends ZipEntry> entries = zf.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (entry.getName().endsWith(".class")) {
					ClassReader reader = new ClassReader(zf.getInputStream(entry));
					ClassNode node = new ClassNode();
					reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
					AnnotationInfo ai = new AnnotationInfo(this, node);
					if (ai.hasData()) {
						logger.debug("From " + entry.toString() + " got " + ai.toAnnotationString());
						annotatedTypes.put(node.name, ai);
					}
				}
				// TODO resources?
			}
		} catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}
	}

	private void scanFiles(File file, File base) {
		if (file.isDirectory()) {
			File[] files = file.listFiles();
			for (File f : files) {
				scanFiles(f, base);
			}
		} else if (file.getName().endsWith(".class")) {
			try {
				byte[] bytes = Files.readAllBytes(Paths.get(file.toURI()));
				ClassReader reader = new ClassReader(bytes);
				ClassNode node = new ClassNode();
				reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
				AnnotationInfo ai = new AnnotationInfo(this, node);
				if (ai.hasData()) {
					logger.debug("From " + file.getName() + " got " + ai.toAnnotationString());
					annotatedTypes.put(node.name, ai);
				}
			} catch (IOException ioe) {
				throw new IllegalStateException(ioe);
			}
		}
//		else // resource?
	}

	public static class AnnotationInfo {

		private String name;
		private TypeSystem typeSystem;
		private List<AnnotationNode> annotations;

		// if this is the annotationinfo for an annotation, this will cache meta
		// annotations
		private List<AnnotationNode> metaAnnotationsList = null;

		// need file?

		public AnnotationInfo(TypeSystem typeSystem, ClassNode node) {
			this.typeSystem = typeSystem;
			this.name = node.name;
			annotations = node.visibleAnnotations;
		}

		public boolean hasData() {
			return annotations != null && annotations.size() != 0;
		}

		public String toAnnotationString() {
			StringBuilder sb = new StringBuilder();
			if (annotations != null) {
				for (AnnotationNode an : annotations) {
					sb.append(an.desc);
					sb.append("(");
					List<Object> values = an.values;
					if (values != null) {
						for (int j = 0; j < values.size(); j += 2) {
							sb.append(values.get(j));
							sb.append("=");
							sb.append(values.get(j + 1));
						}
					}
					sb.append(")");
				}
			}
			return sb.toString();
		}

		public boolean hasDescriptor(String annotationDescriptor) {
			for (AnnotationNode an : annotations) {
				if (an.desc.equals(annotationDescriptor)) {
					return true;
				}
			}
			return false;
		}

		// TODO filter out java/lang/annotation annotations? Surely we don't need all of them
		List<AnnotationNode> getMetaAnnotations() {
			if (metaAnnotationsList == null) {
				metaAnnotationsList = new ArrayList<>();
				collectMetaAnnotations();
				if (metaAnnotationsList.size() == 0) {
					metaAnnotationsList = Collections.emptyList();
				}
			}
			return metaAnnotationsList;
		}

		public boolean hasDescriptorMeta(String annotationDescriptor) {
			for (AnnotationNode an : annotations) {
				if (an.desc.equals(annotationDescriptor)) {
					return true;
				}
			}
			for (AnnotationNode an : getMetaAnnotations()) {
				if (an.desc.equals(annotationDescriptor)) {
					return true;
				}
			}
			return false;
		}

		private void collectMetaAnnotations() {
			for (AnnotationNode an : annotations) {
				// Go through our annotations and grab their meta annotations
				AnnotationInfo ai = typeSystem.annotatedTypes.get(an.desc.substring(1, an.desc.length() - 1));
				if (ai != null && ai.hasData()) {
					metaAnnotationsList.addAll(ai.getAnnotations());
					metaAnnotationsList.addAll(ai.getMetaAnnotations());
				}
			}
		}

		private Collection<? extends AnnotationNode> getAnnotations() {
			return annotations;
		}
	}
	
	private void ensureSpringConfigurationDiscovered() {
		if (hintLocator == null) {
			hintLocator = new SpringConfiguration(this);
		}
	}

	private void ensureScanned() {
		if (annotatedTypes == null) {
			annotatedTypes = new HashMap<>();
			long t = System.currentTimeMillis();
			scan();
			logger.debug("SBG: scan time: " + (System.currentTimeMillis() - t) + "ms");
		}
	}

	public List<String> findTypesAnnotated(String annotationDescriptor, boolean metaAnnotated) {
		ensureScanned();
		if (metaAnnotated) {
			return annotatedTypes.values().stream().filter(ai -> ai.hasDescriptorMeta(annotationDescriptor))
					.map(ai -> ai.name).collect(Collectors.toList());
		} else {
			return annotatedTypes.values().stream().filter(ai -> ai.hasDescriptor(annotationDescriptor))
					.map(ai -> ai.name).collect(Collectors.toList());
		}
	}

	public List<String> findTypesAnnotationAtConfiguration(boolean metaAnnotated) {
		return findTypesAnnotated(SPRING_AT_CONFIGURATION, metaAnnotated);
	}

	public List<HintDeclaration> findActiveDefaultHints() {
		List<HintDeclaration> activeDefaultHints = new ArrayList<>();
		activeDefaultHints.addAll(findHints("java.lang.Object"));
		Map<String, List<HintDeclaration>> proposedhints = hintLocator.getProposedhints();
		for (Map.Entry<String,List<HintDeclaration>> proposedhint: proposedhints.entrySet()) {
			String keytype = proposedhint.getKey();
			if (keytype.equals("java.lang.Object")) {
				continue;
			}
			Type type = resolveDotted(keytype,true);
			if (type != null) {
				if (type.isAtConfiguration() || 
					type.isImportRegistrar() || type.isImportSelector() || 
					type.isCondition() || type.isConditional() || type.isAtImport()
					) {
					// These are triggered by 'exploration' under default/hybrid mode
				} else {
					for (HintDeclaration hint: proposedhint.getValue()) {
						logger.debug("Considering hint not targeting config (trigger="+keytype+") as applicable: "+hint);
						activeDefaultHints.add(hint);
					}
				}
			}
		}
		return activeDefaultHints;
	}

	public List<HintDeclaration> findHints(String typename) {
		if (typename.contains("/")) {
			if (typename.endsWith(";")) {
				typename= typename.substring(1,typename.length()-1).replace("/", ".");
			} else {
				typename= typename.replace("/", ".");
			}
		}
		if (hintLocator == null) {
			ensureSpringConfigurationDiscovered();
		}
		// The result should include hints directly on the type as well
		// as discovered hints from separate configuration
		List<HintDeclaration> results = new ArrayList<>();
		results.addAll(hintLocator.findProposedHints(typename));
		List<HintDeclaration> declaredHints = resolveName(typename).getCompilationHints();
		results.addAll(declaredHints);
		return results;
	}
	
	static class Tuple<K,V> {
		private final K key;
		private final V value;
		Tuple(K key,V value) {
			this.key = key;
			this.value = value;
		}
		public K getKey() {
			return key;
		}
		public V getValue() {
			return value;
		}
		public String toString() { return key+": hasData?"+(value!=null); }
	}

	static class ClassCollectorFileVisitor implements FileVisitor<Path> {
		
		private final List<Path> collector = new ArrayList<>();
	
		public List<Path> getClassFiles() {
			return collector;
		}
	
		@Override
		public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
			return FileVisitResult.CONTINUE;
		}
	
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (file.getFileName().toString().endsWith(".class")) {
				collector.add(file);
			}
			return FileVisitResult.CONTINUE;
		}
	
		@Override
		public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
			return FileVisitResult.CONTINUE;
		}
	
		@Override
		public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
			return FileVisitResult.CONTINUE;
		}
	
		
	}

	/**
	 * Retrieve the map from files (possibly inside jar files) to {@link ResourcesDescriptor} objects parsed
	 * from the contents of those files. This method is looking for files that start {@code META-INF/native-image}
	 * and end with {@code resource-config.json}. If the file is a path into a jar it has the form
	 * {@code path/to/foo.jar!path/to/file}.
	 * 
	 * @return map from files to @link {@link ResourcesDescriptor}
	 */
	public Map<String, ResourcesDescriptor> getResourceConfigurationsOnClasspath() {
		if (this.resourceConfigurations == null) {
			Map<String,ResourcesDescriptor> configs = new HashMap<>();
			for (String s: classpath) {
				File f = new File(s);
				if (f.isDirectory()) {
					searchDir(f, filepath -> { 
						return filepath.contains("META-INF/native-image") && filepath.endsWith("resource-config.json");
					}, 
					ResourcesJsonMarshaller::read,
					configs);
				} else if (f.isFile() && f.toString().endsWith(".jar")) {
					searchJar(f, filepath -> { 
						return filepath.contains("META-INF/native-image") && filepath.endsWith("resource-config.json");
					}, 
					ResourcesJsonMarshaller::read,
					configs);
				}
			}
			if (configs.isEmpty()) {
				this.resourceConfigurations = Collections.emptyMap();
			} else {
				this.resourceConfigurations = configs;
			}
		}
		return this.resourceConfigurations;
	}
	
	public Map<String, Map<String, String>> scanForApplicationProperties() {
		Map<String, Map<String,String>> collectedProperties = new HashMap<>();
		for (String s: classpath) {
			File f = new File(s);
			if (f.isDirectory()) {
				searchDir(f, filepath -> { 
					return filepath.contains("application") && filepath.endsWith(".properties");
				},
				TypeSystem::loadApplicationProperties,
				collectedProperties);
			} else if (f.isFile() && f.toString().endsWith(".jar")) {
				searchJar(f, filepath -> { 
					return filepath.contains("application") && filepath.endsWith(".properties");
				}, 
				TypeSystem::loadApplicationProperties,
				collectedProperties);
			}
		}	
		return collectedProperties;
	}
	
	public List<String> getExcludedAutoConfigurations() {
		if (this.excludedAutoConfigurations == null) {
			excludedAutoConfigurations = new ArrayList<>();
			Map<String, List<String>> collectedExclusions = new HashMap<>();
			for (String s: classpath) {
				File f = new File(s);
				if (f.isDirectory()) {
					searchDir(f, filepath -> { 
						return  filepath.contains("application") && filepath.endsWith(".properties");
					},
					TypeSystem::findExcludedAutoconfigurationsInPropertiesFile,
					collectedExclusions);
				} else if (f.isFile() && f.toString().endsWith(".jar")) {
					searchJar(f, filepath -> { 
						return filepath.contains("application") && filepath.endsWith(".properties");
					}, 
					TypeSystem::findExcludedAutoconfigurationsInPropertiesFile,
					collectedExclusions);
				}
			}
			for (Map.Entry<String,List<String>> entry: collectedExclusions.entrySet()) {
				excludedAutoConfigurations.addAll(entry.getValue());
			}
			logger.debug("INFO: these spring auto configuration exclusions have been detected: "+excludedAutoConfigurations);
		}
		return this.excludedAutoConfigurations;
	}
	
	public static List<String> findExcludedAutoconfigurationsInPropertiesFile(InputStream is) {
		try {
			Properties p = new Properties();
			p.load(is);
			String value = p.getProperty("spring.autoconfigure.exclude");
			if (value == null) {
				return Collections.emptyList();
			} else {
				return Arrays.asList(value.split(","));
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read properties file",e);
		}
	}

	public static Map<String,String> loadApplicationProperties(InputStream is) {
		try {
			Properties p = new Properties();
			p.load(is);
			Map<String,String> ret = new HashMap<>();
			for (final String name: p.stringPropertyNames()) {
				ret.put(name, p.getProperty(name));
			}
			return ret;
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read properties file",e);
		}
	}
	

	public Map<String, ReflectionDescriptor> getReflectionConfigurationsOnClasspath() {
		if (this.reflectionConfigurations == null) {
			Map<String,ReflectionDescriptor> configs = new HashMap<>();
			for (String s: classpath) {
				File f = new File(s);
				if (f.isDirectory()) {
					searchDir(f, filepath -> { 
						return filepath.contains("META-INF/native-image") && filepath.endsWith("reflect-config.json");
					},
					JsonMarshaller::read,
					configs);
				} else if (f.isFile() && f.toString().endsWith(".jar")) {
					searchJar(f, filepath -> { 
						return filepath.contains("META-INF/native-image") && filepath.endsWith("reflect-config.json");
					}, 
					JsonMarshaller::read,
					configs);
				}
			}
			if (configs.isEmpty()) {
				this.reflectionConfigurations = Collections.emptyMap();
			} else {
				this.reflectionConfigurations = configs;
			}
		}
		return this.reflectionConfigurations;
	}
	

	/**
	 * Recursively search a specified directory. Any files that match the specified predicate will have
	 * their contents converted by the supplied function and the resultant information stored in the collector map.
	 * 
	 * @param <T> The type of object produced by the converter function
	 * @param dir the directory to recursively search
	 * @param matchPredicate the predicate against which to match file paths
	 * @param converter the converter that processes file contents to produce something of type T
	 * @param collector the place to store mappings from matched file paths to T objects
	 */
	private <T> void searchDir(File dir, Predicate<String> matchPredicate, Function<InputStream, T> converter,
			Map<String, T> collector) {
		Path root = Paths.get(dir.toURI());
		try {
			List<Tuple<String, T>> found =
					Files.walk(root).filter(p -> matchPredicate.test(p.toAbsolutePath().toString())).map(p -> {
				try {
					T t = converter.apply(Files.newInputStream(p));
					return new Tuple<>(p.toString(), t);
				} catch (Exception e) {
					System.err.println("Unexpected problem reading " + p + ": " + e);
					return new Tuple<>(p.toString(), (T)null);
				}
			}).collect(Collectors.toList());
			for (Tuple<String,T> t: found) {
				collector.put(t.getKey(), t.getValue());
			}
		} catch (IOException ioe) {
			throw new IllegalStateException("Unable to walk " + dir, ioe);
		}
	}

	/**
	 * Scan the entries in a specified jar file. Any entry paths that match the specified predicate will have
	 * their contents converted by the supplied function and the resultant information stored in the collector map.
	 * Jar file paths are of the form {@code foo/bar/boo.jar!path/index/jar.txt}.
	 * 
	 * @param <T> The type of object produced by the converter function
	 * @param jar the jar to scan
	 * @param matchPredicate the predicate against which to match file paths
	 * @param converter the converter that processes file contents to produce something of type T
	 * @param collector the place to store mappings from matched jar file paths to T objects
	 */
	private <T> void searchJar(File jar, Predicate<String> matchPredicate, Function<InputStream, T> converter, Map<String, T> collector) {
		try {
			try (ZipFile zf = new ZipFile(jar)) {
				Enumeration<? extends ZipEntry> entries = zf.entries();
				while (entries.hasMoreElements()) {
					ZipEntry entry = entries.nextElement();
					String name = entry.getName();
					if (matchPredicate.test(name)) {
						collector.put(jar.toURI().getPath().toString()+"!"+name, converter.apply(zf.getInputStream(entry)));
					}
				}
			}
		} catch (FileNotFoundException fnfe) {
			System.err.println("WARNING: Unable to find jar '" + jar + "' whilst scanning filesystem");
		} catch (IOException ioe) {
			throw new RuntimeException("Problem during scan of " + jar, ioe);
		}
	}

	/**
	 * Discover if there is any {@code resource-config.json} on the classpath for this type system that
	 * contains an entry that would include {@code META-INF/spring.factories}.
	 * 
	 * @return the file path to the {@code resource-config.json} containing {@code META-INF/spring.factories} or null if there is none
	 */
	public String findAnyResourceConfigIncludingSpringFactoriesPattern() {
		String existingConfigThatIncludesSpringFactories = null; 
		Map<String,ResourcesDescriptor> resourceConfigurations = getResourceConfigurationsOnClasspath();
		outer: for (Map.Entry<String,ResourcesDescriptor> resourceConfiguration: resourceConfigurations.entrySet()) {
			if (resourceConfiguration.getValue() == null) {
				logger.debug("WARNING: unexpected null resourceconfiguration loaded from spring.factories at "+resourceConfiguration.getKey());
				continue;
			}
			Set<String> patterns = resourceConfiguration.getValue().getPatterns();
			for (String pattern: patterns) {
				String slash = File.separator;
				// Catches it raw or escaped (as the agent would do) - will not currently catch funky wildcarded variants
				if (pattern.equals("META-INF"+slash+"spring.factories") || pattern.equals("\\QMETA-INF"+slash+"spring.factories\\E")) {
					existingConfigThatIncludesSpringFactories = resourceConfiguration.getKey();
					break outer;
				}
			}
		}
		return existingConfigThatIncludesSpringFactories;
	}

	/**
	 * Check if there are guard type(s) for this key from a spring.factory file.  The key (and associated entries it points at)
	 * should only be processed if one of the guard types exists. If none of them exist, this spring.factories entry is irrelevant.
	 * @param key entry key from a spring.factories file
	 * @return true if the key and associated value should be processed
	 */
	public boolean shouldBeProcessed(String key) {
		String[] guardTypes = SpringConfiguration.findProposedFactoryGuards(key);
		if (guardTypes == null) {
			return true;
		} else {
			for (String guardType : guardTypes) {
				Type resolvedType = resolveDotted(guardType, true);
				if (resolvedType != null) {
					return true;
				}
			}
			return false;
		}
	}

	public List<ComponentProcessor> getComponentProcessors() {
		ensureSpringConfigurationDiscovered();
		return hintLocator.getComponentProcessors();
	}

	public List<AccessChecker> getAccessCheckers() {
		ensureSpringConfigurationDiscovered();
		return hintLocator.getAccessVerifiers();
	}
	
	public List<SpringFactoriesProcessor> getSpringFactoryProcessors() {
		ensureSpringConfigurationDiscovered();
		return hintLocator.getSpringFactoriesProcessors();
	}

	public synchronized Map<String,List<String>> getSpringClassesMakingIsPresentChecks() {
		if (typesMakingIsPresentChecksInStaticInitializers == null) {
			for (String classpathentry : classpath) {
				if (classpathentry.endsWith(".jar") && classpathentry.contains("spring") && !classpathentry.contains("test")) {
					try {
						try (ZipFile zf = new ZipFile(classpathentry)) {
							Enumeration<? extends ZipEntry> entries = zf.entries();
							while (entries.hasMoreElements()) {
								ZipEntry entry = entries.nextElement();
								String name = entry.getName();
								if (name.endsWith(".class")) {
									List<String> presenceCheckedTypes = IsPresentDetectionVisitor.run(zf.getInputStream(entry));
									if (presenceCheckedTypes != null) {
										if (typesMakingIsPresentChecksInStaticInitializers == null) {
											typesMakingIsPresentChecksInStaticInitializers = new HashMap<>();
										}
										typesMakingIsPresentChecksInStaticInitializers.put(name.substring(0,name.length()-6).replace('/', '.'),presenceCheckedTypes);
									}
								}
							}
						}
					} catch (FileNotFoundException fnfe) {
						System.err.println("WARNING: Unable to find jar '" + classpathentry + "' whilst scanning filesystem for isPresent() checking Spring classes");
					} catch (IOException ioe) {
						throw new RuntimeException("Problem during isPresent() checking scan of " + classpathentry, ioe);
					}
				}
			}
			if (typesMakingIsPresentChecksInStaticInitializers == null) {
				typesMakingIsPresentChecksInStaticInitializers = Collections.emptyMap();
			}
		}
		return typesMakingIsPresentChecksInStaticInitializers;
	}

	// TODO Should be able to perform an AOT analysis of @ComponentScan, see https://github.com/spring-projects-experimental/spring-native/issues/801
	public Stream<Path> findDirectoriesOrTargetDirJar(List<String> classpath) {
		List<Path> result = new ArrayList<>();
		String mainPackagePath = getMainPackagePath(classpath);
		for (String classpathEntry : classpath) {
			File f = new File(classpathEntry);
			if (f.isDirectory()) {
				result.add(Paths.get(f.toURI()));
			} else if (f.isFile() && f.getName().endsWith(".jar") && (
					f.getParent().endsWith(File.separator + "target") ||  // Maven multi-module
							f.getParent().endsWith(File.separator + "libs") || // Gradle multi-module
							f.getAbsolutePath().contains(mainPackagePath))) { // Same package than the main application class
				result.add(Paths.get(f.toURI()));
			}
		}
		return result.stream();
	}

	private String getMainPackagePath(List<String> classpath) {
		for (String path : classpath) {
			String mainClass = null;
			try {
				mainClass = MainClassFinder.findSingleMainClass(new File(path));
			}
			catch (IOException e) {
				logger.error(e);
			}
			if (mainClass != null) {
				String[] mainClassParts = mainClass.split("\\.");
				String[] mainPackageParts = Arrays.copyOfRange(mainClassParts, 0, mainClassParts.length - 1);
				String mainPackagePath = String.join(File.separator, mainPackageParts);
				logger.debug("TypeSystem found Spring Boot main package path: " + mainPackagePath);
				return mainPackagePath;
			}
		}
		logger.debug("Unable to find main class");
		return null;
	}

	public Stream<Path> findUserCodeDirectoriesAndSpringJars(List<String> classpath) {
		List<Path> result = new ArrayList<>();
		for (String classpathEntry : classpath) {
			File f = new File(classpathEntry);
			if (f.isDirectory()) {
				result.add(Paths.get(f.toURI()));
			} else if (f.isFile() &&
					   f.getName().endsWith(".jar") && 
					   (f.getParent().endsWith(File.separator+"target") ||
					    // This pattern recognizes libs/foo.jar which occurs with gradle multi module setups
					    f.getParent().endsWith(File.separator+"libs") || f.getName().contains("spring"))
					   ) {
				result.add(Paths.get(f.toURI()));
			}
		}
		return result.stream();
	}

	public Stream<Path> findClasses(Path path) {
		ArrayList<Path> classfiles = new ArrayList<>();
		if (Files.isDirectory(path)) {
			walk(path, classfiles);
		} else {
			walkJar(path, classfiles);
		}
		return classfiles.stream();
	}

	public void walk(Path dir, ArrayList<Path> classfiles) {
		try {
			TypeSystem.ClassCollectorFileVisitor x = new TypeSystem.ClassCollectorFileVisitor();
			Files.walkFileTree(dir, x);
			classfiles.addAll(x.getClassFiles());
		} catch (IOException e) {
			throw new IllegalStateException("Problem walking directory "+dir, e);
			
		}
	}

	public void walkJar(Path jarfile, ArrayList<Path> classfiles) {
		try {
			FileSystem jarfs = FileSystems.newFileSystem(jarfile,(ClassLoader)null);
			Iterable<Path> rootDirectories = jarfs.getRootDirectories();
			TypeSystem.ClassCollectorFileVisitor x = new TypeSystem.ClassCollectorFileVisitor();
			for (Path path: rootDirectories) {
				Files.walkFileTree(path, x);
			}
			classfiles.addAll(x.getClassFiles());
		} catch (IOException e) {
			throw new IllegalStateException("Problem opening "+jarfile,e);
		}
	}

	/**
	 * Search for any relevant stereotypes on the specified type. Return entries of
	 * the form:
	 * "com.foo.MyType=org.springframework.stereotype.Component,javax.transaction.Transactional"
	 * @param slashedClassname type upon which to locate stereotypes
	 */
	public Entry<Type, List<Type>> getStereoTypesOnType(String slashedClassname) {
		return resolveSlashed(slashedClassname).getRelevantStereotypes();
	}
	
	public Optional<org.springframework.nativex.domain.reflect.ClassDescriptor> findMembersAutowiredOrBean(String classname) {
		Type t = resolveSlashed(classname);
		if (t.isComponent()) {
			return Optional.empty();
		}
		List<Method> ms = t.getMethodsWithAnnotationName("org.springframework.beans.factory.annotation.Autowired", false);
		ms.addAll(t.getMethodsWithAnnotationName("org.springframework.context.annotation.Bean", false));
		List<Field> fs = t.getFieldsWithAnnotationName("org.springframework.beans.factory.annotation.Autowired", false);
		fs.addAll(t.getFieldsWithAnnotationName("org.springframework.context.annotation.Bean", false));
		org.springframework.nativex.domain.reflect.ClassDescriptor cd = null;
		if (!ms.isEmpty() || !fs.isEmpty()) {
			cd = org.springframework.nativex.domain.reflect.ClassDescriptor.of(classname);
		}
		if (ms.size()!=0) {
			System.out.println("Found Autowired/Bean stuff on "+t.getDottedName()+": "+ms);
			for (Method m: ms) {
				String[] array = m.asConfigurationArray();
				if (array != null) {
					// some parts of it could not be resolved, ignore
					cd.addMethodDescriptor(org.springframework.nativex.domain.reflect.MethodDescriptor.of(array));
				}
			}
		}
		if (fs.size()!=0) {
			System.out.println("Found Autowired/Bean stuff on "+t.getDottedName()+": "+fs);
			for (Field f: fs) {
				cd.addFieldDescriptor(org.springframework.nativex.domain.reflect.FieldDescriptor.of(f.getName(),false,false));
			}
		}
		return cd==null?Optional.empty():Optional.of(cd);
	}

	public String typenameOfClass(Path p) {
		return Utils.scanClass(p).getClassname();
	}

	public List<Entry<Type, List<Type>>> scanForSpringComponents() {
		return findDirectoriesOrTargetDirJar(getClasspath()).flatMap(this::findClasses).map(p -> {
			try {
				return getStereoTypesOnType(typenameOfClass(p));
			} catch (IllegalStateException|MissingTypeException ex) {
				logger.debug("Error during scanning Spring components : " + ex.getMessage());
			}
			return null;
			}).filter(Objects::nonNull).collect(Collectors.toList());
	}

	// TODO memory management when exploding typecache with scans done here
	/**
	 * Scan all classes considered to be 'bits of the application' (so everything apart
	 * from system classes and spring jars) for any types matching the predicate.
	 * 
	 * <p>WARNING: likely to make our cached set of type data explode.
	 * 
	 * @param test the test condition to run against each class
	 * @return return list of types matching the predicate
	 */
	public List<Type> scan(Predicate<Type> test) {
		List<Type> matches = findDirectoriesOrTargetDirJar(getClasspath())
				.flatMap(this::findClasses)
				.map(this::resolve)
				.filter(test)
				.collect(Collectors.toList());
		return matches;
	}

	/**
	 * Scan all classes considered user code and spring jars.
	 *
	 * @param filter must not be {@literal null}.
	 * @return a {@link Stream} of matching {@link Type types}.
	 */
	public Stream<Type> scanUserCodeDirectoriesAndSpringJars(Predicate<Type> filter) {

		return this.findUserCodeDirectoriesAndSpringJars(this.getClasspath())
				.flatMap(this::findClasses)
				.map(this::typenameOfClass)
				.map(this::resolveSlashed)
				.filter(filter);
	}

	public ReflectionDescriptor scanForLiteUsesOfAutowiredAndBean() {
		List<org.springframework.nativex.domain.reflect.ClassDescriptor> classDescriptors = 
				findUserCodeDirectoriesAndSpringJars(getClasspath())
				.flatMap(this::findClasses)
				.map(this::typenameOfClass)
				.map(this::findMembersAutowiredOrBean)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
		if (!classDescriptors.isEmpty()) {
			return new ReflectionDescriptor(classDescriptors);
		} else {
			return null;
		}
	}

	public Type getType_Import() {
		return resolve(Type.fromLdescriptorToSlashed(Type.AtImports));
	}
	
	enum TypeId {
		INT("I"), DOUBLE("D"), LONG("J"), SHORT("S"), BYTE("B"), CHAR("C"), FLOAT("F"), BOOLEAN("Z"), REFERENCE(null);
		final String signature;
		final boolean isPrimitive;
		TypeId(String signature) {
			this.signature = signature;
			this.isPrimitive = (signature!=null);
		}
		public String getSignature() {
			return signature;
		}
	}

	private static Map<String, Map<String, String>> applicationPropertiesFiles = null;
	private static Map<String, String> mergedApplicationProperties = null;

	public Map<String,String> getActiveProperties() {
		if (mergedApplicationProperties == null) {
			applicationPropertiesFiles = scanForApplicationProperties();
			mergedApplicationProperties = new HashMap<>();
			Collection<Map<String, String>> propertiesFiles = applicationPropertiesFiles.values();
			for (Map<String,String> propertiesFile: propertiesFiles) {
				for (Map.Entry<String,String> property: propertiesFile.entrySet()) {
					mergedApplicationProperties.put(property.getKey(), property.getValue());
				}
			}
		}
		return mergedApplicationProperties;
	}

	public boolean isVoidOrPrimitive(String type) {
		return type.length()==1;
		/*
		switch (type) {
		case "void":
		case "int":
		case "double":
		case "float":
		case "long":
		case "byte":
		case "char":
		case "short":
		case "boolean":
			return true;
		}
		return false;
		*/
	}

	public <T> T getJson(String string,Function<InputStream,T> reader) {
		long t = System.currentTimeMillis();
		Map<String,T> configs = new HashMap<>();
		for (String s: classpath) {
			File f = new File(s);
			if (f.isDirectory()) {
				searchDir(f, filepath -> { 
					return filepath.equals(string);
				}, 
						reader,
//				ResourcesJsonMarshaller::read,
				configs);
			} else if (f.isFile() && f.toString().endsWith(".jar")) {
				searchJar(f, filepath -> { 
					return filepath.equals(string);
				}, 
				reader,
//				ResourcesJsonMarshaller::read,
				configs);
			}
		}
		logger.debug("Took: "+(System.currentTimeMillis()-t)+"ms");
		return configs.values().iterator().next();
	}
	
	private byte[] readInputStream(InputStream is) {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		int c;
		byte[] buf = new byte[16384];
		try {
			while ((c = is.read(buf, 0, buf.length)) != -1) {
				data.write(buf, 0, c);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Problem reading input stream", e);
		}
		return data.toByteArray();
	}
	

	public Collection<String> getBundles(String prefix) {
		long t = System.currentTimeMillis();
		Map<String, byte[]> resources = new HashMap<>();
		String filePathPrefix = prefix.replace(".", "/");
		for (String s: classpath) {
			File f = new File(s);
			if (f.isDirectory()) {
				searchDir(f, filepath -> { 
					return filepath.startsWith(filePathPrefix) && filepath.endsWith(".properties");
				}, 
				this::readInputStream, // InputStream to a byte array?
				resources);
			} else if (f.isFile() && f.toString().endsWith(".jar")) {
				searchJar(f, filepath -> { 
					return filepath.startsWith(filePathPrefix) && filepath.endsWith(".properties");
				}, 
				this::readInputStream,
				resources);
			}
		}
		logger.debug("Took: "+(System.currentTimeMillis()-t)+"ms "+resources.size()+" resource bundles (name: "+prefix+")");
		return resources.keySet();
	}

	public Collection<byte[]> getResources(String resource) {
		long t = System.currentTimeMillis();
		Map<String, byte[]> resources = new HashMap<>();
		boolean specific = resource.startsWith("/");
		for (String s: classpath) {
			File f = new File(s);
			if (f.isDirectory()) {
				searchDir(f, filepath -> { 
					return specific?filepath.equals(resource):filepath.endsWith(resource);
				}, 
				this::readInputStream, // InputStream to a byte array?
//				ResourcesJsonMarshaller::read,
				resources);
			} else if (f.isFile() && f.toString().endsWith(".jar")) {
				searchJar(f, filepath -> { 
					return specific?filepath.equals(resource):filepath.endsWith(resource);
				}, 
				this::readInputStream,
//				ResourcesJsonMarshaller::read,
				resources);
			}
		}
		logger.debug("Took: "+(System.currentTimeMillis()-t)+"ms to find "+resource+" returning "+resources.values().size()+" entries: "+resources.keySet());
		return resources.values();
	}

	public Type resolve(ClassDescriptor classDescriptor) {
		return resolveDotted(classDescriptor.getClassName());
	}

	public boolean isBuildTimePropertyChecking() {
		return aotOptions.isBuildTimePropertyChecking();
	}

	public boolean buildTimeCheckableProperty(String prefix) {
		return aotOptions.buildTimeCheckableProperty(prefix);
	}

	public boolean shouldRemoveXmlSupport() {
		return aotOptions.isRemoveXmlSupport();
	}

	public boolean failOnMissingSelectorHint() {
		return aotOptions.isFailOnMissingSelectorHint();
	}

	public void setAotOptions(AotOptions aotOptions) {
		this.aotOptions = aotOptions;
	}

	static class JavaModuleLookupSystem {

		private final URI JRTURI = URI.create("jrt:/");

		private final FileSystem fs;

		private Map<String, Path> packages = new HashMap<>();

		private JavaModuleLookupSystem() throws IOException {
			Map<String, String> env = new HashMap<>();
			env.put("java.home",  System.getProperty("java.home"));
			fs = FileSystems.newFileSystem(JRTURI, env);
			// Cache the packages from the modules
			Iterable<Path> rootDirectories = fs.getRootDirectories();
			PackageCacheBuilderVisitor visitor = new PackageCacheBuilderVisitor();
			for (Path rootDirectory : rootDirectories) {
				Files.walkFileTree(rootDirectory, visitor);
			}
		}

		public static JavaModuleLookupSystem get() {
			try {
				return new JavaModuleLookupSystem();
			} catch (Exception e) {
				return null;
			}
		}

		class PackageCacheBuilderVisitor extends SimpleFileVisitor<Path> {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getNameCount() > 3 && file.toString().endsWith(".class")) {
					int nameCount = file.getNameCount();
					if (nameCount > 3) { // /modules/java.base/java/lang/String.class
						Path packagePath = file.subpath(2, nameCount-1); // e.g. java/lang
						packages.put(packagePath.toString(), file.subpath(0, nameCount-1)); // java/lang -> /modules/java.base/java/lang
					}
				}
				return FileVisitResult.CONTINUE;
			}
		}

		/**
		 * @param classfileName of the form java/lang/String.class
		 * @return an InputStream for loading the bytes for the class if it can be found, otherwise null
		 */
		public InputStream findClassfile(String classfileName) {
			int idx = classfileName.lastIndexOf('/');
			if (idx == -1) {
				return null; // no package
			}
			Path packageStart = packages.get(classfileName.substring(0, idx));
			try {
				if (packageStart != null) {
					ClassFileLocator locator = new ClassFileLocator(classfileName);
					Files.walkFileTree(packageStart, locator);
					Path classfile = locator.locatedClassfile;
					if (classfile != null) {
						return Files.newInputStream(classfile);
					}
				}
			} catch (IOException ioe) {
				return null;
			}
			return null;
		}

		class ClassFileLocator extends SimpleFileVisitor<Path> {

			private String searchClassfile;

			Path locatedClassfile;

			public ClassFileLocator(String classfile) {
				this.searchClassfile = classfile;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (file.getNameCount() > 2 && file.toString().endsWith(".class")) { // check if candidate
					Path filePath = file.subpath(2, file.getNameCount());
					if (filePath.toString().equals(searchClassfile)) {
						locatedClassfile = file;
						return FileVisitResult.TERMINATE;
					}
				}
				return FileVisitResult.CONTINUE;
			}
		}
	}

	public Type resolve(Class<?> clazz) {
		return resolve(clazz.getName().replace(".","/"), false);
	}

	public Type resolve(Class<?> clazz, boolean silent) {
		return resolve(clazz.getName().replace(".","/"), silent);
	}

	public boolean exists(TypeName typename) {
		Type resolvedType = resolve(typename);
		return resolvedType != null;
	}

}