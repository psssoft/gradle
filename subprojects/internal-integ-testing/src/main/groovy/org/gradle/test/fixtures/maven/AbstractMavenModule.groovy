/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.test.fixtures.maven

import groovy.xml.MarkupBuilder
import org.gradle.internal.hash.HashUtil
import org.gradle.test.fixtures.AbstractModule
import org.gradle.test.fixtures.GradleModuleMetadata
import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.file.TestFile

import java.text.SimpleDateFormat

abstract class AbstractMavenModule extends AbstractModule implements MavenModule {
    protected static final String MAVEN_METADATA_FILE = "maven-metadata.xml"
    private final TestFile rootDir
    final TestFile moduleDir
    final String groupId
    final String artifactId
    final String version
    Map<String, String> parentPom
    String type = 'jar'
    String packaging
    int publishCount = 1
    private boolean noMetaData
    private boolean moduleMetadata
    private final List dependencies = []
    private final List artifacts = []
    final updateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    final timestampFormat = new SimpleDateFormat("yyyyMMdd.HHmmss")

    AbstractMavenModule(TestFile rootDir, TestFile moduleDir, String groupId, String artifactId, String version) {
        this.rootDir = rootDir
        this.moduleDir = moduleDir
        this.groupId = groupId
        this.artifactId = artifactId
        this.version = version
    }

    @Override
    String getGroup() {
        return groupId
    }

    @Override
    String getModule() {
        return artifactId
    }

    @Override
    String getPath() {
        return "${moduleRootPath}/${version}"
    }

    String getModuleRootPath() {
        return "${groupId ? groupId.replace('.', '/') + '/' : ''}${artifactId}"
    }

    @Override
    MavenModule parent(String group, String artifactId, String version) {
        parentPom = [groupId: group, artifactId: artifactId, version: version]
        return this
    }

    TestFile getArtifactFile(Map options = [:]) {
        return getArtifact(options).file
    }

    @Override
    MavenModule withModuleMetadata() {
        moduleMetadata = true
        return this
    }

    @Override
    String getPublishArtifactVersion() {
        if (uniqueSnapshots && version.endsWith("-SNAPSHOT")) {
            return "${version.replaceFirst('-SNAPSHOT$', '')}-${getUniqueSnapshotVersion()}"
        }
        return version
    }

    private String getUniqueSnapshotVersion() {
        assert uniqueSnapshots && version.endsWith('-SNAPSHOT')
        if (metaDataFile.isFile()) {
            def metaData = new XmlParser().parse(metaDataFile.assertIsFile())
            def timestamp = metaData.versioning.snapshot.timestamp[0].text().trim()
            def build = metaData.versioning.snapshot.buildNumber[0].text().trim()
            return "${timestamp}-${build}"
        }
        return "${timestampFormat.format(publishTimestamp)}-${publishCount}"
    }

    @Override
    MavenModule dependsOnModules(String... dependencyArtifactIds) {
        for (String id : dependencyArtifactIds) {
            dependsOn(groupId, id, '1.0')
        }
        return this
    }

    @Override
    MavenModule dependsOn(Module target) {
        dependsOn(target.group, target.module, target.version)
    }

    @Override
    MavenModule dependsOn(Map<String, ?> attributes, Module target) {
        this.dependencies << [groupId: target.group, artifactId: target.module, version: target.version, type: attributes.type, scope: attributes.scope, classifier: attributes.classifier, optional: attributes.optional, exclusions: attributes.exclusions]
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version, String type = null, String scope = null, String classifier = null, Collection<Map> exclusions = null) {
        this.dependencies << [groupId: group, artifactId: artifactId, version: version, type: type, scope: scope, classifier: classifier, exclusions: exclusions]
        return this
    }

    @Override
    MavenModule hasPackaging(String packaging) {
        this.packaging = packaging
        return this
    }

