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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_REPEATED;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropId.TokenReferenceOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenCancelAirdropTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.TokenCancelAirdropHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.util.PendingAirdropUpdater;
import com.hedera.node.app.spi.workflows.PreCheckException;
import java.util.Arrays;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TokenCancelAirdropHandlerPureChecksTest extends CryptoTokenHandlerTestBase {

    private static final AccountID ACCOUNT_SENDER = asAccount(4444);
    private static final AccountID ACCOUNT_RECEIVER = asAccount(3333);

    private static final TokenID TOKEN_FUNGIBLE = asToken(2468);
    private static final TokenID NFT_TOKEN = asToken(2468);
    private static final NftID NFT_ID =
            NftID.newBuilder().tokenId(NFT_TOKEN).serialNumber(1L).build();

    private static final PendingAirdropId pendingAirdropIdFungible = PendingAirdropId.newBuilder()
            .fungibleTokenType(TOKEN_FUNGIBLE)
            .receiverId(ACCOUNT_RECEIVER)
            .senderId(ACCOUNT_SENDER)
            .build();

    private static final PendingAirdropId pendingAirdropIdNFT = PendingAirdropId.newBuilder()
            .nonFungibleToken(NFT_ID)
            .receiverId(ACCOUNT_RECEIVER)
            .senderId(ACCOUNT_SENDER)
            .build();

    private TokenCancelAirdropHandler subject;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = new TokenCancelAirdropHandler(new PendingAirdropUpdater());
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void pureChecksNullArgThrows() {
        Assertions.assertThatThrownBy(() -> subject.pureChecks(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void handleCancelAirdropWithRepeatedFungiblePendingAirdrops() {
        final var txn = newTokenCancelAirdrop(repeatedAirdrops(pendingAirdropIdFungible, 2));

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(PENDING_AIRDROP_ID_REPEATED));
    }

    @Test
    void handleCancelAirdropWithRepeatedNFTPendingAirdrops() {
        final var txn = newTokenCancelAirdrop(repeatedAirdrops(pendingAirdropIdNFT, 2));

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(PENDING_AIRDROP_ID_REPEATED));
    }

    @Test
    void handleCancelAirdropWithMissingReceiver() {
        final var pendingAirdropWithNoReceiver = PendingAirdropId.newBuilder()
                .nonFungibleToken(NFT_ID)
                .fungibleTokenType(TOKEN_FUNGIBLE)
                .senderId(ACCOUNT_SENDER)
                .build();
        final var txn = newTokenCancelAirdrop(pendingAirdropWithNoReceiver);

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_ID));
    }

    @Test
    void handleCancelAirdropWithMissingSender() {
        final var pendingAirdropWithNoSender = PendingAirdropId.newBuilder()
                .nonFungibleToken(NFT_ID)
                .fungibleTokenType(TOKEN_FUNGIBLE)
                .receiverId(ACCOUNT_RECEIVER)
                .build();
        final var txn = newTokenCancelAirdrop(pendingAirdropWithNoSender);

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_ID));
    }

    @Test
    void handleCancelAirdropWithInvalidNFT() {
        final var pendingAirdropIdWithInvalidNft = PendingAirdropId.newBuilder()
                .senderId(ACCOUNT_SENDER)
                .receiverId(ACCOUNT_RECEIVER)
                .nonFungibleToken(NftID.newBuilder()
                        .tokenId(nonFungibleTokenId)
                        .serialNumber(0)
                        .build())
                .build();
        final var txn = newTokenCancelAirdrop(pendingAirdropIdWithInvalidNft);

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void handleCancelAirdropWithReferenceForNFTAndFungible() {
        // Since the Protobuf body is composed of OneOf<> token type kind.
        // Setting the both types of token in the same PendingAirdropId, would just use the second one.
        final var pendingAirdropIdWithBothTypes = PendingAirdropId.newBuilder()
                .fungibleTokenType(TOKEN_FUNGIBLE)
                .nonFungibleToken(NFT_ID)
                .receiverId(ACCOUNT_RECEIVER)
                .senderId(ACCOUNT_SENDER)
                .build();
        final var txn = newTokenCancelAirdrop(pendingAirdropIdWithBothTypes);

        assertDoesNotThrow(() -> subject.pureChecks(txn));
        assertThat(pendingAirdropIdWithBothTypes.hasFungibleTokenType()).isFalse();
        assertThat(pendingAirdropIdWithBothTypes.hasNonFungibleToken()).isTrue();
        assertThat(pendingAirdropIdWithBothTypes.tokenReference().kind())
                .isEqualTo(TokenReferenceOneOfType.NON_FUNGIBLE_TOKEN);
    }

    @Test
    void handleCancelAirdropWithReferenceForNFTWorks() {
        final var txn = newTokenCancelAirdrop(pendingAirdropIdNFT);
        assertDoesNotThrow(() -> subject.pureChecks(txn));
    }

    @Test
    void handleCancelAirdropWithReferenceForFungibleWorks() {
        final var txn = newTokenCancelAirdrop(pendingAirdropIdFungible);
        assertDoesNotThrow(() -> subject.pureChecks(txn));
    }

    @Test
    void handleCancelAirdropWithEmptyPendingAirdrops() {
        final var txn = newTokenCancelAirdrop();

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(EMPTY_PENDING_AIRDROP_ID_LIST));
    }

    private TransactionBody newTokenCancelAirdrop(final PendingAirdropId... pendingAirdropIds) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp))
                .tokenCancelAirdrop(TokenCancelAirdropTransactionBody.newBuilder()
                        .pendingAirdrops(Arrays.stream(pendingAirdropIds).toList()))
                .build();
    }

    private PendingAirdropId[] repeatedAirdrops(final PendingAirdropId airDrop, final int count) {
        return Collections.nCopies(count, airDrop).toArray(new PendingAirdropId[0]);
    }
}
