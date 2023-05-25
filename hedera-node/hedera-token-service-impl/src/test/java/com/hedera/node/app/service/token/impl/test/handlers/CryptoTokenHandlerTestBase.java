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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.protoToPbj;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static com.hedera.test.utils.KeyUtils.C_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenSupplyType;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

public class CryptoTokenHandlerTestBase {
    public static final String ACCOUNTS = "ACCOUNTS";
    protected static final String ALIASES = "ALIASES";
    protected static final String TOKENS = "TOKENS";
    protected static final String TOKEN_RELS = "TOKEN_RELS";

    protected final Key key = A_COMPLEX_KEY;

    protected static final Key payerKey = A_COMPLEX_KEY;

    protected final AccountID id = AccountID.newBuilder().accountNum(3).build();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final Key accountKey = A_COMPLEX_KEY;

    protected final Long accountNum = id.accountNum();
    protected final EntityNumVirtualKey accountEntityNumVirtualKey = new EntityNumVirtualKey(accountNum);
    protected final AccountID alias =
            AccountID.newBuilder().alias(Bytes.wrap("testAlias")).build();
    protected final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
    protected final ContractID contractAlias =
            ContractID.newBuilder().evmAddress(Bytes.wrap(evmAddress)).build();
    protected final ContractID contract =
            ContractID.newBuilder().contractNum(1234).build();

    protected final AccountID deleteAccountId =
            AccountID.newBuilder().accountNum(3213).build();
    protected final AccountID transferAccountId =
            AccountID.newBuilder().accountNum(32134).build();
    protected final Long deleteAccountNum = deleteAccountId.accountNum();
    protected final Long transferAccountNum = transferAccountId.accountNum();

    protected final TokenID nft = TokenID.newBuilder().tokenNum(56789).build();
    protected final TokenID tokenID = TokenID.newBuilder().tokenNum(6789).build();
    protected final AccountID spender = AccountID.newBuilder().accountNum(12345).build();
    protected final AccountID delegatingSpender =
            AccountID.newBuilder().accountNum(1234567).build();
    protected final AccountID owner = AccountID.newBuilder().accountNum(123456).build();
    protected final Key ownerKey = B_COMPLEX_KEY;
    protected final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
            .spender(spender)
            .owner(owner)
            .amount(10L)
            .build();
    protected final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
            .spender(spender)
            .amount(10L)
            .tokenId(tokenID)
            .owner(owner)
            .build();
    protected static final long defaultAutoRenewPeriod = 720000L;
    protected static final long payerBalance = 10_000L;
    protected static final HederaKey payerHederaKey = asHederaKey(payerKey).get();
    protected final Key adminKey = A_COMPLEX_KEY;
    protected final Key pauseKey = B_COMPLEX_KEY;
    protected final Key wipeKey = C_COMPLEX_KEY;
    protected final Key kycKey = A_COMPLEX_KEY;
    protected final Key feeScheduleKey = A_COMPLEX_KEY;
    protected final Key supplyKey = A_COMPLEX_KEY;
    protected final Key freezeKey = A_COMPLEX_KEY;
    protected final AccountID payerId = protoToPbj(asAccount("0.0.3"), AccountID.class);
    protected final AccountID treasury = protoToPbj(asAccount("0.0.100"), AccountID.class);
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final HederaKey adminHederaKey = asHederaKey(adminKey).get();
    protected final HederaKey wipeHederaKey = asHederaKey(wipeKey).get();
    protected final HederaKey supplyHederaKey = asHederaKey(supplyKey).get();
    protected final HederaKey kycHederaKey = asHederaKey(kycKey).get();
    protected final HederaKey freezeHederaKey = asHederaKey(freezeKey).get();
    protected final HederaKey feeScheduleHederaKey = asHederaKey(feeScheduleKey).get();
    protected final HederaKey pauseHederaKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final EntityNum fungibleTokenNum = EntityNum.fromLong(1L);
    protected final TokenID fungibleTokenId =
            TokenID.newBuilder().tokenNum(fungibleTokenNum.longValue()).build();
    protected final EntityNum nonFungibleTokenNum = EntityNum.fromLong(2L);
    protected final TokenID nonFungibleTokenId =
            TokenID.newBuilder().tokenNum(nonFungibleTokenNum.longValue()).build();
    protected final EntityNumPair fungiblePair =
            EntityNumPair.fromLongs(accountNum.longValue(), fungibleTokenNum.longValue());
    protected final EntityNumPair nonFungiblePair =
            EntityNumPair.fromLongs(accountNum.longValue(), nonFungibleTokenNum.longValue());
    protected final TokenID tokenId =
            TokenID.newBuilder().tokenNum(fungibleTokenNum.longValue()).build();
    protected final String tokenName = "test token";
    protected final String tokenSymbol = "TT";
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long autoRenewSecs = 100L;
    protected FixedFee fixedFee = FixedFee.newBuilder()
            .amount(1_000L)
            .denominatingTokenId(TokenID.newBuilder().tokenNum(1L).build())
            .build();
    protected FractionalFee fractionalFee = FractionalFee.newBuilder()
            .maximumAmount(1_000L)
            .minimumAmount(1L)
            .fractionalAmount(Fraction.newBuilder().numerator(1).denominator(2).build())
            .build();
    protected RoyaltyFee royaltyFee = RoyaltyFee.newBuilder()
            .exchangeValueFraction(
                    Fraction.newBuilder().numerator(1).denominator(2).build())
            .fallbackFee(fixedFee)
            .build();
    protected List<CustomFee> customFees = List.of(withFixedFee(fixedFee), withFractionalFee(fractionalFee));

