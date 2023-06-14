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

package com.hedera.node.app.service.token.impl.test.handlers.util;

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
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableNftStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CryptoTokenHandlerTestBase extends StateBuilderUtil {
    /* ---------- Keys */
    protected final Key key = A_COMPLEX_KEY;
    protected static final Key payerKey = A_COMPLEX_KEY;
    protected final Key ownerKey = B_COMPLEX_KEY;
    protected final Key spenderKey = C_COMPLEX_KEY;
    protected final Key adminKey = A_COMPLEX_KEY;
    protected final Key pauseKey = B_COMPLEX_KEY;
    protected final Key wipeKey = C_COMPLEX_KEY;
    protected final Key kycKey = A_COMPLEX_KEY;
    protected final Key feeScheduleKey = A_COMPLEX_KEY;
    protected final Key supplyKey = A_COMPLEX_KEY;
    protected final Key freezeKey = A_COMPLEX_KEY;
    protected final Key treasuryKey = C_COMPLEX_KEY;

    /* ---------- Account IDs */
    protected final AccountID payerId = AccountID.newBuilder().accountNum(3).build();
    protected final AccountID deleteAccountId =
            AccountID.newBuilder().accountNum(3213).build();
    protected final AccountID transferAccountId =
            AccountID.newBuilder().accountNum(32134).build();
    protected final AccountID delegatingSpenderId =
            AccountID.newBuilder().accountNum(1234567).build();
    protected final AccountID ownerId =
            AccountID.newBuilder().accountNum(123456).build();
    protected final AccountID treasuryId =
            AccountID.newBuilder().accountNum(1000000).build();
    protected final AccountID autoRenewId = AccountID.newBuilder().accountNum(4).build();
    protected final AccountID spenderId =
            AccountID.newBuilder().accountNum(12345).build();

    /* ---------- Account Numbers ---------- */
    protected final Long accountNum = payerId.accountNum();

    /* ---------- Aliases ----------  */
    protected final AccountID alias =
            AccountID.newBuilder().alias(Bytes.wrap("testAlias")).build();
    protected final byte[] evmAddress = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
    protected final ContractID contractAlias =
            ContractID.newBuilder().evmAddress(Bytes.wrap(evmAddress)).build();
    /*Contracts */
    protected final ContractID contract =
            ContractID.newBuilder().contractNum(1234).build();
    /* ---------- Tokens ---------- */
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
    protected final EntityNumPair ownerFTPair =
            EntityNumPair.fromLongs(ownerId.accountNum(), fungibleTokenNum.longValue());
    protected final EntityNumPair ownerNFTPair =
            EntityNumPair.fromLongs(ownerId.accountNum(), nonFungibleTokenNum.longValue());

    protected final EntityNumPair treasuryFTPair =
            EntityNumPair.fromLongs(treasuryId.accountNum(), fungibleTokenNum.longValue());
    protected final EntityNumPair treasuryNFTPair =
            EntityNumPair.fromLongs(treasuryId.accountNum(), nonFungibleTokenNum.longValue());
    protected final UniqueTokenId uniqueTokenIdSl1 = UniqueTokenId.newBuilder()
            .tokenTypeNumber(nonFungibleTokenId.tokenNum())
            .serialNumber(1L)
            .build();
    protected final UniqueTokenId uniqueTokenIdSl2 = UniqueTokenId.newBuilder()
            .tokenTypeNumber(nonFungibleTokenId.tokenNum())
            .serialNumber(2L)
            .build();

    /* ---------- Allowances --------------- */
    protected final CryptoAllowance cryptoAllowance = CryptoAllowance.newBuilder()
            .spender(spenderId)
            .owner(ownerId)
            .amount(10L)
            .build();
    protected final TokenAllowance tokenAllowance = TokenAllowance.newBuilder()
            .spender(spenderId)
            .amount(10L)
            .tokenId(fungibleTokenId)
            .owner(ownerId)
            .build();
    protected final NftAllowance nftAllowance = NftAllowance.newBuilder()
            .spender(spenderId)
            .owner(ownerId)
            .tokenId(nonFungibleTokenId)
            .serialNumbers(List.of(1L, 2L))
            .build();
    protected final NftAllowance nftAllowanceWithApproveForALl =
            nftAllowance.copyBuilder().approvedForAll(Boolean.TRUE).build();
    protected final NftAllowance nftAllowanceWithDelegatingSpender = NftAllowance.newBuilder()
            .spender(spenderId)
            .owner(ownerId)
            .tokenId(nonFungibleTokenId)
            .approvedForAll(Boolean.FALSE)
            .serialNumbers(List.of(1L, 2L))
            .delegatingSpender(delegatingSpenderId)
            .build();
    /* ---------- Fees ------------------ */
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

    /* ---------- Misc ---------- */
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final Instant consensusInstant = Instant.ofEpochSecond(1_234_567L);
    protected final String tokenName = "test token";
    protected final String tokenSymbol = "TT";
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long autoRenewSecs = 100L;
    protected static final long payerBalance = 10_000L;
    /* ---------- States ---------- */
    protected MapReadableKVState<String, EntityNumValue> readableAliases;
    protected MapReadableKVState<AccountID, Account> readableAccounts;
    protected MapWritableKVState<String, EntityNumValue> writableAliases;
    protected MapWritableKVState<AccountID, Account> writableAccounts;
    protected MapReadableKVState<EntityNum, Token> readableTokenState;
    protected MapWritableKVState<EntityNum, Token> writableTokenState;
    protected MapReadableKVState<EntityNumPair, TokenRelation> readableTokenRelState;
    protected MapWritableKVState<EntityNumPair, TokenRelation> writableTokenRelState;
    protected MapReadableKVState<UniqueTokenId, Nft> readableNftState;
    protected MapWritableKVState<UniqueTokenId, Nft> writableNftState;

    /* ---------- Stores */

    protected ReadableTokenStore readableTokenStore;
    protected WritableTokenStore writableTokenStore;

    protected ReadableAccountStore readableAccountStore;
    protected WritableAccountStore writableAccountStore;
    protected ReadableTokenRelationStore readableTokenRelStore;
    protected WritableTokenRelationStore writableTokenRelStore;
    protected ReadableNftStore readableNftStore;
    protected WritableNftStore writableNftStore;
    /* ---------- Tokens ---------- */
    protected Token fungibleToken;
    protected Token nonFungibleToken;
    protected Nft nftSl1;
    protected Nft nftSl2;
    /* ---------- Token Relations ---------- */
    protected TokenRelation fungibleTokenRelation;
    protected TokenRelation nonFungibleTokenRelation;
    protected TokenRelation ownerFTRelation;
    protected TokenRelation ownerNFTRelation;
    protected TokenRelation treasuryFTRelation;
    protected TokenRelation treasuryNFTRelation;

    /* ---------- Accounts ---------- */
    protected Account account;
    protected Account deleteAccount;
    protected Account transferAccount;
    protected Account ownerAccount;
    protected Account spenderAccount;
    protected Account delegatingSpenderAccount;
    protected Account treasuryAccount;

    private Map<AccountID, Account> accountsMap;
    private Map<Bytes, AccountID> aliasesMap;
    private Map<EntityNum, Token> tokensMap;
    private Map<EntityNumPair, TokenRelation> tokenRelsMap;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    protected Configuration configuration;
    protected VersionedConfigImpl versionedConfig;

    @BeforeEach
    public void setUp() {
        configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        versionedConfig = new VersionedConfigImpl(configuration, 1);
        givenValidAccounts();
        givenValidTokens();
        givenValidTokenRelations();
        setUpAllEntities();
        refreshReadableStores();
    }

    private void setUpAllEntities() {
        accountsMap = new HashMap<>();
        accountsMap.put(payerId, account);
        accountsMap.put(deleteAccountId, deleteAccount);
        accountsMap.put(transferAccountId, transferAccount);
        accountsMap.put(ownerId, ownerAccount);
        accountsMap.put(delegatingSpenderId, delegatingSpenderAccount);
        accountsMap.put(spenderId, spenderAccount);
        accountsMap.put(treasuryId, treasuryAccount);

        tokensMap = new HashMap<>();
        tokensMap.put(fungibleTokenNum, fungibleToken);
        tokensMap.put(nonFungibleTokenNum, nonFungibleToken);

        aliasesMap = new HashMap<>();

        tokenRelsMap = new HashMap<>();
        tokenRelsMap.put(fungiblePair, fungibleTokenRelation);
        tokenRelsMap.put(nonFungiblePair, nonFungibleTokenRelation);
        tokenRelsMap.put(ownerFTPair, ownerFTRelation);
        tokenRelsMap.put(ownerNFTPair, ownerNFTRelation);
        tokenRelsMap.put(treasuryFTPair, treasuryFTRelation);
        tokenRelsMap.put(treasuryNFTPair, treasuryNFTRelation);
    }

    protected void basicMetaAssertions(final PreHandleContext context, final int keysSize) {
        assertThat(context.requiredNonPayerKeys()).hasSize(keysSize);
    }

    protected void refreshReadableStores() {
        givenAccountsInReadableStore();
        givenTokensInReadableStore();
        givenReadableTokenRelsStore();
        givenReadableNftStore();
    }

    protected void refreshWritableStores() {
        givenAccountsInWritableStore();
        givenTokensInWritableStore();
        givenWritableTokenRelsStore();
        givenWritableNftStore();
    }

    private void givenAccountsInReadableStore() {
        readableAccounts = readableAccountState();
        writableAccounts = emptyWritableAccountStateBuilder().build();
        readableAliases = readableAliasState();
        writableAliases = emptyWritableAliasStateBuilder().build();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        writableAccountStore = new WritableAccountStore(writableStates);
    }

    private void givenAccountsInWritableStore() {
        readableAccounts = readableAccountState();
        writableAccounts = writableAccountState();
        readableAliases = readableAliasState();
        writableAliases = writableAliasesState();
        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        given(readableStates.<String, EntityNumValue>get(ALIASES)).willReturn(readableAliases);
        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
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
        writableTokenState = writableTokenState();
        given(readableStates.<EntityNum, Token>get(TOKENS)).willReturn(readableTokenState);
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        readableTokenStore = new ReadableTokenStoreImpl(readableStates);
        writableTokenStore = new WritableTokenStore(writableStates);
    }

    private void givenReadableTokenRelsStore() {
        readableTokenRelState = readableTokenRelState();
        given(readableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(readableTokenRelState);
        readableTokenRelStore = new ReadableTokenRelationStoreImpl(readableStates);
    }

    private void givenWritableTokenRelsStore() {
        writableTokenRelState = writableTokenRelState();
        given(writableStates.<EntityNumPair, TokenRelation>get(TOKEN_RELS)).willReturn(writableTokenRelState);
        writableTokenRelStore = new WritableTokenRelationStore(writableStates);
    }

    private void givenReadableNftStore() {
        readableNftState = emptyReadableNftStateBuilder()
                .value(uniqueTokenIdSl1, nftSl1)
                .value(uniqueTokenIdSl2, nftSl2)
                .build();
        given(readableStates.<UniqueTokenId, Nft>get(NFTS)).willReturn(readableNftState);
        readableNftStore = new ReadableNftStoreImpl(readableStates);
    }

    private void givenWritableNftStore() {
        writableNftState = emptyWritableNftStateBuilder()
                .value(uniqueTokenIdSl1, nftSl1)
                .value(uniqueTokenIdSl2, nftSl2)
                .build();
        given(writableStates.<UniqueTokenId, Nft>get(NFTS)).willReturn(writableNftState);
        writableNftStore = new WritableNftStore(writableStates);
    }

    @NonNull
    protected MapWritableKVState<AccountID, Account> writableAccountState() {
        final var builder = emptyWritableAccountStateBuilder();
        for (final var entry : accountsMap.entrySet()) {
            builder.value(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    @NonNull
    protected MapReadableKVState<AccountID, Account> readableAccountState() {
        final var builder = emptyReadableAccountStateBuilder();
        for (final var entry : accountsMap.entrySet()) {
            builder.value(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private MapWritableKVState<EntityNumPair, TokenRelation> writableTokenRelState() {
        final var builder = emptyWritableTokenRelsStateBuilder();
        for (final var entry : tokenRelsMap.entrySet()) {
            builder.value(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private MapReadableKVState<EntityNumPair, TokenRelation> readableTokenRelState() {
        final var builder = emptyReadableTokenRelsStateBuilder();
        for (final var entry : tokenRelsMap.entrySet()) {
            builder.value(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    @NonNull
    protected MapWritableKVState<String, EntityNumValue> writableAliasesState() {
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
    protected MapWritableKVState<EntityNum, Token> writableTokenState() {
        final var builder = emptyWritableTokenStateBuilder();
        for (final var entry : tokensMap.entrySet()) {
            builder.value(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    @NonNull
    protected MapReadableKVState<EntityNum, Token> readableTokenState() {
        final var builder = emptyReadableTokenStateBuilder();
        for (final var entry : tokensMap.entrySet()) {
            builder.value(entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private void givenValidTokenRelations() {
        fungibleTokenRelation = givenFungibleTokenRelation();
        nonFungibleTokenRelation = givenNonFungibleTokenRelation();
        ownerFTRelation = givenFungibleTokenRelation()
                .copyBuilder()
                .accountNumber(ownerId.accountNum())
                .build();
        ownerNFTRelation = givenNonFungibleTokenRelation()
                .copyBuilder()
                .accountNumber(ownerId.accountNum())
                .build();
        treasuryFTRelation = givenFungibleTokenRelation()
                .copyBuilder()
                .accountNumber(treasuryId.accountNum())
                .build();
        treasuryNFTRelation = givenNonFungibleTokenRelation()
                .copyBuilder()
                .accountNumber(treasuryId.accountNum())
                .build();
    }

    private void givenValidTokens() {
        fungibleToken = givenValidFungibleToken();
        nonFungibleToken = givenValidNonFungibleToken();
        nftSl1 = givenNft(uniqueTokenIdSl1);
        nftSl2 = givenNft(uniqueTokenIdSl2);
    }

    private void givenValidAccounts() {
        account = givenValidAccount();
        spenderAccount = givenValidAccount()
                .copyBuilder()
                .key(spenderKey)
                .accountNumber(spenderId.accountNum())
                .build();
        ownerAccount = givenValidAccount()
                .copyBuilder()
                .accountNumber(ownerId.accountNum())
                .cryptoAllowances(AccountCryptoAllowance.newBuilder()
                        .spenderNum(spenderId.accountNum())
                        .amount(100)
                        .build())
                .tokenAllowances(AccountFungibleTokenAllowance.newBuilder()
                        .tokenNum(fungibleTokenId.tokenNum())
                        .spenderNum(spenderId.accountNum())
                        .amount(100)
                        .build())
                .approveForAllNftAllowances(AccountApprovalForAllAllowance.newBuilder()
                        .tokenNum(nonFungibleTokenNum.longValue())
                        .spenderNum(spenderId.accountNum())
                        .build())
                .key(ownerKey)
                .build();
        delegatingSpenderAccount = givenValidAccount()
                .copyBuilder()
                .accountNumber(delegatingSpenderId.accountNum())
                .build();
        treasuryAccount = givenValidAccount()
                .copyBuilder()
                .accountNumber(treasuryId.accountNum())
                .key(treasuryKey)
                .build();
    }

    protected Token givenValidFungibleToken() {
        return givenValidFungibleToken(autoRenewId.accountNum());
    }

    protected Token givenValidFungibleToken(long autoRenewAccountNumber) {
        return givenValidFungibleToken(autoRenewAccountNumber, false, false, false, false);
    }

    protected Token givenValidFungibleToken(
            long autoRenewAccountNumber,
            boolean deleted,
            boolean paused,
            boolean accountsFrozenByDefault,
            boolean accountsKycGrantedByDefault) {
        return new Token(
                fungibleTokenId.tokenNum(),
                tokenName,
                tokenSymbol,
                1000,
                1000,
                treasuryId.accountNum(),
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
                TokenSupplyType.FINITE,
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

    protected Token givenValidNonFungibleToken() {
        givenValidFungibleToken();
        return fungibleToken
                .copyBuilder()
                .tokenNumber(nonFungibleTokenNum.longValue())
                .treasuryAccountNumber(treasuryId.accountNum())
                .customFees(List.of())
                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                .build();
    }

    protected Account givenValidAccount() {
        return new Account(
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

    protected TokenRelation givenFungibleTokenRelation() {
        return TokenRelation.newBuilder()
                .tokenNumber(fungibleTokenId.tokenNum())
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

    protected TokenRelation givenNonFungibleTokenRelation() {
        return TokenRelation.newBuilder()
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

    protected Nft givenNft(UniqueTokenId uniqueTokenId) {
        return Nft.newBuilder()
                .ownerNumber(ownerId.accountNum())
                .id(uniqueTokenId)
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

    protected void givenStoresAndConfig(final ConfigProvider configProvider, final HandleContext handleContext) {
        configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);

        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(readableTokenRelStore);
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);

        given(handleContext.readableStore(ReadableNftStore.class)).willReturn(readableNftStore);
        given(handleContext.writableStore(WritableNftStore.class)).willReturn(writableNftStore);
    }
}
