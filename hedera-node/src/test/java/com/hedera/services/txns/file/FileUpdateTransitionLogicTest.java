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
import com.google.protobuf.StringValue;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.files.HederaFs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;

import static com.hedera.test.utils.TxnUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;

class FileUpdateTransitionLogicTest {
	enum UpdateTarget { KEY, EXPIRY, CONTENTS, MEMO }

	long lifetime = 1_234_567L;
	long txnValidDuration = 180;
	long now = Instant.now().getEpochSecond();
	long oldExpiry = now + lifetime;
	long newExpiry = oldExpiry + 2_345_678L;
	Duration expectedDuration = Duration.newBuilder()
			.setSeconds(newExpiry - now)
			.build();
	FileID nonSysFileTarget = IdUtils.asFile("0.1.2222");
	FileID sysFileTarget = IdUtils.asFile("0.1.121");
	Key newWacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asKey();
	JKey oldWacl, actionableNewWacl;
	HFileMeta oldAttr, newAttr, deletedAttr, immutableAttr;
	String oldMemo = "Past";
	String newMemo = "Future";
	byte[] newContents = "STUFF".getBytes();
	AccountID sysAdmin = IdUtils.asAccount("0.0.50");
	AccountID nonSysAdmin = IdUtils.asAccount("0.0.13257");

	TransactionID txnId;
	TransactionBody fileUpdateTxn;
	private PlatformTxnAccessor accessor;

	HederaFs hfs;
	OptionValidator validator;
	EntityNumbers number = new MockEntityNumbers();
	TransactionContext txnCtx;

	FileUpdateTransitionLogic subject;

	@BeforeEach
	private void setup() throws Throwable {
		oldWacl = TxnHandlingScenario.SIMPLE_NEW_WACL_KT.asJKey();
		oldAttr = new HFileMeta(false, oldWacl, oldExpiry, oldMemo);
		deletedAttr = new HFileMeta(true, oldWacl, oldExpiry);
		immutableAttr = new HFileMeta(false, StateView.EMPTY_WACL, oldExpiry);

		actionableNewWacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		newAttr = new HFileMeta(false, actionableNewWacl, newExpiry, newMemo);

		accessor = mock(PlatformTxnAccessor.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.activePayer()).willReturn(nonSysAdmin);
		hfs = mock(HederaFs.class);
		given(hfs.exists(nonSysFileTarget)).willReturn(true);
		given(hfs.getattr(nonSysFileTarget)).willReturn(oldAttr);

		validator = mock(OptionValidator.class);
		given(validator.isValidAutoRenewPeriod(expectedDuration)).willReturn(false);
		given(validator.hasGoodEncoding(newWacl)).willReturn(true);
		given(validator.memoCheck(newMemo)).willReturn(OK);

		subject = new FileUpdateTransitionLogic(hfs, number, validator, txnCtx);
	}

	@Test
	void doesntUpdateWaclIfNoNew() {
		// setup:
		ArgumentCaptor<HFileMeta> captor = ArgumentCaptor.forClass(HFileMeta.class);

		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(
				new HFileMeta(false, oldWacl, oldExpiry));

		// when:
		subject.doStateTransition();

		// then:
		verify(hfs).setattr(argThat(nonSysFileTarget::equals), captor.capture());
		// and:
		assertEquals(oldAttr.getWacl().toString(), captor.getValue().getWacl().toString());
	}

