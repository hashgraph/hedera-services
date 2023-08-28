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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActiveContractVerificationStrategyTest {
    private static final long ACTIVE_NUMBER = 1234L;
    private static final long SOME_OTHER_NUMBER = 2345L;
    private static final Bytes ACTIVE_ADDRESS = Bytes.fromHex("1234");
    private static final Bytes OTHER_ADDRESS = Bytes.fromHex("abcd");

    private static final Key ACTIVE_ID_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(ACTIVE_NUMBER))
            .build();
    private static final Key DELEGATABLE_ACTIVE_ID_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().contractNum(ACTIVE_NUMBER))
            .build();
    private static final Key INACTIVE_ID_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(SOME_OTHER_NUMBER))
            .build();
    private static final Key DELEGATABLE_INACTIVE_ID_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().contractNum(SOME_OTHER_NUMBER))
            .build();
    private static final Key ACTIVE_ADDRESS_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().evmAddress(ACTIVE_ADDRESS))
            .build();
    private static final Key DELEGATABLE_ACTIVE_ADDRESS_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().evmAddress(ACTIVE_ADDRESS))
            .build();
    private static final Key INACTIVE_ADDRESS_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().evmAddress(OTHER_ADDRESS))
            .build();
    private static final Key DELEGATABLE_INACTIVE_ADDRESS_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().evmAddress(OTHER_ADDRESS))
            .build();
    private static final Key CRYPTO_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("1234567812345678123456781234567812345678123456781234567812345678"))
            .build();

    @Test
    void validatesKeysAsExpectedWhenDelegatePermissionNotRequired() {
        final var subject = new ActiveContractVerificationStrategy(ACTIVE_NUMBER, ACTIVE_ADDRESS, false);

        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(ACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(ACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(INACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(INACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(DELEGATABLE_ACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(DELEGATABLE_ACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(DELEGATABLE_INACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(DELEGATABLE_INACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION,
                subject.maybeVerifySignature(CRYPTO_KEY, VerificationStrategy.KeyRole.OTHER));
    }

    @Test
    void validatesKeysAsExpectedWhenDelegatePermissionRequired() {
        final var subject = new ActiveContractVerificationStrategy(ACTIVE_NUMBER, ACTIVE_ADDRESS, true);

        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(ACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(ACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(INACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(INACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(DELEGATABLE_ACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(DELEGATABLE_ACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(DELEGATABLE_INACTIVE_ID_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(DELEGATABLE_INACTIVE_ADDRESS_KEY, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION,
                subject.maybeVerifySignature(CRYPTO_KEY, VerificationStrategy.KeyRole.OTHER));
    }

    @Test
    void doesNotSupportAmendingTransfer() {
        final var subject = new ActiveContractVerificationStrategy(ACTIVE_NUMBER, ACTIVE_ADDRESS, true);

        final List<Long> noInvalidSigners = List.of();
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.maybeAmendTransfer(CryptoTransferTransactionBody.DEFAULT, noInvalidSigners));
    }
}
