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
import java.util.Arrays;
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
@Mojo(name = "test-generate", defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, requiresProject = true, threadSafe = true,
		requiresDependencyResolution = ResolutionScope.TEST,
		requiresDependencyCollection = ResolutionScope.TEST)
public class TestGenerateMojo extends AbstractBootstrapMojo {

	/**
	 * Location of generated source files created by Spring AOT to bootstrap the test context.
	 */
	@Parameter(defaultValue = "${project.build.directory}/generated-test-sources/spring-aot/")
	private File generatedTestSourcesDirectory;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Set<Path> resourceFolders = new HashSet<>();
		for (Resource r: project.getTestResources()) {
			// TODO respect includes/excludes
			resourceFolders.add(new File(r.getDirectory()).toPath());
		}
		recreateGeneratedSourcesFolder(this.generatedTestSourcesDirectory);
		Path sourcesPath = this.generatedTestSourcesDirectory.toPath().resolve(Paths.get("src", "test", "java"));
		Path resourcesPath = this.generatedTestSourcesDirectory.toPath().resolve(Paths.get("src", "test", "resources"));
		try {
			List<String> testClasspathElements = this.project.getTestClasspathElements();
			BootstrapCodeGenerator generator = new BootstrapCodeGenerator(getAotOptions());
			generator.generate(sourcesPath, resourcesPath, testClasspathElements, resourceFolders);
			compileGeneratedTestSources(sourcesPath, testClasspathElements);
			processGeneratedTestResources(resourcesPath, Paths.get(project.getBuild().getTestOutputDirectory()));
			this.buildContext.refresh(this.buildDir);
		}
		catch (Throwable exc) {
			getLog().error(exc);
			getLog().error(Arrays.toString(exc.getStackTrace()));
			throw new MojoFailureException("Build failed during Spring AOT test code generation", exc);
		}
	}

	protected void compileGeneratedTestSources(Path sourcesPath, List<String> testClasspathElements) throws MojoExecutionException {
		String compilerVersion = this.project.getProperties().getProperty("maven-compiler-plugin.version", DEFAULT_COMPILER_PLUGIN_VERSION);
		project.addTestCompileSourceRoot(sourcesPath.toString());
		Xpp3Dom compilerConfig = configuration(
				element("compileSourceRoots", element("compileSourceRoot", sourcesPath.toString())),
				element("compilePath", testClasspathElements.stream()
						.map(classpathElement -> element("compilePath", classpathElement)).toArray(MojoExecutor.Element[]::new))
		);
		executeMojo(
				plugin(groupId("org.apache.maven.plugins"), artifactId("maven-compiler-plugin"), version(compilerVersion)),
				goal("testCompile"), compilerConfig, executionEnvironment(this.project, this.session, this.pluginManager));
	}



	protected void processGeneratedTestResources(Path sourcePath, Path destinationPath) throws MojoExecutionException {
		String resourcesVersion = this.project.getProperties().getProperty("maven-resources-plugin.version", "3.2.0");
		Xpp3Dom resourceConfig = configuration(element("resources", element("resource", element("directory", sourcePath.toString()))),
				element("outputDirectory", destinationPath.toString()));
		Resource resource = new Resource();
		resource.setDirectory(sourcePath.toString());
		project.addTestResource(resource);
		executeMojo(
				plugin(groupId("org.apache.maven.plugins"), artifactId("maven-resources-plugin"), version(resourcesVersion)),
				goal("copy-resources"), resourceConfig, executionEnvironment(this.project, this.session, this.pluginManager));
	}
	
}