	@Test
	void doesntOverwriteIfNoContents() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY, UpdateTarget.KEY));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(oldAttr);

		// when:
		subject.doStateTransition();

		// then:
		verify(hfs, never()).overwrite(any(), any());
	}

	@Test
	void allowsSysAdminToUpdateImmutableSysFile() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.CONTENTS), sysFileTarget);
		given(txnCtx.activePayer()).willReturn(sysAdmin);
		// and:
		given(hfs.exists(sysFileTarget)).willReturn(true);
		given(hfs.getattr(sysFileTarget)).willReturn(immutableAttr);

		// when:
		subject.doStateTransition();

		// then:
		verify(hfs).overwrite(
				argThat(sysFileTarget::equals),
				argThat(bytes -> Arrays.equals(newContents, bytes)));
	}

	@Test
	void doesntAllowSysAdminToUpdateImmutableNonSysFile() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.CONTENTS), nonSysFileTarget);
		given(txnCtx.activePayer()).willReturn(sysAdmin);
		// and:
		given(hfs.exists(nonSysFileTarget)).willReturn(true);
		given(hfs.getattr(nonSysFileTarget)).willReturn(immutableAttr);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), UNAUTHORIZED);

		// then:
		verify(hfs, never()).overwrite(
				argThat(nonSysFileTarget::equals),
				argThat(bytes -> Arrays.equals(newContents, bytes)));
	}

	@Test
	void rejectsUpdatingImmutableContents() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.CONTENTS));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(immutableAttr);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), UNAUTHORIZED);

		// then:
		verify(hfs, never()).overwrite(any(), any());
	}

	@Test
	void rejectsUpdatingImmutableKey() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.KEY));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(immutableAttr);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), UNAUTHORIZED);

		// then:
		verify(hfs, never()).overwrite(any(), any());
	}

	@Test
	void allowsUpdatingExpirationOnly() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(immutableAttr);

		// when:
		subject.doStateTransition();

		// then:
		verify(hfs, never()).overwrite(any(), any());
	}

	@Test
	void doesntUpdateExpiryIfRetraction() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY, UpdateTarget.KEY));
		fileUpdateTxn = fileUpdateTxn.toBuilder()
				.setFileUpdate(fileUpdateTxn.getFileUpdate().toBuilder()
						.setExpirationTime(Timestamp.newBuilder().setSeconds(now)))
				.build();
		given(accessor.getTxn()).willReturn(fileUpdateTxn);
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(oldAttr);

		// when:
		subject.doStateTransition();

		// then:
		assertEquals(oldExpiry, oldAttr.getExpiry());
	}

	@Test
	void shortCircuitsOnFailedOverwrite() {
		givenTxnCtxUpdating(EnumSet.allOf(UpdateTarget.class));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(oldAttr);
		doThrow(new InvalidTransactionException(UNAUTHORIZED)).when(hfs).overwrite(any(), any());

		// when:
		assertFailsWith(() -> subject.doStateTransition(), UNAUTHORIZED);

		// then:
		verify(hfs, never()).setattr(any(), any());
	}

	@Test
	void happyPathFlows() {
		// setup:
		InOrder inOrder = inOrder(hfs, txnCtx);

		givenTxnCtxUpdating(EnumSet.allOf(UpdateTarget.class));
		// and:
		given(hfs.getattr(nonSysFileTarget)).willReturn(oldAttr);

		// when:
		subject.doStateTransition();

		// then:
		inOrder.verify(hfs).overwrite(
				argThat(nonSysFileTarget::equals),
				argThat(bytes -> Arrays.equals(newContents, bytes)));
		inOrder.verify(hfs).setattr(
				argThat(nonSysFileTarget::equals),
				argThat(attr -> newAttr.toString().equals(attr.toString())));
	}

	@Test
	void transitionValidatesKeyIfPresent() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.KEY));
		// and:
		given(validator.hasGoodEncoding(newWacl)).willReturn(false);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), BAD_ENCODING);

		// expect:
		verify(validator).hasGoodEncoding(newWacl);
	}

	@Test
	void transitionRejectsDeletedFile() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY));
		// and:
		given(hfs.exists(nonSysFileTarget)).willReturn(true);
		given(hfs.getattr(nonSysFileTarget)).willReturn(deletedAttr);

		// then:
		assertFailsWith(() -> subject.doStateTransition(), FILE_DELETED);
	}

	@Test
	void transitionRejectsMissingFid() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY));
		// and:
		given(hfs.exists(nonSysFileTarget)).willReturn(false);

		// when:
		assertFailsWith(() -> subject.doStateTransition(), INVALID_FILE_ID);

		// expect:
		verify(hfs).exists(nonSysFileTarget);
	}

	@Test
	void transitionCatchesInvalidExpiryIfPresent() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY));

		// when:
		subject.doStateTransition();
	}

	@Test
	void transitionCatchesOversizeIfThrown() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.CONTENTS));

		// when:
		subject.doStateTransition();
	}

	@Test
	void syntaxCheckHappyPath() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.MEMO));
		given(validator.memoCheck(newMemo)).willReturn(OK);

		// when:
		var syntaxCheck = subject.semanticCheck();
		var status = syntaxCheck.apply(fileUpdateTxn);

		// expect:
		assertEquals(OK, status);
	}

	@Test
	void syntaxCheckTestsMemo() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.MEMO));
		given(validator.memoCheck(newMemo)).willReturn(INVALID_ZERO_BYTE_IN_STRING);

		// when:
		var syntaxCheck = subject.semanticCheck();
		var status = syntaxCheck.apply(fileUpdateTxn);

		// expect:
		assertEquals(INVALID_ZERO_BYTE_IN_STRING, status);
	}

	@Test
	void syntaxCheckTestsExpiryAsAutoRenewPeriod() {
		givenTxnCtxUpdating(EnumSet.of(UpdateTarget.EXPIRY));
		given(validator.isValidAutoRenewPeriod(expectedDuration)).willReturn(false);

		// when:
		var syntaxCheck = subject.semanticCheck();
		var status = syntaxCheck.apply(fileUpdateTxn);

		// expect:
		assertEquals(AUTORENEW_DURATION_NOT_IN_RANGE, status);
		verify(validator).isValidAutoRenewPeriod(expectedDuration);
	}

	@Test
	void hasCorrectApplicability() {
		givenTxnCtxUpdating(EnumSet.noneOf(UpdateTarget.class));

		// expect:
		assertTrue(subject.applicability().test(fileUpdateTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	private void givenTxnCtxUpdating(EnumSet<UpdateTarget> targets) {
		givenTxnCtxUpdating(targets, nonSysFileTarget);
	}

	private void givenTxnCtxUpdating(EnumSet<UpdateTarget> targets, FileID target) {
		FileUpdateTransactionBody.Builder op = FileUpdateTransactionBody.newBuilder();

		op.setFileID(target);
		if (targets.contains(UpdateTarget.KEY)) {
			op.setKeys(newWacl.getKeyList());
		}
		if (targets.contains(UpdateTarget.CONTENTS)) {
			op.setContents(ByteString.copyFrom(newContents));
		}
		if (targets.contains(UpdateTarget.EXPIRY)) {
			op.setExpirationTime(MiscUtils.asTimestamp(Instant.ofEpochSecond(newExpiry)));
		}
		if (targets.contains(UpdateTarget.MEMO)) {
			op.setMemo(StringValue.newBuilder().setValue(newMemo).build());
		}

		txnId = TransactionID.newBuilder()
				.setTransactionValidStart(MiscUtils.asTimestamp(Instant.ofEpochSecond(now)))
				.build();
		fileUpdateTxn = TransactionBody.newBuilder()
				.setTransactionID(txnId)
				.setTransactionValidDuration(Duration.newBuilder().setSeconds(txnValidDuration))
				.setFileUpdate(op)
				.build();
		given(accessor.getTxn()).willReturn(fileUpdateTxn);
		given(txnCtx.accessor()).willReturn(accessor);

	}
}
