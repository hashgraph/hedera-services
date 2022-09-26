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
package com.hedera.services.ledger.accounts;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.getStakedId;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.DECLINE_REWARD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.verify;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCustomizerTest {
    @Mock private HederaAccountCustomizer accountCustomizer;
    @Mock private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

    private ContractCustomizer subject;

    @Test
    void worksWithNoCryptoAdminKey() {
        final var captor = ArgumentCaptor.forClass(JKey.class);

        subject = new ContractCustomizer(accountCustomizer);

        subject.customize(newContractId, ledger);

        verify(accountCustomizer).customize(newContractId, ledger);
        verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
        final var keyUsed = captor.getValue();
        assertTrue(JKey.equalUpToDecodability(immutableKey, keyUsed));
    }

    @Test
    void worksWithCryptoAdminKey() {
        final var captor = ArgumentCaptor.forClass(JKey.class);

        subject = new ContractCustomizer(cryptoAdminKey, accountCustomizer);

        subject.customize(newContractId, ledger);

        verify(accountCustomizer).customize(newContractId, ledger);
        verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
        final var keyUsed = captor.getValue();
        assertTrue(JKey.equalUpToDecodability(cryptoAdminKey, keyUsed));
        assertSame(accountCustomizer, subject.accountCustomizer());
    }

    @Test
    void worksFromSponsorCustomizerWithCryptoKey() {
        given(ledger.get(sponsorId, KEY)).willReturn(cryptoAdminKey);
        given(ledger.get(sponsorId, MEMO)).willReturn(memo);
        given(ledger.get(sponsorId, EXPIRY)).willReturn(expiry);
        given(ledger.get(sponsorId, AUTO_RENEW_PERIOD)).willReturn(autoRenewPeriod);
        given(ledger.get(sponsorId, AUTO_RENEW_ACCOUNT_ID)).willReturn(autoRenewAccount);
        given(ledger.get(sponsorId, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(maxAutoAssociation);
        given(ledger.get(sponsorId, STAKED_ID)).willReturn(stakedId);
        given(ledger.get(sponsorId, DECLINE_REWARD)).willReturn(declineReward);

        final var subject = ContractCustomizer.fromSponsorContract(sponsorId, ledger);

        assertCustomizesWithCryptoKey(subject, true);
    }

    @Test
    void worksFromImmutableSponsorCustomizer() {
        final var captor = ArgumentCaptor.forClass(JKey.class);

        given(ledger.get(sponsorId, KEY)).willReturn(immutableSponsorKey);
        given(ledger.get(sponsorId, MEMO)).willReturn(memo);
        given(ledger.get(sponsorId, EXPIRY)).willReturn(expiry);
        given(ledger.get(sponsorId, AUTO_RENEW_PERIOD)).willReturn(autoRenewPeriod);
        given(ledger.get(sponsorId, AUTO_RENEW_ACCOUNT_ID)).willReturn(autoRenewAccount);
        given(ledger.get(sponsorId, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(maxAutoAssociation);
        given(ledger.get(sponsorId, STAKED_ID)).willReturn(stakedId);
        given(ledger.get(sponsorId, DECLINE_REWARD)).willReturn(declineReward);

        final var subject = ContractCustomizer.fromSponsorContract(sponsorId, ledger);

        assertCustomizesWithImmutableKey(subject);
    }

    @Test
    void worksWithParsedStandinKeyAndExplicityProxy() {
        final var op =
                ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setProxyAccountID(proxy.toGrpcAccountId())
                        .setMaxAutomaticTokenAssociations(10)
                        .setMemo(memo)
                        .build();

        final var subject =
                ContractCustomizer.fromHapiCreation(STANDIN_CONTRACT_ID_KEY, consensusNow, op);

        assertCustomizesWithImmutableKey(subject);
    }

    @Test
    void worksForStakedId() {
        final var op =
                ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setProxyAccountID(proxy.toGrpcAccountId())
                        .setMemo(memo)
                        .setStakedAccountId(asAccount("0.0." + stakedId))
                        .setDeclineReward(true)
                        .build();

        final var subject =
                ContractCustomizer.fromHapiCreation(STANDIN_CONTRACT_ID_KEY, consensusNow, op);

        final var captor = ArgumentCaptor.forClass(JKey.class);

        subject.customize(newContractId, ledger);

        verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
        verify(ledger).set(newContractId, MEMO, memo);
        verify(ledger).set(newContractId, EXPIRY, expiry);
        verify(ledger).set(newContractId, IS_SMART_CONTRACT, true);
        verify(ledger).set(newContractId, AUTO_RENEW_PERIOD, autoRenewPeriod);
        verify(ledger).set(newContractId, DECLINE_REWARD, true);
        verify(ledger).set(newContractId, STAKED_ID, stakedId);
        final var keyUsed = captor.getValue();
        assertTrue(JKey.equalUpToDecodability(immutableKey, keyUsed));
    }

    @Test
    void getStakedIdReturnsLatestSet() {
        var op =
                ContractCreateTransactionBody.newBuilder()
                        .setStakedAccountId(asAccount("0.0." + stakedId))
                        .setDeclineReward(true)
                        .build();

        assertEquals(
                stakedId,
                getStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId()));

        op = ContractCreateTransactionBody.newBuilder().setDeclineReward(true).build();

        assertEquals(
                -1,
                getStakedId(
                        op.getStakedIdCase().name(),
                        op.getStakedAccountId(),
                        op.getStakedNodeId()));
    }

    @Test
    void worksWithCryptoKeyAndNoExplicitProxy() {
        final var op =
                ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setMaxAutomaticTokenAssociations(10)
                        .setMemo(memo)
                        .build();

        final var subject = ContractCustomizer.fromHapiCreation(cryptoAdminKey, consensusNow, op);

        assertCustomizesWithCryptoKey(subject);
    }

    @Test
    void worksWithAutoRenewAccount() {
        final var op =
                ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setAutoRenewAccountId(autoRenewAccount.toGrpcAccountId())
                        .setMaxAutomaticTokenAssociations(10)
                        .setMemo(memo)
                        .build();

        final var subject = ContractCustomizer.fromHapiCreation(cryptoAdminKey, consensusNow, op);

        assertCustomizesWithCryptoKey(subject);
        verify(ledger).set(newContractId, AUTO_RENEW_ACCOUNT_ID, autoRenewAccount);
    }

    @Test
    void worksWithAutoAssociationSlots() {
        final var op =
                ContractCreateTransactionBody.newBuilder()
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
                        .setMaxAutomaticTokenAssociations(10)
                        .setMemo(memo)
                        .build();

        final var subject = ContractCustomizer.fromHapiCreation(cryptoAdminKey, consensusNow, op);

        assertCustomizesWithCryptoKey(subject);
        verify(ledger).set(newContractId, MAX_AUTOMATIC_ASSOCIATIONS, 10);
    }

    @Test
    void customizesSyntheticWithCryptoKey() {
        final var subject = new ContractCustomizer(cryptoAdminKey, accountCustomizer);
        final var op = ContractCreateTransactionBody.newBuilder();

        subject.customizeSynthetic(op);

        verify(accountCustomizer).customizeSynthetic(op);
        assertEquals(MiscUtils.asKeyUnchecked(cryptoAdminKey), op.getAdminKey());
    }

    @Test
    void customizesSyntheticWithNegStakedId() {
        given(accountCustomizer.getChanges()).willReturn(Map.of(STAKED_ID, -1L));
        final var subject = new ContractCustomizer(cryptoAdminKey, accountCustomizer);
        final var op = ContractCreateTransactionBody.newBuilder();
        willCallRealMethod().given(accountCustomizer).customizeSynthetic(op);

        subject.customizeSynthetic(op);

        verify(accountCustomizer).customizeSynthetic(op);
        assertEquals(MiscUtils.asKeyUnchecked(cryptoAdminKey), op.getAdminKey());
        assertEquals(AccountID.getDefaultInstance(), op.getStakedAccountId());
        assertEquals(0, op.getStakedNodeId());
    }

    @Test
    void customizesSyntheticWithPositiveStakedId() {
        given(accountCustomizer.getChanges()).willReturn(Map.of(STAKED_ID, 10L));
        final var subject = new ContractCustomizer(cryptoAdminKey, accountCustomizer);
        final var op = ContractCreateTransactionBody.newBuilder();
        willCallRealMethod().given(accountCustomizer).customizeSynthetic(op);

        subject.customizeSynthetic(op);

        verify(accountCustomizer).customizeSynthetic(op);
        assertEquals(MiscUtils.asKeyUnchecked(cryptoAdminKey), op.getAdminKey());
        assertEquals(STATIC_PROPERTIES.scopedAccountWith(10L), op.getStakedAccountId());
        assertEquals(0, op.getStakedNodeId());
    }

    @Test
    void customizesSyntheticWithImmutableKey() {
        final var subject = new ContractCustomizer(accountCustomizer);
        final var op = ContractCreateTransactionBody.newBuilder();

        subject.customizeSynthetic(op);

        verify(accountCustomizer).customizeSynthetic(op);
        assertFalse(op.hasAdminKey());
    }

    private void assertCustomizesWithCryptoKey(final ContractCustomizer subject) {
        assertCustomizesWithCryptoKey(subject, false);
    }

    private void assertCustomizesWithCryptoKey(
            final ContractCustomizer subject, final boolean hasStakedId) {
        final var captor = ArgumentCaptor.forClass(JKey.class);

        subject.customize(newContractId, ledger);

        verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
        verify(ledger).set(newContractId, MEMO, memo);
        verify(ledger).set(newContractId, EXPIRY, expiry);
        verify(ledger).set(newContractId, IS_SMART_CONTRACT, true);
        verify(ledger).set(newContractId, AUTO_RENEW_PERIOD, autoRenewPeriod);
        verify(ledger).set(newContractId, MAX_AUTOMATIC_ASSOCIATIONS, maxAutoAssociation);
        if (hasStakedId) {
            verify(ledger).set(newContractId, STAKED_ID, stakedId);
            verify(ledger).set(newContractId, DECLINE_REWARD, declineReward);
        }

        final var keyUsed = captor.getValue();
        assertTrue(JKey.equalUpToDecodability(cryptoAdminKey, keyUsed));
    }

    private void assertCustomizesWithImmutableKey(final ContractCustomizer subject) {
        final var captor = ArgumentCaptor.forClass(JKey.class);

        subject.customize(newContractId, ledger);

        verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
        verify(ledger).set(newContractId, MEMO, memo);
        verify(ledger).set(newContractId, EXPIRY, expiry);
        verify(ledger).set(newContractId, IS_SMART_CONTRACT, true);
        verify(ledger).set(newContractId, AUTO_RENEW_PERIOD, autoRenewPeriod);
        verify(ledger).set(newContractId, MAX_AUTOMATIC_ASSOCIATIONS, maxAutoAssociation);
        final var keyUsed = captor.getValue();
        assertTrue(JKey.equalUpToDecodability(immutableKey, keyUsed));
    }

    private static final JKey cryptoAdminKey =
            new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private static final AccountID sponsorId = asAccount("0.0.666");
    private static final AccountID newContractId = asAccount("0.0.1234");
    private static final JKey immutableKey = new JContractIDKey(0, 0, 1234);
    private static final JKey immutableSponsorKey = new JContractIDKey(0, 0, 666);
    private static final long expiry = 1_234_567L;
    private static final long autoRenewPeriod = 7776000L;
    private static final Instant consensusNow = Instant.ofEpochSecond(expiry - autoRenewPeriod);
    private static final String memo = "the grey rock";
    private static final EntityId proxy = new EntityId(0, 0, 3);
    private static final long stakedId = 2;
    private static final EntityId autoRenewAccount = new EntityId(0, 0, 4);
    private static final int maxAutoAssociation = 10;
    private static final boolean declineReward = false;
}
