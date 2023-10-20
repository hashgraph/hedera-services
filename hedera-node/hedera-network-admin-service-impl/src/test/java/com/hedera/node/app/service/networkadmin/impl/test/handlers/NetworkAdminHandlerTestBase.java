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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.HapiUtils.asTimestamp;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.fixtures.state.FakeSchemaRegistry;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.state.recordcache.DeduplicationCacheImpl;
import com.hedera.node.app.state.recordcache.RecordCacheImpl;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

public class NetworkAdminHandlerTestBase {
    public static final String ACCOUNTS = "ACCOUNTS";
    protected static final String TOKENS = "TOKENS";
    protected static final String TOKEN_RELS = "TOKEN_RELS";

    private static final OneOf<Account.StakedIdOneOfType> UNSET_STAKED_ID =
            new OneOf<>(Account.StakedIdOneOfType.UNSET, null);

    protected final Bytes ledgerId = Bytes.wrap(new byte[] {0});

    protected final AccountID accountId = AccountID.newBuilder().accountNum(3).build();
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final Long accountNum = accountId.accountNum();
    protected final AccountID alias =
            AccountID.newBuilder().alias(Bytes.wrap("testAlias")).build();

    protected final AccountID deleteAccountId =
            AccountID.newBuilder().accountNum(3213).build();
    protected final AccountID transferAccountId =
            AccountID.newBuilder().accountNum(32134).build();

    protected static final long payerBalance = 10_000L;
    protected final TokenID fungibleTokenId = asToken(1L);
    protected final TokenID nonFungibleTokenId = asToken(2L);
    protected final EntityIDPair fungiblePair = EntityIDPair.newBuilder()
            .accountId(accountId)
            .tokenId(fungibleTokenId)
            .build();
    protected final EntityIDPair nonFungiblePair = EntityIDPair.newBuilder()
            .accountId(accountId)
            .tokenId(nonFungibleTokenId)
            .build();

    protected final String tokenName = "test token";
    protected final String tokenSymbol = "TT";
    protected final AccountID treasury = AccountID.newBuilder().accountNum(100).build();
    protected final long autoRenewSecs = 100L;
    protected final long expirationTime = 1_234_567L;
    protected final String memo = "test memo";

    protected MapReadableKVState<AccountID, Account> readableAccounts;
    protected MapReadableKVState<TokenID, Token> readableTokenState;
    protected MapReadableKVState<EntityIDPair, TokenRelation> readableTokenRelState;

    protected ReadableTokenStore readableTokenStore;

    protected ReadableAccountStore readableAccountStore;
    protected ReadableTokenRelationStore readableTokenRelStore;

    protected Token fungibleToken;
    protected Token nonFungibleToken;
    protected Account account;
    protected TokenRelation fungibleTokenRelation;
    protected TokenRelation nonFungibleTokenRelation;

