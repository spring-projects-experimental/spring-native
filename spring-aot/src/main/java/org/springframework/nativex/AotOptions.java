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

package org.springframework.nativex;

import org.springframework.nativex.support.Mode;

/**
 * Options for Spring Native.
 *
 * @author Sebastien Deleuze
 * @author Andy Clement
 */
public class AotOptions {

	private String mode = Mode.NATIVE.toString();

	/**
	 * Determine if extra debug should come out related to verification of reflection requests.
	 * Spring Native will compute configuration but a final stage verification runs before it is
	 * written out to try and ensure what is requested will not lead to problems when the
	 * native-image is built. This flag enables debugging of that final stage verification.
	 */
	private boolean debugVerify;

	/**
	 * Determine whether configuration should be computed for any configuration explicitly excluded
	 * via an explicit <tt>spring.autoconfigure.exclude</tt> in
	 * <tt>application*.properties</tt> or <tt>application*.yml</tt>.
	 */
	private boolean ignoreHintsOnExcludedConfig = true;

	/**
	 * Determine whether it is safe to discard native-image configuration related to auto-configurations
	 * at build time, where it can be determined those auto-configurations will not be active at runtime
	 * (for example if they use a ConditionalOnClass annotation and the class referenced is not on the
	 * classpath). It is safe to discard this configuration as long as the AOT processing is also making
	 * the same decision about what it discards when creating spring factories code that represents
	 * <tt>spring.factories</tt> content.
	 */
	private boolean removeUnusedConfig = true;

	/**
	 * Determine if the system should fast fail if a hint is missing for an import selector.
	 * Spring Native tries to follow configuration chains (configuration that imports other configuration)
	 * but it struggles when it hits an import selector, since that uses code to determine the next
	 * link in the chain. If a hint is missing to provide this next link in the chain, the system fails
	 * immediately, by default, rather than limping along and potentially failing in a very crypic way
	 * later. 
	 */
	private boolean failOnMissingSelectorHint = true;

	/**
	 * Determine whether to check Spring components are suitable for inclusion in a native-image.
	 * For a concrete example, verification currently checks whether bean factory methods are being
	 * invoked directly in configuration classes. If they are then that must be enforced by using
	 * CGLIB proxies. CGLIB proxies are not supported in native-image and so a verification error
	 * will come out with a message indicating the code should switch to method parameter
	 * injection (allowing Spring to control bean method invocation).
	 */
	private boolean verify = true;

	private boolean removeYamlSupport;

	private boolean removeJmxSupport = true;

	private boolean removeXmlSupport = true;

	private boolean removeSpelSupport;

	/**
	 * When performing build time properties checks (via build-time-properties-checks option) this 
	 * determines how to treat the <tt>match-if-missing</tt> constraint that can be specified in
	 * conditional on property annotations. This option means nothing unless activating build time
	 * property checking. Without this ability to override the behavior, any condition on a
	 * property that specified match-if-missing is true could not benefit from build time property checking if the 
	 * property is not set because match if missing says the condition should pass even if it isn't there.
	 * 
	 * Both this and the build time property checking option are experimental as we explore what it
	 * means to do build time property checking.
	 */
	private boolean buildTimePropertiesMatchIfMissing = true;

	/**
	 * Controls which properties can be evaluated at build time. Checking properties at build time
	 * can cause configurations using property connected conditions like @ConditionalOnProperty to
	 * be discarded early, producing a more optimal image and runtime behaviour.
	 * 
	 * This should be set to an initial control value that specifies the default, then a series of values that
	 * tweak that default for certain properties.
	 * 
	 * The two default settings are: 
	 * <ul>
	 * <li> <tt>default-include-all</tt> indicates by default, unless tweaked otherwise, <b>all</b> properties are safe
	 * to evaluate at build time.</li>
	 * <li> <tt>default-exclude-all</tt> indicates by default, unless tweaked otherwise, <b>no</b> properties are safe
	 * to evaluate at build time.</li>
	 * </ul>
	 * 
	 * Following the default behavior, the tweaks look like a series of property names (or just prefixes) with
	 * an optional ! (not) operator on the front.
	 * 
	 * Example:
	 * <pre><code>
	 * buildTimePropertiesChecks = [default-include-all,!spring.dont.include.these.,!or.these]
	 * </code></pre>
	 * Here the default is evaluate everything but if the property in question starts with <tt>spring.dont.include.these.</tt>
	 * or <tt>or.these.</tt> then those will not be evaluated at build time.
	 * 
	 * 
	 * Both this and the build time properties match if missing option are experimental as we explore what it
	 * means to do build time property checking.
	 */
	private String[] buildTimePropertiesChecks;

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public boolean isIgnoreHintsOnExcludedConfig() {
		return ignoreHintsOnExcludedConfig;
	}

	public void setIgnoreHintsOnExcludedConfig(boolean ignoreHintsOnExcludedConfig) {
		this.ignoreHintsOnExcludedConfig = ignoreHintsOnExcludedConfig;
	}

