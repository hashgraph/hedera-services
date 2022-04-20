package com.hedera.services.state.migration;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MigrationRecordsManagerTest {
	private static final long fundingExpiry = 33197904000L;
	private static final ExpirableTxnRecord.Builder pretend800 = ExpirableTxnRecord.newBuilder();
	private static final ExpirableTxnRecord.Builder pretend801 = ExpirableTxnRecord.newBuilder();
	private static final ExpirableTxnRecord.Builder contractUpdate1 = ExpirableTxnRecord.newBuilder();
	private static final ExpirableTxnRecord.Builder contractUpdate2 = ExpirableTxnRecord.newBuilder();
	private static final Instant now = Instant.ofEpochSecond(1_234_567L);
	private static final Key EXPECTED_KEY = Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
	private static final String MEMO = "Release 0.24.1 migration record";
	private static final String CONTRACT_UPGRADE_MEMO = "Contract {} was renewed during 0.26.0 upgrade. New expiry: {}" +
			" .";
	private static final long contract1Expiry = 2000000L;
	private static final long contract2Expiry = 4000000L;
	private static final EntityId contract1Id = EntityId.fromIdentityCode(1);
	private static final EntityId contract2Id = EntityId.fromIdentityCode(2);

	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private RecordsHistorian recordsHistorian;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private SideEffectsTracker tracker800;
	@Mock
	private SideEffectsTracker tracker801;
	@Mock
	private EntityCreator creator;

	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	@Mock
	private MerkleAccount merkleAccount;

	private final AtomicLong nextTracker = new AtomicLong();
	private final SyntheticTxnFactory factory = new SyntheticTxnFactory();

	private MigrationRecordsManager subject;

	@BeforeEach
	void setUp() {
		accounts.put(EntityNum.fromLong(1L), merkleAccount);
		accounts.put(EntityNum.fromLong(2L), merkleAccount);

		subject = new MigrationRecordsManager(creator, sigImpactHistorian, recordsHistorian, () -> networkCtx,
				() -> accounts);

		subject.setSideEffectsFactory(() -> nextTracker.getAndIncrement() == 0 ? tracker800 : tracker801);
	}

	@Test
	void ifContextIndicatesRecordsNeedToBeStreamedThenDoesSo() {
		final ArgumentCaptor<TransactionBody.Builder> bodyCaptor = forClass(TransactionBody.Builder.class);
		final ArgumentCaptor<ExpirableTxnRecord.Builder> recordCaptor = forClass(ExpirableTxnRecord.Builder.class);
		final var inOrder = Mockito.inOrder(recordsHistorian);
		final var synthBody = expectedSyntheticCreate();

		final var contractUpdateSynthBody1 = factory.synthContractAutoRenew(contract1Id.asNum(), contract1Expiry).build();
		final var contractUpdateSynthBody2 = factory.synthContractAutoRenew(contract2Id.asNum(), contract2Expiry).build();
		given(merkleAccount.isSmartContract()).willReturn(true);
		given(merkleAccount.getExpiry()).willReturn(contract1Expiry).willReturn(contract2Expiry);

		given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker800, MEMO)).willReturn(pretend800);
		given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker801, MEMO)).willReturn(pretend801);

		subject.publishMigrationRecords(now);

		verify(sigImpactHistorian).markEntityChanged(800L);
		verify(sigImpactHistorian).markEntityChanged(801L);
		verify(sigImpactHistorian).markEntityChanged(contract1Id.asNum().longValue());
		verify(sigImpactHistorian).markEntityChanged(contract2Id.asNum().longValue());

		verify(tracker800).trackAutoCreation(AccountID.newBuilder().setAccountNum(800L).build(), ByteString.EMPTY);
		verify(tracker801).trackAutoCreation(AccountID.newBuilder().setAccountNum(801L).build(), ByteString.EMPTY);

		inOrder.verify(recordsHistorian).trackPrecedingChildRecord(eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend800));
		inOrder.verify(recordsHistorian).trackPrecedingChildRecord(eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend801));
		inOrder.verify(recordsHistorian, times(2)).trackPrecedingChildRecord(eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), recordCaptor.capture());

		verify(networkCtx).markMigrationRecordsStreamed();
		final var bodies = bodyCaptor.getAllValues();
		assertEquals(synthBody, bodies.get(0).build());
		assertEquals(synthBody, bodies.get(1).build());
		assertEquals(contractUpdateSynthBody1, bodies.get(2).build());
		assertEquals(contractUpdateSynthBody2, bodies.get(3).build());

		final var records = recordCaptor.getAllValues();
		assertTrue(expectedRecord(contract1Id, contract1Expiry).build().equals(records.get(0).build()));
		assertTrue(expectedRecord(contract2Id, contract2Expiry).build().equals(records.get(1).build()));
	}

	private ExpirableTxnRecord.Builder expectedRecord(final EntityId num, final long newExpiry) {
		final var receipt = new TxnReceipt();
		receipt.setAccountId(num);

		final var memo = String.format(CONTRACT_UPGRADE_MEMO, num.num(), newExpiry);
		final var txnId = new TxnId(num, MISSING_INSTANT, false, USER_TRANSACTION_NONCE);
		return ExpirableTxnRecord.newBuilder()
				.setTxnId(txnId)
				.setMemo(memo)
				.setReceipt(receipt)
				.setConsensusTime(RichInstant.fromJava(now));
	}

	@Test
	void doesNothingIfRecordsAlreadyStreamed() {
		given(networkCtx.areMigrationRecordsStreamed()).willReturn(true);

		subject.publishMigrationRecords(now);

		verifyNoInteractions(sigImpactHistorian);
		verifyNoInteractions(recordsHistorian);
		verifyNoInteractions(accounts);
	}

	@Test
	void doesntStreamRewardAccountCreationIfNotGenesis() {
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(Instant.MAX);

		subject.publishMigrationRecords(now);

		verifyNoInteractions(sigImpactHistorian);
		verifyNoInteractions(recordsHistorian);
	}

	private TransactionBody expectedSyntheticCreate() {
		final var txnBody = CryptoCreateTransactionBody.newBuilder()
				.setKey(EXPECTED_KEY)
				.setMemo(EMPTY_MEMO)
				.setInitialBalance(0)
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(fundingExpiry - now.getEpochSecond()))
				.build();
		return TransactionBody.newBuilder().setCryptoCreateAccount(txnBody).build();
	}
}
