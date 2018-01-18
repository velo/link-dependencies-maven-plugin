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

import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.file.*;

public enum InvalidCrossDeviceLinkFallback {

    log_only {
        @Override
        void fallback(Log log, Path source, Path target, FileSystemException e) {
            log.error(String.format(
                    "Unable create hardlink of %s at %s due to %s",
                    source,
                    target,
                    e.getMessage()));
            log.debug(e);
        }
    },
    copy {
        @Override
        void fallback(Log log, Path source, Path target, FileSystemException e) throws IOException {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    },
    warn_and_copy {
        @Override
        void fallback(Log log, Path source, Path target, FileSystemException e) throws IOException {
            log.warn(String.format(
                    "Unable create hardlink of %s at %s creating a copy",
                    source,
                    target));
            log.debug(e);

            copy.fallback(log, source, target, e);
        }
    },
    symlink {
        @Override
        void fallback(Log log, Path source, Path target, FileSystemException e) throws IOException {
            // reverse order target, source
            Files.createSymbolicLink(target, source);
        }
    },
    warn_and_symlink {
        @Override
        void fallback(Log log, Path source, Path target, FileSystemException e) throws IOException {
            log.warn(String.format(
                    "Unable create hardlink of %s at %s creating a symlink instead",
                    source,
                    target));
            log.debug(e);

            symlink.fallback(log, source, target, e);
        }
    },
    throw_error {
        @Override
        void fallback(Log log, Path source, Path target, FileSystemException e) throws IOException {
            throw e;
        }
    };

    abstract void fallback(Log log, Path source, Path target, FileSystemException e) throws IOException;

}
