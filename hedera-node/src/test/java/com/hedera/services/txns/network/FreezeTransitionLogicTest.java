package com.hedera.services.txns.network;

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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.hedera.services.state.merkle.MerkleNetworkContext.NO_PREPARED_UPDATE_FILE_NUM;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_ABORT;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_ONLY;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.PREPARE_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.TELEMETRY_UPGRADE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FreezeTransitionLogicTest {
	private static final FileID CANONICAL_FILE = IdUtils.asFile("0.0.150");
	private static final FileID ILLEGAL_FILE = IdUtils.asFile("0.0.160");
	private static final AccountID PAYER = IdUtils.asAccount("0.0.1234");
	private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L, 890);
	private static final Instant VALID_START_TIME = CONSENSUS_TIME.plusSeconds(120);
	private static final ByteString PRETEND_HASH =
			ByteString.copyFromUtf8("012345678901234567890123456789012345678901234567");

	private TransactionBody freezeTxn;

	@Mock
	private UpgradeHelper upgradeHelper;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private MerkleSpecialFiles specialFiles;
	@Mock
	private MerkleNetworkContext networkCtx;

	private FreezeTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new FreezeTransitionLogic(upgradeHelper, txnCtx, () -> specialFiles, () -> networkCtx);
	}

	@Test
	void rejectsPostConsensusFreezeOnlyWithInvalidTime() {
		givenTypicalTxnInCtx(true, FREEZE_ONLY, Optional.empty(), Optional.empty());
		given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME.plusSeconds(Integer.MAX_VALUE));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_FREEZE_TRANSACTION_BODY);
	}

	@Test
	void acceptsPostConsensusFreezeOnlyWithValidTime() {
		givenTypicalTxnInCtx(true, FREEZE_ONLY, Optional.empty(), Optional.empty());
		given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);

		subject.doStateTransition();

		verify(upgradeHelper).scheduleFreezeAt(VALID_START_TIME);
	}

	@Test
	void rejectsPostConsensusFreezeUpgradeWithNoUpdatePrepared() {
		given(networkCtx.getPreparedUpdateFileNum()).willReturn(NO_PREPARED_UPDATE_FILE_NUM);
		givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());

		assertFailsWith(() -> subject.doStateTransition(), NO_UPGRADE_HAS_BEEN_PREPARED);
	}

	@Test
	void rejectsPostConsensusFreezeUpgradeWithNonMatchingUpdateFileHash() {
		final var pretendHash = PRETEND_HASH.toByteArray();
		given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);
		given(networkCtx.getPreparedUpdateFileHash()).willReturn(pretendHash);
		givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());

		assertFailsWith(() -> subject.doStateTransition(), UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE);
	}

	@Test
	void rejectsPostConsensusFreezeUpgradeWithInvalidTime() {
		final var pretendHash = PRETEND_HASH.toByteArray();
		given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);
		given(networkCtx.getPreparedUpdateFileHash()).willReturn(pretendHash);
		given(specialFiles.hashMatches(CANONICAL_FILE, pretendHash)).willReturn(true);
		givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());
		given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME.plusSeconds(Integer.MAX_VALUE));

		assertFailsWith(() -> subject.doStateTransition(), INVALID_FREEZE_TRANSACTION_BODY);
	}

	@Test
	void acceptsPostConsensusFreezeUpgradeWithEverythingInPlace() {
		final var pretendHash = PRETEND_HASH.toByteArray();
		given(networkCtx.getPreparedUpdateFileNum()).willReturn(150L);
		given(networkCtx.getPreparedUpdateFileHash()).willReturn(pretendHash);
		given(specialFiles.hashMatches(CANONICAL_FILE, pretendHash)).willReturn(true);
		givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());
		given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);

		subject.doStateTransition();

		verify(upgradeHelper).scheduleFreezeAt(VALID_START_TIME);
	}

	@Test
	void hasCorrectApplicability() {
		givenTypicalTxn(true, FREEZE_ONLY, Optional.empty(), Optional.empty());

		assertTrue(subject.applicability().test(freezeTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void freezeOnlyPrecheckRejectsDeprecatedStartHour() {
		givenTxn(false, FREEZE_ONLY, Optional.empty(), Optional.empty(), false,
				true, false, false, false);

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void freezeOnlyPrecheckRejectsDeprecatedStartMin() {
		givenTxn(false, FREEZE_ONLY, Optional.empty(), Optional.empty(), false,
				false, true, false, false);

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void freezeOnlyPrecheckRejectsDeprecatedEndHour() {
		givenTxn(false, FREEZE_ONLY, Optional.empty(), Optional.empty(), false,
				false, false, true, false);

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void freezeOnlyPrecheckRejectsDeprecatedEndMin() {
		givenTxn(false, FREEZE_ONLY, Optional.empty(), Optional.empty(), false,
				false, false, false, true);

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void freezeOnlyPrecheckRejectsInvalidTime() {
		givenTypicalTxn(false, FREEZE_ONLY, Optional.empty(), Optional.empty());

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void freezeUpgradePrecheckRejectsInvalidTime() {
		givenTypicalTxn(false, FREEZE_UPGRADE, Optional.empty(), Optional.empty());

		assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void freezeOnlyPrecheckAcceptsGivenValidTime() {
		givenTypicalTxn(true, FREEZE_ONLY, Optional.empty(), Optional.empty());

		assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void prepPrecheckRejectsImpossibleFile() {
		givenTypicalTxn(false, PREPARE_UPGRADE, Optional.of(ILLEGAL_FILE), Optional.empty());

		assertEquals(FREEZE_UPDATE_FILE_DOES_NOT_EXIST, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void prepPrecheckRejectsImpossibleHash() {
		given(specialFiles.contains(CANONICAL_FILE)).willReturn(true);
		givenTypicalTxn(false, PREPARE_UPGRADE, Optional.of(CANONICAL_FILE), Optional.empty());

		assertEquals(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void prepPrecheckAcceptsViableValues() {
		given(specialFiles.contains(CANONICAL_FILE)).willReturn(true);
		givenTypicalTxn(false, PREPARE_UPGRADE, Optional.of(CANONICAL_FILE), Optional.of(PRETEND_HASH));

		assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void abortPrecheckAcceptsWhatever() {
		givenTypicalTxn(false, FREEZE_ABORT, Optional.empty(), Optional.of(PRETEND_HASH));

		assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
	}

	@Test
	void telemetryPrecheckAcceptsWhatever() {
		givenTypicalTxn(false, TELEMETRY_UPGRADE, Optional.empty(), Optional.of(PRETEND_HASH));

		assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
	}

	private void givenTypicalTxnInCtx(
			final boolean validTime,
			final FreezeType enumValue,
			final Optional<FileID> target,
			final Optional<ByteString> hash
	) {
		givenTypicalTxn(validTime, enumValue, target, hash);
		given(txnCtx.accessor()).willReturn(accessor);
		given(accessor.getTxn()).willReturn(freezeTxn);
	}

	private void givenTypicalTxn(
			final boolean validTime,
			final FreezeType enumValue,
			final Optional<FileID> target,
			final Optional<ByteString> hash
	) {
		givenTxn(validTime, enumValue, target, hash, true,
				false, false, false, false);
	}

	private void givenTxn(
			final boolean validTime,
			final FreezeType enumValue,
			final Optional<FileID> updateTarget,
			final Optional<ByteString> fileHash,
			final boolean useNonRejectedTimestamp,
			final boolean useRejectedStartHour,
			final boolean useRejectedStartMin,
			final boolean useRejectedEndHour,
			final boolean useRejectedEndMin

	) {
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId());

		final var op = FreezeTransactionBody.newBuilder()
				.setFreezeType(enumValue);
		if (!useNonRejectedTimestamp) {
			if (useRejectedStartHour) {
				addRejectedStartHour(op);
			}
			if (useRejectedStartMin) {
				addRejectedStartMin(op);
			}
			if (useRejectedEndHour) {
				addRejectedEndHour(op);
			}
			if (useRejectedEndMin) {
				addRejectedEndMin(op);
			}
		} else {
			if (validTime) {
				setValidFreezeStartTimeStamp(op);
			} else {
				setInvalidFreezeStartTimeStamp(op);
			}
		}
		updateTarget.ifPresent(op::setUpdateFile);
		fileHash.ifPresent(op::setFileHash);

		txn.setFreeze(op);
		freezeTxn = txn.build();
	}

	private void addRejectedStartHour(final FreezeTransactionBody.Builder op) {
		op.setStartHour(15).setStartMin(15).setEndHour(15).setEndMin(20);
	}

	private void addRejectedStartMin(final FreezeTransactionBody.Builder op) {
		op.setStartMin(15);
	}

	private void addRejectedEndHour(final FreezeTransactionBody.Builder op) {
		op.setEndHour(15);
	}

	private void addRejectedEndMin(final FreezeTransactionBody.Builder op) {
		op.setEndMin(20);
	}

	private void plusInvalidTime(final FreezeTransactionBody.Builder op) {
		op.setStartHour(24).setStartMin(15).setEndHour(15).setEndMin(20);
	}

	private void setValidFreezeStartTimeStamp(final FreezeTransactionBody.Builder op) {
		op.setStartTime(Timestamp.newBuilder()
				.setSeconds(VALID_START_TIME.getEpochSecond())
				.setNanos(VALID_START_TIME.getNano()));
	}

	private void setInvalidFreezeStartTimeStamp(final FreezeTransactionBody.Builder op) {
		final var inValidFreezeStartTime = CONSENSUS_TIME.minusSeconds(60);
		op.setStartTime(Timestamp.newBuilder()
				.setSeconds(inValidFreezeStartTime.getEpochSecond())
				.setNanos(inValidFreezeStartTime.getNano()));
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(PAYER)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(CONSENSUS_TIME.getEpochSecond()))
				.build();
	}
}
