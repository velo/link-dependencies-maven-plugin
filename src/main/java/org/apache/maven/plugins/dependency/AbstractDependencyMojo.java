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
package org.apache.maven.plugins.dependency;

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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.util.List;

import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DependencySilentLog;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public abstract class AbstractDependencyMojo
        extends AbstractMojo {

    /**
     * <p>
     * will use the jvm chmod, this is available for user and all level group level will be ignored
     * </p>
     */
    @Parameter(property = "dependency.useJvmChmod", defaultValue = "true")
    private boolean useJvmChmod = true;

    /**
     * ignore to set file permissions when unpacking a dependency
     */
    @Parameter(property = "dependency.ignorePermissions", defaultValue = "false")
    private boolean ignorePermissions;

    /**
     * POM
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Remote repositories which will be searched for artifacts.
     */
    @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true, required = true)
    private List<ArtifactRepository> remoteRepositories;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter(defaultValue = "${reactorProjects}", readonly = true)
    protected List<MavenProject> reactorProjects;

    /**
     * The Maven session
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * If the plugin should be silent.
     */
    @Parameter(property = "silent", defaultValue = "false")
    private boolean silent;

    /**
     * Output absolute filename for resolved artifacts
     */
    @Parameter(property = "outputAbsoluteArtifactFilename", defaultValue = "false")
    protected boolean outputAbsoluteArtifactFilename;

    /**
     * Skip plugin execution completely.
     */
    @Parameter(property = "mdep.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Fallback action when it's not possible to create a hardlink
     */
    @Parameter(property = "mdep.fallback", defaultValue = "warn_and_copy")
    private InvalidCrossDeviceLinkFallback fallbackAction;

    // Mojo methods -----------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute()
            throws MojoExecutionException, MojoFailureException {
        if (isSkip()) {
            getLog().info("Skipping plugin execution");
            return;
        }

        doExecute();
    }

    protected abstract void doExecute()
            throws MojoExecutionException, MojoFailureException;

    /**
     * Does the actual link of the file and logging.
     *
     * @param artifact represents the file to link.
     * @param destFile file name of destination file.
     * @throws MojoExecutionException with a message if an error occurs.
     */
    protected void linkFile(File artifact, File destFile)
            throws MojoExecutionException {
        try {
            getLog().info("Linking "
                    + (this.outputAbsoluteArtifactFilename ? artifact.getAbsolutePath() : artifact.getName()) + " to "
                    + destFile);

            if (!artifact.isFile()) {
                // usual case is a future jar packaging, but there are special cases: classifier and
                // other packaging
                throw new MojoExecutionException("Artifact has not been packaged yet. When used on reactor artifact, "
                        + "link should be executed after packaging: see MDEP-187.");
            }

            if (destFile.exists()) {
                destFile.delete();
            } else {
                destFile.getParentFile().mkdirs();
            }

            try {
                // reverse order target, source
                Files.createLink(destFile.toPath(), artifact.toPath());
            } catch (final FileSystemException e) {
                fallbackAction.fallback(getLog(), artifact.toPath(), destFile.toPath(), e);
            }
        } catch (final IOException e) {
            throw new MojoExecutionException("Error linking artifact from " + artifact + " to " + destFile, e);
        }
    }

    /**
     * @return Returns a new ProjectBuildingRequest populated from the current session and the current project remote
     *         repositories, used to resolve artifacts.
     */
    public ProjectBuildingRequest newResolveArtifactProjectBuildingRequest() {
        final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());

        buildingRequest.setRemoteRepositories(remoteRepositories);

        return buildingRequest;
    }

    /**
     * @return Returns the project.
     */
    public MavenProject getProject() {
        return this.project;
    }

    public boolean isUseJvmChmod() {
        return useJvmChmod;
    }

    public void setUseJvmChmod(boolean useJvmChmod) {
        this.useJvmChmod = useJvmChmod;
    }

    public boolean isSkip() {
        return skip;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    protected final boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
        if (silent) {
            setLog(new DependencySilentLog());
        }
    }

}
