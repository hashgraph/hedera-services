/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.filesystem.manager;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Responsible for organizing and managing access to the file system.
 */
public interface FileSystemManager {

    /**
     * Resolve a path relative to the root directory of the file system manager.
     *
     * @param relativePath the path to resolve against the root directory
     * @return the resolved path
     * @throws IllegalArgumentException if the path cannot be relative to or scape the root directory
     */
    @NonNull
    Path resolve(@NonNull Path relativePath);

    /**
     * Creates a temporary directory relative to the root directory of the file system manager. Implementations can
     * choose to use an index to assure the file will be unique inside the directory
     *
     * @param name the name to use as suffix for the directory
     * @return the resolved path
     * @throws IllegalArgumentException if the path cannot be relative to or scape the root directory
     */
    @NonNull
    Path createTemporaryPath(@NonNull String name);

    /**
     * deletes the file associated to the path if relative to the root directory of the file system manager.
     *
     * @param relativePath the relative path to recycle
     * @throws IllegalArgumentException if the path cannot be relative to or scape the root directory
     */
    void recycle(@NonNull Path relativePath) throws IOException;
}
