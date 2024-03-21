/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.utility;

import com.swirlds.base.state.Startable;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;

/**
 * This class provides the abstraction of deleting a file, but actually moves the file to a temporary location in case
 * the file becomes useful later for debugging.
 * <p>
 * Data moved to the recycle bin persist in the temporary location for an unspecified amount of time, perhaps even no
 * time at all. Files in this temporary location may be deleted at any time without warning. It is never ok to write
 * code that depends on the existence of files in this temporary location. Files in this temporary location should be
 * treated as deleted by java code, and only used for debugging purposes.
 */
public interface RecycleBin extends Startable {

    /**
     * Remove a file or directory tree from its current location and move it to a temporary location.
     * <p>
     * Recycled data will persist in the temporary location for an unspecified amount of time, perhaps even no time at
     * all. Files in this temporary location may be deleted at any time without warning. It is never ok to write code
     * that depends on the existence of files in this temporary location. Files in this temporary location should be
     * treated as deleted by java code, and only used for debugging purposes.
     *
     * @param path the file or directory to recycle
     */
    void recycle(@NonNull Path path) throws IOException;
}
