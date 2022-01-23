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

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.QueryUtils.payer;
import static com.hedera.test.utils.QueryUtils.txnRecordQuery;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class AnswerFunctionsTest {
	@Mock
	private StateView view;
	@Mock
	private RecordCache recordCache;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;

	private AnswerFunctions subject = new AnswerFunctions();

	@Test
	void returnsEmptyOptionalWhenProblematic() {
		final var validQuery = txnRecordQuery(absentTxnId);
		given(recordCache.getPriorityRecord(absentTxnId)).willReturn(null);

		final var txnRecord = subject.txnRecord(recordCache, validQuery);

		assertFalse(txnRecord.isPresent());
	}

	@Test
	void usesCacheIfPresentThere() {
		final var validQuery = txnRecordQuery(targetTxnId);
		given(recordCache.getPriorityRecord(targetTxnId)).willReturn(cachedTargetRecord);

		final var txnRecord = subject.txnRecord(recordCache, validQuery);

		assertEquals(grpcRecord, txnRecord.get());
		verify(accounts, never()).get(any());
		verify(recordCache, never()).isReceiptPresent(any());
	}

	private void setupAccountsView() {
		final var children = new MutableStateChildren();
		children.setAccounts(accounts);
		view = new StateView(
				null,
				null,
				children,
				EMPTY_UNIQ_TOKEN_VIEW_FACTORY,
				null);
	}

	private static final TransactionID targetTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount(payer))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	private static final TransactionID absentTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount("3.2.1"))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(4_321L))
			.build();
	private static final TransactionRecord grpcRecord = TransactionRecord.newBuilder()
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
	private static final ExpirableTxnRecord targetRecord = fromGprc(grpcRecord);
	private static final ExpirableTxnRecord cachedTargetRecord = targetRecord;
}
