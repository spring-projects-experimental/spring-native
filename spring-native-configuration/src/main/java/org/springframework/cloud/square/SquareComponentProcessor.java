package org.springframework.cloud.square;


import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.Flag;
import org.springframework.nativex.type.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
	* This class registers configuration for Retrofit's declarative, JDK proxy-based HTTP clients.
	*
	* @author Josh Long
	*/
public class SquareComponentProcessor implements ComponentProcessor {

	@Override
	public boolean handle(NativeContext imageContext, String key, List<String> classifiers) {
		String retrofitClientName = "Lorg/springframework/cloud/square/retrofit/core/RetrofitClient;";
		Type resolvedKey = imageContext.getTypeSystem().resolveDotted(key);
		if (resolvedKey.hasAnnotationInHierarchy(retrofitClientName)) {
			log(imageContext, "handling the @RetrofitClient");
			return true;
		}
		return false;
	}


	@Override
	public void process(NativeContext imageContext, String key, List<String> values) {
		try {
			Type repositoryType = imageContext.getTypeSystem().resolveDotted(key);
			log(imageContext, "going to process " + key +
				" with annotations " + String.join(",", values));
			Set<String> processed = new HashSet<>();
			processClientType(repositoryType, imageContext, processed);
			registerClientProxy(imageContext, repositoryType, processed);
		}
		catch (Throwable t) {
			log(imageContext, "WARNING: Problem with " + getClass().getName() + ": " + t.getMessage());
			t.printStackTrace();
		}
	}

	public void processClientType(Type repositoryType, NativeContext imageContext, Set<String> processed) {
		Type[] repositoryInterfaces = repositoryType.getInterfaces();
		for (Type repositoryInterface : repositoryInterfaces) {
			addAllTypesFromSignaturesInRepositoryInterface(repositoryInterface, imageContext, processed);
		}
		log(imageContext, String.format("%s reflective access added - adding this repository type and its hierarchy", repositoryType.getDottedName()));
		imageContext.addReflectiveAccessHierarchy(repositoryType, AccessBits.LOAD_AND_CONSTRUCT_AND_PUBLIC_METHODS | AccessBits.DECLARED_FIELDS);
	}

	public void addAllTypesFromSignaturesInRepositoryInterface(Type repositoryInterface,
																																																												NativeContext imageContext, Set<String> processed) {

		List<Method> publicRepositoryMethods = repositoryInterface.getMethods(Method::isPublic);
		for (Method publicRepositoryMethod : publicRepositoryMethods) {
			Set<Type> types = publicRepositoryMethod.getSignatureTypes(true);
			for (Type type : types) {
				if (type != null && processed.add(type.getDottedName())) {
					if (inSimilarPackage(type, repositoryInterface)) {
						log(imageContext, String.format("%s reflective access added - due to method %s.%s",
							type.getDottedName(), repositoryInterface.getDottedName(), publicRepositoryMethod.toString()));

						List<Flag> flags = new ArrayList<>();
						flags.add(Flag.allPublicMethods);
						flags.add(Flag.allDeclaredMethods);
						flags.add(Flag.allDeclaredConstructors);
						imageContext.addReflectiveAccess(type.getDottedName(), flags.toArray(new Flag[0]));
						processPossibleDomainType(type, imageContext, processed);
					}
				}
			}
		}

	}

	/**
		* Within a domain type there may be other types that need access. For example in class <tt>Pet</tt> there may be a <tt>Set&lt;Visit&gt; visits</tt>
		*/
	private void processPossibleDomainType(Type type, NativeContext imageContext, Set<String> processed) {
		List<Field> fields = type.getFields();
		for (Field field : fields) {
			Set<String> fieldTypes = field.getTypesInSignature();
			for (String fieldType : fieldTypes) {
				if (processed.add(fieldType)) {
					Type resolvedFieldType = imageContext.getTypeSystem().resolveSlashed(fieldType, true);
					if (resolvedFieldType != null && inSimilarPackage(resolvedFieldType, type)) {
						log(imageContext, String.format("%s reflective access added - due to field %s.%s",
							resolvedFieldType.getDottedName(), type.getDottedName(), field.getName()));
						imageContext.addReflectiveAccess(resolvedFieldType.getDottedName(), Flag.allPublicMethods,
							Flag.allDeclaredConstructors);
						// TODO should recurse through domain object graph? processPossibleDomainType(resolvedFieldType, imageContext);
					}
				}
			}
		}
	}

	private boolean inSimilarPackage(Type type, Type repositoryInterface) {
		String repoPackage = repositoryInterface.getPackageName();
		String typePackage = type.getPackageName();
		if (repoPackage.startsWith(typePackage) || typePackage.startsWith(repoPackage)) {
			return true;
		}
		return false;
	}

	public void registerClientProxy(NativeContext imageContext, Type repositoryType, Set<String> processed) {
		List<String> repositoryInterfacesStrings = repositoryType.getInterfacesStrings();
		if (repositoryInterfacesStrings.size() != 0) {
			List<String> repositoryInterfaces = new ArrayList<>();
			log(imageContext, repositoryType.getDottedName() + " is getting a proxy created");
			for (String s : repositoryInterfacesStrings) {
				repositoryInterfaces.add(s.replace("/", "."));
			}
			imageContext.addProxy(repositoryInterfaces);
		}
		else {
			imageContext.addProxy(repositoryType.getDottedName());
			log(imageContext, " creating proxy for @RetrofitClient interface " + repositoryType.getDottedName() + ".");
		}
	}


	private static void log(NativeContext context, String msg) {
		context.log(msg);
		System.err.println(msg);
	}
}
