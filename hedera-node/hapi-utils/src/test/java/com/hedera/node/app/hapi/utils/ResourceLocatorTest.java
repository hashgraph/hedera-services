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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class ResourceLocatorTest {

    @Test
    void doesNotRelocateIfSegmentMissing() {
        final var missingLoc = "test/resources/vectors/genesis.pem";

        final var relocated = ResourceLocator.relocatedIfNotPresentWithCurrentPathPrefix(
                new File(missingLoc), "NOPE", "src" + File.separator);

        assertEquals(missingLoc, relocated.getPath());
    }

    @Test
    void triesToRelocateObviouslyMissingPath() {
        final var notPresent = Paths.get("nowhere/src/main/resources/nothing.txt");

        final var expected = Paths.get("hedera-node/test-clients/src/main/resources/nothing.txt");

        final var actual = ResourceLocator.relocatedIfNotPresentInWorkingDir(notPresent);

        assertEquals(expected, actual);
    }
}
