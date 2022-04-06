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
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MigrationRecordsManagerTest {
	private static final long fundingExpiry = 33197904000L;
	private static final ExpirableTxnRecord.Builder pretend800 = ExpirableTxnRecord.newBuilder();
	private static final ExpirableTxnRecord.Builder pretend801 = ExpirableTxnRecord.newBuilder();
	private static final Instant now = Instant.ofEpochSecond(1_234_567L);
	private static final Key EXPECTED_KEY = Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
	private static final String MEMO = "Release 0.24.1 migration record";

	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private SideEffectsTracker tracker800;
	@Mock
	private SideEffectsTracker tracker801;
	@Mock
	private EntityCreator creator;

	private final AtomicLong nextTracker = new AtomicLong();

	private MigrationRecordsManager subject;

	@BeforeEach
	void setUp() {
		subject = new MigrationRecordsManager(creator, sigImpactHistorian, recordsHistorian, () -> networkCtx);

		subject.setSideEffectsFactory(() -> nextTracker.getAndIncrement() == 0 ? tracker800 : tracker801);
	}

	@Test
	void ifContextIndicatesRecordsNeedToBeStreamedThenDoesSo() {
		final ArgumentCaptor<TransactionBody.Builder> bodyCaptor = forClass(TransactionBody.Builder.class);
		final var synthBody = expectedSyntheticCreate();

		given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker800, MEMO)).willReturn(pretend800);
		given(creator.createSuccessfulSyntheticRecord(NO_CUSTOM_FEES, tracker801, MEMO)).willReturn(pretend801);

		subject.publishMigrationRecords(now);

		verify(sigImpactHistorian).markEntityChanged(800L);
		verify(sigImpactHistorian).markEntityChanged(801L);
		verify(tracker800).trackAutoCreation(AccountID.newBuilder().setAccountNum(800L).build(), ByteString.EMPTY);
		verify(tracker801).trackAutoCreation(AccountID.newBuilder().setAccountNum(801L).build(), ByteString.EMPTY);
		verify(recordsHistorian).trackPrecedingChildRecord(eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend800));
		verify(recordsHistorian).trackPrecedingChildRecord(eq(DEFAULT_SOURCE_ID), bodyCaptor.capture(), eq(pretend801));
		verify(networkCtx).markMigrationRecordsStreamed();
		final var bodies = bodyCaptor.getAllValues();
		assertEquals(synthBody, bodies.get(0).build());
		assertEquals(synthBody, bodies.get(1).build());
	}

	@Test
	void doesNothingIfRecordsAlreadyStreamed() {
		given(networkCtx.areMigrationRecordsStreamed()).willReturn(true);

		subject.publishMigrationRecords(now);

		verifyNoInteractions(sigImpactHistorian);
		verifyNoInteractions(recordsHistorian);
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
