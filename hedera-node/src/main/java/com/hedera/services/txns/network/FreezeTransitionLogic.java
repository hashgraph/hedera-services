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

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.MerkleNetworkContext.UPDATE_FILE_HASH_LEN;
import static com.hedera.services.utils.MiscUtils.timestampToInstant;
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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FreezeTransitionLogic implements TransitionLogic {
    private final UpgradeActions upgradeActions;
    private final TransactionContext txnCtx;
    private final Supplier<MerkleSpecialFiles> specialFiles;
    private final Supplier<MerkleNetworkContext> networkCtx;

    @Inject
    public FreezeTransitionLogic(
            final UpgradeActions upgradeActions,
            final TransactionContext txnCtx,
            final Supplier<MerkleSpecialFiles> specialFiles,
            final Supplier<MerkleNetworkContext> networkCtx) {
        this.txnCtx = txnCtx;
        this.networkCtx = networkCtx;
        this.specialFiles = specialFiles;
        this.upgradeActions = upgradeActions;
    }

    @Override
    public void doStateTransition() {
        final var op = txnCtx.accessor().getTxn().getFreeze();

        assertValidityAtCons(op);

        switch (op.getFreezeType()) {
            case PREPARE_UPGRADE:
                final var softwareUpdateZip = specialFiles.get().get(op.getUpdateFile());
                upgradeActions.extractSoftwareUpgrade(softwareUpdateZip);
                networkCtx.get().recordPreparedUpgrade(op);
                break;
            case FREEZE_UPGRADE:
                upgradeActions.scheduleFreezeUpgradeAt(timestampToInstant(op.getStartTime()));
                break;
            case FREEZE_ABORT:
                upgradeActions.abortScheduledFreeze();
                networkCtx.get().discardPreparedUpgradeMeta();
                break;
            case TELEMETRY_UPGRADE:
                final var telemetryUpdateZip = specialFiles.get().get(op.getUpdateFile());
                upgradeActions.extractTelemetryUpgrade(
                        telemetryUpdateZip, timestampToInstant(op.getStartTime()));
                break;
            default:
            case FREEZE_ONLY:
                upgradeActions.scheduleFreezeOnlyAt(timestampToInstant(op.getStartTime()));
                break;
        }
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasFreeze;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validateBasics;
    }

    private ResponseCodeEnum validateBasics(TransactionBody freezeTxn) {
        final var op = freezeTxn.getFreeze();

        if (op.getStartHour() != 0
                || op.getStartMin() != 0
                || op.getEndHour() != 0
                || op.getEndMin() != 0) {
            return INVALID_FREEZE_TRANSACTION_BODY;
        }

        final var effectiveNow =
                timestampToInstant(freezeTxn.getTransactionID().getTransactionValidStart());
        switch (op.getFreezeType()) {
            case FREEZE_ABORT:
                return OK;
            case FREEZE_ONLY:
                return validate(op, effectiveNow, false);
            case PREPARE_UPGRADE:
                return validate(op, null, true);
            case FREEZE_UPGRADE, TELEMETRY_UPGRADE:
                return validate(op, effectiveNow, true);
            default:
                return INVALID_FREEZE_TRANSACTION_BODY;
        }
    }

    private ResponseCodeEnum validate(
            final FreezeTransactionBody op, @Nullable final Instant now, final boolean checkMeta) {
        if (now != null) {
            final var timeValidity = freezeTimeValidity(op.getStartTime(), now);
            if (timeValidity != OK) {
                return timeValidity;
            }
        }
        return checkMeta ? sanityCheckUpgradeMeta(op) : OK;
    }

    private ResponseCodeEnum sanityCheckUpgradeMeta(final FreezeTransactionBody op) {
        if (!op.hasUpdateFile() || op.getFileHash().isEmpty()) {
            return INVALID_FREEZE_TRANSACTION_BODY;
        }
        final var updateFile = op.getUpdateFile();
        if (!specialFiles.get().contains(updateFile)) {
            return FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
        }
        final var allegedSha384Hash = op.getFileHash();
        if (allegedSha384Hash.size() != UPDATE_FILE_HASH_LEN) {
            return FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
        }
        return OK;
    }

    private ResponseCodeEnum freezeTimeValidity(
            final Timestamp freezeStartTime, final Instant now) {
        return isInTheFuture(freezeStartTime, now) ? OK : FREEZE_START_TIME_MUST_BE_FUTURE;
    }

    private void assertValidityAtCons(FreezeTransactionBody op) {
        final var freezeType = op.getFreezeType();
        switch (freezeType) {
            case FREEZE_UPGRADE:
                assertValidFreezeUpgrade(op);
                break;
            case FREEZE_ONLY:
                assertValidFreezeOnly(op);
                break;
            case FREEZE_ABORT:
                /* Nothing to do here; FREEZE_ABORT is idempotent to allow DevOps to submit
                multiple such transactions if disconnected nodes missed the previous. */
                break;
            case PREPARE_UPGRADE:
                assertValidPrepareUpgrade(op);
                break;
            case TELEMETRY_UPGRADE:
                assertValidTelemetryUpgrade(op);
                break;
            default:
                throw new InvalidTransactionException(
                        "Transaction type '"
                                + freezeType
                                + "' should have been rejected in precheck",
                        FAIL_INVALID);
        }
    }

    private void assertValidFreezeOnly(final FreezeTransactionBody op) {
        assertValidStartTime(op);
        assertNoPendingFreezeOrUpgrade();
    }

    private void assertValidTelemetryUpgrade(final FreezeTransactionBody op) {
        assertValidStartTime(op);
        assertNoPendingFreezeOrUpgrade();
        assertUpdateHashMatches(op);
    }

    private void assertValidPrepareUpgrade(final FreezeTransactionBody op) {
        assertNoPendingFreezeOrUpgrade();
        assertUpdateHashMatches(op);
    }

    private void assertValidFreezeUpgrade(final FreezeTransactionBody op) {
        assertValidStartTime(op);
        validateFalse(upgradeActions.isFreezeScheduled(), FREEZE_ALREADY_SCHEDULED);

        final var curNetworkCtx = networkCtx.get();
        validateTrue(curNetworkCtx.hasPreparedUpgrade(), NO_UPGRADE_HAS_BEEN_PREPARED);

        final var fileMatches =
                op.getUpdateFile().getFileNum() == curNetworkCtx.getPreparedUpdateFileNum();
        validateTrue(fileMatches, UPDATE_FILE_ID_DOES_NOT_MATCH_PREPARED);
        final var hashMatches =
                Arrays.equals(
                        op.getFileHash().toByteArray(), curNetworkCtx.getPreparedUpdateFileHash());
        validateTrue(hashMatches, UPDATE_FILE_HASH_DOES_NOT_MATCH_PREPARED);

        final var isHashUnchanged = curNetworkCtx.isPreparedFileHashValidGiven(specialFiles.get());
        validateTrue(isHashUnchanged, UPDATE_FILE_HASH_CHANGED_SINCE_PREPARE_UPGRADE);
    }

    private void assertNoPendingFreezeOrUpgrade() {
        validateFalse(upgradeActions.isFreezeScheduled(), FREEZE_ALREADY_SCHEDULED);
        validateFalse(networkCtx.get().hasPreparedUpgrade(), FREEZE_UPGRADE_IN_PROGRESS);
    }

    private void assertValidStartTime(final FreezeTransactionBody op) {
        validateTrue(
                isInTheFuture(op.getStartTime(), txnCtx.consensusTime()),
                FREEZE_START_TIME_MUST_BE_FUTURE);
    }

    private void assertUpdateHashMatches(final FreezeTransactionBody op) {
        final var curSpecialFiles = specialFiles.get();
        final var isMatch =
                curSpecialFiles.hashMatches(op.getUpdateFile(), op.getFileHash().toByteArray());
        validateTrue(isMatch, FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
    }

    private boolean isInTheFuture(final Timestamp freezeStartTime, final Instant now) {
        return Instant.ofEpochSecond(freezeStartTime.getSeconds(), freezeStartTime.getNanos())
                .isAfter(now);
    }
}
