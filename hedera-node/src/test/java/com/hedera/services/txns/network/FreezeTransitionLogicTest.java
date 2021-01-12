package com.hedera.services.txns.network;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.interceptors.MockFileNumbers;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNRECOGNIZED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class FreezeTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();

	private Instant consensusTime;
	private FreezeTransitionLogic.LegacyFreezer delegate;
	private TransactionBody freezeTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	FreezeTransitionLogic subject;
	FileNumbers fileNums = new MockFileNumbers();

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();

		delegate = mock(FreezeTransitionLogic.LegacyFreezer.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);

		subject = new FreezeTransitionLogic(fileNums, delegate, txnCtx);
	}

	@Test
	public void hasCorrectApplicability() {
		givenTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(freezeTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void capturesBadFreeze() {
		// setup:
		TransactionRecord freezeRec = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_FREEZE_TRANSACTION_BODY)
						.build())
				.build();

		givenTxnCtx();
		// and:
		given(delegate.perform(freezeTxn, consensusTime)).willReturn(freezeRec);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_FREEZE_TRANSACTION_BODY);
	}

	@Test
	public void followsHappyPathWithOverrides() {
		// setup:
		TransactionRecord freezeRec = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(SUCCESS)
						.build())
				.build();

		givenTxnCtx();
		// and:
		given(delegate.perform(freezeTxn, consensusTime)).willReturn(freezeRec);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void acceptsOkSyntax() {
		givenTxnCtx();

		// expect:
		assertEquals(OK, subject.syntaxCheck().apply(freezeTxn));
	}

	@Test
	public void rejectsInvalidTime() {
		givenTxnCtx(false, Optional.empty(), Optional.empty());

		// expect:
		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.syntaxCheck().apply(freezeTxn));
	}

	@Test
	public void rejectsInvalidUpdateTarget() {
		givenTxnCtx(true, Optional.of(fileNums.toFid(fileNums.feeSchedules())), Optional.empty());

		// expect:
		assertEquals(INVALID_FILE_ID, subject.syntaxCheck().apply(freezeTxn));
	}

	@Test
	public void rejectsMissingFileHash() {
		givenTxnCtx(true, Optional.of(fileNums.toFid(fileNums.softwareUpdateZip())), Optional.empty());

		// expect:
		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.syntaxCheck().apply(freezeTxn));
	}

	@Test
	public void translatesUnknownException() {
		givenTxnCtx();

		given(delegate.perform(any(), any())).willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenTxnCtx() {
		givenTxnCtx(true, Optional.empty(), Optional.empty());
	}

	private void givenTxnCtx(boolean validTime, Optional<FileID> updateTarget, Optional<ByteString> fileHash) {
		var txn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId());

		var op = FreezeTransactionBody.newBuilder();
		if (validTime) {
			plusValidTime(op);
		} else {
			plusInvalidTime(op);
		}
		updateTarget.ifPresent(op::setUpdateFile);
		fileHash.ifPresent(op::setFileHash);

		txn.setFreeze(op);
		freezeTxn = txn.build();
		given(accessor.getTxn()).willReturn(freezeTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private void plusValidTime(FreezeTransactionBody.Builder op) {
		op.setStartHour(15).setStartMin(15).setEndHour(15).setEndMin(20);
	}

	private void plusInvalidTime(FreezeTransactionBody.Builder op) {
		op.setStartHour(24).setStartMin(15).setEndHour(15).setEndMin(20);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
