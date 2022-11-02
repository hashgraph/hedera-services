/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.evm.contracts.execution.BlockMetaSource.UNAVAILABLE_BLOCK_HASH;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.evm.contracts.execution.HederaBlockValues;
import com.hedera.services.state.logic.BlockManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InHandleBlockMetaSourceTest {
    @Mock private BlockManager blockManager;
    @Mock private TransactionContext txnCtx;

    private InHandleBlockMetaSource subject;

    @BeforeEach
    void setUp() {
        subject = new InHandleBlockMetaSource(blockManager, txnCtx);
    }

    @Test
    void delegatesComputeToManagerAtCurrentTime() {
        final var values = new HederaBlockValues(gasLimit, someBlockNo, then);

        given(txnCtx.consensusTime()).willReturn(then);
        given(blockManager.computeBlockValues(then, gasLimit)).willReturn(values);

        final var actual = subject.computeBlockValues(gasLimit);

        assertSame(values, actual);
    }

    @Test
    void delegatesBlockHashToManager() {
        given(blockManager.getBlockHash(someBlockNo)).willReturn(UNAVAILABLE_BLOCK_HASH);

        assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHash(someBlockNo));
    }

    private static final long gasLimit = 888L;
    private static final long someBlockNo = 123L;
    private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);
}
