/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.hederaaccountnumalias;

import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hederaaccountnumalias.HederaAccountNumAliasTranslator.HEDERA_ACCOUNT_NUM_ALIAS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_RECEIVER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.RECEIVER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hederaaccountnumalias.HederaAccountNumAliasCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class HederaAccountNumAliasCallTest extends CallTestBase {

    @Mock
    private HasCallAttempt attempt;

    @Mock
    private com.hedera.hapi.node.state.token.Account account;

    private HederaAccountNumAliasCall subject;

    @Test
    void successfulCall() {

        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());

        // Arrange to use an account that has an alias
        given(nativeOperations.resolveAlias(RECEIVER_ADDRESS))
                .willReturn(ALIASED_RECEIVER.accountId().accountNumOrThrow());
        given(nativeOperations.getAccount(RECEIVER_ID.accountNumOrThrow())).willReturn(ALIASED_RECEIVER);

        subject = new HederaAccountNumAliasCall(attempt, asHeadlongAddress(RECEIVER_ADDRESS.toByteArray()));
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(HEDERA_ACCOUNT_NUM_ALIAS
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                asHeadlongAddress(RECEIVER_ID.accountNumOrThrow()))
                        .array()),
                result.getOutput());
    }

    @Test
    void failsWhenNoAccountHasAlias() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());

        given(nativeOperations.resolveAlias(RECEIVER_ADDRESS))
                .willReturn(ALIASED_RECEIVER.accountId().accountNumOrThrow());
        given(nativeOperations.getAccount(RECEIVER_ID.accountNumOrThrow())).willReturn(null);

        subject = new HederaAccountNumAliasCall(attempt, asHeadlongAddress(RECEIVER_ADDRESS.toByteArray()));
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(HEDERA_ACCOUNT_NUM_ALIAS
                        .getOutputs()
                        .encodeElements((long) ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS.protoOrdinal(), ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }

    @Test
    void failsWhenAliasMapsToAccountButAccountDoesNotExistSomehow() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());

        given(nativeOperations.resolveAlias(RECEIVER_ADDRESS)).willReturn(MISSING_ENTITY_NUMBER);

        subject = new HederaAccountNumAliasCall(attempt, asHeadlongAddress(RECEIVER_ADDRESS.toByteArray()));
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(HEDERA_ACCOUNT_NUM_ALIAS
                        .getOutputs()
                        .encodeElements((long) ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS.protoOrdinal(), ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }
}
