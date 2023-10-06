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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_ARE_ENABLED;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.nftTransferWith;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.List;
import java.util.function.Predicate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoTransferHandlerTest extends CryptoTransferHandlerTestBase {
    @Mock
    private CryptoTransferRecordBuilder transferRecordBuilder;

    private static final TokenID TOKEN_1357 = asToken(1357);
    private static final TokenID TOKEN_9191 = asToken(9191);
    private Configuration config;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = new CryptoTransferHandler(validator);
        given(handleContext.recordBuilder(CryptoTransferRecordBuilder.class)).willReturn(transferRecordBuilder);
    }

    @Test
    void handleNullArgs() {
        Assertions.assertThatThrownBy(() -> subject.handle(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void handleExceedsMaxHbarTransfers() {
        config = defaultConfig().withValue(LEDGER_TRANSFERS_MAX_LEN, 1).getOrCreateConfig();
        final var txn = newCryptoTransfer(ACCT_3333_MINUS_10, ACCT_4444_PLUS_10);
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    void handleHbarAllowancePresentButAllowancesDisabled() {
        config = defaultConfig().withValue(HEDERA_ALLOWANCES_IS_ENABLED, false).getOrCreateConfig();
        final var txn = newCryptoTransfer(
                ACCT_3333_MINUS_10.copyBuilder().isApproval(true).build(), ACCT_4444_PLUS_10);
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void handleExceedsMaxFungibleTokenTransfersInSingleTokenTransferList() {
        config = defaultConfig().withValue(LEDGER_TOKEN_TRANSFERS_MAX_LEN, 1).getOrCreateConfig();
        // Here we configure a SINGLE TokenTransferList that has 2 fungible token transfers
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    void handleExceedsMaxFungibleTokenTransfersAcrossMultipleTokenTransferLists() {
        config = defaultConfig().withValue(LEDGER_TOKEN_TRANSFERS_MAX_LEN, 4).getOrCreateConfig();
        // Here we configure MULTIPLE TokenTransferList objects, each with a fungible token transfer credit and debit
        final var txn = newCryptoTransfer(
                TokenTransferList.newBuilder()
                        .token(TOKEN_1357)
                        .transfers(ACCT_3333_MINUS_10, ACCT_4444_PLUS_10)
                        .build(),
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                        .build(),
                TokenTransferList.newBuilder()
                        .token(TOKEN_9191)
                        .transfers(ACCT_3333_MINUS_10, ACCT_4444_PLUS_10)
                        .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    void handleHasNftTransfersButNftsNotEnabled() {
        config = defaultConfig().withValue(TOKENS_NFTS_ARE_ENABLED, false).getOrCreateConfig();
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void handleExceedsMaxNftTransfersInSingleTokenTransferList() {
        config = defaultConfig().withValue(LEDGER_NFT_TRANSFERS_MAX_LEN, 1).getOrCreateConfig();
        // Here we configure a SINGLE TokenTransferList that has 2 nft transfers

        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444, SERIAL_2_FROM_4444_TO_3333)
                .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(BATCH_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    void handleExceedsMaxNftTransfersAcrossMultipleTokenTransferLists() {
        config = defaultConfig().withValue(LEDGER_NFT_TRANSFERS_MAX_LEN, 1).getOrCreateConfig();
        // Here we configure TWO TokenTransferList objects that each have a single nft transfer
        final var txn = newCryptoTransfer(
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                        .build(),
                TokenTransferList.newBuilder()
                        .token(TOKEN_1357)
                        .nftTransfers(SERIAL_2_FROM_4444_TO_3333)
                        .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(BATCH_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    void handleFungibleTokenAllowancePresentButAllowancesDisabled() {
        config = defaultConfig().withValue(HEDERA_ALLOWANCES_IS_ENABLED, false).getOrCreateConfig();
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_4444_PLUS_10.copyBuilder().isApproval(true).build())
                .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void handleNftAllowancePresentButAllowancesDisabled() {
        config = defaultConfig().withValue(HEDERA_ALLOWANCES_IS_ENABLED, false).getOrCreateConfig();
        final var txn = newCryptoTransfer(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444
                        .copyBuilder()
                        .isApproval(true)
                        .build())
                .build());
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void failsWhenAutoAssociatedTokenHasKycKey() {
        givenTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);

        given(handleContext.dispatchRemovableChildTransaction(
                        any(), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(payerId)))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(hbarReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecEvmAlias, asAccount(hbarReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(hbarReceiver));
                })
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(tokenReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(tokenReceiver));
                });
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN));
    }

    @Test
    void happyPathWorksWithAutoCreation() {
        givenTxn();
        refreshWritableStores();
        writableTokenStore.put(nonFungibleToken.copyBuilder().kycKey((Key) null).build());
        writableTokenStore.put(fungibleToken.copyBuilder().kycKey((Key) null).build());
        givenStoresAndConfig(handleContext);

        given(handleContext.dispatchRemovableChildTransaction(
                        any(), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(payerId)))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(hbarReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecEvmAlias, asAccount(hbarReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(hbarReceiver));
                })
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(tokenReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    writableTokenRelStore.put(fungibleTokenRelation
                            .copyBuilder()
                            .kycGranted(true)
                            .accountId(tokenReceiverId)
                            .build());
                    return cryptoCreateRecordBuilder.accountID(asAccount(tokenReceiver));
                });
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var initialSenderBalance = writableAccountStore.get(ownerId).tinybarBalance();
        final var initialFeeCollectorBalance =
                writableAccountStore.get(feeCollectorId).tinybarBalance();

        subject.handle(handleContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).hasSize(2);
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(4); // includes fee collector for custom fees
        assertThat(writableAccountStore.modifiedAccountsInState())
                .contains(ownerId, asAccount(hbarReceiver), asAccount(tokenReceiver));
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(4);

        assertThat(writableAccountStore.get(asAccount(hbarReceiver))).isNotNull();
        assertThat(writableAccountStore.get(asAccount(tokenReceiver))).isNotNull();
        assertThat(writableAliases.get(ecEvmAlias)).isEqualTo(hbarReceiverId);
        assertThat(writableAliases.get(edKeyAlias)).isEqualTo(tokenReceiverId);

        final var endSenderBalance = writableAccountStore.get(ownerId).tinybarBalance();
        // 1_000 for custom fee, 1_000 for transfer
        assertThat(endSenderBalance).isEqualTo(initialSenderBalance - 1_000 - 1_000);

        final var feeCollectorBalance = writableAccountStore.get(feeCollectorId).tinybarBalance();
        assertThat(feeCollectorBalance)
                .isEqualTo(
                        initialFeeCollectorBalance
                                + 1_000 // fixed custom fee
                                + 1_000 // hbar fallback royalty fee
                        );
    }

    @Test
    void failsOnRepeatedAliasAndCorrespondingNumber() {
        final var txnBody = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(
                                aaWith(ownerId, -2_000),
                                aaWith(unknownAliasedId, +1_000),
                                aaWith(asAccount(hbarReceiver), +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(aaWith(ownerId, -1_000), aaWith(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        givenTxn(txnBody, payerId);
        refreshWritableStores();
        givenStoresAndConfig(handleContext);

        given(handleContext.dispatchRemovableChildTransaction(
                        any(), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(payerId)))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(hbarReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecEvmAlias, asAccount(hbarReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(hbarReceiver));
                })
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(tokenReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(tokenReceiver));
                });
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void failsOnRepeatedAliasAndCorrespondingNumberInTokenTransferList() {
        final var txnBody = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWith(ownerId, -1_000), aaWith(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(
                                        aaWith(ownerId, -2_000),
                                        aaWith(unknownAliasedId1, +1_000),
                                        aaWith(asAccount(tokenReceiver), +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        givenTxn(txnBody, payerId);
        refreshWritableStores();
        givenStoresAndConfig(handleContext);

        given(handleContext.dispatchRemovableChildTransaction(
                        any(), eq(CryptoCreateRecordBuilder.class), any(Predicate.class), eq(payerId)))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(hbarReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecEvmAlias, asAccount(hbarReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(hbarReceiver));
                })
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(tokenReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(tokenReceiver));
                });
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    private HandleContext mockContext(final TransactionBody txn) {
        final var context = mock(HandleContext.class);
        given(context.configuration()).willReturn(config);
        given(context.body()).willReturn(txn);
        return context;
    }

    private static TestConfigBuilder defaultConfig() {
        return HederaTestConfigBuilder.create()
                .withValue(LEDGER_TRANSFERS_MAX_LEN, 10)
                .withValue(LEDGER_TOKEN_TRANSFERS_MAX_LEN, 10)
                .withValue(TOKENS_NFTS_ARE_ENABLED, true)
                .withValue(LEDGER_NFT_TRANSFERS_MAX_LEN, 10)
                .withValue(HEDERA_ALLOWANCES_IS_ENABLED, true);
    }
}
