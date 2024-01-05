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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.setapproval;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.APPROVED_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SetApprovalForAllDecoderTest {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    private final SetApprovalForAllDecoder subject = new SetApprovalForAllDecoder();

    @BeforeEach
    void setup() {
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.senderId()).willReturn(SENDER_ID);
        given(addressIdConverter.convert(APPROVED_HEADLONG_ADDRESS)).willReturn(APPROVED_ID);
    }

    @Test
    void setApprovalForAll_true_Works() {
        // given
        final var encodedInput = SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL
                .encodeCallWithArgs(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS, true)
                .array();
        given(attempt.inputBytes()).willReturn(encodedInput);

        // when
        final var transactionBody = subject.decodeSetApprovalForAll(attempt);

        // then
        verifyDecoding(true, transactionBody);
    }

    @Test
    void setApprovalForAllToken_false_Works() {
        // given
        final var encodedInput = SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL
                .encodeCallWithArgs(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, APPROVED_HEADLONG_ADDRESS, false)
                .array();
        given(attempt.inputBytes()).willReturn(encodedInput);

        // when
        final var transactionBody = subject.decodeSetApprovalForAll(attempt);

        // then
        verifyDecoding(false, transactionBody);
    }

    @Test
    void setApprovalForAllTokenERC_true_Works() {
        // given
        final var encodedInput = SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL
                .encodeCallWithArgs(APPROVED_HEADLONG_ADDRESS, true)
                .array();
        given(attempt.inputBytes()).willReturn(encodedInput);
        given(attempt.redirectTokenId()).willReturn(NON_FUNGIBLE_TOKEN_ID);

        // when
        final var transactionBody = subject.decodeSetApprovalForAllERC(attempt);

        // then
        verifyDecoding(true, transactionBody);
    }

    @Test
    void setApprovalForAllTokenERC_false_Works() {
        // given
        final var encodedInput = SetApprovalForAllTranslator.ERC721_SET_APPROVAL_FOR_ALL
                .encodeCallWithArgs(APPROVED_HEADLONG_ADDRESS, false)
                .array();
        given(attempt.inputBytes()).willReturn(encodedInput);
        given(attempt.redirectTokenId()).willReturn(NON_FUNGIBLE_TOKEN_ID);

        // when
        final var transactionBody = subject.decodeSetApprovalForAllERC(attempt);

        // then
        verifyDecoding(false, transactionBody);
    }

    private void verifyDecoding(final boolean expectedApproval, final TransactionBody body) {
        final var cryptoApproveAllowance = body.cryptoApproveAllowance();
        assertThat(cryptoApproveAllowance).isNotNull();

        final var nftAllowancesList = cryptoApproveAllowance.nftAllowances();
        assertThat(nftAllowancesList).isNotEmpty();

        assertThat(nftAllowancesList.get(0).tokenId()).isEqualTo(NON_FUNGIBLE_TOKEN_ID);
        assertThat(nftAllowancesList.get(0).spender()).isEqualTo(APPROVED_ID);
        assertThat(nftAllowancesList.get(0).owner()).isEqualTo(SENDER_ID);
        assertThat(nftAllowancesList.get(0).approvedForAll()).isEqualTo(expectedApproval);
    }
}
