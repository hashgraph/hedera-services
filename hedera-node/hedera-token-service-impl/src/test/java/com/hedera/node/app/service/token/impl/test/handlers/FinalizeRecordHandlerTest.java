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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.FinalizeRecordHandler;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandlerImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinalizeRecordHandlerTest extends CryptoTokenHandlerTestBase {
    private final AccountID ACCOUNT_1212_ID =
            AccountID.newBuilder().accountNum(1212).build();
    private final Account ACCOUNT_1212 =
            givenValidAccountBuilder().accountId(ACCOUNT_1212_ID).build();
    private final AccountID ACCOUNT_3434_ID =
            AccountID.newBuilder().accountNum(3434).build();
    private final Account ACCOUNT_3434 = givenValidAccountBuilder()
            .accountId(ACCOUNT_3434_ID)
            .tinybarBalance(500)
            .build();
    private final AccountID ACCOUNT_5656_ID =
            AccountID.newBuilder().accountNum(5656).build();
    private final Account ACCOUNT_5656 = givenValidAccountBuilder()
            .accountId(ACCOUNT_5656_ID)
            .tinybarBalance(10000)
            .build();
    private static final TokenID TOKEN_321 = asToken(321);
    private Token TOKEN_321_FUNGIBLE =
            givenValidFungibleToken().copyBuilder().tokenId(TOKEN_321).build();
    private static final List<TransactionRecord> EMPTY_TRANSACTION_RECORD_LIST = Collections.emptyList();

    @Mock(strictness = LENIENT)
    private FinalizeContext context;

    @Mock
    private CryptoTransferStreamBuilder recordBuilder;

    private ReadableAccountStore readableAccountStore;
    private WritableAccountStore writableAccountStore;
    private ReadableNftStore readableNftStore;
    private WritableNftStore writableNftStore;
    private WritableTokenStore writableTokenStore;

    @Mock
    private StakingRewardsHandlerImpl stakingRewardsHandler;

    private FinalizeRecordHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = new FinalizeRecordHandler(stakingRewardsHandler);
    }

    @Test
    void handleNullArg() {
        assertThatThrownBy(() -> subject.finalizeStakingRecord(
                        context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void handleHbarNetTransferAmountIsNotZero() {
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore.put(ACCOUNT_1212
                .copyBuilder()
                .tinybarBalance(ACCOUNT_1212.tinybarBalance() - 5)
                .build());
        context = mockContext();
        given(context.configuration()).willReturn(configuration);
        given(context.userTransactionRecordBuilder(StreamBuilder.class)).willReturn(mock(StreamBuilder.class));

        assertThatThrownBy(() -> subject.finalizeStakingRecord(
                        context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap()))
                .isInstanceOf(HandleException.class)
                .has(responseCode(FAIL_INVALID));
    }

    @Test
    void handleHbarAccountBalanceIsNegative() {
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434);
        // This amount will cause the net transfer amount to be negative for account 1212
        final var amountToAdjust = ACCOUNT_1212.tinybarBalance() + 1;
        writableAccountStore.put(ACCOUNT_1212
                .copyBuilder()
                .tinybarBalance(ACCOUNT_1212.tinybarBalance() - amountToAdjust)
                .build());
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .tinybarBalance(ACCOUNT_3434.tinybarBalance() + amountToAdjust)
                .build());
        context = mockContext();
        given(context.configuration()).willReturn(configuration);
        given(context.userTransactionRecordBuilder(StreamBuilder.class)).willReturn(mock(StreamBuilder.class));

        assertThatThrownBy(() -> subject.finalizeStakingRecord(
                        context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap()))
                .isInstanceOf(HandleException.class)
                .has(responseCode(FAIL_INVALID));
    }

    @Test
    void handleHbarAccountBalanceDoesntChange() {
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        // Account 1212 changes by getting a new memo, but its balance doesn't change
        writableAccountStore.put(
                ACCOUNT_1212.copyBuilder().memo("different memo field").build());
        // Intentionally empty token rel store
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels();
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        context = mockContext();

        given(context.configuration()).willReturn(configuration);
        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verifyNoInteractions(recordBuilder);
    }

    @Test
    void handleHbarTransfersToNewAccountSuccess() {
        // This case handles a successful hbar transfer to an auto-created account

        final var amountToTransfer = ACCOUNT_1212.tinybarBalance() - 1;
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore.put(ACCOUNT_1212.copyBuilder().tinybarBalance(1).build());
        // Putting ACCOUNT_3434 into the writable store here simulates this account being auto-created
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .alias(Bytes.wrap("00000000000000000001"))
                .tinybarBalance(amountToTransfer)
                .build());
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(); // Intentionally empty
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(); // Intentionally empty
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .transferList(TransferList.newBuilder()
                        .accountAmounts(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-amountToTransfer)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(amountToTransfer)
                                        .build())
                        .build());
    }

    @Test
    void handleHbarTransfersToAccountDeductsFromChildRecordsSuccess() {
        // This case handles a successful hbar transfer to an auto-created account
        // deducts the child record transfers from parent transfer list

        final var amountToTransfer = ACCOUNT_1212.tinybarBalance() - 1;
        // 1 tinybar left in parent account , transferred 9999
        final var childRecordTransfer = amountToTransfer / 2; // 1/2 of parent account balance, 4999

        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore.put(ACCOUNT_1212.copyBuilder().tinybarBalance(1).build());
        // Putting ACCOUNT_3434 into the writable store here simulates this account being auto-created
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .alias(Bytes.wrap("00000000000000000001"))
                .tinybarBalance(amountToTransfer)
                .build());
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(); // Intentionally empty
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(); // Intentionally empty
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        final var childRecord = mock(RecordStreamBuilder.class);
        // child record has  1212 (-) -> 3434(+) transfer
        given(childRecord.transferList())
                .willReturn(TransferList.newBuilder()
                        .accountAmounts(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-childRecordTransfer)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(childRecordTransfer)
                                        .build())
                        .build());
        given(context.hasChildOrPrecedingRecords()).willReturn(true);
        doAnswer(invocation -> {
                    final var consumer = invocation.getArgument(1, Consumer.class);
                    consumer.accept(childRecord);
                    return null;
                })
                .when(context)
                .forEachChildRecord(any(), any());

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        final var transferAmount1212 = -amountToTransfer + childRecordTransfer;
        final var transferAmount3434 = amountToTransfer - childRecordTransfer;
        BDDMockito.verify(recordBuilder)
                .transferList(TransferList.newBuilder()
                        .accountAmounts(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(transferAmount1212)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(transferAmount3434)
                                        .build())
                        .build());
    }

    @Test
    void handleFungibleTokenTransfersToAccountDeductsFromChildRecordsSuccess() {
        // This case handles a successful fungible token transfer to an auto-created account
        // does not deduct all child record transfers from parent transfer list

        final var senderAcct = ACCOUNT_1212;
        final var senderTokenRel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_1212_ID)
                .build();
        final var fungibleAmountToTransfer = senderTokenRel.balance() - 1;
        final var childAmount = fungibleAmountToTransfer / 2;
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(senderAcct);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(senderAcct);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(senderTokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(senderTokenRel);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        // Simulate the token receiver's account (ACCOUNT_3434) being auto-created (with an hbar balance of 0)
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .tinybarBalance(0)
                .alias(Bytes.wrap("00000000000000000002"))
                .build());
        // Simulate the receiver's token relation being auto-created (and both the sender and receiver token rel
        // balances adjusted)
        writableTokenRelStore.put(senderTokenRel.copyBuilder().balance(1).build());
        writableTokenRelStore.put(senderTokenRel
                .copyBuilder()
                .accountId(ACCOUNT_3434_ID)
                .balance(fungibleAmountToTransfer)
                .build());
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        readableTokenStore = TestStoreFactory.newReadableStoreWithTokens(TOKEN_321_FUNGIBLE);
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        final var childRecord = mock(TransactionRecord.class);
        // child record has  1212 (-) -> 3434(+) transfer
        lenient()
                .when(childRecord.transferList())
                .thenReturn(TransferList.newBuilder().build());
        lenient()
                .when(childRecord.tokenTransferLists())
                .thenReturn(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .transfers(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-childAmount)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(childAmount)
                                        .build())
                        .build()));

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .transfers(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-fungibleAmountToTransfer)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(fungibleAmountToTransfer)
                                        .build())
                        .build()));
    }

    @Test
    void accountsForDissociatedTokenRelations() {
        // This case handles a successful fungible token relation dissociation when token is deleted
        // When just token is dissociated without any token delete, then transfer list doesn't show that case

        final var senderAcct = ACCOUNT_1212;
        final var senderTokenRel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_1212_ID)
                .build();
        final var receiverRel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_3434_ID)
                .build();
        final var fungibleAmountToTransfer = senderTokenRel.balance() - 1;
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(senderAcct);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(senderAcct);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(senderTokenRel, receiverRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(senderTokenRel, receiverRel);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        // Simulate the receiver's token relation being dissociated, when token is deleted.
        // This shows as a single debit in transfer list, instead of a debit and a credit.
        writableTokenRelStore.remove(senderTokenRel);
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        readableTokenStore = TestStoreFactory.newReadableStoreWithTokens(TOKEN_321_FUNGIBLE);
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .transfers(AccountAmount.newBuilder()
                                .accountID(ACCOUNT_1212_ID)
                                .amount(-1000L)
                                .build())
                        .build()));
    }

    @Test
    void nftBurnsOrWipesAreAccounted() {
        // This case handles a successful NFT burn or wipe
        final var existingTokenRel = givenNonFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_1212_ID)
                .build();
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(existingTokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(existingTokenRel);
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        final var nft1 = givenNft(
                        NftID.newBuilder().tokenId(TOKEN_321).serialNumber(1).build())
                .copyBuilder()
                .ownerId(ACCOUNT_1212_ID)
                .build();
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(nft1);
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(nft1);

        writableNftStore.remove(
                NftID.newBuilder().tokenId(TOKEN_321).serialNumber(1).build());
        readableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .nftTransfers(NftTransfer.newBuilder()
                                .serialNumber(1)
                                .senderAccountID(ACCOUNT_1212_ID)
                                .receiverAccountID(asAccount(0))
                                .build())
                        .build()));
    }

    @Test
    void handleHbarTransfersToExistingAccountSuccess() {
        // This test case handles successfully transferring hbar only (no tokens)
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434, ACCOUNT_5656);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434, ACCOUNT_5656);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(); // Intentionally empty
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        final var acct1212Change = 10;
        writableAccountStore.put(ACCOUNT_1212
                .copyBuilder()
                .tinybarBalance(ACCOUNT_1212.tinybarBalance() - acct1212Change)
                .build());
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .tinybarBalance(ACCOUNT_3434.tinybarBalance() + acct1212Change)
                .build());
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        // Account 5656 changes by getting a new memo, but its balance doesn't change
        writableAccountStore.put(
                ACCOUNT_5656.copyBuilder().memo("different memo field").build());
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .transferList(TransferList.newBuilder()
                        .accountAmounts(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-acct1212Change)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(acct1212Change)
                                        .build())
                        // There shouldn't be any entry for account 5656 because its balance didn't change
                        .build());
    }

    @Test
    void handleFungibleTokenBalanceIsNegative() {
        final var validAcct = givenValidAccountBuilder();
        final var tokenRel = givenFungibleTokenRelation(); // Already tied to validAcct's account ID
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(validAcct.build());
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(validAcct.build());
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(tokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(tokenRel);
        writableTokenRelStore.put(tokenRel.copyBuilder().balance(-1).build());
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        assertThatThrownBy(() -> subject.finalizeStakingRecord(
                        context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap()))
                .isInstanceOf(HandleException.class)
                .has(responseCode(FAIL_INVALID));
    }

    @Test
    void handleFungibleTransferTokenBalancesDontChange() {
        final var validAcct = givenValidAccountBuilder();
        final var tokenRel = givenFungibleTokenRelation();
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(validAcct.build());
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(validAcct.build());
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(tokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(tokenRel);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens();
        // The token relation's 'frozen' property is changed, but its balance doesn't change
        writableTokenRelStore.put(tokenRel.copyBuilder().frozen(true).build());
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verifyNoInteractions(recordBuilder);
    }

    @Test
    void handleFungibleTransfersToNewAccountSuccess() {
        // This case handles a successful fungible token transfer to an auto-created account

        final var senderAcct = ACCOUNT_1212;
        final var senderTokenRel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_1212_ID)
                .build();
        final var fungibleAmountToTransfer = senderTokenRel.balance() - 1;
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(senderAcct);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(senderAcct);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(senderTokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(senderTokenRel);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        // Simulate the token receiver's account (ACCOUNT_3434) being auto-created (with an hbar balance of 0)
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .tinybarBalance(0)
                .alias(Bytes.wrap("00000000000000000002"))
                .build());
        // Simulate the receiver's token relation being auto-created (and both the sender and receiver token rel
        // balances adjusted)
        writableTokenRelStore.put(senderTokenRel.copyBuilder().balance(1).build());
        writableTokenRelStore.put(senderTokenRel
                .copyBuilder()
                .accountId(ACCOUNT_3434_ID)
                .balance(fungibleAmountToTransfer)
                .build());
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        readableTokenStore = TestStoreFactory.newReadableStoreWithTokens(TOKEN_321_FUNGIBLE);
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .transfers(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-fungibleAmountToTransfer)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(fungibleAmountToTransfer)
                                        .build())
                        .build()));
    }

    @Test
    void handleFungibleTransfersToExistingAccountsSuccess() {
        // This test case handles successfully transferring fungible tokens only

        final var token1Id = fungibleTokenId;
        final var token2Id = asToken(2);
        final var token3Id = asToken(3);
        // Note: givenFungibleTokenRelation() has a non-zero balance, so we don't need to modify the token rel balances
        final var acct1212Token1Rel = givenFungibleTokenRelation()
                .copyBuilder()
                .accountId(ACCOUNT_1212_ID)
                .build();
        final var acct3434Token1Rel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(token1Id)
                .accountId(ACCOUNT_3434_ID)
                .balance(0)
                .build();
        final var acct3434Token2Rel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(token2Id)
                .accountId(ACCOUNT_3434_ID)
                .build();
        final var acct5656Token2Rel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(token2Id)
                .accountId(ACCOUNT_5656_ID)
                .balance(0)
                .build();
        final var acct5656Token3Rel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(token3Id)
                .accountId(ACCOUNT_5656_ID)
                .build();
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434, ACCOUNT_5656);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434, ACCOUNT_5656);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(
                acct1212Token1Rel, acct3434Token1Rel, acct3434Token2Rel, acct5656Token2Rel, acct5656Token3Rel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(
                acct1212Token1Rel, acct3434Token1Rel, acct3434Token2Rel, acct5656Token2Rel, acct5656Token3Rel);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        // The account in tokenRel1 will send X fungible units of token 1 to the account on tokenRel2
        // The account in tokenRel2 will send Y fungible units of token 2 to the account on tokenRel3
        // Token rels 1 and 2 will have balance changes, but token rel 3's balance won't change
        final var token1AmountTransferred = acct1212Token1Rel.balance() - 1;
        writableTokenRelStore.put(acct1212Token1Rel.copyBuilder().balance(1).build());
        writableTokenRelStore.put(acct3434Token2Rel
                .copyBuilder()
                .tokenId(token1Id)
                .balance(token1AmountTransferred)
                .build());
        final var token2AmountTransferred = acct3434Token2Rel.balance() - 10;
        writableTokenRelStore.put(acct3434Token2Rel.copyBuilder().balance(10).build());
        writableTokenRelStore.put(acct5656Token3Rel
                .copyBuilder()
                .tokenId(token2Id)
                .balance(token2AmountTransferred)
                .build());

        final var fungibleToken1 =
                givenValidFungibleToken().copyBuilder().tokenId(token1Id).build();
        final var fungibleToken2 =
                givenValidFungibleToken().copyBuilder().tokenId(token2Id).build();
        final var fungibleToken3 =
                givenValidFungibleToken().copyBuilder().tokenId(token3Id).build();
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(
                TOKEN_321_FUNGIBLE, fungibleToken1, fungibleToken2, fungibleToken3);
        readableTokenStore = TestStoreFactory.newReadableStoreWithTokens(
                TOKEN_321_FUNGIBLE, fungibleToken1, fungibleToken2, fungibleToken3);

        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(
                        TokenTransferList.newBuilder()
                                .token(token1Id)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_1212_ID)
                                                .amount(-token1AmountTransferred)
                                                .isApproval(false)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_3434_ID)
                                                .amount(token1AmountTransferred)
                                                .isApproval(false)
                                                .build())
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(token2Id)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_3434_ID)
                                                .amount(-token2AmountTransferred)
                                                .isApproval(false)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_5656_ID)
                                                .amount(token2AmountTransferred)
                                                .isApproval(false)
                                                .build())
                                .build()));
    }

    @Test
    void handleNftTransfersToNewAccountSuccess() {
        // This case handles a successful NFT transfer to an auto-created account
        final var existingTokenRel = givenNonFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_1212_ID)
                .build();
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(existingTokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(existingTokenRel);
        final var nft = givenNft(
                        NftID.newBuilder().tokenId(TOKEN_321).serialNumber(1).build())
                .copyBuilder()
                .ownerId(ACCOUNT_1212_ID)
                .build();
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(nft);
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(nft);
        // Simulate the token receiver's account (ACCOUNT_3434) being auto-created
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .tinybarBalance(0)
                .alias(Bytes.wrap("00000000000000000003"))
                .build());
        writableNftStore.put(nft.copyBuilder().ownerId(ACCOUNT_3434_ID).build());
        readableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .nftTransfers(NftTransfer.newBuilder()
                                .serialNumber(1)
                                .senderAccountID(ACCOUNT_1212_ID)
                                .receiverAccountID(ACCOUNT_3434_ID)
                                .build())
                        .build()));
    }

    @Test
    void handleNewNftTransferToAccountSuccess() {
        final var existingTokenRel = givenNonFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_1212_ID)
                .build();
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(existingTokenRel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(existingTokenRel);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(); // Intentionally empty
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(); // Intentionally empty
        // Simulate the NFT being created and transferred to the receiver's account (ACCOUNT_3434)
        final var newNft = givenNft(
                        NftID.newBuilder().tokenId(TOKEN_321).serialNumber(1).build())
                .copyBuilder()
                .ownerId(ACCOUNT_1212_ID)
                .build();
        writableNftStore.put(newNft.copyBuilder().ownerId(ACCOUNT_3434_ID).build());
        readableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE);
        context = mockContext();

        given(context.configuration()).willReturn(configuration);
        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(TokenTransferList.newBuilder()
                        .token(TOKEN_321)
                        .nftTransfers(NftTransfer.newBuilder()
                                .serialNumber(1)
                                .senderAccountID(AccountID.newBuilder().accountNum(0))
                                .receiverAccountID(ACCOUNT_3434_ID)
                                .build())
                        .build()));
    }

    @Test
    void handleNftTransfersToExistingAccountSuccess() {
        // This test case handles successfully transferring NFTs only

        // Set up NFTs for token ID 531 (serials 111, 112)
        final var nftId111 =
                NftID.newBuilder().tokenId(TOKEN_321).serialNumber(111).build();
        final var nft111 =
                Nft.newBuilder().nftId(nftId111).ownerId(ACCOUNT_1212_ID).build();
        final var nft112 = nft111.copyBuilder()
                .nftId(nftId111.copyBuilder().serialNumber(112).build())
                .build();
        final var acct1212tokenRel1 = givenNonFungibleTokenRelation()
                .copyBuilder()
                .accountId(ACCOUNT_1212_ID)
                .build();
        final var acct3434tokenRel1 = givenNonFungibleTokenRelation()
                .copyBuilder()
                .accountId(ACCOUNT_3434_ID)
                .build();

        // Set up NFTs for token ID 246 (serials 222, 223)
        final var token246Id = asToken(246);
        final var token264 = Token.newBuilder().tokenId(token246Id).build();
        final var nftId222 =
                NftID.newBuilder().tokenId(token246Id).serialNumber(222).build();
        final var nft222 =
                nft111.copyBuilder().nftId(nftId222).ownerId(ACCOUNT_3434_ID).build();
        final var nft223 = nft222.copyBuilder()
                .nftId(nftId222.copyBuilder().serialNumber(223).build())
                .build();
        final var acct1212tokenRel2 = givenNonFungibleTokenRelation()
                .copyBuilder()
                .accountId(ACCOUNT_1212_ID)
                .build();
        final var acct3434tokenRel2 = givenNonFungibleTokenRelation()
                .copyBuilder()
                .accountId(ACCOUNT_3434_ID)
                .build();

        // Set up stores
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(
                acct1212tokenRel1, acct3434tokenRel1, acct1212tokenRel2, acct3434tokenRel2);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(
                acct1212tokenRel1, acct3434tokenRel1, acct1212tokenRel2, acct3434tokenRel2);
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(nft111, nft112, nft222, nft223);
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(nft111, nft112, nft222, nft223);
        writableNftStore.put(nft111.copyBuilder().ownerId(ACCOUNT_3434_ID).build());
        writableNftStore.put(nft112.copyBuilder().ownerId(ACCOUNT_3434_ID).build());
        writableNftStore.put(nft222.copyBuilder().ownerId(ACCOUNT_1212_ID).build());
        writableNftStore.put(nft223.copyBuilder().ownerId(ACCOUNT_1212_ID).build());
        readableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE, token264);
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE, token264);
        context = mockContext();
        final var config = HederaTestConfigBuilder.create()
                .withValue("staking.isEnabled", String.valueOf(false))
                .getOrCreateConfig();
        given(context.configuration()).willReturn(config);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        // The transfer list should be sorted by token ID, then by serial number
        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(
                        // Expected transfer list for token246
                        TokenTransferList.newBuilder()
                                .token(token246Id)
                                .nftTransfers(
                                        NftTransfer.newBuilder()
                                                .serialNumber(222)
                                                .senderAccountID(ACCOUNT_3434_ID)
                                                .receiverAccountID(ACCOUNT_1212_ID)
                                                .build(),
                                        NftTransfer.newBuilder()
                                                .serialNumber(223)
                                                .senderAccountID(ACCOUNT_3434_ID)
                                                .receiverAccountID(ACCOUNT_1212_ID)
                                                .build())
                                .build(),
                        // Expected transfer list for TOKEN_531
                        TokenTransferList.newBuilder()
                                .token(TOKEN_321)
                                .nftTransfers(
                                        NftTransfer.newBuilder()
                                                .serialNumber(111)
                                                .senderAccountID(ACCOUNT_1212_ID)
                                                .receiverAccountID(ACCOUNT_3434_ID)
                                                .build(),
                                        NftTransfer.newBuilder()
                                                .serialNumber(112)
                                                .senderAccountID(ACCOUNT_1212_ID)
                                                .receiverAccountID(ACCOUNT_3434_ID)
                                                .build())
                                .build()));

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());
        verify(stakingRewardsHandler, never())
                .applyStakingRewards(context, Collections.emptySet(), Collections.emptyMap());
    }

    @Test
    void handleCombinedHbarAndTokenTransfersSuccess() {
        // This test case tests the combined success of hbar, fungible token, and nft transfers

        final var token321Rel = givenFungibleTokenRelation()
                .copyBuilder()
                .tokenId(TOKEN_321)
                .accountId(ACCOUNT_3434_ID)
                .balance(50)
                .build();
        final var token654Id = asToken(654);
        final var token654 = Token.newBuilder().tokenId(token654Id).build();
        final var token654Rel = givenNonFungibleTokenRelation()
                .copyBuilder()
                .tokenId(token654Id)
                .accountId(ACCOUNT_5656_ID)
                .build();
        readableAccountStore = TestStoreFactory.newReadableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434, ACCOUNT_5656);
        writableAccountStore = TestStoreFactory.newWritableStoreWithAccounts(ACCOUNT_1212, ACCOUNT_3434, ACCOUNT_5656);
        readableTokenRelStore = TestStoreFactory.newReadableStoreWithTokenRels(token321Rel, token654Rel);
        writableTokenRelStore = TestStoreFactory.newWritableStoreWithTokenRels(token321Rel, token654Rel);
        final var nft = givenNft(
                        NftID.newBuilder().tokenId(token654Id).serialNumber(2).build())
                .copyBuilder()
                .ownerId(ACCOUNT_5656_ID)
                .build();
        readableNftStore = TestStoreFactory.newReadableStoreWithNfts(nft);
        writableNftStore = TestStoreFactory.newWritableStoreWithNfts(nft);
        // Make hbar changes
        final var hbar1212Change = ACCOUNT_1212.tinybarBalance() - 5;
        writableAccountStore.put(ACCOUNT_1212.copyBuilder().tinybarBalance(5).build());
        writableAccountStore.put(ACCOUNT_3434
                .copyBuilder()
                .tinybarBalance(ACCOUNT_3434.tinybarBalance() + hbar1212Change)
                .build());
        // Make fungible token changes
        final var fungible321Change = token321Rel.balance() - 25;
        final var fungible654Change = token654Rel.balance() - 1;
        writableTokenRelStore.put(token321Rel.copyBuilder().balance(25).build());
        writableTokenRelStore.put(token321Rel
                .copyBuilder()
                .accountId(ACCOUNT_5656_ID)
                .balance(fungible321Change)
                .build());
        writableTokenRelStore.put(
                token654Rel.copyBuilder().balance(fungible654Change).build());
        writableTokenRelStore.put(
                token654Rel.copyBuilder().accountId(ACCOUNT_1212_ID).balance(1).build());
        // Make NFT changes
        writableNftStore.put(nft.copyBuilder().ownerId(ACCOUNT_1212_ID).build());
        writableTokenStore = TestStoreFactory.newWritableStoreWithTokens(TOKEN_321_FUNGIBLE, token654);
        readableTokenStore = TestStoreFactory.newReadableStoreWithTokens(TOKEN_321_FUNGIBLE, token654);
        context = mockContext();
        given(context.configuration()).willReturn(configuration);

        subject.finalizeStakingRecord(context, HederaFunctionality.CRYPTO_DELETE, Collections.emptySet(), emptyMap());

        BDDMockito.verify(recordBuilder)
                .transferList(TransferList.newBuilder()
                        .accountAmounts(
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_1212_ID)
                                        .amount(-hbar1212Change)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(ACCOUNT_3434_ID)
                                        .amount(hbar1212Change)
                                        .build())
                        .build());
        BDDMockito.verify(recordBuilder)
                .tokenTransferLists(List.of(
                        TokenTransferList.newBuilder()
                                .token(TOKEN_321)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_3434_ID)
                                                .amount(-fungible321Change)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ACCOUNT_5656_ID)
                                                .amount(fungible321Change)
                                                .build())
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(token654Id)
                                .nftTransfers(NftTransfer.newBuilder()
                                        .serialNumber(2)
                                        .senderAccountID(ACCOUNT_5656_ID)
                                        .receiverAccountID(ACCOUNT_1212_ID)
                                        .build())
                                .build()));
    }

    private FinalizeContext mockContext() {
        given(context.userTransactionRecordBuilder(CryptoTransferStreamBuilder.class))
                .willReturn(recordBuilder);

        given(context.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(context.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(context.readableStore(ReadableTokenRelationStore.class)).willReturn(readableTokenRelStore);
        given(context.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(context.readableStore(ReadableNftStore.class)).willReturn(readableNftStore);
        given(context.writableStore(WritableNftStore.class)).willReturn(writableNftStore);
        given(context.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);
        given(context.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);

        return context;
    }
}
