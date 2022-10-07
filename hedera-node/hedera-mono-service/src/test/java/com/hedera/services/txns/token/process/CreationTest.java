/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token.process;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreationTest {
    private final long now = 1_234_567L;
    private final long initialSupply = 777L;
    private final Id provisionalId = new Id(0, 0, 666);

    private TokenCreateTransactionBody op;

    @Mock private EntityIdSource ids;
    @Mock private Token provisionalToken;
    @Mock private Account treasury;
    @Mock private Account autoRenew;
    @Mock private FcCustomFee customFee;
    @Mock private TokenRelationship newRel;
    @Mock private FcTokenAssociation autoAssociation;
    @Mock private OptionValidator validator;
    @Mock private AccountStore accountStore;
    @Mock private TypedTokenStore tokenStore;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private Creation.NewRelsListing listing;
    @Mock private Creation.TokenModelFactory modelFactory;

    private Creation subject;

    @Test
    void getsExpectedAutoAssociations() {
        givenSubjectWithEverything();
        given(newRel.asAutoAssociation()).willReturn(autoAssociation);
        subject.setNewRels(List.of(newRel));

        final var actual = subject.newAssociations();

        assertEquals(List.of(autoAssociation), actual);
    }

    @Test
    void persistWorks() {
        givenSubjectWithEverything();
        given(newRel.getAccount()).willReturn(treasury);
        subject.setNewRels(List.of(newRel));
        subject.setProvisionalToken(provisionalToken);

        subject.persist();

        verify(tokenStore).persistNew(provisionalToken);
        verify(tokenStore).commitTokenRelationships(List.of(newRel));
        verify(accountStore).commitAccount(treasury);
    }

    @Test
    void verifiesExpiryBeforeLoading() {
        givenSubjectWithInvalidExpiry();

        assertFailsWith(
                () -> subject.loadModelsWith(grpcSponsor, ids, validator), INVALID_EXPIRATION_TIME);
    }

    @Test
    void onlyLoadsTreasuryWithNoAutoRenew() {
        givenSubjectNoAutoRenew();
        given(accountStore.loadAccountOrFailWith(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN))
                .willReturn(treasury);
        given(ids.newTokenId(grpcSponsor)).willReturn(provisionalId.asGrpcToken());

        subject.loadModelsWith(grpcSponsor, ids, validator);

        assertSame(treasury, subject.getTreasury());
        assertNull(subject.getAutoRenew());
        assertEquals(provisionalId, subject.newTokenId());
    }

    @Test
    void loadsAutoRenewWhenAvail() {
        givenSubjectWithEverything();
        given(accountStore.loadAccountOrFailWith(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN))
                .willReturn(treasury);
        given(accountStore.loadAccountOrFailWith(autoRenewId, INVALID_AUTORENEW_ACCOUNT))
                .willReturn(autoRenew);
        given(ids.newTokenId(grpcSponsor)).willReturn(provisionalId.asGrpcToken());

        subject.loadModelsWith(grpcSponsor, ids, validator);

        assertSame(treasury, subject.getTreasury());
        assertSame(autoRenew, subject.getAutoRenew());
    }

    @Test
    void validatesNumCustomFees() {
        givenSubjectWithEverything();
        given(dynamicProperties.maxCustomFeesAllowed()).willReturn(1);

        assertFailsWith(
                () -> subject.doProvisionallyWith(now, modelFactory, listing),
                CUSTOM_FEES_LIST_TOO_LONG);
    }

    @Test
    void mintsInitialSupplyIfSet() {
        givenSubjectWithEverything();

        given(dynamicProperties.maxCustomFeesAllowed()).willReturn(2);
        given(modelFactory.createFrom(provisionalId, op, treasury, autoRenew, now))
                .willReturn(provisionalToken);
        given(listing.listFrom(provisionalToken, tokenStore, dynamicProperties))
                .willReturn(List.of(newRel));
        given(provisionalToken.getCustomFees()).willReturn(List.of(customFee));

        subject.setProvisionalId(provisionalId);
        subject.setProvisionalToken(provisionalToken);
        subject.setTreasury(treasury);
        subject.setAutoRenew(autoRenew);

        subject.doProvisionallyWith(now, modelFactory, listing);

        verify(customFee).validateAndFinalizeWith(provisionalToken, accountStore, tokenStore);
        verify(customFee).nullOutCollector();
        verify(provisionalToken).mint(newRel, initialSupply, true);
    }

    @Test
    void mintsInitialSupplyIfSetWithExistingAssociations() {
        givenSubjectWithEverything();

        given(dynamicProperties.maxCustomFeesAllowed()).willReturn(2);
        given(modelFactory.createFrom(provisionalId, op, treasury, autoRenew, now))
                .willReturn(provisionalToken);
        given(listing.listFrom(provisionalToken, tokenStore, dynamicProperties))
                .willReturn(List.of(newRel));
        given(provisionalToken.getCustomFees()).willReturn(List.of(customFee));

        subject.setProvisionalId(provisionalId);
        subject.setProvisionalToken(provisionalToken);
        subject.setTreasury(treasury);
        subject.setAutoRenew(autoRenew);

        subject.doProvisionallyWith(now, modelFactory, listing);

        verify(customFee).validateAndFinalizeWith(provisionalToken, accountStore, tokenStore);
        verify(customFee).nullOutCollector();
        verify(provisionalToken).mint(newRel, initialSupply, true);
    }

    @Test
    void doesntMintInitialSupplyIfNotSet() {
        givenSubjectWithEverythingExceptInitialSupply();

        given(dynamicProperties.maxCustomFeesAllowed()).willReturn(2);
        given(modelFactory.createFrom(provisionalId, op, treasury, autoRenew, now))
                .willReturn(provisionalToken);
        given(listing.listFrom(provisionalToken, tokenStore, dynamicProperties))
                .willReturn(List.of(newRel));

        subject.setProvisionalId(provisionalId);
        subject.setProvisionalToken(provisionalToken);
        subject.setTreasury(treasury);
        subject.setAutoRenew(autoRenew);

        subject.doProvisionallyWith(now, modelFactory, listing);

        verify(provisionalToken, never()).mint(any(), anyLong(), anyBoolean());
    }

    private void givenSubjectWithEverything() {
        subject =
                new Creation(accountStore, tokenStore, dynamicProperties, creationWithEverything());
    }

    private void givenSubjectWithEverythingExceptInitialSupply() {
        subject =
                new Creation(
                        accountStore,
                        tokenStore,
                        dynamicProperties,
                        creationWithEverythingExceptInitialSupply());
    }

    private void givenSubjectNoAutoRenew() {
        subject = new Creation(accountStore, tokenStore, dynamicProperties, creationNoAutoRenew());
    }

    private void givenSubjectWithInvalidExpiry() {
        subject =
                new Creation(accountStore, tokenStore, dynamicProperties, creationInvalidExpiry());
    }

    private final AccountID grpcSponsor = IdUtils.asAccount("0.0.3");
    private final AccountID grpcTreasuryId = IdUtils.asAccount("0.0.1234");
    private final AccountID grpcAutoRenewId = IdUtils.asAccount("0.0.2345");
    private final Id treasuryId = Id.fromGrpcAccount(grpcTreasuryId);
    private final Id autoRenewId = Id.fromGrpcAccount(grpcAutoRenewId);

    private TokenCreateTransactionBody creationInvalidExpiry() {
        op =
                TokenCreateTransactionBody.newBuilder()
                        .setExpiry(Timestamp.newBuilder().setSeconds(now))
                        .setTreasury(grpcTreasuryId)
                        .setAutoRenewAccount(grpcAutoRenewId)
                        .build();
        return op;
    }

    private TokenCreateTransactionBody creationWithEverything() {
        op =
                TokenCreateTransactionBody.newBuilder()
                        .setTreasury(grpcTreasuryId)
                        .setInitialSupply(initialSupply)
                        .setAutoRenewAccount(grpcAutoRenewId)
                        .addCustomFees(CustomFee.getDefaultInstance())
                        .addCustomFees(CustomFee.getDefaultInstance())
                        .build();
        return op;
    }

    private TokenCreateTransactionBody creationWithEverythingExceptInitialSupply() {
        op = creationWithEverything().toBuilder().clearInitialSupply().build();
        return op;
    }

    private TokenCreateTransactionBody creationNoAutoRenew() {
        op =
                TokenCreateTransactionBody.newBuilder()
                        .setTreasury(grpcTreasuryId)
                        .setAutoRenewAccount(grpcAutoRenewId)
                        .build();
        return op;
    }
}
