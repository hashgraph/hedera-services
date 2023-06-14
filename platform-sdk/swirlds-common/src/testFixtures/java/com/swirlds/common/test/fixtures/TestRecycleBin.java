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

package com.swirlds.common.test.fixtures;

import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import java.io.IOException;
import java.nio.file.Path;

/**
 * An implementation of a {@link RecycleBin} that immediately deletes recycled files. Technically speaking this
 * is not a violation of the recycle bin contract. Handy for test scenarios where you don't want to worry about
 * cleaning up the recycle bin.
 */
public class TestRecycleBin implements RecycleBin {

    private static final TestRecycleBin INSTANCE = new TestRecycleBin();

    /**
     * Get the singleton instance of this class.
     */
    public static TestRecycleBin getInstance() {
        return INSTANCE;
    }

    private TestRecycleBin() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void recycle(final Path path) throws IOException {
        FileUtils.deleteDirectory(path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() throws IOException {
        // Nothing to clear, files are deleted immediately
    }
}
