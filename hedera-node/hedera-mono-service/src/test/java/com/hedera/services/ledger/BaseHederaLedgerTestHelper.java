/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.ALIAS;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.test.mocks.TestContextValidator.TEST_VALIDATOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.backing.BackingTokenRels;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.MutableEntityAccess;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class BaseHederaLedgerTestHelper {
    protected OptionValidator validator = TEST_VALIDATOR;

    protected long GENESIS_BALANCE = 50_000_000_000L;
    protected long NEXT_ID = 1_000_000L;
    protected AccountID genesis = AccountID.newBuilder().setAccountNum(2).build();

    protected HederaLedger subject;

    protected MutableEntityAccess mutableEntityAccess;
    protected SideEffectsTracker sideEffectsTracker;
    protected HederaTokenStore tokenStore;
    protected EntityIdSource ids;
    protected ExpiringCreations creator;
    protected RecordsHistorian historian;
    protected TransferLogic transferLogic;
    protected TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    protected TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
    protected TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;
    protected TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRelsLedger;
    protected AccountID misc = AccountID.newBuilder().setAccountNum(1_234).build();
    protected long MISC_BALANCE = 1_234L;
    protected long RAND_BALANCE = 2_345L;
    protected long miscFrozenTokenBalance = 500L;
    protected MerkleAccount account;
    protected MerkleToken frozenToken;
    protected MerkleToken token;
    protected TokenID missingId = IdUtils.tokenWith(333);
    protected TokenID tokenId = IdUtils.tokenWith(222);
    protected TokenID frozenId = IdUtils.tokenWith(111);
    protected ByteString alias =
            ByteString.copyFromUtf8("These aren't the droids you're looking for");
    protected HederaAccountCustomizer noopCustomizer = new HederaAccountCustomizer();
    protected AccountID deletable = AccountID.newBuilder().setAccountNum(666).build();
    protected AccountID rand = AccountID.newBuilder().setAccountNum(2_345).build();
    protected AccountID aliasAccountId = AccountID.newBuilder().setAlias(alias).build();
    protected AccountID deleted = AccountID.newBuilder().setAccountNum(3_456).build();
    protected AccountID detached = AccountID.newBuilder().setAccountNum(4_567).build();

    protected void commonSetup() {
        sideEffectsTracker = mock(SideEffectsTracker.class);
        creator = mock(ExpiringCreations.class);
        historian = mock(RecordsHistorian.class);

        ids =
                new EntityIdSource() {
                    long nextId = NEXT_ID;

                    @Override
                    public TopicID newTopicId(final AccountID sponsor) {
                        return TopicID.newBuilder().setTopicNum(nextId++).build();
                    }

                    @Override
                    public AccountID newAccountId(AccountID newAccountSponsor) {
                        return AccountID.newBuilder().setAccountNum(nextId++).build();
                    }

                    @Override
                    public ContractID newContractId(AccountID newContractSponsor) {
                        return ContractID.newBuilder().setContractNum(nextId++).build();
                    }

                    @Override
                    public FileID newFileId(AccountID newFileSponsor) {
                        return FileID.newBuilder().setFileNum(nextId++).build();
                    }

                    @Override
                    public TokenID newTokenId(AccountID sponsor) {
                        return TokenID.newBuilder().setTokenNum(nextId++).build();
                    }

                    @Override
                    public ScheduleID newScheduleId(AccountID sponsor) {
                        return ScheduleID.newBuilder().setScheduleNum(nextId++).build();
                    }

                    @Override
                    public void reclaimLastId() {
                        nextId--;
                    }

                    @Override
                    public void reclaimProvisionalIds() {}

                    @Override
                    public void resetProvisionalIds() {}
                };
    }

    protected AccountAmount aa(AccountID account, long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
    }

    protected void addToLedger(AccountID id, long balance, Map<TokenID, TokenInfo> tokenInfo) {
        when(accountsLedger.get(id, EXPIRY)).thenReturn(1_234_567_890L);
        when(accountsLedger.get(id, PROXY)).thenReturn(new EntityId(0, 0, 1_234L));
        when(accountsLedger.get(id, AUTO_RENEW_PERIOD)).thenReturn(7776000L);
        when(accountsLedger.get(id, BALANCE)).thenReturn(balance);
        when(accountsLedger.get(id, IS_DELETED)).thenReturn(false);
        when(accountsLedger.get(id, IS_RECEIVER_SIG_REQUIRED)).thenReturn(true);
        when(accountsLedger.get(id, IS_SMART_CONTRACT)).thenReturn(false);
        when(accountsLedger.get(id, MAX_AUTOMATIC_ASSOCIATIONS)).thenReturn(8);
        when(accountsLedger.get(id, USED_AUTOMATIC_ASSOCIATIONS)).thenReturn(5);
        when(accountsLedger.exists(id)).thenReturn(true);
        // and:
        final int numPositiveBalances = 6;
        for (TokenID tId : tokenInfo.keySet()) {
            var info = tokenInfo.get(tId);
            var relationship = BackingTokenRels.asTokenRel(id, tId);
            when(tokenRelsLedger.get(relationship, TOKEN_BALANCE)).thenReturn(info.balance);
        }
        when(accountsLedger.get(id, NUM_POSITIVE_BALANCES)).thenReturn(numPositiveBalances);
    }

    protected void addDeletedAccountToLedger(AccountID id) {
        when(accountsLedger.get(id, BALANCE)).thenReturn(0L);
        when(accountsLedger.get(id, IS_DELETED)).thenReturn(true);
    }

    protected void addToLedger(AccountID id, long balance, HederaAccountCustomizer customizer) {
        addToLedger(id, balance, Collections.emptyMap());
    }

    protected void setupWithMockLedger() {
        var freezeKey = new JEd25519Key("w/e".getBytes());

        account = mock(MerkleAccount.class);

        frozenToken = mock(MerkleToken.class);
        given(frozenToken.freezeKey()).willReturn(Optional.of(freezeKey));
        given(frozenToken.accountsAreFrozenByDefault()).willReturn(true);
        token = mock(MerkleToken.class);
        given(token.freezeKey()).willReturn(Optional.empty());

        nftsLedger = mock(TransactionalLedger.class);
        accountsLedger = mock(TransactionalLedger.class);
        tokenRelsLedger = mock(TransactionalLedger.class);
        tokensLedger = mock(TransactionalLedger.class);
        addToLedger(
                misc,
                MISC_BALANCE,
                Map.of(frozenId, new TokenInfo(miscFrozenTokenBalance, frozenToken)));
        addToLedger(deletable, MISC_BALANCE, Map.of(frozenId, new TokenInfo(0, frozenToken)));
        addToLedger(rand, RAND_BALANCE, noopCustomizer);
        given(accountsLedger.get(rand, ALIAS)).willReturn(ByteString.EMPTY);
        addToLedger(aliasAccountId, RAND_BALANCE, noopCustomizer);
        given(accountsLedger.get(aliasAccountId, ALIAS)).willReturn(alias);
        addToLedger(genesis, GENESIS_BALANCE, noopCustomizer);
        addToLedger(detached, 0L, new HederaAccountCustomizer().expiry(1_234_567L));
        addDeletedAccountToLedger(deleted);
        given(tokenRelsLedger.isInTransaction()).willReturn(true);

        tokenStore = mock(HederaTokenStore.class);
        given(tokenStore.exists(frozenId)).willReturn(true);
        given(tokenStore.exists(tokenId)).willReturn(true);
        given(tokenStore.exists(missingId)).willReturn(false);
        given(tokenStore.resolve(missingId)).willReturn(TokenStore.MISSING_TOKEN);
        given(tokenStore.resolve(frozenId)).willReturn(frozenId);
        given(tokenStore.resolve(tokenId)).willReturn(tokenId);
        given(tokenStore.get(frozenId)).willReturn(frozenToken);
        sideEffectsTracker = mock(SideEffectsTracker.class);
        mutableEntityAccess = mock(MutableEntityAccess.class);
        final var autoCreationLogic = mock(AutoCreationLogic.class);
        subject =
                new HederaLedger(
                        tokenStore,
                        ids,
                        creator,
                        validator,
                        sideEffectsTracker,
                        historian,
                        tokensLedger,
                        accountsLedger,
                        transferLogic,
                        autoCreationLogic);
        subject.setTokenRelsLedger(tokenRelsLedger);
        subject.setNftsLedger(nftsLedger);
        subject.setTokenRelsLedger(tokenRelsLedger);
        subject.setMutableEntityAccess(mutableEntityAccess);
    }

    protected void givenOkTokenXfers(AccountID misc, TokenID tokenId, long i) {
        given(tokenStore.adjustBalance(misc, tokenId, i)).willReturn(OK);
    }

    protected static class TokenInfo {
        final long balance;
        final MerkleToken token;

        public TokenInfo(long balance, MerkleToken token) {
            this.balance = balance;
            this.token = token;
        }
    }
}
