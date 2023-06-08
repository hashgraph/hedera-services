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

package com.hedera.node.app.spi.meta.bni;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActiveContractVerificationStrategyTest {
    private static final long ACTIVE_NUMBER = 1234L;
    private static final long SOME_OTHER_NUMBER = 2345L;
    private static final Bytes ACTIVE_ADDRESS = Bytes.fromHex("1234");
    private static final Bytes OTHER_ADDRESS = Bytes.fromHex("abcd");

    private ActiveContractVerificationStrategy subject =
            new ActiveContractVerificationStrategy(ACTIVE_NUMBER, ACTIVE_ADDRESS);

    @Test
    void validatesAsExpected() {
        final var activeIdKey = Key.newBuilder()
                .contractID(ContractID.newBuilder().contractNum(ACTIVE_NUMBER))
                .build();
        final var inactiveIdKey = Key.newBuilder()
                .contractID(ContractID.newBuilder().contractNum(SOME_OTHER_NUMBER))
                .build();
        final var activeAddressKey = Key.newBuilder()
                .contractID(ContractID.newBuilder().evmAddress(ACTIVE_ADDRESS))
                .build();
        final var inactiveAddressKey = Key.newBuilder()
                .contractID(ContractID.newBuilder().evmAddress(OTHER_ADDRESS))
                .build();
        final var cryptoKey = Key.newBuilder()
                .ed25519(Bytes.fromHex("1234567812345678123456781234567812345678123456781234567812345678"))
                .build();

        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(activeIdKey, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.VALID,
                subject.maybeVerifySignature(activeAddressKey, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(inactiveIdKey, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.INVALID,
                subject.maybeVerifySignature(inactiveAddressKey, VerificationStrategy.KeyRole.OTHER));
        assertEquals(
                VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION,
                subject.maybeVerifySignature(cryptoKey, VerificationStrategy.KeyRole.OTHER));
    }

    @Test
    void doesNotSupportAmendingTransfer() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.maybeAmendTransfer(CryptoTransferTransactionBody.DEFAULT, List.of()));
    }
}