    protected static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1001).build();

    protected TransactionID transactionID;
    protected TransactionID otherNonceOneTransactionID;
    protected TransactionID otherNonceTwoTransactionID;
    protected TransactionID otherNonceThreeTransactionID;
    protected TransactionID transactionIDNotInCache;

    protected TransactionRecord primaryRecord;
    protected TransactionRecord duplicate1;
    protected TransactionRecord duplicate2;
    protected TransactionRecord duplicate3;
    protected TransactionRecord recordOne;
    protected TransactionRecord recordTwo;
    protected TransactionRecord recordThree;

    private static final int MAX_QUERYABLE_PER_ACCOUNT = 10;

    protected RecordCacheImpl cache;

    @Mock
    private DeduplicationCache dedupeCache;

    @Mock
    private WorkingStateAccessor wsa;

    @Mock
    private ConfigProvider props;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected Account deleteAccount;

    @Mock
    protected Account transferAccount;

    @Mock
    private VersionedConfiguration versionedConfig;

    @Mock
    private HederaConfig hederaConfig;

    @Mock
    private LedgerConfig ledgerConfig;

    @Mock
    private NetworkInfo networkInfo;

    @BeforeEach
    void commonSetUp() {
        final var validStartTime = Instant.ofEpochMilli(123456789L); // aligned to millisecond boundary for convenience.
        transactionID = transactionID(validStartTime, 0);
        otherNonceOneTransactionID = transactionID(validStartTime.plusNanos(1), 1);
        otherNonceTwoTransactionID = transactionID(validStartTime.plusNanos(2), 2);
        otherNonceThreeTransactionID = transactionID(validStartTime.plusNanos(3), 3);
        transactionIDNotInCache = transactionID(validStartTime.plusNanos(5), 5);

        givenValidAccount(false, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        refreshStoresWithEntitiesOnlyInReadable();
        refreshRecordCache();
    }

    protected void refreshStoresWithEntitiesOnlyInReadable() {
        givenAccountsInReadableStore();
        givenTokensInReadableStore();
        givenReadableTokenRelsStore();
    }

    protected void refreshRecordCache() {
        final var state = new FakeHederaState();
        final var registry = new FakeSchemaRegistry();
        final var svc = new RecordCacheService();
        svc.registerSchemas(registry);
        registry.migrate(svc.getServiceName(), state, networkInfo);
        lenient().when(wsa.getHederaState()).thenReturn(state);
        lenient().when(props.getConfiguration()).thenReturn(versionedConfig);
        lenient().when(versionedConfig.getConfigData(HederaConfig.class)).thenReturn(hederaConfig);
        lenient().when(hederaConfig.transactionMaxValidDuration()).thenReturn(180L);
        lenient().when(versionedConfig.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        lenient().when(ledgerConfig.recordsMaxQueryableByAccount()).thenReturn(MAX_QUERYABLE_PER_ACCOUNT);
        givenRecordCacheState();
    }

    private void givenAccountsInReadableStore() {
        readableAccounts = readableAccountState();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
    }

    private void givenTokensInReadableStore() {
        readableTokenState = readableTokenState();
        given(readableStates.<TokenID, Token>get(TOKENS)).willReturn(readableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
    }

    private void givenReadableTokenRelsStore() {
        readableTokenRelState = emptyReadableTokenRelsStateBuilder()
                .value(fungiblePair, fungibleTokenRelation)
                .value(nonFungiblePair, nonFungibleTokenRelation)
                .build();
        given(readableStates.<EntityIDPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);
    }

    private void givenRecordCacheState() {
        cache = emptyRecordCacheBuilder();
        final var consensusTimestamp = Instant.ofEpochSecond(123456789L).plusNanos(1000);
        final var receipt = TransactionReceipt.newBuilder()
                .accountID(accountId)
                .status(ResponseCodeEnum.UNKNOWN)
                .build();
        primaryRecord = TransactionRecord.newBuilder()
                .transactionID(transactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp))
                .build();
        duplicate1 = TransactionRecord.newBuilder()
                .transactionID(transactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp.plusMillis(1)))
                .build();
        duplicate2 = TransactionRecord.newBuilder()
                .transactionID(transactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp.plusMillis(2)))
                .build();
        duplicate3 = TransactionRecord.newBuilder()
                .transactionID(transactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp.plusMillis(3)))
                .build();
        recordOne = TransactionRecord.newBuilder()
                .transactionID(otherNonceOneTransactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp.plusNanos(1)))
                .parentConsensusTimestamp(asTimestamp(consensusTimestamp))
                .build();
        recordTwo = TransactionRecord.newBuilder()
                .transactionID(otherNonceTwoTransactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp.plusNanos(2)))
                .parentConsensusTimestamp(asTimestamp(consensusTimestamp))
                .build();
        recordThree = TransactionRecord.newBuilder()
                .transactionID(otherNonceThreeTransactionID)
                .receipt(receipt)
                .consensusTimestamp(asTimestamp(consensusTimestamp.plusNanos(3)))
                .parentConsensusTimestamp(asTimestamp(consensusTimestamp))
                .build();
        cache.add(0, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(primaryRecord)));
        cache.add(1, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(duplicate1)));
        cache.add(2, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(duplicate2)));
        cache.add(3, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(duplicate3)));
        cache.add(0, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(recordOne)));
        cache.add(0, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(recordTwo)));
        cache.add(0, PAYER_ACCOUNT_ID, List.of(singleTransactionRecord(recordThree)));
    }

    private SingleTransactionRecord singleTransactionRecord(TransactionRecord record) {
        return new SingleTransactionRecord(Transaction.DEFAULT, record, List.of());
    }

    protected MapReadableKVState<AccountID, Account> readableAccountState() {
        return emptyReadableAccountStateBuilder()
                .value(accountId, account)
                .value(deleteAccountId, deleteAccount)
                .value(transferAccountId, transferAccount)
                .build();
    }

    @NonNull
    protected MapReadableKVState.Builder<AccountID, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityIDPair, TokenRelation> emptyReadableTokenRelsStateBuilder() {
        return MapReadableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected RecordCacheImpl emptyRecordCacheBuilder() {
        dedupeCache = new DeduplicationCacheImpl(props);
        return new RecordCacheImpl(dedupeCache, wsa, props);
    }

    @NonNull
    protected MapReadableKVState<TokenID, Token> readableTokenState() {
        return MapReadableKVState.<TokenID, Token>builder(TOKENS)
                .value(fungibleTokenId, fungibleToken)
                .value(nonFungibleTokenId, nonFungibleToken)
                .build();
    }

    protected void givenValidFungibleToken() {
        givenValidFungibleToken(autoRenewId);
    }

    protected void givenValidFungibleToken(AccountID autoRenewAccountId) {
        givenValidFungibleToken(autoRenewAccountId, false, false, false, false, true, true);
    }

    protected void givenValidNonFungibleToken() {
        givenValidFungibleToken();
        nonFungibleToken = fungibleToken
                .copyBuilder()
                .tokenId(nonFungibleTokenId)
                .customFees(List.of())
                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                .build();
    }

    protected void givenValidFungibleToken(
            AccountID autoRenewAccountId,
            boolean deleted,
            boolean paused,
            boolean accountsFrozenByDefault,
            boolean accountsKycGrantedByDefault,
            boolean withAdminKey,
            boolean withSubmitKey) {
        fungibleToken = new Token(
                fungibleTokenId,
                tokenName,
                tokenSymbol,
                1000,
                1000,
                treasury,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                deleted,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                autoRenewAccountId,
                autoRenewSecs,
                expirationTime,
                memo,
                100000,
                paused,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                Collections.emptyList());
    }

    protected void givenValidAccount(
            boolean isDeleted,
            @Nullable List<AccountCryptoAllowance> cryptoAllowances,
            @Nullable List<AccountApprovalForAllAllowance> approveForAllNftAllowances,
            @Nullable List<AccountFungibleTokenAllowance> tokenAllowances) {
        account = new Account(
                AccountID.newBuilder().accountNum(accountNum).build(),
                alias.alias(),
                null, //  key,
                1_234_567L,
                payerBalance,
                "testAccount",
                isDeleted,
                1_234L,
                1_234_568L,
                UNSET_STAKED_ID,
                true,
                true,
                TokenID.newBuilder().tokenNum(2L).build(),
                NftID.newBuilder().tokenId(TokenID.newBuilder().tokenNum(2L)).build(),
                1,
                2,
                10,
                1,
                3,
                false,
                2,
                0,
                1000L,
                AccountID.newBuilder().accountNum(2L).build(),
                72000,
                0,
                cryptoAllowances,
                approveForAllNftAllowances,
                tokenAllowances,
                2,
                false,
                null);
    }

    protected void givenFungibleTokenRelation() {
        fungibleTokenRelation = TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(accountId)
                .balance(1000L)
                .frozen(false)
                .kycGranted(false)
                .deleted(false)
                .automaticAssociation(true)
                .nextToken(asToken(0L))
                .previousToken(asToken(3L))
                .build();
    }

    protected void givenNonFungibleTokenRelation() {
        nonFungibleTokenRelation = TokenRelation.newBuilder()
                .tokenId(nonFungibleTokenId)
                .accountId(asAccount(accountNum))
                .balance(1000L)
                .frozen(false)
                .kycGranted(false)
                .deleted(false)
                .automaticAssociation(true)
                .nextToken(asToken(0L))
                .previousToken(asToken(3L))
                .build();
    }

    private TransactionID transactionID(Instant validStartTime, int nonce) {
        return transactionID(validStartTime, 0, nonce);
    }

    private TransactionID transactionID(Instant validStartTime, int nanos, int nonce) {
        return TransactionID.newBuilder()
                .transactionValidStart(Timestamp.newBuilder()
                        .seconds(validStartTime.getEpochSecond())
                        .nanos(nanos))
                .accountID(PAYER_ACCOUNT_ID)
                .nonce(nonce)
                .build();
    }

    protected TransactionID transactionIDWithoutAccount(int nanos, int nonce) {
        final var now = Instant.now();
        return TransactionID.newBuilder()
                .transactionValidStart(
                        Timestamp.newBuilder().seconds(now.getEpochSecond()).nanos(nanos))
                .nonce(nonce)
                .build();
    }
}
