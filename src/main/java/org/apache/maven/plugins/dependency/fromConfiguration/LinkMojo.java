/**
 * Copyright (C) 2017 Marvin Herman Froeder (marvin@marvinformatics.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.maven.plugins.dependency.fromConfiguration;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.Dependency;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactItemFilter;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.repository.RepositoryManager;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Goal that links a list of artifacts from the repository to defined locations.
 *
 * @author marvin
 */
@Mojo(name = "link", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = false, threadSafe = true)
public class LinkMojo
        extends AbstractDependencyMojo {

    /**
     * Default output location used for mojo, unless overridden in ArtifactItem.
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/dependency")
    private File outputDirectory;

    /**
     * Overwrite release artifacts
     */
    @Parameter(property = "mdep.overWriteReleases", defaultValue = "false")
    private boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts
     */
    @Parameter(property = "mdep.overWriteSnapshots", defaultValue = "false")
    private boolean overWriteSnapshots;

    /**
     * Overwrite if newer
     */
    @Parameter(property = "mdep.overIfNewer", defaultValue = "true")
    private boolean overWriteIfNewer;

    /**
     * Collection of ArtifactItems to work on. (ArtifactItem contains groupId, artifactId, version,
     * type, classifier, outputDirectory, destFileName, overWrite and encoding.) See
     * <a href="./usage.html">Usage</a> for details.
     */
    @Parameter
    private List<ArtifactItem> artifactItems;

    /**
     * Path to override default local repository during plugin's execution. To remove all downloaded
     * artifacts as part of the build, set this value to a location under your project's target
     * directory
     */
    @Parameter
    private File localRepositoryDirectory;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private RepositoryManager repositoryManager;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Strip artifact version during link
     */
    @Parameter(property = "mdep.stripVersion", defaultValue = "false")
    private boolean stripVersion = false;

    /**
     * Strip artifact classifier during link
     */
    @Parameter(property = "mdep.stripClassifier", defaultValue = "false")
    private boolean stripClassifier = false;

    /**
     * Prepend artifact groupId during link
     */
    @Parameter(property = "mdep.prependGroupId", defaultValue = "false")
    private final boolean prependGroupId = false;

    /**
     * Use artifact baseVersion during link
     */
    @Parameter(property = "mdep.useBaseVersion", defaultValue = "false")
    private boolean useBaseVersion = false;

    /**
     * The artifact to link from command line. A string of the form
     * groupId:artifactId:version[:packaging[:classifier]]. Use {@link #artifactItems} within the
     * POM configuration.
     */
    @SuppressWarnings("unused") // marker-field, setArtifact(String) does the magic
    @Parameter(property = "artifact")
    private String artifact;

    /**
     * <i>not used in this goal</i>
     */
    @Parameter
    protected boolean useJvmChmod = true;

    /**
     * <i>not used in this goal</i>
     */
    @Parameter
    protected boolean ignorePermissions;

    // artifactItems is filled by either field injection or by setArtifact()
    protected void verifyRequirements()
            throws MojoFailureException {
        if (artifactItems == null || artifactItems.isEmpty()) {
            throw new MojoFailureException("Either artifact or artifactItems is required ");
        }
    }

    /**
     * Preprocesses the list of ArtifactItems. This method defaults the outputDirectory if not set
     * and creates the output Directory if it doesn't exist.
     *
     * @param processArtifactItemsRequest preprocessing instructions
     * @return An ArrayList of preprocessed ArtifactItems
     * @throws MojoExecutionException with a message if an error occurs.
     * @see ArtifactItem
     */
    protected List<ArtifactItem> getProcessedArtifactItems(ProcessArtifactItemsRequest processArtifactItemsRequest)
            throws MojoExecutionException {

        final boolean removeVersion = processArtifactItemsRequest.isRemoveVersion(),
                prependGroupId = processArtifactItemsRequest.isPrependGroupId(),
                useBaseVersion = processArtifactItemsRequest.isUseBaseVersion();

        final boolean removeClassifier = processArtifactItemsRequest.isRemoveClassifier();

        if (artifactItems == null || artifactItems.size() < 1) {
            throw new MojoExecutionException("There are no artifactItems configured.");
        }

        for (final ArtifactItem artifactItem : artifactItems) {
            this.getLog().info("Configured Artifact: " + artifactItem.toString());

            if (artifactItem.getOutputDirectory() == null) {
                artifactItem.setOutputDirectory(this.outputDirectory);
            }
            artifactItem.getOutputDirectory().mkdirs();

            // make sure we have a version.
            if (StringUtils.isEmpty(artifactItem.getVersion())) {
                fillMissingArtifactVersion(artifactItem);
            }

            artifactItem.setArtifact(this.getArtifact(artifactItem));

            if (StringUtils.isEmpty(artifactItem.getDestFileName())) {
                artifactItem.setDestFileName(DependencyUtil.getFormattedFileName(artifactItem.getArtifact(),
                        removeVersion, prependGroupId,
                        useBaseVersion, removeClassifier));
            }

            try {
                artifactItem.setNeedsProcessing(checkIfProcessingNeeded(artifactItem));
            } catch (final ArtifactFilterException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        return artifactItems;
    }

    private boolean checkIfProcessingNeeded(ArtifactItem item)
            throws MojoExecutionException, ArtifactFilterException {
        return StringUtils.equalsIgnoreCase(item.getOverWrite(), "true")
                || getMarkedArtifactFilter(item).isArtifactIncluded(item);
    }

    /**
     * Resolves the Artifact from the remote repository if necessary. If no version is specified, it
     * will be retrieved from the dependency list or from the DependencyManagement section of the
     * pom.
     *
     * @param artifactItem containing information about artifact from plugin configuration.
     * @return Artifact object representing the specified file.
     * @throws MojoExecutionException with a message if the version can't be found in
     *             DependencyManagement.
     */
    protected Artifact getArtifact(ArtifactItem artifactItem)
            throws MojoExecutionException {
        Artifact artifact;

        try {
            // mdep-50 - rolledback for now because it's breaking some functionality.
            /*
             * List listeners = new ArrayList(); Set theSet = new HashSet(); theSet.add( artifact );
             * ArtifactResolutionResult artifactResolutionResult = artifactCollector.collect(
             * theSet, project .getArtifact(), managedVersions, this.local,
             * project.getRemoteArtifactRepositories(), artifactMetadataSource, null, listeners );
             * Iterator iter = artifactResolutionResult.getArtifactResolutionNodes().iterator();
             * while ( iter.hasNext() ) { ResolutionNode node = (ResolutionNode) iter.next();
             * artifact = node.getArtifact(); }
             */

            ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

            if (localRepositoryDirectory != null) {
                buildingRequest = repositoryManager.setLocalRepositoryBasedir(buildingRequest,
                        localRepositoryDirectory);
            }

            // Map dependency to artifact coordinate
            final DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
            coordinate.setGroupId(artifactItem.getGroupId());
            coordinate.setArtifactId(artifactItem.getArtifactId());
            coordinate.setVersion(artifactItem.getVersion());
            coordinate.setClassifier(artifactItem.getClassifier());

            final String extension;
            final ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler(artifactItem.getType());
            if (artifactHandler != null) {
                extension = artifactHandler.getExtension();
            } else {
                extension = artifactItem.getType();
            }
            coordinate.setExtension(extension);

            artifact = artifactResolver.resolveArtifact(buildingRequest, coordinate).getArtifact();
        } catch (final ArtifactResolverException e) {
            throw new MojoExecutionException("Unable to find/resolve artifact.", e);
        }

        return artifact;
    }

    /**
     * Tries to find missing version from dependency list and dependency management. If found, the
     * artifact is updated with the correct version. It will first look for an exact match on
     * artifactId/groupId/classifier/type and if it doesn't find a match, it will try again looking
     * for artifactId and groupId only.
     *
     * @param artifact representing configured file.
     * @throws MojoExecutionException
     */
    private void fillMissingArtifactVersion(ArtifactItem artifact)
            throws MojoExecutionException {
        final MavenProject project = getProject();
        final List<Dependency> deps = project.getDependencies();
        final List<Dependency> depMngt = project.getDependencyManagement() == null ? Collections.<Dependency> emptyList()
                : project.getDependencyManagement().getDependencies();

        if (!findDependencyVersion(artifact, deps, false)
                && (project.getDependencyManagement() == null || !findDependencyVersion(artifact, depMngt, false))
                && !findDependencyVersion(artifact, deps, true)
                && (project.getDependencyManagement() == null || !findDependencyVersion(artifact, depMngt, true))) {
            throw new MojoExecutionException("Unable to find artifact version of " + artifact.getGroupId() + ":"
                    + artifact.getArtifactId() + " in either dependency list or in project's dependency management.");
        }
    }

    /**
     * Tries to find missing version from a list of dependencies. If found, the artifact is updated
     * with the correct version.
     *
     * @param artifact representing configured file.
     * @param dependencies list of dependencies to search.
     * @param looseMatch only look at artifactId and groupId
     * @return the found dependency
     */
    private boolean findDependencyVersion(ArtifactItem artifact, List<Dependency> dependencies, boolean looseMatch) {
        for (final Dependency dependency : dependencies) {
            if (StringUtils.equals(dependency.getArtifactId(), artifact.getArtifactId())
                    && StringUtils.equals(dependency.getGroupId(), artifact.getGroupId())
                    && (looseMatch || StringUtils.equals(dependency.getClassifier(), artifact.getClassifier()))
                    && (looseMatch || StringUtils.equals(dependency.getType(), artifact.getType()))) {
                artifact.setVersion(dependency.getVersion());

                return true;
            }
        }

        return false;
    }

    /**
     * @return Returns the artifactItems.
     */
    public List<ArtifactItem> getArtifactItems() {
        return this.artifactItems;
    }

    /**
     * @param theArtifactItems The artifactItems to set.
     */
    public void setArtifactItems(List<ArtifactItem> theArtifactItems) {
        this.artifactItems = theArtifactItems;
    }

    /**
     * @return Returns the outputDirectory.
     */
    public File getOutputDirectory() {
        return this.outputDirectory;
    }

    /**
     * @param theOutputDirectory The outputDirectory to set.
     */
    public void setOutputDirectory(File theOutputDirectory) {
        this.outputDirectory = theOutputDirectory;
    }

    /**
     * @return Returns the overWriteIfNewer.
     */
    public boolean isOverWriteIfNewer() {
        return this.overWriteIfNewer;
    }

    /**
     * @param theOverWriteIfNewer The overWriteIfNewer to set.
     */
    public void setOverWriteIfNewer(boolean theOverWriteIfNewer) {
        this.overWriteIfNewer = theOverWriteIfNewer;
    }

    /**
     * @return Returns the overWriteReleases.
     */
    public boolean isOverWriteReleases() {
        return this.overWriteReleases;
    }

    /**
     * @param theOverWriteReleases The overWriteReleases to set.
     */
    public void setOverWriteReleases(boolean theOverWriteReleases) {
        this.overWriteReleases = theOverWriteReleases;
    }

    /**
     * @return Returns the overWriteSnapshots.
     */
    public boolean isOverWriteSnapshots() {
        return this.overWriteSnapshots;
    }

    /**
     * @param theOverWriteSnapshots The overWriteSnapshots to set.
     */
    public void setOverWriteSnapshots(boolean theOverWriteSnapshots) {
        this.overWriteSnapshots = theOverWriteSnapshots;
    }

    public void setLocalRepositoryDirectory(File localRepositoryDirectory) {
        this.localRepositoryDirectory = localRepositoryDirectory;
    }

    public void setArtifact(String artifact)
            throws MojoFailureException {
        if (artifact != null) {
            String packaging = "jar";
            String classifier;
            final String[] tokens = StringUtils.split(artifact, ":");
            if (tokens.length < 3 || tokens.length > 5) {
                throw new MojoFailureException("Invalid artifact, "
                        + "you must specify groupId:artifactId:version[:packaging[:classifier]] " + artifact);
            }
            final String groupId = tokens[0];
            final String artifactId = tokens[1];
            final String version = tokens[2];
            if (tokens.length >= 4) {
                packaging = tokens[3];
            }
            if (tokens.length == 5) {
                classifier = tokens[4];
            } else {
                classifier = null;
            }

            final ArtifactItem artifactItem = new ArtifactItem();
            artifactItem.setGroupId(groupId);
            artifactItem.setArtifactId(artifactId);
            artifactItem.setVersion(version);
            artifactItem.setType(packaging);
            artifactItem.setClassifier(classifier);

            setArtifactItems(Collections.singletonList(artifactItem));
        }
    }

    /**
     * Main entry into mojo. This method gets the ArtifactItems and iterates through each one
     * passing it to linkArtifact.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     * @see ArtifactItem
     * @see #getArtifactItems
     * @see #linkArtifact(ArtifactItem)
     */
    @Override
    protected void doExecute()
            throws MojoExecutionException, MojoFailureException {
        verifyRequirements();

        final List<ArtifactItem> theArtifactItems = getProcessedArtifactItems(
                new ProcessArtifactItemsRequest(stripVersion, prependGroupId, useBaseVersion,
                        stripClassifier));
        for (final ArtifactItem artifactItem : theArtifactItems) {
            if (artifactItem.isNeedsProcessing()) {
                linkArtifact(artifactItem);
            } else {
                this.getLog().info(artifactItem + " already exists in " + artifactItem.getOutputDirectory());
            }
        }
    }

    /**
     * Resolves the artifact from the repository and copies it to the specified location.
     *
     * @param artifactItem containing the information about the Artifact to link.
     * @throws MojoExecutionException with a message if an error occurs.
     * @see #linkFile(File, File)
     */
    protected void linkArtifact(ArtifactItem artifactItem)
            throws MojoExecutionException {
        final File destFile = new File(artifactItem.getOutputDirectory(), artifactItem.getDestFileName());

        linkFile(artifactItem.getArtifact().getFile(), destFile);
    }

    protected ArtifactItemFilter getMarkedArtifactFilter(ArtifactItem item) {
        final ArtifactItemFilter destinationNameOverrideFilter = new DestFileFilter(this.isOverWriteReleases(),
                this.isOverWriteSnapshots(), this.isOverWriteIfNewer(),
                false, false, false, false, this.stripVersion, prependGroupId, useBaseVersion,
                item.getOutputDirectory());
        return destinationNameOverrideFilter;
    }

    /**
     * @return Returns the stripVersion.
     */
    public boolean isStripVersion() {
        return this.stripVersion;
    }

    /**
     * @param stripVersion The stripVersion to set.
     */
    public void setStripVersion(boolean stripVersion) {
        this.stripVersion = stripVersion;
    }

    /**
     * @return Returns the stripClassifier.
     */
    public boolean isStripClassifier() {
        return this.stripClassifier;
    }

    /**
     * @param stripClassifier The stripClassifier to set.
     */
    public void setStripClassifier(boolean stripClassifier) {
        this.stripClassifier = stripClassifier;
    }

    /**
     * @param useBaseVersion The useBaseVersion to set.
     */
    public void setUseBaseVersion(boolean useBaseVersion) {
        this.useBaseVersion = useBaseVersion;
    }
}
