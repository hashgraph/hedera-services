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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.isauthorizedraw;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ACCOUNT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ACCOUNT_NUM;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.messageHash;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.revertOutputFor;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.signature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.isauthorizedraw.IsAuthorizedRawCall.SignatureType;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

class IsAuthorizedRawCallTest extends CallTestBase {
    private IsAuthorizedRawCall subject;

    @Mock
    private HasCallAttempt attempt;

    private final CustomGasCalculator customGasCalculator = new CustomGasCalculator();

    @BeforeEach
    void setup() {
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
        given(attempt.enhancement()).willReturn(mockEnhancement());
        lenient().when(frame.getRemainingGas()).thenReturn(10_000_000L);
    }

    @Test
    void revertsWithNoAccountAtAddress() {
        given(nativeOperations.resolveAlias(any())).willReturn(MISSING_ENTITY_NUMBER);

        subject = getSubject(APPROVED_HEADLONG_ADDRESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_ACCOUNT_ID), result.getOutput());
    }

    @Test
    void revertsWhenEcdsaIsNotEvmAddress() {
        given(nativeOperations.getAccount(OWNER_ACCOUNT_NUM)).willReturn(OWNER_ACCOUNT);

        subject = getSubject(asHeadlongAddress(OWNER_ACCOUNT_NUM));

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_ACCOUNT_ID), result.getOutput());
    }

    @Test
    void notValidAccountIfNegative() {
        final var result = getSubject(mock(Address.class)).isValidAccount(-25L, mock(SignatureType.class));
        assertFalse(result);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 10L, 100L, 1_000_000_000_000L, Long.MAX_VALUE})
    void anyNonNegativeAccountValidIfED(final long account) {
        final var result = getSubject(mock(Address.class)).isValidAccount(account, SignatureType.ED);
        assertTrue(result);
    }

    @ParameterizedTest
    @CsvSource({})
    void noLongZeroAddressesValidIfEC(final long account, final boolean expected) {}

    @ParameterizedTest
    @CsvSource({"0,27", "1,28", "27,27", "28,28", "45,27", "46,28", "18,"})
    void reverseVTest(final byte fromV, final Byte expectedV) {
        subject = getSubject(APPROVED_HEADLONG_ADDRESS);
        final var v = subject.reverseV(fromV);
        if (expectedV == null) assertTrue(v.isEmpty());
        else assertEquals(expectedV, v.get());
    }

    @NonNull
    IsAuthorizedRawCall getSubject(@NonNull final Address address) {
        return new IsAuthorizedRawCall(attempt, address, messageHash, signature, customGasCalculator);
    }
}
