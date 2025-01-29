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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantrevokekyc;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycTranslator;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantRevokeKycDecoderTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    private final GrantRevokeKycDecoder subject = new GrantRevokeKycDecoder();

    @Test
    void grantKycWorks() {
        final var encoded = GrantRevokeKycTranslator.GRANT_KYC
                .encodeCallWithArgs(FUNGIBLE_TOKEN_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS)
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);

        final var body = subject.decodeGrantKyc(attempt);
        assertGrantKycPresent(body, FUNGIBLE_TOKEN_ID, OWNER_ID);
    }

    @Test
    void revokeKycWorks() {
        final var encoded = GrantRevokeKycTranslator.REVOKE_KYC
                .encodeCallWithArgs(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, OWNER_HEADLONG_ADDRESS)
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        givenConvertible(OWNER_HEADLONG_ADDRESS, OWNER_ID);

        final var body = subject.decodeRevokeKyc(attempt);
        assertRevokeKycPresent(body, NON_FUNGIBLE_TOKEN_ID, OWNER_ID);
    }

    private void assertGrantKycPresent(
            @NonNull final TransactionBody body, @NonNull final TokenID tokenId, @NonNull AccountID accountId) {
        final var grantKyc = body.tokenGrantKycOrThrow();
        org.assertj.core.api.Assertions.assertThat(grantKyc.token()).isEqualTo(tokenId);
        org.assertj.core.api.Assertions.assertThat(grantKyc.account()).isEqualTo(accountId);
    }

    private void assertRevokeKycPresent(
            @NonNull final TransactionBody body, @NonNull final TokenID tokenId, @NonNull AccountID accountId) {
        final var grantKyc = body.tokenRevokeKycOrThrow();
        org.assertj.core.api.Assertions.assertThat(grantKyc.token()).isEqualTo(tokenId);
        org.assertj.core.api.Assertions.assertThat(grantKyc.account()).isEqualTo(accountId);
    }

    private void givenConvertible(@NonNull final Address address, @NonNull final AccountID id) {
        given(addressIdConverter.convert(address)).willReturn(id);
    }
}