    protected TokensConfig tokensConfig;
    protected MapReadableKVState<String, EntityNumValue> readableAliases;
    protected MapReadableKVState<EntityNumVirtualKey, Account> readableAccounts;
    protected MapWritableKVState<String, EntityNumValue> writableAliases;
    protected MapWritableKVState<EntityNumVirtualKey, Account> writableAccounts;
    protected MapReadableKVState<EntityNum, Token> readableTokenState;
    protected MapWritableKVState<EntityNum, Token> writableTokenState;
    protected MapReadableKVState<EntityNumPair, TokenRelation> readableTokenRelState;

    protected ReadableTokenStore readableTokenStore;
    protected WritableTokenStore writableTokenStore;

    protected ReadableAccountStore readableAccountStore;
    protected WritableAccountStore writableAccountStore;
    protected ReadableTokenRelationStore readableTokenRelStore;

    protected Token fungibleToken;
    protected Token nonFungibleToken;
    protected Account account;
    protected TokenRelation fungibleTokenRelation;
    protected TokenRelation nonFungibleTokenRelation;

    @Mock
    protected Account deleteAccount;

    @Mock
    protected Account transferAccount;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock
    protected CryptoSignatureWaiversImpl waivers;

    protected Configuration configuration;

    @BeforeEach
    public void setUp() {
        configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        tokensConfig = configuration.getConfigData(TokensConfig.class);
        givenValidAccount();
        givenValidFungibleToken();
        givenValidNonFungibleToken();
        givenFungibleTokenRelation();
        givenNonFungibleTokenRelation();
        refreshStoresWithEntitiesOnlyInReadable();
    }

    protected void basicMetaAssertions(final PreHandleContext context, final int keysSize) {
        assertThat(context.requiredNonPayerKeys()).hasSize(keysSize);
    }

