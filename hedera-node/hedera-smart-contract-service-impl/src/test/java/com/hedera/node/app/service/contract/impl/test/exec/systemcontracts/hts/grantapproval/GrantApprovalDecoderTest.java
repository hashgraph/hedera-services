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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantapproval;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantApprovalDecoderTest {
    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HtsCallAttempt attempt;

    private final GrantApprovalDecoder subject = new GrantApprovalDecoder();

    @Test
    void grantApprovalWorks() {
        final var encoded = GrantApprovalTranslator.GRANT_APPROVAL
                .encodeCallWithArgs(
                        FUNGIBLE_TOKEN_HEADLONG_ADDRESS, UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS, BigInteger.valueOf(10L))
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS)).willReturn(UNAUTHORIZED_SPENDER_ID);
        final var body = subject.decodeGrantApproval(attempt);
        assertGrantApprovePresent(body, FUNGIBLE_TOKEN_ID, UNAUTHORIZED_SPENDER_ID, 10L);
    }

    @Test
    void grantApprovalNFT() {
        final var encoded = GrantApprovalTranslator.GRANT_APPROVAL_NFT
                .encodeCallWithArgs(
                        NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                        UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS,
                        BigInteger.valueOf(1L))
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(addressIdConverter.convert(UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS)).willReturn(UNAUTHORIZED_SPENDER_ID);
        final var body = subject.decodeGrantApprovalNFT(attempt);
        assertGrantApproveNFTPresent(body, NON_FUNGIBLE_TOKEN_ID, UNAUTHORIZED_SPENDER_ID, 1L);
    }

    private void assertGrantApprovePresent(
            final TransactionBody body, final TokenID tokenID, final AccountID accountID, final long amount) {
        final var approval = body.cryptoApproveAllowanceOrThrow();
        final var tokenAllowances = TokenAllowance.newBuilder()
                .tokenId(tokenID)
                .spender(accountID)
                .amount(amount)
                .build();
        assertEquals(approval.tokenAllowances(), List.of(tokenAllowances));
    }

    private void assertGrantApproveNFTPresent(
            final TransactionBody body, final TokenID tokenID, final AccountID accountID, final long serialNumber) {
        final var approval = body.cryptoApproveAllowanceOrThrow();
        final var nftAllowances = NftAllowance.newBuilder()
                .tokenId(tokenID)
                .spender(accountID)
                .serialNumbers(serialNumber)
                .build();
        assertEquals(approval.nftAllowances(), List.of(nftAllowances));
    }
}
