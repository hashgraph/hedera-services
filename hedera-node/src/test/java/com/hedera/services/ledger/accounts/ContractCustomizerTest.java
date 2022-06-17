package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.services.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContractCustomizerTest {
	@Mock
	private HederaAccountCustomizer accountCustomizer;
	@Mock
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

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
		given(ledger.get(sponsorId, PROXY)).willReturn(proxy);
		given(ledger.get(sponsorId, EXPIRY)).willReturn(expiry);
		given(ledger.get(sponsorId, AUTO_RENEW_PERIOD)).willReturn(autoRenewPeriod);
		given(ledger.get(sponsorId, AUTO_RENEW_ACCOUNT_ID)).willReturn(autoRenewAccount);
		given(ledger.get(sponsorId, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(maxAutoAssociation);

		final var subject = ContractCustomizer.fromSponsorContract(sponsorId, ledger);

		assertCustomizesWithCryptoKey(subject, proxy);
	}

	@Test
	void worksFromImmutableSponsorCustomizer() {
		final var captor = ArgumentCaptor.forClass(JKey.class);

		given(ledger.get(sponsorId, KEY)).willReturn(immutableSponsorKey);
		given(ledger.get(sponsorId, MEMO)).willReturn(memo);
		given(ledger.get(sponsorId, PROXY)).willReturn(proxy);
		given(ledger.get(sponsorId, EXPIRY)).willReturn(expiry);
		given(ledger.get(sponsorId, AUTO_RENEW_PERIOD)).willReturn(autoRenewPeriod);
		given(ledger.get(sponsorId, AUTO_RENEW_ACCOUNT_ID)).willReturn(autoRenewAccount);
		given(ledger.get(sponsorId, MAX_AUTOMATIC_ASSOCIATIONS)).willReturn(maxAutoAssociation);

		final var subject = ContractCustomizer.fromSponsorContract(sponsorId, ledger);

		assertCustomizesWithImmutableKey(subject);
	}

	@Test
	void worksWithParsedStandinKeyAndExplicityProxy() {
		final var op = ContractCreateTransactionBody.newBuilder()
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
				.setProxyAccountID(proxy.toGrpcAccountId())
				.setMaxAutomaticTokenAssociations(10)
				.setMemo(memo)
				.build();

		final var subject = ContractCustomizer.fromHapiCreation(
				STANDIN_CONTRACT_ID_KEY, consensusNow, op);

		assertCustomizesWithImmutableKey(subject);
	}

	@Test
	void worksWithCryptoKeyAndNoExplicitProxy() {
		final var op = ContractCreateTransactionBody.newBuilder()
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
				.setMaxAutomaticTokenAssociations(10)
				.setMemo(memo)
				.build();

		final var subject = ContractCustomizer.fromHapiCreation(
				cryptoAdminKey, consensusNow, op);

		assertCustomizesWithCryptoKey(subject, EntityId.MISSING_ENTITY_ID);
	}

	@Test
	void worksWithAutoRenewAccount() {
		final var op = ContractCreateTransactionBody.newBuilder()
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
				.setAutoRenewAccountId(autoRenewAccount.toGrpcAccountId())
				.setMaxAutomaticTokenAssociations(10)
				.setMemo(memo)
				.build();

		final var subject = ContractCustomizer.fromHapiCreation(
				cryptoAdminKey, consensusNow, op);

		assertCustomizesWithCryptoKey(subject, EntityId.MISSING_ENTITY_ID);
		verify(ledger).set(newContractId, AUTO_RENEW_ACCOUNT_ID, autoRenewAccount);
	}

	@Test
	void worksWithAutoAssociationSlots() {
		final var op = ContractCreateTransactionBody.newBuilder()
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod))
				.setMaxAutomaticTokenAssociations(10)
				.setMemo(memo)
				.build();

		final var subject = ContractCustomizer.fromHapiCreation(
				cryptoAdminKey, consensusNow, op);

		assertCustomizesWithCryptoKey(subject, EntityId.MISSING_ENTITY_ID);
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
	void customizesSyntheticWithImmutableKey() {
		final var subject = new ContractCustomizer(accountCustomizer);
		final var op = ContractCreateTransactionBody.newBuilder();

		subject.customizeSynthetic(op);

		verify(accountCustomizer).customizeSynthetic(op);
		assertFalse(op.hasAdminKey());
	}

	private void assertCustomizesWithCryptoKey(final ContractCustomizer subject, final EntityId expectedProxy) {
		final var captor = ArgumentCaptor.forClass(JKey.class);

		subject.customize(newContractId, ledger);

		verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
		verify(ledger).set(newContractId, MEMO, memo);
		verify(ledger).set(newContractId, PROXY, expectedProxy);
		verify(ledger).set(newContractId, EXPIRY, expiry);
		verify(ledger).set(newContractId, IS_SMART_CONTRACT, true);
		verify(ledger).set(newContractId, AUTO_RENEW_PERIOD, autoRenewPeriod);
		verify(ledger).set(newContractId, MAX_AUTOMATIC_ASSOCIATIONS, maxAutoAssociation);
		final var keyUsed = captor.getValue();
		assertTrue(JKey.equalUpToDecodability(cryptoAdminKey, keyUsed));
	}

	private void assertCustomizesWithImmutableKey(final ContractCustomizer subject) {
		final var captor = ArgumentCaptor.forClass(JKey.class);

		subject.customize(newContractId, ledger);

		verify(ledger).set(eq(newContractId), eq(KEY), captor.capture());
		verify(ledger).set(newContractId, MEMO, memo);
		verify(ledger).set(newContractId, PROXY, proxy);
		verify(ledger).set(newContractId, EXPIRY, expiry);
		verify(ledger).set(newContractId, IS_SMART_CONTRACT, true);
		verify(ledger).set(newContractId, AUTO_RENEW_PERIOD, autoRenewPeriod);
		verify(ledger).set(newContractId, MAX_AUTOMATIC_ASSOCIATIONS, maxAutoAssociation);
		final var keyUsed = captor.getValue();
		assertTrue(JKey.equalUpToDecodability(immutableKey, keyUsed));
	}

	private static final JKey cryptoAdminKey = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
	private static final AccountID sponsorId = asAccount("0.0.666");
	private static final AccountID newContractId = asAccount("0.0.1234");
	private static final JKey immutableKey = new JContractIDKey(0, 0, 1234);
	private static final JKey immutableSponsorKey = new JContractIDKey(0, 0, 666);
	private static final long expiry = 1_234_567L;
	private static final long autoRenewPeriod = 7776000L;
	private static final Instant consensusNow = Instant.ofEpochSecond(expiry - autoRenewPeriod);
	private static final String memo = "the grey rock";
	private static final EntityId proxy = new EntityId(0, 0, 3);
	private static final EntityId autoRenewAccount = new EntityId(0, 0, 4);
	private static final int maxAutoAssociation = 10;
}
