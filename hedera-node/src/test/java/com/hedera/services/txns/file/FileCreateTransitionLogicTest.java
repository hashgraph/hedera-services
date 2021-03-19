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

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;

import static com.hedera.services.txns.file.FileCreateTransitionLogicTest.ValidProperty.CONTENTS;
import static com.hedera.services.txns.file.FileCreateTransitionLogicTest.ValidProperty.EXPIRY;
import static com.hedera.services.txns.file.FileCreateTransitionLogicTest.ValidProperty.KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_WACL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;

class FileCreateTransitionLogicTest {
	enum ValidProperty { KEY, EXPIRY, CONTENTS, MEMO }

	String memo = "Originally I thought";
	long lifetime = 1_234_567L;
	long txnValidDuration = 180;
	long now = Instant.now().getEpochSecond();
	long expiry = now + lifetime;
	Duration expectedDuration = Duration.newBuilder()
			.setSeconds(lifetime)
			.build();
	AccountID genesis = IdUtils.asAccount("0.0.2");
	FileID created = IdUtils.asFile("0.0.13257");
	KeyTree waclSkeleton = TxnHandlingScenario.MISC_FILE_WACL_KT;
	Key wacl = waclSkeleton.asKey();
	JKey hederaWacl;
	HFileMeta attr;
	byte[] contents = "STUFF".getBytes();

	TransactionID txnId;
	TransactionBody fileCreateTxn;
	private PlatformTxnAccessor accessor;

	HederaFs hfs;
	OptionValidator validator;
	TransactionContext txnCtx;

	FileCreateTransitionLogic subject;

	@BeforeEach
	private void setup() throws Throwable {
		hederaWacl = waclSkeleton.asJKey();
		attr = new HFileMeta(false, hederaWacl, expiry);

		accessor = mock(PlatformTxnAccessor.class);
		txnCtx = mock(TransactionContext.class);
		hfs = mock(HederaFs.class);

		validator = mock(OptionValidator.class);
		given(validator.isValidAutoRenewPeriod(expectedDuration)).willReturn(true);
		given(validator.hasGoodEncoding(wacl)).willReturn(true);
		given(validator.memoCheck(any())).willReturn(OK);

		subject = new FileCreateTransitionLogic(hfs, validator, txnCtx);
	}

	@Test
	public void hasCorrectApplicability() {
		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));

		// expect:
		assertTrue(subject.applicability().test(fileCreateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	public void happyPathFlows() {
		// setup:
		InOrder inOrder = inOrder(hfs, txnCtx);

		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));
		// and:
		given(hfs.create(any(), any(), any())).willReturn(created);

		// when:
		subject.doStateTransition();

		// then:
		inOrder.verify(txnCtx).activePayer();
		inOrder.verify(hfs).create(
				argThat(bytes -> Arrays.equals(contents, bytes)),
				argThat(info ->
						info.getWacl().toString().equals(hederaWacl.toString()) &&
						info.getExpiry() == expiry &&
						memo.equals(info.getMemo())),
				argThat(genesis::equals));
		inOrder.verify(txnCtx).setCreated(created);
		inOrder.verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void syntaxCheckTestsMemo() {
		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));
		given(validator.memoCheck(memo)).willReturn(INVALID_ZERO_BYTE_IN_STRING);

		// when:
		var syntaxCheck = subject.syntaxCheck();
		var status = syntaxCheck.apply(fileCreateTxn);

		// expect:
		assertEquals(INVALID_ZERO_BYTE_IN_STRING, status);
	}

	@Test
	public void syntaxCheckTestsExpiryAsAutoRenewPeriod() {
		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));
		given(validator.isValidAutoRenewPeriod(expectedDuration)).willReturn(false);

		// when:
		var syntaxCheck = subject.syntaxCheck();
		var status = syntaxCheck.apply(fileCreateTxn);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, status);
		verify(validator).isValidAutoRenewPeriod(expectedDuration);
	}

	@Test
	public void syntaxCheckRejectsMissingExpiry() {
		givenTxnCtxCreating(EnumSet.of(CONTENTS, KEY));

		// when:
		var syntaxCheck = subject.syntaxCheck();
		var status = syntaxCheck.apply(fileCreateTxn);

		// expect:
		assertEquals(INVALID_EXPIRATION_TIME, status);
	}

	@Test
	public void handleAllowsImmutable() {
		givenTxnCtxCreating(EnumSet.of(CONTENTS, EXPIRY));

		// when:
		subject.doStateTransition();

		// expect:
		verify(hfs).create(
				argThat(bytes -> Arrays.equals(contents, bytes)),
				argThat(info ->
						info.getWacl().toString().equals(StateView.EMPTY_WACL.toString()) &&
								info.getExpiry() == expiry),
				argThat(genesis::equals));
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	public void handleRejectsBadWacl() {
		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));
		given(validator.hasGoodEncoding(wacl)).willReturn(false);

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(INVALID_FILE_WACL);
	}

	@Test
	public void handleRejectsAlreadyExpired() {
		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));
		willThrow(new IllegalArgumentException(TieredHederaFs.IllegalArgumentType.FILE_WOULD_BE_EXPIRED.toString()))
				.given(hfs).create(any(), any(), any());

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(INVALID_EXPIRATION_TIME);
	}

	@Test
	public void recoversFromUnknownException() {
		givenTxnCtxCreating(EnumSet.allOf(ValidProperty.class));
		willThrow(new IllegalStateException("OOPS!")).given(hfs).create(any(), any(), any());

		// when:
		subject.doStateTransition();

		// expect:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenTxnCtxCreating(EnumSet<ValidProperty> validProps) {
		FileCreateTransactionBody.Builder op = FileCreateTransactionBody.newBuilder();

		if (validProps.contains(ValidProperty.KEY)) {
			op.setKeys(wacl.getKeyList());
		}
		if (validProps.contains(ValidProperty.CONTENTS)) {
			op.setContents(ByteString.copyFrom(contents));
		}
		if (validProps.contains(ValidProperty.EXPIRY)) {
			op.setExpirationTime(MiscUtils.asTimestamp(Instant.ofEpochSecond(expiry)));
		}
		if (validProps.contains(ValidProperty.MEMO)) {
			op.setMemo(memo);
		}

		txnId = TransactionID.newBuilder()
				.setTransactionValidStart(MiscUtils.asTimestamp(Instant.ofEpochSecond(now)))
				.build();
		fileCreateTxn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setTransactionValidDuration(Duration.newBuilder().setSeconds(txnValidDuration))
				.setFileCreate(op)
				.build();
		given(accessor.getTxn()).willReturn(fileCreateTxn);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(genesis);
	}
}
