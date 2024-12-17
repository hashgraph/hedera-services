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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.allowance;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.allowance.GetAllowanceTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class GetAllowanceCallTest extends CallTestBase {
    private GetAllowanceCall subject;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Test
    void revertsWithFungibleToken() {
        subject = new GetAllowanceCall(
                addressIdConverter,
                gasCalculator,
                mockEnhancement(),
                NON_FUNGIBLE_TOKEN,
                OWNER_HEADLONG_ADDRESS,
                APPROVED_HEADLONG_ADDRESS,
                true,
                true);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_TOKEN_ID), result.getOutput());
    }

    @Test
    void revertsWithInvalidAccountId() {
        subject = new GetAllowanceCall(
                addressIdConverter,
                gasCalculator,
                mockEnhancement(),
                FUNGIBLE_TOKEN,
                OWNER_HEADLONG_ADDRESS,
                APPROVED_HEADLONG_ADDRESS,
                true,
                true);
        given(addressIdConverter.convert(OWNER_HEADLONG_ADDRESS)).willReturn(A_NEW_ACCOUNT_ID);
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID)).willReturn(null);
        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_ALLOWANCE_OWNER_ID), result.getOutput());
    }

    @Test
    void ERCGetAllowance() {
        subject = new GetAllowanceCall(
                addressIdConverter,
                gasCalculator,
                mockEnhancement(),
                FUNGIBLE_TOKEN,
                OWNER_HEADLONG_ADDRESS,
                APPROVED_HEADLONG_ADDRESS,
                true,
                false);
        given(addressIdConverter.convert(any())).willReturn(B_NEW_ACCOUNT_ID);
        given(nativeOperations.getAccount(B_NEW_ACCOUNT_ID)).willReturn(OPERATOR);

        final var result = subject.execute().fullResult().result();
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(GetAllowanceTranslator.ERC_GET_ALLOWANCE
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(0L))
                        .array()),
                result.getOutput());
    }

    @Test
    void getAllowance() {
        subject = new GetAllowanceCall(
                addressIdConverter,
                gasCalculator,
                mockEnhancement(),
                FUNGIBLE_TOKEN,
                OWNER_HEADLONG_ADDRESS,
                APPROVED_HEADLONG_ADDRESS,
                false,
                true);
        given(addressIdConverter.convert(any())).willReturn(B_NEW_ACCOUNT_ID);
        given(nativeOperations.getAccount(B_NEW_ACCOUNT_ID)).willReturn(OPERATOR);

        final var result = subject.execute().fullResult().result();
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(GetAllowanceTranslator.GET_ALLOWANCE
                        .getOutputs()
                        .encodeElements((long) SUCCESS.getNumber(), BigInteger.valueOf(0L))
                        .array()),
                result.getOutput());
    }
}
