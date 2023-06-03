/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils;

import java.io.File;
import java.nio.file.Path;

public class ResourceLocator {

    public static final String RESOURCE_PATH_SEGMENT = "src/main/resource";
    public static final String TEST_CLIENTS_PREFIX = "hedera-node" + File.separator + "test-clients" + File.separator;

    public static Path relocatedIfNotPresentInWorkingDir(final Path p) {
        return relocatedIfNotPresentInWorkingDir(p.toFile()).toPath();
    }

    public static File relocatedIfNotPresentInWorkingDir(final File f) {
        return relocatedIfNotPresentWithCurrentPathPrefix(f, RESOURCE_PATH_SEGMENT, TEST_CLIENTS_PREFIX);
    }

    public static File relocatedIfNotPresentWithCurrentPathPrefix(
            final File f, final String firstSegmentToRelocate, final String newPathPrefix) {
        if (!f.exists()) {
            final var absPath = f.getAbsolutePath();
            final var idx = absPath.indexOf(firstSegmentToRelocate);
            if (idx == -1) {
                return f;
            }
            final var relocatedPath = newPathPrefix + absPath.substring(idx);
            return new File(relocatedPath);
        } else {
            return f;
        }
    }
}
