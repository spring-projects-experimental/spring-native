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

package org.springframework.aot.maven;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.twdata.maven.mojoexecutor.MojoExecutor;

import org.springframework.aot.BootstrapCodeGenerator;
import org.springframework.aot.context.bootstrap.BootstrapCodeGeneratorRunner;
import org.springframework.boot.loader.tools.RunProcess;
import org.springframework.nativex.support.Mode;
import org.springframework.util.StringUtils;

import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * @author Brian Clozel
 * @author Sebastien Deleuze
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, requiresProject = true, threadSafe = true,
		requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
		requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GenerateMojo extends AbstractBootstrapMojo {

	/**
	 * Location of generated source files created by Spring AOT to bootstrap the application.
	 */
	@Parameter(defaultValue = "${project.build.directory}/generated-sources/spring-aot/")
	private File generatedSourcesDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Set<Path> resourceFolders = new HashSet<>();
		for (Resource r : project.getResources()) {
			// TODO respect includes/excludes
			resourceFolders.add(new File(r.getDirectory()).toPath());
		}
		recreateGeneratedSourcesFolder(this.generatedSourcesDirectory);
		Path sourcesPath = this.generatedSourcesDirectory.toPath().resolve(Paths.get("src", "main", "java"));
		Path resourcesPath = this.generatedSourcesDirectory.toPath().resolve(Paths.get("src", "main", "resources"));
		try {
			List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();

			findJarFile(this.pluginArtifacts, "org.springframework.experimental", "spring-native-configuration")
					.ifPresent(artifact -> runtimeClasspathElements.add(1, artifact.getFile().getAbsolutePath()));
			findJarFile(this.pluginArtifacts, "org.springframework.experimental", "spring-aot")
					.ifPresent(artifact -> runtimeClasspathElements.add(1, artifact.getFile().getAbsolutePath()));
			findJarFile(this.pluginArtifacts, "org.springframework.boot", "spring-boot-loader-tools")
					.ifPresent(artifact -> runtimeClasspathElements.add(1, artifact.getFile().getAbsolutePath()));
			findJarFile(this.pluginArtifacts, "com.squareup", "javapoet")
					.ifPresent(artifact -> runtimeClasspathElements.add(1, artifact.getFile().getAbsolutePath()));

			if (getAotOptions().toMode().equals(Mode.NATIVE_NEXT)) {
				RunProcess runProcess = new RunProcess(Paths.get(this.project.getBuild().getDirectory()).toFile(), getJavaExecutable());
				Runtime.getRuntime().addShutdownHook(new Thread(new RunProcessKiller(runProcess)));

				List<String> args = new ArrayList<>();
				// plugin classpath
				args.add("-cp");
				args.add(asClasspathArgument(runtimeClasspathElements));
				// main class
				args.add(BootstrapCodeGeneratorRunner.class.getCanonicalName());
				// sourcesPath
				args.add(sourcesPath.toAbsolutePath().toString());
				// resourcesPath
				args.add(resourcesPath.toAbsolutePath().toString());
				// resourcesFolders
				args.add(StringUtils.collectionToDelimitedString(resourceFolders, File.pathSeparator));

				int exitCode = runProcess.run(true, args, Collections.emptyMap());
				if (exitCode != 0 && exitCode != 130) {
					throw new IllegalStateException("Bootstrap code generator finished with exit code: " + exitCode);
				}
			}
			else {
				BootstrapCodeGenerator generator = new BootstrapCodeGenerator(getAotOptions());
				generator.generate(sourcesPath, resourcesPath, runtimeClasspathElements, resourceFolders);
			}
			compileGeneratedSources(sourcesPath, runtimeClasspathElements);
			processGeneratedResources(resourcesPath, Paths.get(project.getBuild().getOutputDirectory()));
			this.buildContext.refresh(this.buildDir);
		}
		catch (Throwable exc) {
			getLog().error(exc);
			getLog().error(Arrays.toString(exc.getStackTrace()));
			throw new MojoFailureException("Build failed during Spring AOT code generation", exc);
		}
	}

	protected void compileGeneratedSources(Path sourcesPath, List<String> runtimeClasspathElements) throws MojoExecutionException {
		String compilerVersion = this.project.getProperties().getProperty("maven-compiler-plugin.version", DEFAULT_COMPILER_PLUGIN_VERSION);
		project.addCompileSourceRoot(sourcesPath.toString());
		Xpp3Dom compilerConfig = configuration(
				element("compileSourceRoots", element("compileSourceRoot", sourcesPath.toString())),
				element("compilePath", runtimeClasspathElements.stream()
						.map(classpathElement -> element("compilePath", classpathElement)).toArray(MojoExecutor.Element[]::new))
		);
		executeMojo(
				plugin(groupId("org.apache.maven.plugins"), artifactId("maven-compiler-plugin"), version(compilerVersion)),
				goal("compile"), compilerConfig, executionEnvironment(this.project, this.session, this.pluginManager));
	}

	protected void processGeneratedResources(Path sourcePath, Path destinationPath) throws MojoExecutionException {
		String resourcesVersion = this.project.getProperties().getProperty("maven-resources-plugin.version", "3.2.0");
		Xpp3Dom resourceConfig = configuration(element("resources", element("resource", element("directory", sourcePath.toString()))),
				element("outputDirectory", destinationPath.toString()));
		Resource resource = new Resource();
		resource.setDirectory(sourcePath.toString());
		project.addResource(resource);
		executeMojo(
				plugin(groupId("org.apache.maven.plugins"), artifactId("maven-resources-plugin"), version(resourcesVersion)),
				goal("copy-resources"), resourceConfig, executionEnvironment(this.project, this.session, this.pluginManager));
	}

}
