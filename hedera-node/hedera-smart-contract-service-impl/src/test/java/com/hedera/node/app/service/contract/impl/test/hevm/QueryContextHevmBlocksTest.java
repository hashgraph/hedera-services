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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks.UNAVAILABLE_BLOCK_HASH;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETERNAL_NOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.contract.impl.hevm.QueryContextHevmBlocks;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.QueryContext;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryContextHevmBlocksTest {
    @Mock
    private QueryContext context;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    private QueryContextHevmBlocks subject;

    @BeforeEach
    void setUp() {
        subject = new QueryContextHevmBlocks(context);
        given(context.blockRecordInfo()).willReturn(blockRecordInfo);
    }

    @Test
    void blockHashDelegates() {
        given(blockRecordInfo.blockHashByBlockNumber(123L)).willReturn(ConversionUtils.tuweniToPbjBytes(Hash.EMPTY));
        assertEquals(Hash.EMPTY, subject.blockHashOf(123L));
    }

    @Test
    void returnsUnavailableHashIfNecessary() {
        assertSame(UNAVAILABLE_BLOCK_HASH, subject.blockHashOf(123L));
    }

    @Test
    void blockValuesHasExpectedValues() {
        final var now = new Timestamp(1_234_567L, 890);
        given(blockRecordInfo.blockNo()).willReturn(123L);
        given(blockRecordInfo.blockTimestamp()).willReturn(now);
        given(context.blockRecordInfo()).willReturn(blockRecordInfo);

        final var blockValues = subject.blockValuesOf(456L);

        assertEquals(456L, blockValues.getGasLimit());
        assertEquals(123L, blockValues.getNumber());
        assertEquals(ETERNAL_NOW.getEpochSecond(), blockValues.getTimestamp());
        assertEquals(Optional.of(Wei.ZERO), blockValues.getBaseFee());
        assertSame(Bytes.EMPTY, blockValues.getDifficultyBytes());
    }
}
