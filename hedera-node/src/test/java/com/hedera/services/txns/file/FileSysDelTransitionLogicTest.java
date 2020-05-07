package com.hedera.services.txns.file;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class FileSysDelTransitionLogicTest {
	enum TargetType { VALID, MISSING, DELETED }
	enum NewExpiryType { NONE, FUTURE, PAST }

	long now = Instant.now().getEpochSecond();
	long lifetime = 1_000_000L;
	long oldExpiry = now + lifetime;
	long newExpiry = now + lifetime / 2;

	FileID tbd = IdUtils.asFile("0.0.13257");
	FileID missing = IdUtils.asFile("0.0.75231");
	FileID deleted = IdUtils.asFile("0.0.666");

	HederaFs.UpdateResult success = new TieredHederaFs.SimpleUpdateResult(
			true,
			false,
			SUCCESS);

	JKey wacl;
	JFileInfo attr, deletedAttr;

	TransactionID txnId;
	TransactionBody fileSysDelTxn;
	PlatformTxnAccessor accessor;

	HederaFs hfs;
	Map<FileID, Long> oldExpiries;
	TransactionContext txnCtx;

	FileSysDelTransitionLogic subject;

	@BeforeEach
	private void setup() throws Throwable {
		wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
		attr = new JFileInfo(false, wacl, oldExpiry);
		deletedAttr = new JFileInfo(true, wacl, oldExpiry);

		accessor = mock(PlatformTxnAccessor.class);
		txnCtx = mock(TransactionContext.class);
		oldExpiries = mock(Map.class);

		hfs = mock(HederaFs.class);
		given(hfs.exists(tbd)).willReturn(true);
		given(hfs.exists(deleted)).willReturn(true);
		given(hfs.exists(missing)).willReturn(false);
		given(hfs.getattr(tbd)).willReturn(attr);
		given(hfs.getattr(deleted)).willReturn(deletedAttr);

		subject = new FileSysDelTransitionLogic(hfs, oldExpiries, txnCtx);
	}

	@Test
	public void destroysIfNewExpiryIsPast() {
		givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.PAST);

		// when:
		subject.doStateTransition();

		// then:
		verify(hfs).rm(tbd);
		verify(oldExpiries, never()).put(any(), any());
	}

	@Test
	public void leavesUnspecifiedExpiryUntouched() {
		// setup:
		InOrder inOrder = inOrder(hfs, txnCtx, oldExpiries);

		givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.NONE);
		// and:
		given(hfs.setattr(any(), any())).willReturn(success);

		// when:
		subject.doStateTransition();

		// then:
		assertTrue(attr.isDeleted());;
		assertEquals(oldExpiry, attr.getExpirationTimeSeconds());
		inOrder.verify(hfs).setattr(tbd, attr);
		inOrder.verify(oldExpiries).put(tbd, Long.valueOf(oldExpiry));
		inOrder.verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void setsFailInvalidOnException() {
		givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.PAST);
		willThrow(new IllegalStateException("Hmm...")).given(hfs).rm(any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void happyPathFlows() {
		// setup:
		InOrder inOrder = inOrder(hfs, txnCtx, oldExpiries);

		givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.FUTURE);
		// and:
		given(hfs.setattr(any(), any())).willReturn(success);

		// when:
		subject.doStateTransition();

		// then:
		assertTrue(attr.isDeleted());;
		assertEquals(newExpiry, attr.getExpirationTimeSeconds());
		inOrder.verify(hfs).setattr(tbd, attr);
		inOrder.verify(oldExpiries).put(tbd, Long.valueOf(oldExpiry));
		inOrder.verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void detectsDeleted() {
		givenTxnCtxSysDeleting(TargetType.DELETED, NewExpiryType.FUTURE);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FILE_DELETED);
	}

	@Test
	public void detectsMissing() {
		givenTxnCtxSysDeleting(TargetType.MISSING, NewExpiryType.FUTURE);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_FILE_ID);
	}

	@Test
	public void hasCorrectApplicability() {
		// setup:
		SystemDeleteTransactionBody.Builder op = SystemDeleteTransactionBody.newBuilder()
				.setContractID(IdUtils.asContract("0.0.1001"));
		var contractSysdelTxn = TransactionBody.newBuilder()
				.setSystemDelete(op)
				.build();

		givenTxnCtxSysDeleting(TargetType.VALID, NewExpiryType.FUTURE);

		// expect:
		assertTrue(subject.applicability().test(fileSysDelTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
		assertFalse(subject.applicability().test(contractSysdelTxn));
	}

	@Test
	public void syntaxCheckRubberstamps() {
		// given:
		var syntaxCheck = subject.syntaxCheck();

		// expect:
		assertEquals(ResponseCodeEnum.OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
	}

	private void givenTxnCtxSysDeleting(TargetType type, NewExpiryType expiryType) {
		SystemDeleteTransactionBody.Builder op = SystemDeleteTransactionBody.newBuilder();

		switch (type) {
			case VALID:
				op.setFileID(tbd);
				break;
			case MISSING:
				op.setFileID(missing);
				break;
			case DELETED:
				op.setFileID(deleted);
				break;
		}

		switch (expiryType) {
			case NONE:
				break;
			case FUTURE:
				op.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(newExpiry));
				break;
			case PAST:
				op.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(now - 1));
				break;
		}

		txnId = TransactionID.newBuilder()
				.setTransactionValidStart(MiscUtils.asTimestamp(Instant.ofEpochSecond(Instant.now().getEpochSecond())))
				.build();
		fileSysDelTxn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
				.setSystemDelete(op)
				.build();
		given(accessor.getTxn()).willReturn(fileSysDelTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(Instant.ofEpochSecond(now));
	}
}
