package com.hedera.services.txns.file;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;

class FileDeleteTransitionLogicTest {
	enum TargetType { IMMUTABLE, VALID, MISSING, DELETED }

	FileID tbd = IdUtils.asFile("0.0.13257");
	FileID missing = IdUtils.asFile("0.0.75231");
	FileID deleted = IdUtils.asFile("0.0.666");
	FileID immutable = IdUtils.asFile("0.0.667");

	HederaFs.UpdateResult success = new TieredHederaFs.SimpleUpdateResult(
			true,
			true,
			SUCCESS);
	HederaFs.UpdateResult noAuth = new TieredHederaFs.SimpleUpdateResult(
			true,
			true,
			ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE);
	JKey wacl;
	JFileInfo attr, deletedAttr, immutableAttr;

	TransactionID txnId;
	TransactionBody fileDeleteTxn;
	PlatformTxnAccessor accessor;

	HederaFs hfs;
	TransactionContext txnCtx;

	FileDeleteTransitionLogic subject;

	@BeforeEach
	private void setup() throws Throwable {
		wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
		attr = new JFileInfo(false, wacl, 2_000_000L);
		deletedAttr = new JFileInfo(true, wacl, 2_000_000L);
		immutableAttr = new JFileInfo(false, StateView.EMPTY_WACL, 2_000_000L);

		accessor = mock(PlatformTxnAccessor.class);
		txnCtx = mock(TransactionContext.class);

		hfs = mock(HederaFs.class);
		given(hfs.exists(tbd)).willReturn(true);
		given(hfs.exists(deleted)).willReturn(true);
		given(hfs.exists(immutable)).willReturn(true);
		given(hfs.exists(missing)).willReturn(false);
		given(hfs.getattr(tbd)).willReturn(attr);
		given(hfs.getattr(deleted)).willReturn(deletedAttr);
		given(hfs.getattr(immutable)).willReturn(immutableAttr);

		subject = new FileDeleteTransitionLogic(hfs, txnCtx);
	}

	@Test
	public void happyPathFlows() {
		// setup:
		InOrder inOrder = inOrder(hfs, txnCtx);

		givenTxnCtxDeleting(TargetType.VALID);
		// and:
		given(hfs.delete(any())).willReturn(success);

		// when:
		subject.doStateTransition();

		// then:
		inOrder.verify(hfs).delete(tbd);
		inOrder.verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void detectsDeleted() {
		givenTxnCtxDeleting(TargetType.DELETED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FILE_DELETED);
	}

	@Test
	public void detectsMissing() {
		givenTxnCtxDeleting(TargetType.MISSING);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_FILE_ID);
	}

	@Test
	public void setsFailInvalidOnException() {
		givenTxnCtxDeleting(TargetType.VALID);
		willThrow(new IllegalStateException("Hmm...")).given(hfs).delete(any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void resultIsRespected() {
		givenTxnCtxDeleting(TargetType.VALID);
		// and:
		given(hfs.delete(any())).willReturn(noAuth);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(ENTITY_NOT_ALLOWED_TO_DELETE);
	}

	@Test
	public void rejectsImmutableTarget() {
		givenTxnCtxDeleting(TargetType.IMMUTABLE);
		// and:
		given(hfs.delete(any())).willReturn(success);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(UNAUTHORIZED);
	}

	@Test
	public void hasCorrectApplicability() {
		givenTxnCtxDeleting(TargetType.VALID);

		// expect:
		assertTrue(subject.applicability().test(fileDeleteTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void syntaxCheckRubberstamps() {
		// given:
		var syntaxCheck = subject.syntaxCheck();

		// expect:
		assertEquals(ResponseCodeEnum.OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
	}

	private void givenTxnCtxDeleting(TargetType type) {
		FileDeleteTransactionBody.Builder op = FileDeleteTransactionBody.newBuilder();

		switch (type) {
			case IMMUTABLE:
				op.setFileID(immutable);
				break;
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

		txnId = TransactionID.newBuilder()
				.setTransactionValidStart(MiscUtils.asTimestamp(Instant.ofEpochSecond(Instant.now().getEpochSecond())))
				.build();
		fileDeleteTxn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
				.setFileDelete(op)
				.build();
		given(accessor.getTxn()).willReturn(fileDeleteTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}
}
