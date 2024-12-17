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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.balanceof;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_FUNGIBLE_RELATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.revertOutputFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.balanceof.BalanceOfTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;

class BalanceOfCallTest extends CallTestBase {
    private final Address OWNER = asHeadlongAddress(EIP_1014_ADDRESS);
    private BalanceOfCall subject;

    @Test
    void revertsWithMissingToken() {
        subject = new BalanceOfCall(mockEnhancement(), gasCalculator, null, OWNER);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_TOKEN_ID), result.getOutput());
    }

    @Test
    void revertsWithMissingAccount() {
        subject = new BalanceOfCall(mockEnhancement(), gasCalculator, FUNGIBLE_TOKEN, OWNER);
        given(nativeOperations.resolveAlias(tuweniToPbjBytes(EIP_1014_ADDRESS))).willReturn(MISSING_ENTITY_NUMBER);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_ACCOUNT_ID), result.getOutput());
    }

    @Test
    void returnsZeroBalanceForAbsentRelationship() {
        subject = new BalanceOfCall(mockEnhancement(), gasCalculator, FUNGIBLE_TOKEN, OWNER);
        given(nativeOperations.resolveAlias(tuweniToPbjBytes(EIP_1014_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID.accountNumOrThrow());
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(ALIASED_SOMEBODY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(BalanceOfTranslator.BALANCE_OF
                        .getOutputs()
                        .encodeElements(BigInteger.ZERO)
                        .array()),
                result.getOutput());
    }

    @Test
    void returnsNominalBalanceForPresentRelationship() {
        subject = new BalanceOfCall(mockEnhancement(), gasCalculator, FUNGIBLE_TOKEN, OWNER);
        given(nativeOperations.resolveAlias(tuweniToPbjBytes(EIP_1014_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID.accountNumOrThrow());
        given(nativeOperations.getTokenRelation(A_NEW_ACCOUNT_ID.accountNumOrThrow(), FUNGIBLE_TOKEN_ID.tokenNum()))
                .willReturn(A_FUNGIBLE_RELATION);
        given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(ALIASED_SOMEBODY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(BalanceOfTranslator.BALANCE_OF
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(A_FUNGIBLE_RELATION.balance()))
                        .array()),
                result.getOutput());
    }
}
