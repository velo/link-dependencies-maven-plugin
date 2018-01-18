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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import org.apache.maven.plugins.dependency.TestSkip.CapturingLog;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;

public class InvalidCrossDeviceLinkFallbackTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private CapturingLog log;
    private FileSystemException error;
    private Path sourceFile;
    private Path destFile;

    @Before
    public void runAction() throws IOException {
        log = new CapturingLog();
        error = new FileSystemException("a", "b", "This is a test");

        sourceFile = tempDir.newFile().toPath();
        destFile = tempDir.newFile().toPath();
        Files.deleteIfExists(destFile);
    }

    @Test
    public void logOnly() throws Exception {
        InvalidCrossDeviceLinkFallback.log_only.fallback(
                log,
                sourceFile,
                destFile,
                error);

        final String logText = log.getContent();
        assertThat(logText, containsString("Unable create hardlink"));
        assertThat(logText, containsString("This is a test"));
    }

    @Test
    public void copy() throws Exception {
        InvalidCrossDeviceLinkFallback.copy.fallback(
                log,
                sourceFile,
                destFile,
                error);

        assertThat(log.getContent().length(), equalTo(0));
        assertThat(Files.isRegularFile(destFile), equalTo(true));
        assertThat(Files.isSameFile(sourceFile, destFile), equalTo(false));
    }

    @Test
    public void symlink() throws Exception {
        InvalidCrossDeviceLinkFallback.symlink.fallback(
                log,
                sourceFile,
                destFile,
                error);

        assertThat(log.getContent().length(), equalTo(0));
        assertThat(Files.isRegularFile(destFile), equalTo(true));
        assertThat(Files.isSameFile(sourceFile, destFile), equalTo(true));
    }

    @Test
    public void warnAndCopy() throws Exception {
        InvalidCrossDeviceLinkFallback.warn_and_copy.fallback(
                log,
                sourceFile,
                destFile,
                error);

        final String logText = log.getContent();
        assertThat(logText, containsString("Unable create hardlink"));
        assertThat(logText, containsString("creating a copy"));

        assertThat(Files.isRegularFile(destFile), equalTo(true));
        assertThat(Files.isSameFile(sourceFile, destFile), equalTo(false));
    }

    @Test
    public void warnAndSymlink() throws Exception {
        InvalidCrossDeviceLinkFallback.warn_and_symlink.fallback(
                log,
                sourceFile,
                destFile,
                error);

        final String logText = log.getContent();
        assertThat(logText, containsString("Unable create hardlink"));
        assertThat(logText, containsString("creating a symlink"));
        assertThat(Files.isRegularFile(destFile), equalTo(true));
        assertThat(Files.isSameFile(sourceFile, destFile), equalTo(true));
    }

    @Test
    public void throwError() throws Exception {
        thrown.expect(FileSystemException.class);
        thrown.expectMessage(error.getMessage());

        InvalidCrossDeviceLinkFallback.throw_error.fallback(
                log,
                sourceFile,
                destFile,
                error);
    }

}
