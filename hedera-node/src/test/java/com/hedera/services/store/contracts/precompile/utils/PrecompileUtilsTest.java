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
package com.hedera.services.store.contracts.precompile.utils;

import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;

class PrecompileUtilsTest {

    @Test
    void addsContractCallResultToRecord() {
        final var childRecord = ExpirableTxnRecord.newBuilder();
        final var frame = mock(MessageFrame.class);
        given(frame.getValue()).willReturn(Wei.of(100L));
        given(frame.getInputData()).willReturn(Bytes.EMPTY);

        PrecompileUtils.addContractCallResultToRecord(
                1000L,
                childRecord,
                Bytes.ofUnsignedInt(10),
                Optional.of(ResponseCodeEnum.FAIL_INVALID),
                frame,
                true,
                true,
                ALTBN128_ADD);
        assertEquals("FAIL_INVALID", childRecord.getContractCallResult().getError());
        assertEquals(10, Bytes.wrap(childRecord.getContractCallResult().getResult()).toInt());
    }
}