    @Override
    MavenModule hasType(String type) {
        this.type = type
        return this
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    MavenModule artifact(Map<String, ?> options) {
        artifacts << options
        return this
    }

    String getPackaging() {
        return packaging
    }

    List getDependencies() {
        return dependencies
    }

    List getArtifacts() {
        return artifacts
    }

    void assertNotPublished() {
        pomFile.assertDoesNotExist()
    }

    void assertPublished() {
        assert pomFile.assertExists()
        assert parsedPom.groupId == groupId
        assert parsedPom.artifactId == artifactId
        assert parsedPom.version == version
        if (getModuleMetadata().file.exists()) {
            def metadata = parsedModuleMetadata
            if (metadata.component) {
                assert metadata.component.group == groupId
                assert metadata.component.module == artifactId
                assert metadata.component.version == version
                assert metadata.owner == null
            } else {
                assert metadata.owner
            }
            metadata.variants.each { variant ->
                def ref = variant.availableAt
                if (ref != null) {
                    def otherMetadataArtifact = getArtifact(ref.url)
                    assert otherMetadataArtifact.file.file
                    def otherMetadata = new GradleModuleMetadata(otherMetadataArtifact.file)
                    assert otherMetadata.owner.group == groupId
                    assert otherMetadata.owner.module == artifactId
                    assert otherMetadata.owner.version == version
                }
                variant.files.each { file ->
                    def artifact = getArtifact(file.url)
                    assert artifact.file.file
                    assert artifact.file.length() == file.size
                    assert HashUtil.createHash(artifact.file, "sha1") == file.sha1
                    assert HashUtil.createHash(artifact.file, "md5") == file.md5
                }
            }
        }
    }

    void assertPublishedAsPomModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == "pom"
    }

