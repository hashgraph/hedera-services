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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.has.evmaddressalias;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias.EvmAddressAliasTranslator.EVM_ADDRESS_ALIAS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.hbarapprove.HbarApproveTranslator.HBAR_APPROVE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.CallAttemptHelpers.prepareHasAttemptWithSelector;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias.EvmAddressAliasCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.getevmaddressalias.EvmAddressAliasTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmAddressAliasTranslatorTest {
    @Mock
    private HasCallAttempt attempt;

    @Mock
    private SystemContractGasCalculator gasCalculator;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private VerificationStrategies verificationStrategies;

    @Mock
    private SignatureVerifier signatureVerifier;

    @Mock
    private HederaNativeOperations nativeOperations;

    private EvmAddressAliasTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new EvmAddressAliasTranslator();
    }

    @Test
    void matchesEvmAddressAlias() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHasAttemptWithSelector(
                EVM_ADDRESS_ALIAS,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                signatureVerifier,
                gasCalculator);
        assertTrue(subject.matches(attempt));
        assertEquals("0xdea3d081" /*copied from HIP-632*/, "0x" + EVM_ADDRESS_ALIAS.selectorHex());
    }

    @Test
    void failsOnInvalidSelector() {
        given(enhancement.nativeOperations()).willReturn(nativeOperations);
        attempt = prepareHasAttemptWithSelector(
                HBAR_APPROVE,
                subject,
                enhancement,
                addressIdConverter,
                verificationStrategies,
                signatureVerifier,
                gasCalculator);
        assertFalse(subject.matches(attempt));
    }

    @Test
    void callFromEvmAddressAliasTest() {
        final Bytes inputBytes =
                Bytes.wrapByteBuffer(EVM_ADDRESS_ALIAS.encodeCall(Tuple.of(APPROVED_HEADLONG_ADDRESS)));
        givenCommonForCall(inputBytes);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(EvmAddressAliasCall.class);
    }

    private void givenCommonForCall(Bytes inputBytes) {
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.systemContractGasCalculator()).willReturn(gasCalculator);
    }
}
