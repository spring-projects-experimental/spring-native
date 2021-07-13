package org.springframework.cloud.square;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientConfiguration;
import org.springframework.cloud.square.retrofit.DefaultRetrofitClientConfiguration;
import org.springframework.cloud.square.retrofit.RetrofitAutoConfiguration;
import org.springframework.cloud.square.retrofit.RetrofitClientFactoryBean;
import org.springframework.cloud.square.retrofit.core.AbstractRetrofitClientFactoryBean;
import org.springframework.cloud.square.retrofit.core.RetrofitClientSpecification;
import org.springframework.nativex.hint.AccessBits;
import org.springframework.nativex.hint.NativeHint;
import org.springframework.nativex.hint.TypeHint;
import org.springframework.nativex.type.NativeConfiguration;
import retrofit2.Retrofit;

/**
	* Provides hints for commonly required dependencies when using Spring Cloud Square Retrofit.
	*
	* @author Josh Long
	* @author Andy Clement
	*/
@NativeHint(
	trigger = RetrofitAutoConfiguration.class,
	options = "-H:+AddAllCharsets --enable-url-protocols=http,https",
	types = {
		@TypeHint(typeNames = {
			"jdk.vm.ci.meta.JavaKind$FormatWithToString[]",
			"java.lang.reflect.AnnotatedElement[]",
			"java.lang.reflect.GenericDeclaration[]",
			"com.oracle.svm.core.hub.Target_java_lang_constant_Constable[]",
			"com.oracle.svm.core.hub.Target_java_lang_invoke_TypeDescriptor_OfField[]",
			"com.oracle.svm.core.hub.Target_java_lang_invoke_TypeDescriptor[]"
		}),
		@TypeHint(
			access = AccessBits.ALL,
			types = {
				Retrofit.Builder.class,
				AbstractRetrofitClientFactoryBean.class,
				RetrofitClientFactoryBean.class,
				RetrofitClientSpecification.class,
				DefaultRetrofitClientConfiguration.class,
				LoadBalancerClientConfiguration.class,
			})
	}
)
public class SquareHints implements NativeConfiguration {
}

