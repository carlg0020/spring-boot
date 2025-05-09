/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.boot.build.antora;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.gradle.api.Project;

import org.springframework.boot.build.artifacts.ArtifactRelease;
import org.springframework.boot.build.bom.BomExtension;
import org.springframework.boot.build.bom.Library;
import org.springframework.boot.build.properties.BuildProperties;
import org.springframework.boot.build.properties.BuildType;
import org.springframework.util.Assert;

/**
 * Generates Asciidoctor attributes for use with Antora.
 *
 * @author Phillip Webb
 */
public class AntoraAsciidocAttributes {

	private static final String DASH_SNAPSHOT = "-SNAPSHOT";

	private final String version;

	private final boolean latestVersion;

	private final BuildType buildType;

	private final ArtifactRelease artifactRelease;

	private final List<Library> libraries;

	private final Map<String, String> dependencyVersions;

	private final Map<String, ?> projectProperties;

	public AntoraAsciidocAttributes(Project project, BomExtension dependencyBom,
			Map<String, String> dependencyVersions) {
		this.version = String.valueOf(project.getVersion());
		this.latestVersion = Boolean.parseBoolean(String.valueOf(project.findProperty("latestVersion")));
		this.buildType = BuildProperties.get(project).buildType();
		this.artifactRelease = ArtifactRelease.forProject(project);
		this.libraries = dependencyBom.getLibraries();
		this.dependencyVersions = dependencyVersions;
		this.projectProperties = project.getProperties();
	}

	AntoraAsciidocAttributes(String version, boolean latestVersion, BuildType buildType, List<Library> libraries,
			Map<String, String> dependencyVersions, Map<String, ?> projectProperties) {
		this.version = version;
		this.latestVersion = latestVersion;
		this.buildType = buildType;
		this.artifactRelease = ArtifactRelease.forVersion(version);
		this.libraries = (libraries != null) ? libraries : Collections.emptyList();
		this.dependencyVersions = (dependencyVersions != null) ? dependencyVersions : Collections.emptyMap();
		this.projectProperties = (projectProperties != null) ? projectProperties : Collections.emptyMap();
	}

	public Map<String, String> get() {
		Map<String, String> attributes = new LinkedHashMap<>();
		addBuildTypeAttribute(attributes);
		addGitHubAttributes(attributes);
		addVersionAttributes(attributes);
		addArtifactAttributes(attributes);
		addUrlJava(attributes);
		addUrlLibraryLinkAttributes(attributes);
		addPropertyAttributes(attributes);
		return attributes;
	}

	private void addBuildTypeAttribute(Map<String, String> attributes) {
		attributes.put("build-type", this.buildType.toIdentifier());
	}

	private void addGitHubAttributes(Map<String, String> attributes) {
		attributes.put("github-repo", "spring-projects/spring-boot");
		attributes.put("github-ref", determineGitHubRef());
	}

	private String determineGitHubRef() {
		int snapshotIndex = this.version.lastIndexOf(DASH_SNAPSHOT);
		if (snapshotIndex == -1) {
			return "v" + this.version;
		}
		if (this.latestVersion) {
			return "main";
		}
		String versionRoot = this.version.substring(0, snapshotIndex);
		int lastDot = versionRoot.lastIndexOf('.');
		return versionRoot.substring(0, lastDot) + ".x";
	}

	private void addVersionAttributes(Map<String, String> attributes) {
		this.libraries.forEach((library) -> {
			String name = "version-" + library.getLinkRootName();
			String value = library.getVersion().toString();
			attributes.put(name, value);
		});
		attributes.put("version-native-build-tools", (String) this.projectProperties.get("nativeBuildToolsVersion"));
		attributes.put("version-graal", (String) this.projectProperties.get("graalVersion"));
		addDependencyVersion(attributes, "jackson-annotations", "com.fasterxml.jackson.core:jackson-annotations");
		addDependencyVersion(attributes, "jackson-core", "com.fasterxml.jackson.core:jackson-core");
		addDependencyVersion(attributes, "jackson-databind", "com.fasterxml.jackson.core:jackson-databind");
		addSpringDataDependencyVersion(attributes, "spring-data-commons");
		addSpringDataDependencyVersion(attributes, "spring-data-couchbase");
		addSpringDataDependencyVersion(attributes, "spring-data-cassandra");
		addSpringDataDependencyVersion(attributes, "spring-data-elasticsearch");
		addSpringDataDependencyVersion(attributes, "spring-data-jdbc");
		addSpringDataDependencyVersion(attributes, "spring-data-jpa");
		addSpringDataDependencyVersion(attributes, "spring-data-mongodb");
		addSpringDataDependencyVersion(attributes, "spring-data-neo4j");
		addSpringDataDependencyVersion(attributes, "spring-data-r2dbc");
		addSpringDataDependencyVersion(attributes, "spring-data-rest", "spring-data-rest-core");
		addSpringDataDependencyVersion(attributes, "spring-data-ldap");
	}

	private void addSpringDataDependencyVersion(Map<String, String> attributes, String artifactId) {
		addSpringDataDependencyVersion(attributes, artifactId, artifactId);
	}

	private void addSpringDataDependencyVersion(Map<String, String> attributes, String name, String artifactId) {
		String version = getVersion("org.springframework.data:" + artifactId);
		String majorMinor = Arrays.stream(version.split("\\.")).limit(2).collect(Collectors.joining("."));
		String antoraVersion = version.endsWith(DASH_SNAPSHOT) ? majorMinor + DASH_SNAPSHOT : majorMinor;
		attributes.put("version-" + name + "-docs", antoraVersion);
		attributes.put("version-" + name + "-javadoc", majorMinor + ".x");
	}

	private void addDependencyVersion(Map<String, String> attributes, String name, String groupAndArtifactId) {
		attributes.put("version-" + name, getVersion(groupAndArtifactId));
	}

	private String getVersion(String groupAndArtifactId) {
		String version = this.dependencyVersions.get(groupAndArtifactId);
		Assert.notNull(version, () -> "No version found for " + groupAndArtifactId);
		return version;
	}

	private void addArtifactAttributes(Map<String, String> attributes) {
		attributes.put("url-artifact-repository", this.artifactRelease.getDownloadRepo());
		attributes.put("artifact-release-type", this.artifactRelease.getType());
		attributes.put("build-and-artifact-release-type",
				this.buildType.toIdentifier() + "-" + this.artifactRelease.getType());
	}

	private void addUrlJava(Map<String, String> attributes) {
		attributes.put("url-javase-javadoc", "https://docs.oracle.com/en/java/javase/17/docs/api/");
	}

	private void addUrlLibraryLinkAttributes(Map<String, String> attributes) {
		this.libraries.forEach((library) -> {
			String prefix = "url-" + library.getLinkRootName() + "-";
			library.getLinks().forEach((name, link) -> {
				String linkName = prefix + name;
				attributes.put(linkName, link.url(library));
				link.packages()
					.stream()
					.map(this::packageAttributeName)
					.forEach((packageAttributeName) -> attributes.put(packageAttributeName, "{" + linkName + "}"));
			});
		});
	}

	private String packageAttributeName(String packageName) {
		return "javadoc-location-" + packageName.replace('.', '-');
	}

	private void addPropertyAttributes(Map<String, String> attributes) {
		Properties properties = new Properties() {

			@Override
			public synchronized Object put(Object key, Object value) {
				// Put directly because order is important for us
				return attributes.put(key.toString(), value.toString());
			}

		};
		try (InputStream in = getClass().getResourceAsStream("antora-asciidoc-attributes.properties")) {
			properties.load(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
