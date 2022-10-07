/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.txns.network;

import static com.hedera.services.utils.MiscUtils.timestampToInstant;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_ABORT;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_ONLY;
import static com.hederahashgraph.api.proto.java.FreezeType.FREEZE_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.PREPARE_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.TELEMETRY_UPGRADE;
import static com.hederahashgraph.api.proto.java.FreezeType.UNKNOWN_FREEZE_TYPE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_ALREADY_SCHEDULED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_UPGRADE_IN_PROGRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreezeTransitionLogicTest {
    private static final FileID SOFTWARE_UPGRADE_FILE = IdUtils.asFile("0.0.150");
    private static final FileID TELEMETRY_UPGRADE_FILE = IdUtils.asFile("0.0.159");
    private static final FileID ILLEGAL_FILE = IdUtils.asFile("0.0.160");
    private static final AccountID PAYER = IdUtils.asAccount("0.0.1234");
    private static final Instant CONSENSUS_TIME = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Instant VALID_START_TIME = CONSENSUS_TIME.plusSeconds(120);
    private static final ByteString IMPOSSIBLE_HASH =
            ByteString.copyFromUtf8("012345678901234567890123456789");
    private static final ByteString PRETEND_HASH =
            ByteString.copyFromUtf8("012345678901234567890123456789012345678901234567");
    private static final byte[] hashBytes = PRETEND_HASH.toByteArray();
    private static final ByteString ALSO_PRETEND_HASH =
            ByteString.copyFromUtf8("x123456789x123456789x123456789x123456789x1234567");
    private static final byte[] PRETEND_ARCHIVE =
            "This is missing something. Hard to put a finger on what..."
                    .getBytes(StandardCharsets.UTF_8);

    private TransactionBody freezeTxn;

    @Mock private UpgradeActions upgradeActions;
    @Mock private SignedTxnAccessor accessor;
    @Mock private TransactionContext txnCtx;
    @Mock private MerkleSpecialFiles specialFiles;
    @Mock private MerkleNetworkContext networkCtx;

    private FreezeTransitionLogic subject;

    @BeforeEach
    void setup() {
        subject =
                new FreezeTransitionLogic(
                        upgradeActions, txnCtx, () -> specialFiles, () -> networkCtx);
    }

    @Test
    void rejectsPostConsensusTelemetryUpgradeWithInvalidTime() {
        givenTypicalTxnInCtx(true, TELEMETRY_UPGRADE, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME.plusSeconds(Integer.MAX_VALUE));

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_START_TIME_MUST_BE_FUTURE);
    }

    @Test
    void rejectsPostConsensusTelemetryUpgradeWithAlreadyScheduledFreeze() {
        givenTypicalTxnInCtx(true, TELEMETRY_UPGRADE, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(upgradeActions.isFreezeScheduled()).willReturn(true);

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_ALREADY_SCHEDULED);
    }

    @Test
    void rejectsPostConsensusTelemetryUpgradeWithAlreadyPreparedUpgrade() {
        givenTypicalTxnInCtx(true, TELEMETRY_UPGRADE, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_UPGRADE_IN_PROGRESS);
    }

    @Test
    void rejectsPostConsensusTelemetryUpgradeWithMismatchedHash() {
        givenTypicalTxnInCtx(
                true,
                TELEMETRY_UPGRADE,
                Optional.of(TELEMETRY_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
    }

    @Test
    void acceptsPostConsensusTelemetryUpgradeWithMatchingHash() {
        givenTypicalTxnInCtx(
                true,
                TELEMETRY_UPGRADE,
                Optional.of(TELEMETRY_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(specialFiles.hashMatches(TELEMETRY_UPGRADE_FILE, hashBytes)).willReturn(true);
        given(specialFiles.get(TELEMETRY_UPGRADE_FILE)).willReturn(PRETEND_ARCHIVE);

        subject.doStateTransition();

        final var timeUsed = timestampToInstant(freezeTxn.getFreeze().getStartTime());
        verify(upgradeActions).extractTelemetryUpgrade(PRETEND_ARCHIVE, timeUsed);
    }

    @Test
    void rejectsPostConsensusFreezeOnlyWithInvalidTime() {
        givenTypicalTxnInCtx(true, FREEZE_ONLY, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME.plusSeconds(Integer.MAX_VALUE));

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_START_TIME_MUST_BE_FUTURE);
    }

    @Test
    void rejectsPostConsensusFreezeOnlyWithPendingFreeze() {
        givenTypicalTxnInCtx(true, FREEZE_ONLY, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(upgradeActions.isFreezeScheduled()).willReturn(true);

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_ALREADY_SCHEDULED);
    }

    @Test
    void rejectsPostConsensusFreezeOnlyWithPendingUpgrade() {
        givenTypicalTxnInCtx(true, FREEZE_ONLY, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_UPGRADE_IN_PROGRESS);
    }

    @Test
    void rejectsPostConsensusFreezeUpgradeWithPendingFreeze() {
        givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());
        given(upgradeActions.isFreezeScheduled()).willReturn(true);
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_ALREADY_SCHEDULED);
    }

    @Test
    void rejectsPostConsensusFreezeUpgradeWithDifferentFileId() {
        givenTypicalTxnInCtx(
                true,
                FREEZE_UPGRADE,
                Optional.of(TELEMETRY_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(SOFTWARE_UPGRADE_FILE.getFileNum());

        assertFailsWith(() -> subject.doStateTransition(), UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED);
    }

    @Test
    void rejectsPostConsensusFreezeUpgradeWithDifferentFileHash() {
        givenTypicalTxnInCtx(
                true,
                FREEZE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(ALSO_PRETEND_HASH));
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(SOFTWARE_UPGRADE_FILE.getFileNum());
        given(networkCtx.getPreparedUpdateFileHash()).willReturn(hashBytes);

        assertFailsWith(
                () -> subject.doStateTransition(), UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED);
    }

    @Test
    void acceptsPostConsensusFreezeOnlyWithValidTimeNoPendingWork() {
        givenTypicalTxnInCtx(true, FREEZE_ONLY, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);

        subject.doStateTransition();

        verify(upgradeActions).scheduleFreezeOnlyAt(VALID_START_TIME);
    }

    @Test
    void rejectsPostConsensusFreezeUpgradeWithNoUpdatePrepared() {
        givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);

        assertFailsWith(() -> subject.doStateTransition(), NO_UPGRADE_HAS_BEEN_PREPARED);
    }

    @Test
    void rejectsPostConsensusFreezeUpgradeWithNonMatchingUpdateFileHash() {
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        givenTypicalTxnInCtx(
                true,
                FREEZE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(SOFTWARE_UPGRADE_FILE.getFileNum());
        given(networkCtx.getPreparedUpdateFileHash()).willReturn(hashBytes);

        assertFailsWith(
                () -> subject.doStateTransition(), UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE);
    }

    @Test
    void rejectsPostConsensusFreezeUpgradeWithInvalidTime() {
        givenTypicalTxnInCtx(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME.plusSeconds(Integer.MAX_VALUE));

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_START_TIME_MUST_BE_FUTURE);
    }

    @Test
    void acceptsPostConsensusFreezeUpgradeWithEverythingInPlace() {
        given(networkCtx.hasPreparedUpgrade()).willReturn(true);
        given(networkCtx.isPreparedFileHashValidGiven(specialFiles)).willReturn(true);
        givenTypicalTxnInCtx(
                true,
                FREEZE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));
        given(txnCtx.consensusTime()).willReturn(CONSENSUS_TIME);
        given(networkCtx.getPreparedUpdateFileNum()).willReturn(SOFTWARE_UPGRADE_FILE.getFileNum());
        given(networkCtx.getPreparedUpdateFileHash()).willReturn(hashBytes);

        subject.doStateTransition();

        verify(upgradeActions).scheduleFreezeUpgradeAt(VALID_START_TIME);
    }

    @Test
    void acceptsFreezeAbortEvenWithNoPendingFreezeOrPreparedUpgrade() {
        givenTypicalTxnInCtx(false, FREEZE_ABORT, Optional.empty(), Optional.empty());

        subject.doStateTransition();

        verify(upgradeActions).abortScheduledFreeze();
    }

    @Test
    void rejectsPostConsensusPrepareUpgradeWithUnmatchedHash() {
        givenTypicalTxnInCtx(
                false,
                PREPARE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));

        assertFailsWith(() -> subject.doStateTransition(), FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
    }

    @Test
    void rejectsPostConsensusUnrecognizedFreeze() {
        givenTypicalTxnInCtx(false, UNKNOWN_FREEZE_TYPE, Optional.empty(), Optional.empty());

        assertFailsWith(() -> subject.doStateTransition(), FAIL_INVALID);
    }

    @Test
    void unarchivesDataWithMatchingHash() {
        givenTypicalTxnInCtx(
                false,
                PREPARE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));
        given(specialFiles.hashMatches(SOFTWARE_UPGRADE_FILE, hashBytes)).willReturn(true);
        given(specialFiles.get(SOFTWARE_UPGRADE_FILE)).willReturn(PRETEND_ARCHIVE);

        subject.doStateTransition();

        verify(upgradeActions).extractSoftwareUpgrade(PRETEND_ARCHIVE);
        verify(networkCtx).recordPreparedUpgrade(freezeTxn.getFreeze());
    }

    @Test
    void hasCorrectApplicability() {
        givenTypicalTxn(true, FREEZE_ONLY, Optional.empty(), Optional.empty());

        assertTrue(subject.applicability().test(freezeTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void mustHaveValidTypeSetExplicitly() {
        givenTypicalTxn(true, UNKNOWN_FREEZE_TYPE, Optional.empty(), Optional.empty());

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeOnlyPrecheckRejectsDeprecatedStartHour() {
        givenTxn(
                false,
                FREEZE_ONLY,
                Optional.empty(),
                Optional.empty(),
                false,
                true,
                false,
                false,
                false);

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeOnlyPrecheckRejectsDeprecatedStartMin() {
        givenTxn(
                false,
                FREEZE_ONLY,
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                true,
                false,
                false);

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeOnlyPrecheckRejectsDeprecatedEndHour() {
        givenTxn(
                false,
                FREEZE_ONLY,
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                false,
                true,
                false);

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeOnlyPrecheckRejectsDeprecatedEndMin() {
        givenTxn(
                false,
                FREEZE_ONLY,
                Optional.empty(),
                Optional.empty(),
                false,
                false,
                false,
                false,
                true);

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeOnlyPrecheckRejectsInvalidTime() {
        givenTypicalTxn(false, FREEZE_ONLY, Optional.empty(), Optional.empty());

        assertEquals(FREEZE_START_TIME_MUST_BE_FUTURE, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeUpgradePrecheckRejectsInvalidTime() {
        givenTypicalTxn(false, FREEZE_UPGRADE, Optional.empty(), Optional.empty());

        assertEquals(FREEZE_START_TIME_MUST_BE_FUTURE, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeUpgradePrecheckRejectsMissingFile() {
        givenTypicalTxn(true, FREEZE_UPGRADE, Optional.empty(), Optional.empty());

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void telemetryUpgradePrecheckRejectsInvalidTime() {
        givenTypicalTxn(false, TELEMETRY_UPGRADE, Optional.empty(), Optional.empty());

        assertEquals(FREEZE_START_TIME_MUST_BE_FUTURE, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void telemetryUpgradePrecheckRejectsImpossibleFile() {
        givenTypicalTxn(
                true, TELEMETRY_UPGRADE, Optional.of(ILLEGAL_FILE), Optional.of(PRETEND_HASH));

        assertEquals(FREEZE_UPDATE_FILE_DOES_NOT_EXIST, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void freezeOnlyPrecheckAcceptsGivenValidTime() {
        givenTypicalTxn(true, FREEZE_ONLY, Optional.empty(), Optional.empty());

        assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void prepPrecheckRejectsMissingFile() {
        givenTypicalTxn(false, PREPARE_UPGRADE, Optional.empty(), Optional.empty());

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void prepPrecheckRejectsMissingHash() {
        givenTypicalTxn(
                false, PREPARE_UPGRADE, Optional.of(SOFTWARE_UPGRADE_FILE), Optional.empty());

        assertEquals(INVALID_FREEZE_TRANSACTION_BODY, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void prepPrecheckRejectsImpossibleFile() {
        givenTypicalTxn(
                false, PREPARE_UPGRADE, Optional.of(ILLEGAL_FILE), Optional.of(PRETEND_HASH));

        assertEquals(FREEZE_UPDATE_FILE_DOES_NOT_EXIST, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void prepPrecheckRejectsImpossibleHash() {
        given(specialFiles.contains(SOFTWARE_UPGRADE_FILE)).willReturn(true);
        givenTypicalTxn(
                false,
                PREPARE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(IMPOSSIBLE_HASH));

        assertEquals(
                FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void prepPrecheckAcceptsViableValues() {
        given(specialFiles.contains(SOFTWARE_UPGRADE_FILE)).willReturn(true);
        givenTypicalTxn(
                false,
                PREPARE_UPGRADE,
                Optional.of(SOFTWARE_UPGRADE_FILE),
                Optional.of(PRETEND_HASH));

        assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
    }

    @Test
    void abortPrecheckAcceptsWhatever() {
        givenTypicalTxn(false, FREEZE_ABORT, Optional.empty(), Optional.of(PRETEND_HASH));

        assertEquals(OK, subject.semanticCheck().apply(freezeTxn));
    }

    private void givenTypicalTxnInCtx(
            final boolean validTime,
            final FreezeType enumValue,
            final Optional<FileID> target,
            final Optional<ByteString> hash) {
        givenTypicalTxn(validTime, enumValue, target, hash);
        given(txnCtx.accessor()).willReturn(accessor);
        given(accessor.getTxn()).willReturn(freezeTxn);
    }

    private void givenTypicalTxn(
            final boolean validTime,
            final FreezeType enumValue,
            final Optional<FileID> target,
            final Optional<ByteString> hash) {
        givenTxn(validTime, enumValue, target, hash, true, false, false, false, false);
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
            final boolean useRejectedEndMin) {

        final var txn = TransactionBody.newBuilder().setTransactionID(ourTxnId());

        final var op = FreezeTransactionBody.newBuilder().setFreezeType(enumValue);
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
        op.setStartTime(
                Timestamp.newBuilder()
                        .setSeconds(VALID_START_TIME.getEpochSecond())
                        .setNanos(VALID_START_TIME.getNano()));
    }

    private void setInvalidFreezeStartTimeStamp(final FreezeTransactionBody.Builder op) {
        final var inValidFreezeStartTime = CONSENSUS_TIME.minusSeconds(60);
        op.setStartTime(
                Timestamp.newBuilder()
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
