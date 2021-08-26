package com.hedera.services.queries.answering;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

class AnswerFunctionsTest {
	private String payer = "0.0.12345";
	private TransactionID targetTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount(payer))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	private TransactionID absentTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("3.2.1"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(4_321L))
			.build();

	private ExpirableTxnRecord targetRecord = constructTargetRecord();
	private ExpirableTxnRecord cachedTargetRecord = targetRecord;
	private MerkleAccount payerAccount;
	private String target = payer;
	private StateView view;
	private RecordCache recordCache;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private NodeLocalProperties nodeProps;

	private AnswerFunctions subject;

	@BeforeEach
	private void setup() {
		payerAccount = MerkleAccountFactory.newAccount().get();
		payerAccount.records().offer(recordOne());
		payerAccount.records().offer(targetRecord);

		accounts = mock(FCMap.class);
		given(accounts.get(PermHashInteger.fromAccountId(asAccount(target)))).willReturn(payerAccount);
		nodeProps = mock(NodeLocalProperties.class);
		final var children = new StateChildren();
		children.setAccounts(accounts);
		view = new StateView(
				null,
				null,
				nodeProps,
				children,
				EMPTY_UNIQ_TOKEN_VIEW_FACTORY);

		recordCache = mock(RecordCache.class);

		subject = new AnswerFunctions();
	}

	@Test
	void returnsEmptyOptionalWhenProblematic() {
		// setup:
		Query validQuery = getRecordQuery(absentTxnId);

		given(recordCache.getPriorityRecord(absentTxnId)).willReturn(null);

		// when:
		Optional<TransactionRecord> record = subject.txnRecord(recordCache, view, validQuery);

		// then:
		assertFalse(record.isPresent());
	}

	@Test
	void findsInPayerAccountIfPresentThere() {
		// setup:
		Query validQuery = getRecordQuery(targetTxnId);

		given(recordCache.getPriorityRecord(targetTxnId)).willReturn(null);

		// when:
		Optional<TransactionRecord> record = subject.txnRecord(recordCache, view, validQuery);

		// then:
		assertEquals(cachedTargetRecord.asGrpc(), record.get());
	}

	@Test
	void usesCacheIfPresentThere() {
		// setup:
		Query validQuery = getRecordQuery(targetTxnId);

		given(recordCache.getPriorityRecord(targetTxnId)).willReturn(cachedTargetRecord);

		// when:
		Optional<TransactionRecord> record = subject.txnRecord(recordCache, view, validQuery);

		// then:
		assertEquals(cachedTargetRecord.asGrpc(), record.get());
		verify(accounts, never()).get(any());
		verify(recordCache, never()).isReceiptPresent(any());
	}

	ExpirableTxnRecord constructTargetRecord() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder().setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS))
				.setTransactionID(targetTxnId)
				.setMemo("Dim galleries, dusk winding stairs got past...")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(9_999_999_999L))
				.setTransactionFee(555L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"), -2L,
						asAccount("0.0.2"), -2L,
						asAccount("0.0.1001"), 2L,
						asAccount("0.0.1002"), 2L))
				.build();
		return fromGprc(record);
	}

	Query getRecordQuery(TransactionID txnId) {
		TransactionGetRecordQuery.Builder op = TransactionGetRecordQuery.newBuilder().setTransactionID(txnId);
		return Query.newBuilder().setTransactionGetRecord(op).build();
	}
}
