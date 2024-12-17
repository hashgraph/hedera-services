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

package com.hedera.node.app.service.token.impl.test.handlers.transfer.customfees;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase.withFixedFee;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TransferUtil.asNftTransferList;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AssessmentResult;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CustomFixedFeeAssessorTest {

    private CustomFixedFeeAssessor subject;

    private AssessmentResult result;

    private final AccountID payer = asAccount(4001);
    private final AccountID otherCollector = asAccount(1001);
    private final AccountID funding = asAccount(98);
    private final TokenID firstFungibleTokenId = asToken(3000);
    private final AccountID minter = asAccount(6000);
    private final TokenID nonFungibleTokenId = asToken(70000);
    private final TokenTransferList nftTransferList = asNftTransferList(nonFungibleTokenId, payer, funding, 1);
    final FixedFee htsFixedFee = FixedFee.newBuilder()
            .denominatingTokenId(firstFungibleTokenId)
            .amount(1)
            .build();
    final FixedFee hbarFixedFee = FixedFee.newBuilder().amount(1).build();
    private final AssessedCustomFee htsAssessedFee = AssessedCustomFee.newBuilder()
            .amount(1)
            .tokenId(firstFungibleTokenId)
            .effectivePayerAccountId(payer)
            .feeCollectorAccountId(otherCollector)
            .build();
    private final AssessedCustomFee hbarAssessedFee = AssessedCustomFee.newBuilder()
            .amount(1)
            .effectivePayerAccountId(payer)
            .feeCollectorAccountId(otherCollector)
            .build();

    @BeforeEach
    void setUp() {
        subject = new CustomFixedFeeAssessor();
    }

    @Test
    void delegatesToHbarWhenDenomIsNull() {
        result = new AssessmentResult(List.of(nftTransferList), List.of());
        final var hbarFee = withFixedFee(hbarFixedFee, otherCollector, false);
        final var feeMeta = withCustomToken(List.of(hbarFee), TokenType.FUNGIBLE_COMMON);

        subject.assessFixedFees(feeMeta, payer, result);
        assertThat(result.getAssessedCustomFees()).isNotEmpty();
        assertThat(result.getAssessedCustomFees()).contains(hbarAssessedFee);
    }

    @Test
    void delegatesToHtsWhenDenomIsNonNull() {
        result = new AssessmentResult(List.of(nftTransferList), List.of());
        final var hbarFee = withFixedFee(htsFixedFee, otherCollector, false);
        final var feeMeta = withCustomToken(List.of(hbarFee), TokenType.FUNGIBLE_COMMON);

        subject.assessFixedFees(feeMeta, payer, result);
        assertThat(result.getAssessedCustomFees()).isNotEmpty();
        assertThat(result.getAssessedCustomFees()).contains(htsAssessedFee);
    }

    @Test
    void fixedCustomFeeExemptIsOk() {
        result = new AssessmentResult(List.of(nftTransferList), List.of());
        final var hbarFee = withFixedFee(htsFixedFee, payer, false);
        final var feeMeta = withCustomToken(List.of(hbarFee), TokenType.FUNGIBLE_COMMON);

        subject.assessFixedFees(feeMeta, payer, result);
        assertThat(result.getAssessedCustomFees()).isEmpty();
    }

    @Test
    void exemptsAssessmentWhenSenderSameAsCollector() {
        result = new AssessmentResult(List.of(nftTransferList), List.of());
        final var hbarFee = withFixedFee(htsFixedFee, payer, false);
        final var feeMeta = withCustomToken(List.of(hbarFee), TokenType.FUNGIBLE_COMMON);

        subject.assessFixedFees(feeMeta, payer, result);
        assertThat(result.getAssessedCustomFees()).isEmpty();
    }

    @Test
    void ignoresIfPayerExempt() {
        result = new AssessmentResult(List.of(nftTransferList), List.of());
        final var hbarFee = withFixedFee(htsFixedFee, payer, false);
        final var feeMeta = withCustomToken(List.of(hbarFee), TokenType.FUNGIBLE_COMMON);

        subject.assessFixedFee(feeMeta, payer, hbarFee, result);
        assertThat(result.getAssessedCustomFees()).isEmpty();
    }

    public Token withCustomToken(List<CustomFee> customFees, TokenType tokenType) {
        return Token.newBuilder()
                .customFees(customFees)
                .tokenId(firstFungibleTokenId)
                .tokenType(tokenType)
                .treasuryAccountId(minter)
                .build();
    }
}
