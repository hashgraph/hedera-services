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

package com.hedera.node.app.service.network.impl.test;

import static com.hedera.node.app.service.network.impl.NetworkServiceImpl.RUNNING_HASHES_KEY;
import static com.hedera.node.app.spi.fixtures.Utils.randomUtf8Bytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.network.impl.ReadableRunningHashLeafStoreImpl;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadableRunningHashLeafStoreImplTest {
    @Mock
    private ReadableStates states;

    @Mock
    private ReadableSingletonState runningHashState;

    @Mock
    private RecordsRunningHashLeaf recordsRunningHashLeaf;

    private ReadableRunningHashLeafStoreImpl subject;

    @BeforeEach
    void setup() {
        given(states.getSingleton(RUNNING_HASHES_KEY)).willReturn(runningHashState);
        given(runningHashState.get()).willReturn(new RecordsRunningHashLeaf());

        subject = new ReadableRunningHashLeafStoreImpl(states);
    }

    @Test
    void getsNullNMinusThreeRunningHash() {
        assertNull(subject.getNMinusThreeRunningHash());
    }

    @Test
    void getsHashesAsExpected() {
        final var hash = new RunningHash(new Hash(randomUtf8Bytes(48)));

        given(runningHashState.get()).willReturn(recordsRunningHashLeaf);
        given(recordsRunningHashLeaf.getNMinus3RunningHash()).willReturn(hash);

        assertEquals(hash, subject.getNMinusThreeRunningHash());
    }
}
