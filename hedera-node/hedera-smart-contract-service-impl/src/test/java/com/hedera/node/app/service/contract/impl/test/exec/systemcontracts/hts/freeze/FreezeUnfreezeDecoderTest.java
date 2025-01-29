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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.freeze;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeTranslator;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreezeUnfreezeDecoderTest {
    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HtsCallAttempt attempt;

    private final FreezeUnfreezeDecoder subject = new FreezeUnfreezeDecoder();

    @Test
    void freezeWorks() {
        final var encoded = FreezeUnfreezeTranslator.FREEZE
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS)
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(OWNER_ID);
        final var body = subject.decodeFreeze(attempt);
        assertFreezePresent(body, FUNGIBLE_TOKEN_ID, OWNER_ID);
    }

    @Test
    void unfreezeWorks() {
        final var encoded = FreezeUnfreezeTranslator.UNFREEZE
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS)
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(any())).willReturn(OWNER_ID);
        final var body = subject.decodeUnfreeze(attempt);
        assertUnfreezePresent(body, FUNGIBLE_TOKEN_ID, OWNER_ID);
    }

    private void assertFreezePresent(
            @NonNull final TransactionBody body, @NonNull final TokenID tokenId, @NonNull final AccountID accountID) {
        final var freeze = body.tokenFreezeOrThrow();
        assertEquals(freeze.token(), tokenId);
        assertEquals(freeze.account(), accountID);
    }

    private void assertUnfreezePresent(
            @NonNull final TransactionBody body, @NonNull final TokenID tokenId, @NonNull final AccountID accountID) {
        final var unfreeze = body.tokenUnfreezeOrThrow();
        assertEquals(unfreeze.token(), tokenId);
        assertEquals(unfreeze.account(), accountID);
    }
}
