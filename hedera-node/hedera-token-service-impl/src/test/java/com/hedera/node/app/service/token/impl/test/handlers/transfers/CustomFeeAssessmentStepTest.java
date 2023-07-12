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

package com.hedera.node.app.service.token.impl.test.handlers.transfers;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.CustomFeeAssessmentStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.records.CryptoTransferRecordBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeeAssessmentStepTest extends StepsBase {
    @Mock
    private CryptoTransferRecordBuilder recordBuilder;

    private TransferContextImpl transferContext;
    private CustomFeeAssessmentStep subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();

        givenStoresAndConfig(handleContext);
        givenTxn();
        given(handleContext.body()).willReturn(txn);
        givenConditions();

        transferContext = new TransferContextImpl(handleContext);
        ensureAliasesStep = new EnsureAliasesStep(body);
        replaceAliasesWithIDsInOp = new ReplaceAliasesWithIDsInOp();
        associateTokenRecepientsStep = new AssociateTokenRecipientsStep(body);

        final var replacedOp = getReplacedOp();
        subject = new CustomFeeAssessmentStep(replacedOp, transferContext);
        given(handleContext.recordBuilder(CryptoTransferRecordBuilder.class)).willReturn(recordBuilder);
    }

    @Test
    @DisplayName("Transfer which triggers hbar fixed fee, fractional fee and royalty fee with fall back")
    void hbarFixedFeeAndRoyaltyFeeWithFallback() {
        // fungible token transfer with custom hbar fixed fee
        // and a fractional fee with netOfTransfers false
        // NFT transfer with royalty fee (fraction 1/2), fallbackFee with fixed hbar fee
        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);

        givenTxn();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(2);

        final var givenOp = listOfOps.get(0);
        final var level1Op = listOfOps.get(1);

        final var expectedLevel1Trasfers = Map.of(
                feeCollectorId,
                2000L,
                tokensReceiver,
                -1000L, // royalty fee
                ownerId,
                -1000L); // fixed fee
        final var expectedGivenOpTokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        feeCollectorId, 10L, // fractional fee
                        tokensReceiver, 990L,
                        ownerId, -1000L));
        final var expectedGivenOpHbarTransfers = Map.of(hbarsReceiver, 1000L, ownerId, -1000L);

        assertThatTransfersContains(level1Op.transfers().accountAmountsOrElse(emptyList()), expectedLevel1Trasfers);
        assertThatTransferListContains(givenOp.tokenTransfers(), expectedGivenOpTokenTransfers);
        assertThatTransfersContains(
                givenOp.transfers().accountAmountsOrElse(emptyList()), expectedGivenOpHbarTransfers);
    }

    @Test
    @DisplayName("Transfer which triggers hts fixed fee that is self denominated, fractional fee "
            + "and royalty fee with fall back that is hts fixed fee")
    void htsFixedFeeAndRoyaltyFeeWithFallbackSelfDenomination() {
        // fungible token transfer with custom hts fixed fee
        // and a fractional fee with netOfTransfers false
        // NFT transfer with royalty fee (fraction 1/2), fallbackFee with fixed hts fee
        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);
        final var customfees = List.of(withFixedFee(htsFixedFee));
        writableTokenStore.put(
                fungibleToken.copyBuilder().customFees(customfees).build());
        writableTokenStore.put(nonFungibleToken
                .copyBuilder()
                .customFees(List.of(withRoyaltyFee(
                        royaltyFee.copyBuilder().fallbackFee(htsFixedFee).build())))
                .build());
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);

        givenTxn();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(2);

        final var givenOp = listOfOps.get(0);
        final var level1Op = listOfOps.get(1);

        final var expectedLevel1TokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(feeCollectorId, 20L, tokensReceiver, -20L)); // First 10 is for fallback royalty fee from level 0
        // second 10 is for next assessment of hts fee, since sef denominated will be modified in existing txn

        final var expectedGivenOpTokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        tokensReceiver, 1000L,
                        ownerId, -1010L,
                        feeCollectorId, 10L)); // hts self denominated fee will be adjusted in given txn
        final var expectedGivenOpHbarTransfers = Map.of(hbarsReceiver, 1000L, ownerId, -1000L);

        assertThatTransferListContains(level1Op.tokenTransfers(), expectedLevel1TokenTransfers);
        assertThatTransferListContains(givenOp.tokenTransfers(), expectedGivenOpTokenTransfers);
        assertThatTransfersContains(
                givenOp.transfers().accountAmountsOrElse(emptyList()), expectedGivenOpHbarTransfers);

        verify(recordBuilder).assessedCustomFees(anyList());
    }

    @Test
    @DisplayName("Transfer which triggers hbar fixed fee and royalty fee with no fall back")
    void hbarFixedFeeAndRoyaltyFeeNoFallback() {
        // fungible token transfer with custom hbar fixed fee
        // and a fractional fee with netOfTransfers false
        // NFT transfer with royalty fee (fraction 1/2), no fallbackFee
        writableTokenStore.put(nonFungibleToken
                .copyBuilder()
                .customFees(List.of(withRoyaltyFee(
                        royaltyFee.copyBuilder().fallbackFee((FixedFee) null).build())))
                .build());
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);

        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);

        givenTxn();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(2);

        final var givenOp = listOfOps.get(0);
        final var level1Op = listOfOps.get(1);
        // since no fallback fee, there is no fallback fee deduction
        final var expectedLevel1Trasfers = Map.of(feeCollectorId, 1000L, ownerId, -1000L); // fixed fee
        final var expectedGivenOpTokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        feeCollectorId, 10L, // fractional fee
                        tokensReceiver, 990L,
                        ownerId, -1000L));
        final var expectedGivenOpHbarTransfers = Map.of(hbarsReceiver, 1000L, ownerId, -1000L);

        assertThatTransfersContains(level1Op.transfers().accountAmountsOrElse(emptyList()), expectedLevel1Trasfers);
        assertThatTransferListContains(givenOp.tokenTransfers(), expectedGivenOpTokenTransfers);
        assertThatTransfersContains(
                givenOp.transfers().accountAmountsOrElse(emptyList()), expectedGivenOpHbarTransfers);
    }

    @Test
    @DisplayName(
            "Transfer which triggers hts fixed fee that is self denominated " + "and royalty fee with no fall back")
    void htsFixedFeeSelfDenominationAndRoyaltyFeeNoFallback() {
        // fungible token transfer with custom hts fixed fee
        // and a fractional fee with netOfTransfers false
        // NFT transfer with royalty fee (fraction 1/2), no fallbackFee
        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);
        final var customfees = List.of(withFixedFee(htsFixedFee));
        writableTokenStore.put(
                fungibleToken.copyBuilder().customFees(customfees).build());
        writableTokenStore.put(nonFungibleToken
                .copyBuilder()
                .customFees(List.of(withRoyaltyFee(
                        royaltyFee.copyBuilder().fallbackFee((FixedFee) null).build())))
                .build());
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);

        givenTxn();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(1);

        final var givenOp = listOfOps.get(0);

        final var expectedGivenOpTokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        tokensReceiver, 1000L,
                        ownerId, -1010L,
                        feeCollectorId, 10L)); // fixed fee with self denomination will be adjusted in same txn body
        final var expectedGivenOpHbarTransfers = Map.of(hbarsReceiver, 1000L, ownerId, -1000L);

        assertThatTransferListContains(givenOp.tokenTransfers(), expectedGivenOpTokenTransfers);
        assertThatTransfersContains(
                givenOp.transfers().accountAmountsOrElse(emptyList()), expectedGivenOpHbarTransfers);

        verify(recordBuilder).assessedCustomFees(anyList());
    }

    @Test
    @DisplayName("Transfer which triggers hbar fixed fee, fractional fee with netOfTransfers is true "
            + "and royalty fee with fall back hbar fixed fee")
    void hbarFixedFeeAndRoyaltyFeeWithFallbackNetOfTransfers() {
        // fungible token transfer with custom hbar fixed fee
        // and a fractional fee with netOfTransfers true
        // NFT transfer with royalty fee (fraction 1/2), fallbackFee with fixed hbar fee

        final var customfees = List.of(
                withFixedFee(hbarFixedFee),
                withFractionalFee(
                        fractionalFee.copyBuilder().netOfTransfers(true).build()));
        writableTokenStore.put(
                fungibleToken.copyBuilder().customFees(customfees).build());
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);

        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);

        givenTxn();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(2);

        final var givenOp = listOfOps.get(0);
        final var level1Op = listOfOps.get(1);

        final var expectedLevel1Trasfers = Map.of(
                feeCollectorId,
                2000L,
                tokensReceiver,
                -1000L, // royalty fee
                ownerId,
                -1000L); // fixed fee
        final var expectedLevel1TokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        feeCollectorId,
                                10L, // since netOfTransfers is true fractional fee is charged to payer in next level
                        ownerId, -10L));
        final var expectedGivenOpTokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        tokensReceiver, 1000L,
                        ownerId, -1010L,
                        feeCollectorId, 10L)); // fractional fees all are adjusted to input txn
        final var expectedGivenOpHbarTransfers = Map.of(hbarsReceiver, 1000L, ownerId, -1000L);

        assertThatTransfersContains(level1Op.transfers().accountAmountsOrElse(emptyList()), expectedLevel1Trasfers);
        assertThatTransferListContains(level1Op.tokenTransfers(), expectedLevel1TokenTransfers);
        assertThatTransferListContains(givenOp.tokenTransfers(), expectedGivenOpTokenTransfers);
        assertThatTransfersContains(
                givenOp.transfers().accountAmountsOrElse(emptyList()), expectedGivenOpHbarTransfers);
    }

    @Test
    @DisplayName("Transfer which triggers hts fixed fee with self denomination, fractional fee "
            + "with netOfTransfers is true and royalty fee with fall back hbar fixed fee")
    void htsFixedFeeAndRoyaltyFeeWithFallbackNetOfTransfersSelfDenomination() {
        // fungible token transfer with custom hts fixed fee
        // and a fractional fee with netOfTransfers true
        // NFT transfer with royalty fee (fraction 1/2), fallbackFee with fixed hts fee
        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);
        final var customfees = List.of(
                withFixedFee(htsFixedFee),
                withFractionalFee(
                        fractionalFee.copyBuilder().netOfTransfers(true).build()));
        // fractional fee with self denomination will modify given transaction
        writableTokenStore.put(
                fungibleToken.copyBuilder().customFees(customfees).build());
        writableTokenStore.put(nonFungibleToken
                .copyBuilder()
                .customFees(List.of(withRoyaltyFee(
                        royaltyFee.copyBuilder().fallbackFee(htsFixedFee).build())))
                .build());
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);

        givenTxn();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(2);

        final var givenOp = listOfOps.get(0);
        final var level1Op = listOfOps.get(1);

        final var expectedLevel1TokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        feeCollectorId,
                        20L,
                        tokensReceiver,
                        -20L)); // 10 is for fallback royalty fee from given transaction.
        // When assessing the next level, since it is self denominated we append in same transaction body

        final var expectedGivenOpTokenTransfers = Map.of(
                fungibleTokenId,
                Map.of(
                        tokensReceiver, 1000L,
                        ownerId, -1010L,
                        feeCollectorId, 10L)); // fractional fees with self denomination
        // all are adjusted to input txn
        final var expectedGivenOpHbarTransfers = Map.of(hbarsReceiver, 1000L, ownerId, -1000L);

        assertThatTransferListContains(level1Op.tokenTransfers(), expectedLevel1TokenTransfers);
        assertThatTransferListContains(givenOp.tokenTransfers(), expectedGivenOpTokenTransfers);
        assertThatTransfersContains(
                givenOp.transfers().accountAmountsOrElse(emptyList()), expectedGivenOpHbarTransfers);

        verify(recordBuilder).assessedCustomFees(anyList());
    }

    private void assertThatTransferListContains(
            final List<TokenTransferList> tokenTransferLists,
            final Map<TokenID, Map<AccountID, Long>> expectedTransfers) {
        for (final var tokenTransferList : tokenTransferLists) {
            final var tokenId = tokenTransferList.token();
            final var fungibleTransfers = tokenTransferList.transfers();
            if (expectedTransfers.containsKey(tokenId)) {
                assertThatTransfersContains(fungibleTransfers, expectedTransfers.get(tokenId));
            }
        }
    }

    private void assertThatTransfersContains(
            final List<AccountAmount> transfers, final Map<AccountID, Long> expectedTransfers) {
        for (final var entry : expectedTransfers.entrySet()) {
            assertThat(transfers.contains(AccountAmount.newBuilder()
                            .accountID(entry.getKey())
                            .amount(entry.getValue())
                            .build()))
                    .isTrue();
        }
    }

    CryptoTransferTransactionBody getReplacedOp() {
        givenConditions();
        ensureAliasesStep.doIn(transferContext);
        associateTokenRecepientsStep.doIn(transferContext);
        return replaceAliasesWithIDsInOp.replaceAliasesWithIds(body, transferContext);
    }
}
