package com.hedera.node.app.service.token.impl.test.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;

public class TokenAirdropHandlerPureChecksTest extends TokenAirdropHandlerTestBase{

    @SuppressWarnings("DataFlowIssue")
    @Test
    void pureChecksNullArgThrows() {
        Assertions.assertThatThrownBy(() -> subject.pureChecks(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void pureChecksFungibleTokenTransfersHasMissingTokenId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token((TokenID) null)
                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasMissingAccountId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are TOKEN fungible amount transfers, not HBAR amount transfers
                .transfers(
                        ACCT_3333_MINUS_10,
                        ACCT_4444_PLUS_10
                                .copyBuilder()
                                .accountID((AccountID) null)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasRepeatedAccountId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(
                        ACCT_4444_MINUS_5,
                        ACCT_4444_MINUS_5.copyBuilder().amount(5).build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasNonZeroTokenSum() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(
                        ACCT_3333_MINUS_10,
                        ACCT_4444_PLUS_10.copyBuilder().amount(5).build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN));
    }

    @Test
    void pureChecksHasValidFungibleTokenTransfers() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                .build());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingTokenId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token((TokenID) null)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasInvalidNftId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(
                        SERIAL_1_FROM_3333_TO_4444.copyBuilder().serialNumber(0).build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingSenderId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(SERIAL_2_FROM_4444_TO_3333
                        .copyBuilder()
                        .senderAccountID((AccountID) null)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingReceiverId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444
                        .copyBuilder()
                        .receiverAccountID((AccountID) null)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasRepeatedNftId() {
        final var txn = newTokenAirdrop(
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                        .build(),
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .nftTransfers(SERIAL_2_FROM_4444_TO_3333)
                        .build());
        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST));
    }

    @Test
    void pureChecksHasValidNonFungibleTokenTransfers() {
        // Note: this test only checks for valid non-fungible token transfers (WITHOUT any hbar or fungible token
        // transfers)
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(
                        SERIAL_1_FROM_3333_TO_4444,
                        SERIAL_2_FROM_4444_TO_3333,
                        SERIAL_1_FROM_3333_TO_4444.copyBuilder().serialNumber(3).build())
                .build());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksTokenTransfersDoesNotHaveFungibleOrNonFungibleAmount() {
        // This test checks that, if any token transfer is present, it must have at least one fungible or non-fungible
        // balance not equal to zero
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // transfers and nftTransfers are intentionally empty (will result in a count of zero transfers)
                .transfers()
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
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_3333_MINUS_10, ACCT_4444_PLUS_10)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444, SERIAL_1_FROM_3333_TO_4444)
                .build());

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksForEmptyHbarTransferAndEmptyTokenTransfers() {
        // It's actually valid to have no hbar transfers and no token transfers
        final var txn = newTokenAirdrop(Collections.emptyList());
        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksHasValidHbarAndTokenTransfers() {
        // Tests that valid hbar transfers, fungible transfers, and non-fungible transfers are all valid when given
        // together
        final var token9753 = asToken(9753);
        final var token9754 = asToken(9754);
        final var txn = newTokenAirdrop(
                    List.of(
                        // Valid fungible token transfers
                        TokenTransferList.newBuilder()
                                .token(TOKEN_2468)
                                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(token9754)
                                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                                .build(),
                        // Valid nft token transfers
                        TokenTransferList.newBuilder()
                                .token(token9753)
                                .nftTransfers(SERIAL_1_FROM_3333_TO_4444, SERIAL_2_FROM_4444_TO_3333)
                                .build()));

        Assertions.assertThatCode(() -> subject.pureChecks(txn)).doesNotThrowAnyException();
    }

}