    @Override
    void assertPublishedAsJavaModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.jar", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == null
    }

    void assertPublishedAsWebModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.war", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == 'war'
    }

    void assertPublishedAsEarModule() {
        assertPublished()
        assertArtifactsPublished("${artifactId}-${publishArtifactVersion}.ear", "${artifactId}-${publishArtifactVersion}.pom")
        assert parsedPom.packaging == 'ear'
    }

    /**
     * Asserts that exactly the given artifacts have been deployed, along with their checksum files
     */
    void assertArtifactsPublished(String... names) {
        Set allFileNames = []
        for (name in names) {
            allFileNames.addAll([name, "${name}.sha1", "${name}.md5"])
        }

        assert moduleDir.list() as Set == allFileNames
        for (name in names) {
            assertChecksumsPublishedFor(moduleDir.file(name))
        }
    }

    void assertChecksumsPublishedFor(TestFile testFile) {
        def sha1File = sha1File(testFile)
        sha1File.assertIsFile()
        assert new BigInteger(sha1File.text, 16) == getHash(testFile, "SHA1")
        def md5File = md5File(testFile)
        md5File.assertIsFile()
        assert new BigInteger(md5File.text, 16) == getHash(testFile, "MD5")
    }

    @Override
    MavenPom getParsedPom() {
        return new MavenPom(pomFile)
    }

    @Override
    GradleModuleMetadata getParsedModuleMetadata() {
        return new GradleModuleMetadata(artifactFile(classifier: 'module', type: 'json'))
    }

    @Override
    DefaultMavenMetaData getRootMetaData() {
        new DefaultMavenMetaData("$moduleRootPath/${MAVEN_METADATA_FILE}", rootMetaDataFile)
    }

    @Override
    ModuleArtifact getArtifact() {
        return getArtifact([:])
    }

    @Override
    ModuleArtifact getPom() {
        return getArtifact(type: 'pom')
    }

    @Override
    ModuleArtifact getModuleMetadata() {
        return getArtifact(classifier: 'module', type: 'json')
    }

    @Override
    TestFile getPomFile() {
        return getPom().file
    }

    TestFile getPomFileForPublish() {
        return moduleDir.file("$artifactId-${publishArtifactVersion}.pom")
    }

    @Override
    TestFile getMetaDataFile() {
        moduleDir.file(MAVEN_METADATA_FILE)
    }

    TestFile getRootMetaDataFile() {
        moduleDir.parentFile.file(MAVEN_METADATA_FILE)
    }

    TestFile artifactFile(Map<String, ?> options) {
        return getArtifact(options).file
    }

    @Override
    ModuleArtifact getArtifact(String relativePath) {
        def file = moduleDir.file(relativePath)
        def path = file.relativizeFrom(rootDir).path
        return new ModuleArtifact() {
            @Override
            String getPath() {
                return path
            }

            @Override
            TestFile getFile() {
                return file
            }
        }
    }

    @Override
    ModuleArtifact getArtifact(Map<String, ?> options) {
        def artifact = toArtifact(options)
        def suffix = (artifact.classifier ? "-${artifact.classifier}" : "") + (artifact.type ? ".${artifact.type}" : "")
        return new ModuleArtifact() {
            String getFileName() {
                if (version.endsWith("-SNAPSHOT") && !metaDataFile.exists() && uniqueSnapshots) {
                    return "${artifactId}-${version}${suffix}"
                } else {
                    return "$artifactId-${publishArtifactVersion}${suffix}"
                }
            }

            @Override
            String getPath() {
                return "${AbstractMavenModule.this.getPath()}/$fileName"
            }

            @Override
            TestFile getFile() {
                return moduleDir.file(fileName)
            }
        }
    }

    @Override
    MavenModule publishWithChangedContent() {
        publishCount++
        return publish()
    }

    protected Map<String, Object> toArtifact(Map<String, ?> options) {
        options = new HashMap<String, Object>(options)
        def artifact = [type: options.containsKey('type') ? options.remove('type') : type, classifier: options.remove('classifier') ?: null]
        assert options.isEmpty(): "Unknown options : ${options.keySet()}"
        return artifact
    }

    Date getPublishTimestamp() {
        return new Date(updateFormat.parse("20100101120000").time + publishCount * 1000)
    }

    private void publishModuleMetadata() {
        moduleDir.createDir()
        def file = moduleDir.file("$artifactId-${publishArtifactVersion}-module.json")
        def artifact = getArtifact([:])
        def value = new StringBuilder()
        value << """
            { 
                "formatVersion": "0.2", 
                "builtBy": { "gradle": { } },
                "variants": [
                    { 
                        "name": "default",
                        "files": [
                            { "name": "${artifact.file.name}", "url": "${artifact.file.name}" }
                        ],
                        "dependencies": [
"""
        value << dependencies.collect { d ->
            "  { \"group\": \"$d.groupId\", \"module\": \"$d.artifactId\", \"version\": \"$d.version\" }\n"
        }.join(",\n")

        value << """                        
                        ]
                    }
                ]
            }
        """

        file.text = value.toString()
    }

    @Override
    MavenModule publishPom() {
        moduleDir.createDir()
        def rootMavenMetaData = getRootMetaDataFile()

        updateRootMavenMetaData(rootMavenMetaData)

        if (publishesMetaDataFile()) {
            publish(metaDataFile) { Writer writer ->
                writer << getMetaDataFileContent()
            }
        }

        publish(pomFileForPublish) { Writer writer ->
            def pomPackaging = packaging ?: type;
            new MarkupBuilder(writer).project {
                mkp.comment(artifactContent)
                modelVersion("4.0.0")
                groupId(groupId)
                artifactId(artifactId)
                version(version)
                packaging(pomPackaging)
                description("Published on ${publishTimestamp}")
                if (parentPom) {
                    parent {
                        groupId(parentPom.groupId)
                        artifactId(parentPom.artifactId)
                        version(parentPom.version)
                    }
                }
                if (dependencies) {
                    dependencies {
                        dependencies.each { dep ->
                            dependency {
                                groupId(dep.groupId)
                                artifactId(dep.artifactId)
                                if (dep.version) {
                                    version(dep.version)
                                }
                                if (dep.type) {
                                    type(dep.type)
                                }
                                if (dep.scope) {
                                    scope(dep.scope)
                                }
                                if (dep.classifier) {
                                    classifier(dep.classifier)
                                }
                                if (dep.optional) {
                                    optional(true)
                                }
                                if (dep.exclusions) {
                                    exclusions {
                                        for (exc in dep.exclusions) {
                                            exclusion {
                                                groupId(exc.groupId)
                                                artifactId(exc.artifactId)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return this
    }

    private void updateRootMavenMetaData(TestFile rootMavenMetaData) {
        def allVersions = rootMavenMetaData.exists() ? new XmlParser().parseText(rootMavenMetaData.text).versioning.versions.version*.value().flatten() : []
        allVersions << version;
        publish(rootMavenMetaData) { Writer writer ->
            def builder = new MarkupBuilder(writer)
            builder.metadata {
                groupId(groupId)
                artifactId(artifactId)
                version(allVersions.max())
                versioning {
                    versions {
                        allVersions.each {currVersion ->
                            version(currVersion)
                        }
                    }
                }
            }
        }
    }

    abstract String getMetaDataFileContent()

    @Override
    MavenModule withNoPom() {
        noMetaData = true
        return this
    }

    @Override
    MavenModule publish() {
        if(!noMetaData) {
            publishPom()
        }
        if (moduleMetadata) {
            publishModuleMetadata()
        }

        artifacts.each { artifact ->
            publishArtifact(artifact)
        }
        if (type != 'pom') {
            publishArtifact([:])
        }

        return this
    }

    File publishArtifact(Map<String, ?> artifact) {
        def artifactFile = artifactFile(artifact)

        publish(artifactFile) { Writer writer ->
            writer << "${artifactFile.name} : $artifactContent"
        }
        return artifactFile
    }

    protected String getArtifactContent() {
        // Some content to include in each artifact, so that its size and content varies on each publish
        return (0..publishCount).join("-")
    }

    protected abstract boolean publishesMetaDataFile()
}
