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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
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
import java.util.Arrays;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;

class FileAppendTransitionLogicTest {
	enum TargetType { VALID, MISSING, DELETED, IMMUTABLE }

	byte[] moreContents = "MORE".getBytes();
	FileID target = IdUtils.asFile("0.0.13257");
	FileID missing = IdUtils.asFile("0.0.75231");
	FileID deleted = IdUtils.asFile("0.0.666");
	FileID immutable = IdUtils.asFile("0.0.667");

	HederaFs.UpdateResult success = new TieredHederaFs.SimpleUpdateResult(
			false,
			true,
			SUCCESS);
	HederaFs.UpdateResult okWithCaveat = new TieredHederaFs.SimpleUpdateResult(
			false,
			true,
			ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED);

	JKey wacl;
	JFileInfo attr, deletedAttr, immutableAttr;

	TransactionID txnId;
	TransactionBody fileAppendTxn;
	PlatformTxnAccessor accessor;

	HederaFs hfs;
	TransactionContext txnCtx;

	FileAppendTransitionLogic subject;

	@BeforeEach
	private void setup() throws Throwable {
		wacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
		attr = new JFileInfo(false, wacl, 2_000_000L);
		deletedAttr = new JFileInfo(true, wacl, 2_000_000L);
		immutableAttr = new JFileInfo(false, StateView.EMPTY_WACL, 2_000_000L);

		accessor = mock(PlatformTxnAccessor.class);
		txnCtx = mock(TransactionContext.class);

		hfs = mock(HederaFs.class);
		given(hfs.exists(target)).willReturn(true);
		given(hfs.exists(deleted)).willReturn(true);
		given(hfs.exists(immutable)).willReturn(true);
		given(hfs.exists(missing)).willReturn(false);
		given(hfs.getattr(target)).willReturn(attr);
		given(hfs.getattr(deleted)).willReturn(deletedAttr);
		given(hfs.getattr(immutable)).willReturn(immutableAttr);

		subject = new FileAppendTransitionLogic(hfs, txnCtx);
	}

	@Test
	public void resultIsRespected() {
		givenTxnCtxAppending(TargetType.VALID);
		// and:
		given(hfs.append(any(), any())).willReturn(okWithCaveat);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FEE_SCHEDULE_FILE_PART_UPLOADED);
	}

	@Test
	public void setsFailInvalidOnException() {
		givenTxnCtxAppending(TargetType.VALID);
		willThrow(new IllegalStateException("Hmm...")).given(hfs).append(any(), any());

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	@Test
	public void catchesOversize() {
		givenTxnCtxAppending(TargetType.VALID);
		given(hfs.append(any(), any()))
				.willThrow(new IllegalArgumentException(TieredHederaFs.IllegalArgumentType.OVERSIZE_CONTENTS.toString()));

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(MAX_FILE_SIZE_EXCEEDED);
	}

	@Test
	public void detectsDeleted() {
		givenTxnCtxAppending(TargetType.DELETED);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FILE_DELETED);
	}

	@Test
	public void detectsImmutable() {
		givenTxnCtxAppending(TargetType.IMMUTABLE);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(UNAUTHORIZED);
	}

	@Test
	public void detectsMissing() {
		givenTxnCtxAppending(TargetType.MISSING);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_FILE_ID);
	}

	@Test
	public void happyPathFlows() {
		// setup:
		InOrder inOrder = inOrder(hfs, txnCtx);

		givenTxnCtxAppending(TargetType.VALID);
		// and:
		given(hfs.append(any(), any())).willReturn(success);

		// when:
		subject.doStateTransition();

		// then:
		inOrder.verify(hfs).append(argThat(target::equals), argThat(bytes -> Arrays.equals(moreContents, bytes)));
		inOrder.verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void syntaxCheckRubberstamps() {
		// given:
		var syntaxCheck = subject.syntaxCheck();

		// expect:
		assertEquals(ResponseCodeEnum.OK, syntaxCheck.apply(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void hasCorrectApplicability() {
		givenTxnCtxAppending(TargetType.VALID);

		// expect:
		assertTrue(subject.applicability().test(fileAppendTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	private void givenTxnCtxAppending(TargetType type) {
		FileAppendTransactionBody.Builder op = FileAppendTransactionBody.newBuilder();

		switch (type) {
			case IMMUTABLE:
				op.setFileID(immutable);
				break;
			case VALID:
				op.setFileID(target);
				break;
			case MISSING:
				op.setFileID(missing);
				break;
			case DELETED:
				op.setFileID(deleted);
				break;
		}
		op.setContents(ByteString.copyFrom(moreContents));

		txnId = TransactionID.newBuilder()
				.setTransactionValidStart(MiscUtils.asTimestamp(Instant.ofEpochSecond(Instant.now().getEpochSecond())))
				.build();
		fileAppendTxn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setTransactionValidDuration(Duration.newBuilder().setSeconds(180))
				.setFileAppend(op)
				.build();
		given(accessor.getTxn()).willReturn(fileAppendTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}
}