    protected void resetStores() {
        readableAccounts = emptyReadableAccountStateBuilder().build();
        writableAccounts = emptyWritableAccountStateBuilder().build();
        readableAliases = emptyReadableAliasStateBuilder().build();
        writableAliases = emptyWritableAliasStateBuilder().build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<String, EntityNumValue>get(ALIASES)).willReturn(writableAliases);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        writableAccountStore = new WritableAccountStore(writableStates);
    }

    protected void refreshStoresWithEntitiesOnlyInReadable() {
        givenAccountsInReadableStore();
        givenTokensInReadableStore();
        givenReadableTokenRelsStore();
    }

    protected void refreshStoresWithEntitiesInWritable() {
        givenAccountsInWritableStore();
        givenTokensInWritableStore();
        givenReadableTokenRelsStore();
    }

    private void givenAccountsInReadableStore() {
        readableAccounts = readableAccountState();
        writableAccounts = emptyWritableAccountStateBuilder().build();
        readableAliases = readableAliasState();
        writableAliases = emptyWritableAliasStateBuilder().build();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        writableAccountStore = new WritableAccountStore(writableStates);
    }

    private void givenAccountsInWritableStore() {
        readableAccounts = readableAccountState();
        writableAccounts = writableAccountStateWithOneKey();
        readableAliases = readableAliasState();
        writableAliases = writableAliasesStateWithOneKey();
        given(readableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<EntityNumVirtualKey, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        given(writableStates.<String, EntityNumValue>get(ALIASES)).willReturn(writableAliases);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        writableAccountStore = new WritableAccountStore(writableStates);
    }

    private void givenTokensInReadableStore() {
        readableTokenState = readableTokenState();
        writableTokenState = emptyWritableTokenState();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
        writableTokenStore = new WritableTokenStore(writableStates);
    }

    private void givenTokensInWritableStore() {
        readableTokenState = readableTokenState();
        writableTokenState = writableTokenStateWithTwoKeys();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
        writableTokenStore = new WritableTokenStore(writableStates);
    }

    private void givenReadableTokenRelsStore() {
        readableTokenRelState = emptyReadableTokenRelsStateBuilder()
                .value(fungiblePair, fungibleTokenRelation)
                .value(nonFungiblePair, nonFungibleTokenRelation)
                .build();
        given(readableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);
    }

    @NonNull
    protected MapWritableKVState<EntityNumVirtualKey, Account> writableAccountStateWithOneKey() {
        return emptyWritableAccountStateBuilder()
                .value(accountEntityNumVirtualKey, account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNumVirtualKey, Account> readableAccountState() {
        return emptyReadableAccountStateBuilder()
                .value(accountEntityNumVirtualKey, account)
                .value(EntityNumVirtualKey.fromLong(deleteAccountNum), deleteAccount)
                .value(EntityNumVirtualKey.fromLong(transferAccountNum), transferAccount)
                .build();
    }

    @NonNull
    protected MapWritableKVState<String, EntityNumValue> writableAliasesStateWithOneKey() {
        return emptyWritableAliasStateBuilder()
                .value(alias.toString(), new EntityNumValue(accountNum))
                .value(contractAlias.toString(), new EntityNumValue(contract.contractNum()))
                .build();
    }

    @NonNull
    protected MapReadableKVState<String, EntityNumValue> readableAliasState() {
        return emptyReadableAliasStateBuilder()
                .value(alias.toString(), new EntityNumValue(accountNum))
                .value(contractAlias.toString(), new EntityNumValue(contract.contractNum()))
                .build();
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNumVirtualKey, Account> emptyReadableAccountStateBuilder() {
        return MapReadableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapWritableKVState.Builder<EntityNumVirtualKey, Account> emptyWritableAccountStateBuilder() {
        return MapWritableKVState.builder(ACCOUNTS);
    }

    @NonNull
    protected MapReadableKVState.Builder<EntityNumPair, TokenRelation> emptyReadableTokenRelsStateBuilder() {
        return MapReadableKVState.builder(TOKEN_RELS);
    }

    @NonNull
    protected MapWritableKVState.Builder<String, EntityNumValue> emptyWritableAliasStateBuilder() {
        return MapWritableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapReadableKVState.Builder<String, EntityNumValue> emptyReadableAliasStateBuilder() {
        return MapReadableKVState.builder(ALIASES);
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Token> emptyWritableTokenState() {
        return MapWritableKVState.<EntityNum, Token>builder(TOKENS).build();
    }

    @NonNull
    protected MapWritableKVState<EntityNum, Token> writableTokenStateWithTwoKeys() {
        return MapWritableKVState.<EntityNum, Token>builder(TOKENS)
                .value(fungibleTokenNum, fungibleToken)
                .value(nonFungibleTokenNum, nonFungibleToken)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNum, Token> readableTokenState() {
        return MapReadableKVState.<EntityNum, Token>builder(TOKENS)
                .value(fungibleTokenNum, fungibleToken)
                .value(nonFungibleTokenNum, nonFungibleToken)
                .build();
    }

    protected void givenValidFungibleToken() {
        givenValidFungibleToken(autoRenewId.accountNum());
    }

    protected void givenValidFungibleToken(long autoRenewAccountNumber) {
        givenValidFungibleToken(autoRenewAccountNumber, false, false, false, false, true, true);
    }

    protected void givenValidFungibleToken(
            long autoRenewAccountNumber,
            boolean deleted,
            boolean paused,
            boolean accountsFrozenByDefault,
            boolean accountsKycGrantedByDefault,
            boolean withAdminKey,
            boolean withSubmitKey) {
        fungibleToken = new Token(
                tokenId.tokenNum(),
                tokenName,
                tokenSymbol,
                1000,
                1000,
                treasury.accountNum(),
                adminKey,
                kycKey,
                freezeKey,
                wipeKey,
                supplyKey,
                feeScheduleKey,
                pauseKey,
                0,
                deleted,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                autoRenewAccountNumber,
                autoRenewSecs,
                expirationTime,
                memo,
                100000,
                paused,
                accountsFrozenByDefault,
                accountsKycGrantedByDefault,
                Collections.emptyList());
    }

    protected void givenValidNonFungibleToken() {
        givenValidFungibleToken();
        nonFungibleToken = fungibleToken
                .copyBuilder()
                .tokenNumber(nonFungibleTokenNum.longValue())
                .customFees(List.of())
                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                .build();
    }

    protected void givenValidAccount() {
        account = new Account(
                accountNum,
                alias.alias(),
                key,
                1_234_567L,
                payerBalance,
                "testAccount",
                false,
                1_234L,
                1_234_568L,
                0,
                true,
                true,
                3,
                2,
                1,
                2,
                10,
                1,
                3,
                false,
                2,
                0,
                1000L,
                2,
                72000,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                2,
                false,
                null);
    }

    protected void givenFungibleTokenRelation() {
        fungibleTokenRelation = TokenRelation.newBuilder()
                .tokenNumber(tokenId.tokenNum())
                .accountNumber(accountNum)
                .balance(1000L)
                .frozen(false)
                .kycGranted(false)
                .deleted(false)
                .automaticAssociation(true)
                .nextToken(2L)
                .previousToken(3L)
                .build();
    }

    protected void givenNonFungibleTokenRelation() {
        nonFungibleTokenRelation = TokenRelation.newBuilder()
                .tokenNumber(nonFungibleTokenNum.longValue())
                .accountNumber(accountNum)
                .balance(1000L)
                .frozen(false)
                .kycGranted(false)
                .deleted(false)
                .automaticAssociation(true)
                .nextToken(2L)
                .previousToken(3L)
                .build();
    }

    protected CustomFee withFixedFee(final FixedFee fixedFee) {
        return CustomFee.newBuilder()
                .feeCollectorAccountId(
                        AccountID.newBuilder().accountNum(accountNum).build())
                .fixedFee(fixedFee)
                .build();
    }

    protected CustomFee withFractionalFee(final FractionalFee fractionalFee) {
        return CustomFee.newBuilder()
                .fractionalFee(fractionalFee)
                .feeCollectorAccountId(
                        AccountID.newBuilder().accountNum(accountNum).build())
                .build();
    }

    protected CustomFee withRoyaltyFee(final RoyaltyFee royaltyFee) {
        return CustomFee.newBuilder()
                .royaltyFee(royaltyFee)
                .feeCollectorAccountId(
                        AccountID.newBuilder().accountNum(accountNum).build())
                .build();
    }
}
