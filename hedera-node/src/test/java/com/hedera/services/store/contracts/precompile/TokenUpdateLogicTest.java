/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.StringValue;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdateLogicTest {
    private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L, 890);
    private static final TokenID fungible = IdUtils.asToken("0.0.888");
    private static final TokenID nonFungible = IdUtils.asToken("0.0.889");
    private static final AccountID account = IdUtils.asAccount("0.0.3");
    private static final AccountID treasury = IdUtils.asAccount("0.0.4");
    private static final Timestamp EXPIRY = Timestamp.getDefaultInstance();
    private static final EntityId treasuryId = EntityId.fromGrpcAccountId(treasury);
    @Mock private OptionValidator validator;
    @Mock private HederaTokenStore store;
    @Mock private WorldLedgers ledgers;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private MerkleToken merkleToken;
    @Mock private TransactionBody transactionBody;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts;

    @Mock
    private TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRels;

    @Mock private TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nfts;

    private TokenUpdateLogic subject;
    private TokenUpdateTransactionBody op;

    @BeforeEach
    private void setup() {
        subject =
                new TokenUpdateLogic(
                        true, validator, store, ledgers, sideEffectsTracker, sigImpactHistorian);
    }

    @Test
    void updateTokenHappyPathForFungibleToken() {
        // given
        givenValidTransactionBody(true);
        givenContextForSuccessFullCalls();
        givenLedgers();
        givenHederaStoreContextForFungible();
        givenKeys();
        given(merkleToken.tokenType()).willReturn(FUNGIBLE_COMMON);
        given(merkleToken.treasury()).willReturn(treasuryId);
        given(store.adjustBalance(treasury, fungible, -100L)).willReturn(OK);
        given(store.adjustBalance(account, fungible, 100L)).willReturn(OK);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME.getEpochSecond());
        // then
        verify(store).update(op, CONSENSUS_TIME.getEpochSecond());
        verify(sigImpactHistorian).markEntityChanged(fungible.getTokenNum());
    }

    @Test
    void updateTokenForFungibleTokenFailsWhenTransferingBetweenTreasuries() {
        // given
        givenValidTransactionBody(true);
        givenContextForSuccessFullCalls();
        givenLedgers();
        givenHederaStoreContextForFungible();
        givenKeys();
        given(merkleToken.tokenType()).willReturn(FUNGIBLE_COMMON);
        given(merkleToken.treasury()).willReturn(treasuryId);
        given(store.adjustBalance(treasury, fungible, -100L)).willReturn(FAIL_INVALID);
        given(ledgers.nfts()).willReturn(nfts);
        // then
        Assertions.assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME.getEpochSecond()));
    }

    @Test
    void updateTokenForFungibleTokenWithoutAdminKey() {
        // given
        givenValidTransactionBody(true);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(store.get(fungible)).willReturn(merkleToken);
        // then
        Assertions.assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME.getEpochSecond()));
    }

    @Test
    void updateTokenFailsWithAutoAssosiationErrorForFungibleToken() {
        // given
        givenValidTransactionBody(true);
        givenContextForSuccessFullCalls();
        given(ledgers.accounts()).willReturn(accounts);
        given(ledgers.tokenRels()).willReturn(tokenRels);
        given(ledgers.nfts()).willReturn(nfts);
        given(store.get(fungible)).willReturn(merkleToken);
        given(store.associationExists(any(), any())).willReturn(false);
        given(store.autoAssociate(any(), any())).willReturn(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
        // then
        Assertions.assertThrows(
                InvalidTransactionException.class,
                () -> subject.updateToken(op, CONSENSUS_TIME.getEpochSecond()));
    }

    @Test
    void updateTokenHappyPathForNonFungibleToken() {
        // given
        givenValidTransactionBody(false);
        givenContextForSuccessFullCalls();
        givenLedgers();
        givenHederaStoreContextForNonFungible();
        given(merkleToken.tokenType()).willReturn(NON_FUNGIBLE_UNIQUE);
        given(merkleToken.treasury()).willReturn(treasuryId);
        given(transactionBody.getTokenUpdate()).willReturn(op);
        // when
        subject.validate(transactionBody);
        subject.updateToken(op, CONSENSUS_TIME.getEpochSecond());
        // then
        verify(store).update(op, CONSENSUS_TIME.getEpochSecond());
        verify(sigImpactHistorian).markEntityChanged(nonFungible.getTokenNum());
    }

    private void givenContextForSuccessFullCalls() {
        given(merkleToken.hasAdminKey()).willReturn(true);
        given(merkleToken.hasAutoRenewAccount()).willReturn(true);
        given(merkleToken.autoRenewAccount()).willReturn(treasuryId);
        given(validator.isValidExpiry(EXPIRY)).willReturn(true);
        given(validator.expiryStatusGiven(accounts, account)).willReturn(OK);
        given(validator.expiryStatusGiven(accounts, treasury)).willReturn(OK);
    }

    private void givenLedgers() {
        given(ledgers.accounts()).willReturn(accounts);
        given(ledgers.tokenRels()).willReturn(tokenRels);
        given(accounts.get(any(), any())).willReturn(3);
        given(tokenRels.get(any(), any())).willReturn(100L);
    }

    private void givenHederaStoreContextForFungible() {
        given(store.get(fungible)).willReturn(merkleToken);
        given(store.autoAssociate(any(), any())).willReturn(OK);
        given(store.update(op, CONSENSUS_TIME.getEpochSecond())).willReturn(OK);
    }

    private void givenHederaStoreContextForNonFungible() {
        final var nftId =
                new NftId(
                        nonFungible.getShardNum(),
                        nonFungible.getRealmNum(),
                        nonFungible.getTokenNum(),
                        -1);
        given(store.get(nonFungible)).willReturn(merkleToken);
        given(store.autoAssociate(any(), any())).willReturn(OK);
        given(store.update(op, CONSENSUS_TIME.getEpochSecond())).willReturn(OK);
        given(store.changeOwnerWildCard(nftId, treasury, account)).willReturn(OK);
    }

    private void givenValidTransactionBody(boolean isFungible) {
        final var builder = TokenUpdateTransactionBody.newBuilder();
        op =
                isFungible
                        ? builder.setToken(fungible)
                                .setName("name")
                                .setMemo(StringValue.of("memo"))
                                .setSymbol("symbol")
                                .setTreasury(account)
                                .setExpiry(EXPIRY)
                                .setAutoRenewAccount(account)
                                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(2L))
                                .build()
                        : builder.setToken(nonFungible)
                                .setName("NFT")
                                .setMemo(StringValue.of("NftMemo"))
                                .setSymbol("NftSymbol")
                                .setTreasury(account)
                                .setExpiry(EXPIRY)
                                .setAutoRenewAccount(account)
                                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(2L))
                                .build();
    }

    private void givenKeys() {
        given(merkleToken.hasFreezeKey()).willReturn(true);
        given(merkleToken.hasKycKey()).willReturn(true);
        given(store.unfreeze(any(), any())).willReturn(OK);
        given(store.grantKyc(any(), any())).willReturn(OK);
    }
}
