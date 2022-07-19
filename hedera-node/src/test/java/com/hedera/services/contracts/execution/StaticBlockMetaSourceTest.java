/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

import static com.hedera.services.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaSourceTest {
    @Mock private MerkleNetworkContext networkCtx;

    private StaticBlockMetaSource subject;

    @BeforeEach
    void setUp() {
        subject = new StaticBlockMetaSource(networkCtx);
    }

    @Test
    void blockValuesAreForCurrentBlockAfterSync() {
        given(networkCtx.getAlignmentBlockNo()).willReturn(someBlockNo);
        given(networkCtx.firstConsTimeOfCurrentBlock()).willReturn(then);

        final var ans = subject.computeBlockValues(gasLimit);

        assertEquals(gasLimit, ans.getGasLimit());
        assertEquals(someBlockNo, ans.getNumber());
        assertEquals(then.getEpochSecond(), ans.getTimestamp());
    }

    @Test
    void usesNowIfFirstConsTimeIsStillSomehowNull() {
        given(networkCtx.getAlignmentBlockNo()).willReturn(someBlockNo);

        final var ans = subject.computeBlockValues(gasLimit);

        assertEquals(gasLimit, ans.getGasLimit());
        assertEquals(someBlockNo, ans.getNumber());
        assertNotEquals(0, ans.getTimestamp());
    }

    @Test
    void alwaysDelegatesBlockHashLookup() {
        given(networkCtx.getBlockHashByNumber(someBlockNo)).willReturn(UNAVAILABLE_BLOCK_HASH);

        assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHash(someBlockNo));
    }

    private static final long gasLimit = 888L;
    private static final long someBlockNo = 123L;
    private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);
}
