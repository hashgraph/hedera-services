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

package com.hedera.node.app.records.impl.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunningHashesTranslatorTest {

    private com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf subject;

    @BeforeEach
    void setup() {
        RunningHash runningHash =
                new RunningHash(new Hash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()));
        subject = new com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf(runningHash);
    }

    @Test
    void createBlockInfoFromMerkleNetworkContext() throws IOException {

        final RunningHashes runningHashes = RunningHashesTranslator.runningHashesFromRecordsRunningHashLeaf(subject);

        assertTrue(runningHashes.runningHash().length() > 0);
        assertEquals(
                runningHashes.runningHash(), Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()));
        assertTrue(runningHashes.nMinus1RunningHash().length() > 0);
        assertEquals(runningHashes.nMinus1RunningHash(), Bytes.wrap(new byte[48]));
        assertTrue(runningHashes.nMinus2RunningHash().length() > 0);
        assertEquals(runningHashes.nMinus2RunningHash(), Bytes.wrap(new byte[48]));
        assertTrue(runningHashes.nMinus3RunningHash().length() > 0);
        assertEquals(runningHashes.nMinus3RunningHash(), Bytes.wrap(new byte[48]));
    }
}
