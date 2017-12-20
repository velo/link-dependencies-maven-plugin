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
package org.apache.maven.plugins.dependency.fromDependencies;

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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;
import org.apache.maven.plugins.dependency.utils.translators.ArtifactTranslator;
import org.apache.maven.plugins.dependency.utils.translators.ClassifierTypeTranslator;
import org.apache.maven.project.*;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.DefaultArtifactCoordinate;
import org.apache.maven.shared.artifact.filter.collection.*;
import org.apache.maven.shared.artifact.install.ArtifactInstaller;
import org.apache.maven.shared.artifact.install.ArtifactInstallerException;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.dependencies.resolve.DependencyResolver;
import org.apache.maven.shared.repository.RepositoryManager;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.util.*;

/**
 * Goal that copies the project dependencies from the repository to a defined location.
 *
 * @author marvin
 */
@Mojo(name = "link-dependencies", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.PROCESS_SOURCES, threadSafe = true)
public class LinkDependenciesMojo
        extends AbstractDependencyMojo {
    /**
     * Also link the pom of each artifact.
     */
    @Parameter(property = "mdep.linkPom", defaultValue = "false")
    protected boolean linkPom = true;

    /**
     *
     */
    @Component
    private ArtifactInstaller installer;

    /**
     *
     */
    @Component(role = ArtifactRepositoryLayout.class)
    private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     * Either append the artifact's baseVersion or uniqueVersion to the filename. Will only be used
     * if {@link #isStripVersion()} is {@code false}.
     */
    @Parameter(property = "mdep.useBaseVersion", defaultValue = "true")
    protected boolean useBaseVersion = true;

    /**
     * Add parent poms to the list of copied dependencies (both current project pom parents and
     * dependencies parents).
     */
    @Parameter(property = "mdep.addParentPoms", defaultValue = "false")
    protected boolean addParentPoms;

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

    /**
     * Output location.
     */
    @Parameter(property = "outputDirectory", defaultValue = "${project.build.directory}/dependency")
    protected File outputDirectory;

    /**
     * Strip artifact version during link
     */
    @Parameter(property = "mdep.stripVersion", defaultValue = "false")
    protected boolean stripVersion = false;

    /**
     * Strip artifact classifier during link
     */
    @Parameter(property = "mdep.stripClassifier", defaultValue = "false")
    protected boolean stripClassifier = false;

    /**
     * <p>
     * Place each artifact in the same directory layout as a default repository.
     * </p>
     * <p>
     * example:
     * </p>
     *
     * <pre>
     *   /outputDirectory/junit/junit/3.8.1/junit-3.8.1.jar
     * </pre>
     */
    @Parameter(property = "mdep.useRepositoryLayout", defaultValue = "false")
    protected boolean useRepositoryLayout;

    /**
     * Place each type of file in a separate subdirectory. (example /outputDirectory/runtime
     * /outputDirectory/provided etc)
     */
    @Parameter(property = "mdep.useSubDirectoryPerScope", defaultValue = "false")
    protected boolean useSubDirectoryPerScope;

    /**
     * Place each type of file in a separate subdirectory. (example /outputDirectory/jars
     * /outputDirectory/wars etc)
     */
    @Parameter(property = "mdep.useSubDirectoryPerType", defaultValue = "false")
    protected boolean useSubDirectoryPerType;

    /**
     * Place each file in a separate subdirectory. (example
     * <code>/outputDirectory/junit-3.8.1-jar</code>)
     */
    @Parameter(property = "mdep.useSubDirectoryPerArtifact", defaultValue = "false")
    protected boolean useSubDirectoryPerArtifact;

    /**
     * This only applies if the classifier parameter is used.
     */
    @Parameter(property = "mdep.failOnMissingClassifierArtifact", defaultValue = "false")
    protected boolean failOnMissingClassifierArtifact = true;
    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private DependencyResolver dependencyResolver;

    @Component
    private RepositoryManager repositoryManager;

    /**
     * Overwrite release artifacts
     */
    @Parameter(property = "overWriteReleases", defaultValue = "false")
    protected boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts
     */
    @Parameter(property = "overWriteSnapshots", defaultValue = "false")
    protected boolean overWriteSnapshots;

    /**
     * Overwrite artifacts that don't exist or are older than the source.
     */
    @Parameter(property = "overWriteIfNewer", defaultValue = "true")
    protected boolean overWriteIfNewer;

    /**
     * If we should exclude transitive dependencies
     */
    @Parameter(property = "excludeTransitive", defaultValue = "false")
    protected boolean excludeTransitive;

    /**
     * Comma Separated list of Types to include. Empty String indicates include everything
     * (default).
     */
    @Parameter(property = "includeTypes", defaultValue = "")
    protected String includeTypes;

    /**
     * Comma Separated list of Types to exclude. Empty String indicates don't exclude anything
     * (default).
     */
    @Parameter(property = "excludeTypes", defaultValue = "")
    protected String excludeTypes;

    /**
     * Scope to include. An Empty string indicates all scopes (default). The scopes being
     * interpreted are the scopes as Maven sees them, not as specified in the pom. In summary:
     * <ul>
     * <li><code>runtime</code> scope gives runtime and compile dependencies,</li>
     * <li><code>compile</code> scope gives compile, provided, and system dependencies,</li>
     * <li><code>test</code> (default) scope gives all dependencies,</li>
     * <li><code>provided</code> scope just gives provided dependencies,</li>
     * <li><code>system</code> scope just gives system dependencies.</li>
     * </ul>
     */
    @Parameter(property = "includeScope", defaultValue = "")
    protected String includeScope;

    /**
     * Scope to exclude. An Empty string indicates no scopes (default).
     */
    @Parameter(property = "excludeScope", defaultValue = "")
    protected String excludeScope;

    /**
     * Comma Separated list of Classifiers to include. Empty String indicates include everything
     * (default).
     */
    @Parameter(property = "includeClassifiers", defaultValue = "")
    protected String includeClassifiers;

    /**
     * Comma Separated list of Classifiers to exclude. Empty String indicates don't exclude anything
     * (default).
     */
    @Parameter(property = "excludeClassifiers", defaultValue = "")
    protected String excludeClassifiers;

    /**
     * Specify classifier to look for. Example: sources
     */
    @Parameter(property = "classifier", defaultValue = "")
    protected String classifier;

    /**
     * Specify type to look for when constructing artifact based on classifier. Example:
     * java-source,jar,war
     */
    @Parameter(property = "type", defaultValue = "")
    protected String type;

    /**
     * Comma separated list of Artifact names to exclude.
     */
    @Parameter(property = "excludeArtifactIds", defaultValue = "")
    protected String excludeArtifactIds;

    /**
     * Comma separated list of Artifact names to include. Empty String indicates include everything
     * (default).
     */
    @Parameter(property = "includeArtifactIds", defaultValue = "")
    protected String includeArtifactIds;

    /**
     * Comma separated list of GroupId Names to exclude.
     */
    @Parameter(property = "excludeGroupIds", defaultValue = "")
    protected String excludeGroupIds;

    /**
     * Comma separated list of GroupIds to include. Empty String indicates include everything
     * (default).
     */
    @Parameter(property = "includeGroupIds", defaultValue = "")
    protected String includeGroupIds;

    /**
     * Directory to store flag files
     */
    @Parameter(property = "markersDirectory", defaultValue = "${project.build.directory}/dependency-maven-plugin-markers")
    protected File markersDirectory;

    /**
     * Prepend the groupId during link.
     */
    @Parameter(property = "mdep.prependGroupId", defaultValue = "false")
    protected boolean prependGroupId = false;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private ArtifactHandlerManager artifactHandlerManager;

    /**
     * Retrieves dependencies, either direct only or all including transitive.
     *
     * @param stopOnFailure true to fail if resolution does not work or false not to fail.
     * @return A set of artifacts
     * @throws MojoExecutionException in case of errors.
     */
    protected Set<Artifact> getResolvedDependencies(boolean stopOnFailure)
            throws MojoExecutionException

    {
        final DependencyStatusSets status = getDependencySets(stopOnFailure);

        return status.getResolvedDependencies();
    }

    /**
     * @param stopOnFailure true/false.
     * @return {@link DependencyStatusSets}
     * @throws MojoExecutionException in case of an error.
     */
    protected DependencyStatusSets getDependencySets(boolean stopOnFailure)
            throws MojoExecutionException {
        return getDependencySets(stopOnFailure, false);
    }

    /**
     * Method creates filters and filters the projects dependencies. This method also transforms the
     * dependencies if classifier is set. The dependencies are filtered in least specific to most
     * specific order
     *
     * @param stopOnFailure true to fail if artifacts can't be resolved false otherwise.
     * @param includeParents <code>true</code> if parents should be included or not
     *            <code>false</code>.
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects
     *         dependencies
     * @throws MojoExecutionException in case of errors.
     */
    protected DependencyStatusSets getDependencySets(boolean stopOnFailure, boolean includeParents)
            throws MojoExecutionException {
        // add filters in well known order, least specific to most specific
        final FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter(new ProjectTransitivityFilter(getProject().getDependencyArtifacts(),
                this.excludeTransitive));

        filter.addFilter(new ScopeFilter(DependencyUtil.cleanToBeTokenizedString(this.includeScope),
                DependencyUtil.cleanToBeTokenizedString(this.excludeScope)));

        filter.addFilter(new TypeFilter(DependencyUtil.cleanToBeTokenizedString(this.includeTypes),
                DependencyUtil.cleanToBeTokenizedString(this.excludeTypes)));

        filter.addFilter(new ClassifierFilter(DependencyUtil.cleanToBeTokenizedString(this.includeClassifiers),
                DependencyUtil.cleanToBeTokenizedString(this.excludeClassifiers)));

        filter.addFilter(new GroupIdFilter(DependencyUtil.cleanToBeTokenizedString(this.includeGroupIds),
                DependencyUtil.cleanToBeTokenizedString(this.excludeGroupIds)));

        filter.addFilter(new ArtifactIdFilter(DependencyUtil.cleanToBeTokenizedString(this.includeArtifactIds),
                DependencyUtil.cleanToBeTokenizedString(this.excludeArtifactIds)));

        // start with all artifacts.
        Set<Artifact> artifacts = getProject().getArtifacts();

        if (includeParents) {
            // add dependencies parents
            for (final Artifact dep : new ArrayList<Artifact>(artifacts)) {
                addParentArtifacts(buildProjectFromArtifact(dep), artifacts);
            }

            // add current project parent
            addParentArtifacts(getProject(), artifacts);
        }

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
        } catch (final ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // transform artifacts if classifier is set
        DependencyStatusSets status;
        if (StringUtils.isNotEmpty(classifier)) {
            status = getClassifierTranslatedDependencies(artifacts, stopOnFailure);
        } else {
            status = filterMarkedDependencies(artifacts);
        }

        return status;
    }

    private MavenProject buildProjectFromArtifact(Artifact artifact)
            throws MojoExecutionException {
        try {
            return projectBuilder.build(artifact, session.getProjectBuildingRequest()).getProject();
        } catch (final ProjectBuildingException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void addParentArtifacts(MavenProject project, Set<Artifact> artifacts)
            throws MojoExecutionException {
        while (project.hasParent()) {
            project = project.getParent();

            if (artifacts.contains(project.getArtifact())) {
                // artifact already in the set
                break;
            }
            try {
                final ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

                final Artifact resolvedArtifact = artifactResolver
                        .resolveArtifact(buildingRequest, project.getArtifact()).getArtifact();

                artifacts.add(resolvedArtifact);
            } catch (final ArtifactResolverException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Transform artifacts
     *
     * @param artifacts set of artifacts {@link Artifact}.
     * @param stopOnFailure true/false.
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects
     *         dependencies
     * @throws MojoExecutionException in case of an error.
     */
    protected DependencyStatusSets getClassifierTranslatedDependencies(Set<Artifact> artifacts, boolean stopOnFailure)
            throws MojoExecutionException {
        final Set<Artifact> unResolvedArtifacts = new LinkedHashSet<Artifact>();
        Set<Artifact> resolvedArtifacts = artifacts;
        DependencyStatusSets status = new DependencyStatusSets();

        // possibly translate artifacts into a new set of artifacts based on the
        // classifier and type
        // if this did something, we need to resolve the new artifacts
        if (StringUtils.isNotEmpty(classifier)) {
            final ArtifactTranslator translator = new ClassifierTypeTranslator(artifactHandlerManager, this.classifier,
                    this.type);
            final Collection<ArtifactCoordinate> coordinates = translator.translate(artifacts, getLog());

            status = filterMarkedDependencies(artifacts);

            // the unskipped artifacts are in the resolved set.
            artifacts = status.getResolvedDependencies();

            // resolve the rest of the artifacts
            resolvedArtifacts = resolve(new LinkedHashSet<ArtifactCoordinate>(coordinates), stopOnFailure);

            // calculate the artifacts not resolved.
            unResolvedArtifacts.addAll(artifacts);
            unResolvedArtifacts.removeAll(resolvedArtifacts);
        }

        // return a bean of all 3 sets.
        status.setResolvedDependencies(resolvedArtifacts);
        status.setUnResolvedDependencies(unResolvedArtifacts);

        return status;
    }

    /**
     * Filter the marked dependencies
     *
     * @param artifacts The artifacts set {@link Artifact}.
     * @return status set {@link DependencyStatusSets}.
     * @throws MojoExecutionException in case of an error.
     */
    protected DependencyStatusSets filterMarkedDependencies(Set<Artifact> artifacts)
            throws MojoExecutionException {
        // remove files that have markers already
        final FilterArtifacts filter = new FilterArtifacts();
        filter.clearFilters();
        filter.addFilter(getMarkedArtifactFilter());

        Set<Artifact> unMarkedArtifacts;
        try {
            unMarkedArtifacts = filter.filter(artifacts);
        } catch (final ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // calculate the skipped artifacts
        final Set<Artifact> skippedArtifacts = new LinkedHashSet<Artifact>();
        skippedArtifacts.addAll(artifacts);
        skippedArtifacts.removeAll(unMarkedArtifacts);

        return new DependencyStatusSets(unMarkedArtifacts, null, skippedArtifacts);
    }

    /**
     * @param coordinates The set of artifact coordinates{@link ArtifactCoordinate}.
     * @param stopOnFailure <code>true</code> if we should fail with exception if an artifact
     *            couldn't be resolved <code>false</code> otherwise.
     * @return the resolved artifacts. {@link Artifact}.
     * @throws MojoExecutionException in case of error.
     */
    protected Set<Artifact> resolve(Set<ArtifactCoordinate> coordinates, boolean stopOnFailure)
            throws MojoExecutionException {
        final ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

        final Set<Artifact> resolvedArtifacts = new LinkedHashSet<Artifact>();
        for (final ArtifactCoordinate coordinate : coordinates) {
            try {
                final Artifact artifact = artifactResolver.resolveArtifact(buildingRequest, coordinate).getArtifact();
                resolvedArtifacts.add(artifact);
            } catch (final ArtifactResolverException ex) {
                // an error occurred during resolution, log it an continue
                getLog().debug("error resolving: " + coordinate);
                getLog().debug(ex);
                if (stopOnFailure) {
                    throw new MojoExecutionException("error resolving: " + coordinate, ex);
                }
            }
        }
        return resolvedArtifacts;
    }

    /**
     * @return Returns the markersDirectory.
     */
    public File getMarkersDirectory() {
        return this.markersDirectory;
    }

    /**
     * @param theMarkersDirectory The markersDirectory to set.
     */
    public void setMarkersDirectory(File theMarkersDirectory) {
        this.markersDirectory = theMarkersDirectory;
    }

    /**
     * @return true, if the groupId should be prepended to the filename.
     */
    public boolean isPrependGroupId() {
        return prependGroupId;
    }

    /**
     * @param prependGroupId - true if the groupId must be prepended during the link.
     */
    public void setPrependGroupId(boolean prependGroupId) {
        this.prependGroupId = prependGroupId;
    }

    protected final ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    protected final DependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    protected final RepositoryManager getRepositoryManager() {
        return repositoryManager;
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
     * @return Returns the useSubDirectoryPerArtifact.
     */
    public boolean isUseSubDirectoryPerArtifact() {
        return this.useSubDirectoryPerArtifact;
    }

    /**
     * @param theUseSubDirectoryPerArtifact The useSubDirectoryPerArtifact to set.
     */
    public void setUseSubDirectoryPerArtifact(boolean theUseSubDirectoryPerArtifact) {
        this.useSubDirectoryPerArtifact = theUseSubDirectoryPerArtifact;
    }

    /**
     * @return Returns the useSubDirectoryPerScope
     */
    public boolean isUseSubDirectoryPerScope() {
        return this.useSubDirectoryPerScope;
    }

    /**
     * @param theUseSubDirectoryPerScope The useSubDirectoryPerScope to set.
     */
    public void setUseSubDirectoryPerScope(boolean theUseSubDirectoryPerScope) {
        this.useSubDirectoryPerScope = theUseSubDirectoryPerScope;
    }

    /**
     * @return Returns the useSubDirectoryPerType.
     */
    public boolean isUseSubDirectoryPerType() {
        return this.useSubDirectoryPerType;
    }

    /**
     * @param theUseSubDirectoryPerType The useSubDirectoryPerType to set.
     */
    public void setUseSubDirectoryPerType(boolean theUseSubDirectoryPerType) {
        this.useSubDirectoryPerType = theUseSubDirectoryPerType;
    }

    public boolean isFailOnMissingClassifierArtifact() {
        return failOnMissingClassifierArtifact;
    }

    public void setFailOnMissingClassifierArtifact(boolean failOnMissingClassifierArtifact) {
        this.failOnMissingClassifierArtifact = failOnMissingClassifierArtifact;
    }

    public boolean isStripVersion() {
        return stripVersion;
    }

    public void setStripVersion(boolean stripVersion) {
        this.stripVersion = stripVersion;
    }

    /**
     * @return true, if dependencies must be planted in a repository layout
     */
    public boolean isUseRepositoryLayout() {
        return useRepositoryLayout;
    }

    /**
     * @param useRepositoryLayout - true if dependencies must be planted in a repository layout
     */
    public void setUseRepositoryLayout(boolean useRepositoryLayout) {
        this.useRepositoryLayout = useRepositoryLayout;
    }

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through calling
     * linkArtifact.
     *
     * @throws MojoExecutionException with a message if an error occurs.
     * @see #getDependencySets(boolean, boolean)
     * @see #linkArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    @Override
    protected void doExecute()
            throws MojoExecutionException {
        final DependencyStatusSets dss = getDependencySets(this.failOnMissingClassifierArtifact, addParentPoms);
        final Set<Artifact> artifacts = dss.getResolvedDependencies();

        if (!useRepositoryLayout) {
            for (final Artifact artifact : artifacts) {
                linkArtifact(artifact, isStripVersion(), this.prependGroupId, this.useBaseVersion,
                        this.stripClassifier);
            }
        } else {
            final ProjectBuildingRequest buildingRequest = getRepositoryManager().setLocalRepositoryBasedir(session.getProjectBuildingRequest(),
                    outputDirectory);

            for (final Artifact artifact : artifacts) {
                installArtifact(artifact, buildingRequest);
            }
        }

        final Set<Artifact> skippedArtifacts = dss.getSkippedDependencies();
        for (final Artifact artifact : skippedArtifacts) {
            getLog().info(artifact.getId() + " already exists in destination.");
        }

        if (isLinkPom() && !useRepositoryLayout) {
            linkPoms(getOutputDirectory(), artifacts, this.stripVersion);
            linkPoms(getOutputDirectory(), skippedArtifacts, this.stripVersion, this.stripClassifier); // Artifacts
                                                                                                       // that already
                                                                                                       // exist may
                                                                                                       // not yet have
                                                                                                       // poms
        }
    }

    /**
     * install the artifact and the corresponding pom if linkPoms=true
     *
     * @param artifact
     * @param targetRepository
     */
    private void installArtifact(Artifact artifact, ProjectBuildingRequest buildingRequest) {
        try {
            installer.install(buildingRequest, Collections.singletonList(artifact));
            installBaseSnapshot(artifact, buildingRequest);

            if (!"pom".equals(artifact.getType()) && isLinkPom()) {
                final Artifact pomArtifact = getResolvedPomArtifact(artifact);
                if (pomArtifact != null && pomArtifact.getFile() != null && pomArtifact.getFile().exists()) {
                    installer.install(buildingRequest, Collections.singletonList(pomArtifact));
                    installBaseSnapshot(pomArtifact, buildingRequest);
                }
            }
        } catch (final ArtifactInstallerException e) {
            getLog().warn("unable to install " + artifact, e);
        }
    }

    private void installBaseSnapshot(Artifact artifact, ProjectBuildingRequest buildingRequest)
            throws ArtifactInstallerException {
        if (artifact.isSnapshot() && !artifact.getBaseVersion().equals(artifact.getVersion())) {
            final String version = artifact.getVersion();
            try {
                artifact.setVersion(artifact.getBaseVersion());
                installer.install(buildingRequest, Collections.singletonList(artifact));
            } finally {
                artifact.setVersion(version);
            }
        }
    }

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also
     * checks if the classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact representing the object to be copied.
     * @param removeVersion specifies if the version should be removed from the file name when
     *            linking.
     * @param prependGroupId specifies if the groupId should be prepend to the file while linking.
     * @param useBaseVersion specifies if the baseVersion of the artifact should be used instead of
     *            the version.
     * @throws MojoExecutionException with a message if an error occurs.
     * @see #linkArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    protected void linkArtifact(Artifact artifact, boolean removeVersion, boolean prependGroupId,
            boolean useBaseVersion)
            throws MojoExecutionException {
        linkArtifact(artifact, removeVersion, prependGroupId, useBaseVersion, false);
    }

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also
     * checks if the classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact representing the object to be copied.
     * @param removeVersion specifies if the version should be removed from the file name when
     *            linking.
     * @param prependGroupId specifies if the groupId should be prepend to the file while linking.
     * @param useBaseVersion specifies if the baseVersion of the artifact should be used instead of
     *            the version.
     * @param removeClassifier specifies if the classifier should be removed from the file name when
     *            linking.
     * @throws MojoExecutionException with a message if an error occurs.
     * @see #linkFile(File, File)
     * @see DependencyUtil#getFormattedOutputDirectory(boolean, boolean, boolean, boolean, boolean,
     *      File, Artifact)
     */
    protected void linkArtifact(Artifact artifact, boolean removeVersion, boolean prependGroupId,
            boolean useBaseVersion, boolean removeClassifier)
            throws MojoExecutionException {

        final String destFileName = DependencyUtil.getFormattedFileName(artifact, removeVersion, prependGroupId,
                useBaseVersion, removeClassifier);

        File destDir;
        destDir = DependencyUtil.getFormattedOutputDirectory(useSubDirectoryPerScope, useSubDirectoryPerType,
                useSubDirectoryPerArtifact, useRepositoryLayout,
                stripVersion, outputDirectory, artifact);
        final File destFile = new File(destDir, destFileName);

        linkFile(artifact.getFile(), destFile);
    }

    /**
     * Link the pom files associated with the artifacts.
     *
     * @param destDir The destination directory {@link File}.
     * @param artifacts The artifacts {@link Artifact}.
     * @param removeVersion remove version or not.
     * @throws MojoExecutionException in case of errors.
     */
    public void linkPoms(File destDir, Set<Artifact> artifacts, boolean removeVersion)
            throws MojoExecutionException

    {
        linkPoms(destDir, artifacts, removeVersion, false);
    }

    /**
     * Link the pom files associated with the artifacts.
     *
     * @param destDir The destination directory {@link File}.
     * @param artifacts The artifacts {@link Artifact}.
     * @param removeVersion remove version or not.
     * @param removeClassifier remove the classifier or not.
     * @throws MojoExecutionException in case of errors.
     */
    public void linkPoms(File destDir, Set<Artifact> artifacts, boolean removeVersion, boolean removeClassifier)
            throws MojoExecutionException

    {
        for (final Artifact artifact : artifacts) {
            final Artifact pomArtifact = getResolvedPomArtifact(artifact);

            // Link the pom
            if (pomArtifact != null && pomArtifact.getFile() != null && pomArtifact.getFile().exists()) {
                final File pomDestFile = new File(destDir, DependencyUtil.getFormattedFileName(pomArtifact, removeVersion, prependGroupId,
                        useBaseVersion, removeClassifier));
                if (!pomDestFile.exists()) {
                    linkFile(pomArtifact.getFile(), pomDestFile);
                }
            }
        }
    }

    protected Artifact getResolvedPomArtifact(Artifact artifact) {
        final DefaultArtifactCoordinate coordinate = new DefaultArtifactCoordinate();
        coordinate.setGroupId(artifact.getGroupId());
        coordinate.setArtifactId(artifact.getArtifactId());
        coordinate.setVersion(artifact.getVersion());
        coordinate.setExtension("pom");

        Artifact pomArtifact = null;
        // Resolve the pom artifact using repos
        try {
            final ProjectBuildingRequest buildingRequest = newResolveArtifactProjectBuildingRequest();

            pomArtifact = getArtifactResolver().resolveArtifact(buildingRequest, coordinate).getArtifact();
        } catch (final ArtifactResolverException e) {
            getLog().info(e.getMessage());
        }
        return pomArtifact;
    }

    protected ArtifactsFilter getMarkedArtifactFilter() {
        return new DestFileFilter(this.overWriteReleases, this.overWriteSnapshots, this.overWriteIfNewer,
                this.useSubDirectoryPerArtifact, this.useSubDirectoryPerType,
                this.useSubDirectoryPerScope, this.useRepositoryLayout, this.stripVersion,
                this.prependGroupId, this.useBaseVersion, this.outputDirectory);
    }

    /**
     * @return true, if the pom of each artifact must be copied
     */
    public boolean isLinkPom() {
        return this.linkPom;
    }

    /**
     * @param linkPom - true if the pom of each artifact must be copied
     */
    public void setLinkPom(boolean linkPom) {
        this.linkPom = linkPom;
    }
}
