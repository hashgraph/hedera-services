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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoTransferHandlerTest {
    private static final AccountID ACCOUNT_3333 = asAccount(3333);
    private static final AccountID ACCOUNT_4444 = asAccount(4444);
    private static final TokenID TOKEN_2468 = asToken(2468);

    private CryptoTransferHandler subject;

    @BeforeEach
    void doSetup() {
        subject = new CryptoTransferHandler();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void pureChecksNullArgThrows() {
        Assertions.assertThatThrownBy(() -> subject.pureChecks(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void pureChecksHasNoCryptoTransfer() {
        final var nonTransferTxnBody = TokenAssociateTransactionBody.newBuilder();
        final var txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_3333))
                .tokenAssociate(nonTransferTxnBody)
                .build();

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void pureChecksHbarTransfersHasNullAccountId() {
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder().accountID((AccountID) null).amount(1).build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_ACCOUNT_ID));
    }

    @Test
    void pureChecksHbarTransfersHasAccountIdWithAliasAndNumber() {
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder()
                        .accountID(AccountID.newBuilder()
                                .accountNum(5555)
                                .alias(Bytes.wrap("non-empty string"))
                                .build())
                        .amount(1)
                        .build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_ACCOUNT_ID));
    }

    @Test
    void pureChecksHbarTransfersHasAccountIdWithIllegalNumber() {
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder().accountID(asAccount(0)).amount(1).build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_ACCOUNT_ID));
    }

    @Test
    void pureChecksHbarTransfersHasAccountIdWithIllegalAlias() {
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder()
                        .accountID(AccountID.newBuilder().alias(Bytes.wrap("")).build())
                        .amount(1)
                        .build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_ACCOUNT_ID));
    }

    @Test
    void pureChecksHbarTransfersHasNonZeroHbarAdjustments() {
        // A net transfer balance of (-1 + 2) = 1 should cause the pure checks to fail
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder().accountID(ACCOUNT_4444).amount(2).build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksHbarTransfersHasRepeatedAccountId() {
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(1).build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksHasValidHbarTransfers() {
        // Note: this test only checks for valid hbar transfers (WITHOUT any token transfers)
        final var txn = newCryptoTransfer(
                AccountAmount.newBuilder().accountID(ACCOUNT_3333).amount(-1).build(),
                AccountAmount.newBuilder().accountID(ACCOUNT_4444).amount(1).build());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksFungibleTokenTransfersHasMissingTokenId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token((TokenID) null)
                // These are TOKEN fungible amount transfers, not HBAR amount transfers
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(-2)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_4444)
                                .amount(2)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasMissingAccountId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are TOKEN fungible amount transfers, not HBAR amount transfers
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(-2)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID((AccountID) null)
                                .amount(2)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasRepeatedAccountId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are TOKEN amount transfers, not HBAR amount transfers
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(-2)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(2)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasNonZeroTokenSum() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are TOKEN amount transfers, not HBAR amount transfers
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(-1)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_4444)
                                .amount(2)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN));
    }

    @Test
    void pureChecksHasValidFungibleTokenTransfers() {
        // Note: this test only checks for valid fungible token transfers (WITHOUT any hbar or nft transfers)
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(-2)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_4444)
                                .amount(2)
                                .build())
                .build());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingTokenId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token((TokenID) null)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(NftTransfer.newBuilder()
                        .serialNumber(1)
                        .senderAccountID(ACCOUNT_3333)
                        .receiverAccountID(ACCOUNT_4444)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasInvalidNftId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(NftTransfer.newBuilder()
                        .serialNumber(0)
                        .senderAccountID(ACCOUNT_3333)
                        .receiverAccountID(ACCOUNT_4444)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingSenderId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(NftTransfer.newBuilder()
                        .serialNumber(1)
                        .senderAccountID((AccountID) null)
                        .receiverAccountID(ACCOUNT_4444)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingReceiverId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(NftTransfer.newBuilder()
                        .serialNumber(1)
                        .senderAccountID(ACCOUNT_3333)
                        .receiverAccountID((AccountID) null)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasRepeatedNftId() {
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(
                        NftTransfer.newBuilder()
                                .serialNumber(1)
                                .senderAccountID(ACCOUNT_3333)
                                .receiverAccountID(ACCOUNT_4444)
                                .build(),
                        NftTransfer.newBuilder()
                                .serialNumber(2)
                                .senderAccountID(ACCOUNT_3333)
                                .receiverAccountID(ACCOUNT_4444)
                                .build(),
                        NftTransfer.newBuilder()
                                .serialNumber(1)
                                .senderAccountID(ACCOUNT_3333)
                                .receiverAccountID(ACCOUNT_4444)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST));
    }

    @Test
    void pureChecksHasValidNonFungibleTokenTransfers() {
        // Note: this test only checks for valid non-fungible token transfers (WITHOUT any hbar or fungible token
        // transfers)
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(
                        NftTransfer.newBuilder()
                                .serialNumber(1)
                                .senderAccountID(ACCOUNT_3333)
                                .receiverAccountID(ACCOUNT_4444)
                                .build(),
                        NftTransfer.newBuilder()
                                .serialNumber(2)
                                .senderAccountID(ACCOUNT_4444)
                                .receiverAccountID(ACCOUNT_3333)
                                .build(),
                        NftTransfer.newBuilder()
                                .serialNumber(3)
                                .senderAccountID(ACCOUNT_3333)
                                .receiverAccountID(ACCOUNT_4444)
                                .build())
                .build());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksTokenTransfersDoesNotHaveFungibleOrNonFungibleAmount() {
        // This test checks that, if any token transfer is present, it must have at least one fungible or non-fungible
        // balance not equal to zero
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(AccountAmount.newBuilder()
                        .accountID(ACCOUNT_3333)
                        .amount(0)
                        .build())
                // Intentionally empty (will result in a count of zero nft transfers)
                .nftTransfers()
                .build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksTokenTransferHasBothFungibleAndNonFungibleAmounts() {
        // This test checks that, if a transfer for a token is present, it must have ONLY a fungible transfer OR an NFT
        // transfer, but not both
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(-1)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_4444)
                                .amount(1)
                                .build())
                .nftTransfers(NftTransfer.newBuilder()
                        .serialNumber(1)
                        .senderAccountID(ACCOUNT_3333)
                        .receiverAccountID(ACCOUNT_4444)
                        .build())
                .build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksForEmptyHbarTransferAndEmptyTokenTransfers() {
        // It's actually valid to have no hbar transfers and no token transfers
        final var txn = newCryptoTransfer(Collections.emptyList(), Collections.emptyList());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksHasValidHbarAndTokenTransfers() {
        // Tests that valid hbar transfers, fungible transfers, and non-fungible transfers are all valid when given
        // together
        final var token9753 = asToken(9753);
        final var txn = newCryptoTransfer(
                List.of(
                        // Valid hbar transfers
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_4444)
                                .amount(-5)
                                .build(),
                        AccountAmount.newBuilder()
                                .accountID(ACCOUNT_3333)
                                .amount(5)
                                .build()),
                List.of(
                        // Valid fungible token transfers
                        TokenTransferList.newBuilder()
                                .token(TOKEN_2468)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_3333)
                                                .amount(-10)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_4444)
                                                .amount(10)
                                                .build())
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(token9753)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_4444)
                                                .amount(-1000)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_3333)
                                                .amount(1000)
                                                .build())
                                .build(),
                        // Valid nft token transfers
                        TokenTransferList.newBuilder()
                                .token(token9753)
                                .nftTransfers(
                                        NftTransfer.newBuilder()
                                                .serialNumber(1)
                                                .senderAccountID(ACCOUNT_4444)
                                                .receiverAccountID(ACCOUNT_3333)
                                                .build(),
                                        NftTransfer.newBuilder()
                                                .serialNumber(2)
                                                .senderAccountID(ACCOUNT_3333)
                                                .receiverAccountID(ACCOUNT_4444)
                                                .build())
                                .build()));

        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    private TransactionBody newCryptoTransfer(AccountAmount... acctAmounts) {
        return newCryptoTransfer(Arrays.stream(acctAmounts).toList(), List.of());
    }

    private TransactionBody newCryptoTransfer(TokenTransferList... tokenTransferLists) {
        return newCryptoTransfer(List.of(), Arrays.stream(tokenTransferLists).toList());
    }

    // Note: `tokenTransferLists` can include both fungible and non-fungible token transfers
    private TransactionBody newCryptoTransfer(
            List<AccountAmount> acctAmounts, List<TokenTransferList> tokenTransferLists) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_3333))
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder().accountAmounts(acctAmounts))
                        .tokenTransfers(tokenTransferLists))
                .build();
    }
}
