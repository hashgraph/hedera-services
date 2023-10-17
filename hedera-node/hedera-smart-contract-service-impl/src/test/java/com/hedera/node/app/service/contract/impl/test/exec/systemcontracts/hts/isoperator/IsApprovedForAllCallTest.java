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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.isoperator;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OPERATOR;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.revertOutputFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall.IsApprovedForAllCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;

class IsApprovedForAllCallTest extends HtsCallTestBase {
    private final Address THE_OWNER = asHeadlongAddress(asEvmAddress(A_NEW_ACCOUNT_ID.accountNumOrThrow()));
    private final Address THE_OPERATOR = asHeadlongAddress(asEvmAddress(B_NEW_ACCOUNT_ID.accountNumOrThrow()));
    private IsApprovedForAllCall subject;

    @Test
    void revertsWithFungibleToken() {
        subject = new IsApprovedForAllCall(gasCalculator, mockEnhancement(), FUNGIBLE_TOKEN, THE_OWNER, THE_OPERATOR);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_TOKEN_ID), result.getOutput());
    }

    @Test
    void revertsWithMissingOwner() {
        subject =
                new IsApprovedForAllCall(gasCalculator, mockEnhancement(), NON_FUNGIBLE_TOKEN, THE_OWNER, THE_OPERATOR);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_ACCOUNT_ID), result.getOutput());
    }

    @Test
    void checksForPresentOwnerAndFindsApprovedOperator() {
        subject =
                new IsApprovedForAllCall(gasCalculator, mockEnhancement(), NON_FUNGIBLE_TOKEN, THE_OWNER, THE_OPERATOR);
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(SOMEBODY);
        given(nativeOperations.getAccount(B_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(OPERATOR);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        final boolean verdict = IsApprovedForAllCall.IS_APPROVED_FOR_ALL
                .getOutputs()
                .decode(result.getOutput().toArray())
                .get(0);
        assertTrue(verdict);
    }

    @Test
    void checksForPresentOwnerAndDetectsNoOperator() {
        subject = new IsApprovedForAllCall(gasCalculator, mockEnhancement(), NON_FUNGIBLE_TOKEN, THE_OWNER, THE_OWNER);
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(SOMEBODY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        final boolean verdict = IsApprovedForAllCall.IS_APPROVED_FOR_ALL
                .getOutputs()
                .decode(result.getOutput().toArray())
                .get(0);
        assertFalse(verdict);
    }

    @Test
    void returnsFalseForPresentOwnerAndMissingOperator() {
        subject =
                new IsApprovedForAllCall(gasCalculator, mockEnhancement(), NON_FUNGIBLE_TOKEN, THE_OWNER, THE_OPERATOR);
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(SOMEBODY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        final boolean verdict = IsApprovedForAllCall.IS_APPROVED_FOR_ALL
                .getOutputs()
                .decode(result.getOutput().toArray())
                .get(0);
        assertFalse(verdict);
    }
}