	public boolean isVerify() {
		return verify;
	}

	public void setVerify(boolean verify) {
		this.verify = verify;
	}

	public boolean isRemoveYamlSupport() {
		return removeYamlSupport;
	}

	public void setRemoveYamlSupport(boolean removeYamlSupport) {
		this.removeYamlSupport = removeYamlSupport;
	}

	public boolean isRemoveJmxSupport() {
		return removeJmxSupport;
	}

	public void setRemoveJmxSupport(boolean removeJmxSupport) {
		this.removeJmxSupport = removeJmxSupport;
	}

	public boolean isDebugVerify() {
		return debugVerify;
	}

	public void setDebugVerify(boolean debugVerify) {
		this.debugVerify = debugVerify;
	}

	public boolean isRemoveUnusedConfig() {
		return removeUnusedConfig;
	}

	public void setRemoveUnusedConfig(boolean removeUnusedConfig) {
		this.removeUnusedConfig = removeUnusedConfig;
	}

	public boolean isBuildTimePropertiesMatchIfMissing() {
		return buildTimePropertiesMatchIfMissing;
	}

	public void setBuildTimePropertiesMatchIfMissing(boolean buildTimePropertiesMatchIfMissing) {
		this.buildTimePropertiesMatchIfMissing = buildTimePropertiesMatchIfMissing;
	}

	public String[] getBuildTimePropertiesChecks() {
		return buildTimePropertiesChecks;
	}

	public void setBuildTimePropertiesChecks(String[] buildTimePropertiesChecks) {
		this.buildTimePropertiesChecks = buildTimePropertiesChecks;
	}

	public boolean isRemoveXmlSupport() {
		return removeXmlSupport;
	}

	public void setRemoveXmlSupport(boolean removeXmlSupport) {
		this.removeXmlSupport = removeXmlSupport;
	}

	public boolean isRemoveSpelSupport() {
		return removeSpelSupport;
	}

	public void setRemoveSpelSupport(boolean removeSpelSupport) {
		this.removeSpelSupport = removeSpelSupport;
	}

	public boolean isFailOnMissingSelectorHint() {
		return failOnMissingSelectorHint;
	}

	public void setFailOnMissingSelectorHint(boolean failOnMissingSelectorHint) {
		this.failOnMissingSelectorHint = failOnMissingSelectorHint;
	}

	public Mode toMode() {
		if (this.mode == null || this.mode.equals(Mode.NATIVE.toString())) {
			return Mode.NATIVE;
		} else if (this.mode.equals(Mode.NATIVE_NEXT.toString())) {
			return Mode.NATIVE_NEXT;
		} else if (this.mode.equals(Mode.NATIVE_AGENT.toString())) {
			return Mode.NATIVE_AGENT;
		} else if (this.mode.equals(Mode.NATIVE_INIT.toString())) {
			return Mode.NATIVE_INIT;
		}
		throw new IllegalStateException(this.mode + " is not a valid mode. Valid modes are: " + Mode.NATIVE_NEXT + ", "
				+ Mode.NATIVE + ", " + Mode.NATIVE_AGENT + ", " + Mode.NATIVE_INIT);
	}

	/**
	 * Determine if the specified property should be checked at build time.
	 *
	 * @param key the property key (e.g. spring.application.name)
	 * @return true if the property should be checked at build time
	 */
	public boolean buildTimeCheckableProperty(String key) {
		if (!isBuildTimePropertyChecking()) {
			return false;
		}
		boolean defaultResult = true;
		int maxExplicitExclusionMatchLength = -1;
		int maxExplicitInclusionMatchLength = -1;
		for (String btpcPattern: buildTimePropertiesChecks) {
			if (btpcPattern.equals("default-include-all")) {
				defaultResult = true;
			} else if (btpcPattern.equals("default-exclude-all")) {
				defaultResult = false;
			} else if (btpcPattern.startsWith("!")) {
				// Exclusion: e.g. !management.foo.bar.
				if (key.startsWith(btpcPattern.substring(1))) {
					if ((btpcPattern.length()-1)>maxExplicitExclusionMatchLength) {
						maxExplicitExclusionMatchLength = btpcPattern.length()-1;
					}
				}
			} else {
				// Inclusion: e.g. spring.foo.
				if (key.startsWith(btpcPattern)) {
					if ((btpcPattern.length())>maxExplicitInclusionMatchLength) {
						maxExplicitInclusionMatchLength = btpcPattern.length();
					}
				}
			}
		}
		if (maxExplicitExclusionMatchLength==-1 && maxExplicitInclusionMatchLength==-1) {
			return defaultResult;
		}
		if (maxExplicitExclusionMatchLength>maxExplicitInclusionMatchLength) {
			// Explicit exclusion match was more specific
			return false;
		} else {
			return true;
		}
	}

	public boolean isBuildTimePropertyChecking() {
		if (buildTimePropertiesChecks == null) {
			return false;
		}
		if (buildTimePropertiesChecks.length > 0) {
			return true;
		}
		return false;
	}
}
