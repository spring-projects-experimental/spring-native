/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.aot.context.bootstrap;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.aot.BootstrapCodeGenerator;
import org.springframework.nativex.AotOptions;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class BootstrapCodeGeneratorRunner {

	public static void main(String[] args) throws IOException {
		Assert.state(args.length == 3, "Missing argument");
		AotOptions aotOptions = new AotOptions();
		aotOptions.setMode("native");
		BootstrapCodeGenerator generator = new BootstrapCodeGenerator(aotOptions);
		Path sourcesPath = Paths.get(args[0]);
		Path resourcesPath = Paths.get(args[1]);
		URLClassLoader classLoader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
		String[] folders = StringUtils.split(args[2], File.pathSeparator);
		Set<Path> resourceFolders = folders != null ? Arrays.stream(folders)
				.map(Paths::get).collect(Collectors.toSet()) : Collections.emptySet();
		generator.generate(sourcesPath, resourcesPath, classLoader, resourceFolders);
	}
}
