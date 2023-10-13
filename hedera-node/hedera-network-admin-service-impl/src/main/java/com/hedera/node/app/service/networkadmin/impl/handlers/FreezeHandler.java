/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ONLY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.PREPARE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.TELEMETRY_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.UNKNOWN_FREEZE_TYPE;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.mono.fees.calculation.system.txns.FreezeResourceUsage;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.service.networkadmin.impl.WritableFreezeStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FREEZE}.
 */
@Singleton
public class FreezeHandler implements TransactionHandler {
    // length of the hash of the update file included in the FreezeTransactionBody
    // used for a quick sanity check that the file hash is not invalid
    public static final int UPDATE_FILE_HASH_LEN = 48;

    private final Executor freezeExecutor;

    @Inject
    public FreezeHandler(@NonNull @Named("FreezeService") final Executor freezeExecutor) {
        this.freezeExecutor = requireNonNull(freezeExecutor);
    }

    /**
     * This method is called during the pre-handle workflow for Freeze transactions.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @see <a href="https://hashgraph.github.io/hedera-protobufs/#freeze.proto">Protobuf freeze documentation</a>
     */
    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());

        final FreezeTransactionBody freezeTxn = context.body().freezeOrThrow();
        final FreezeType freezeType = freezeTxn.freezeType();
        if (Arrays.asList(FREEZE_ONLY, FREEZE_UPGRADE, TELEMETRY_UPGRADE).contains(freezeType)) {
            final Timestamp txValidStart = context.body().transactionIDOrThrow().transactionValidStartOrThrow();
            verifyFreezeStartTimeIsInFuture(freezeTxn, txValidStart);
        }
        if (Arrays.asList(FREEZE_UPGRADE, TELEMETRY_UPGRADE, PREPARE_UPGRADE).contains(freezeType)) {
            // from proto specs, it looks like updateFileId not required for FREEZE_UPGRADE and TELEMETRY_UPGRADE
            // but specs aren't very clear previous code in FreezeTransitionLogic checks for it in all 3 cases,
            // so we will do the same
            final ReadableUpgradeFileStore upgradeStore = context.createStore(ReadableUpgradeFileStore.class);
            final var filesConfig = context.configuration().getConfigData(FilesConfig.class);
            verifyUpdateFileAndHash(freezeTxn, upgradeStore, filesConfig.upgradeFileNumber());
        }
        // no need to add any keys to the context because this transaction does not require any signatures
        // it must be submitted by an account with superuser privileges, that is checked during ingest
    }

    /**
     * Performs checks independent of state or context
     */
    @SuppressWarnings("java:S1874") // disables the warnings for use of deprecated code
    // it is necessary to check startHour, startMin, endHour, endMin, all of which are deprecated
    // because if any are present then we set a status of INVALID_FREEZE_TRANSACTION_BODY
    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {

        FreezeTransactionBody freezeTxn = txn.freezeOrThrow();
        // freeze.proto properties startHour, startMin, endHour, endMin are deprecated in the protobuf
        // reject any freeze transactions that set these properties
        if (freezeTxn.startHour() != 0
                || freezeTxn.startMin() != 0
                || freezeTxn.endHour() != 0
                || freezeTxn.endMin() != 0) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
        }

        final FreezeType freezeType = freezeTxn.freezeType();
        if (freezeType == UNKNOWN_FREEZE_TYPE) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final NetworkAdminConfig adminServiceConfig = context.configuration().getConfigData(NetworkAdminConfig.class);
        final ReadableUpgradeFileStore upgradeFileStore = context.readableStore(ReadableUpgradeFileStore.class);
        final WritableFreezeStore freezeStore = context.writableStore(WritableFreezeStore.class);

        final FreezeTransactionBody freezeTxn = txn.freezeOrThrow();

        validateSemantics(freezeTxn, freezeStore, upgradeFileStore);

        final FreezeUpgradeActions upgradeActions =
                new FreezeUpgradeActions(adminServiceConfig, freezeStore, freezeExecutor);
        final Timestamp freezeStartTime = freezeTxn.startTime(); // may be null for some freeze types

        switch (freezeTxn.freezeType()) {
            case PREPARE_UPGRADE -> {
                // by the time we get here, we've already checked that fileHash is non-null in preHandle()
                freezeStore.updateFileHash(freezeTxn.fileHash());
                try {
                    upgradeActions.extractSoftwareUpgrade(upgradeFileStore.getFull());
                } catch (IOException e) {
                    throw new IllegalStateException("Error extracting upgrade file", e);
                }
            }
            case FREEZE_UPGRADE -> upgradeActions.scheduleFreezeUpgradeAt(requireNonNull(freezeStartTime));
            case FREEZE_ABORT -> {
                upgradeActions.abortScheduledFreeze();
                freezeStore.updateFileHash(null);
            }
            case TELEMETRY_UPGRADE -> {
                try {
                    upgradeActions.extractTelemetryUpgrade(upgradeFileStore.getFull(), requireNonNull(freezeStartTime));
                } catch (IOException e) {
                    throw new IllegalStateException("Error extracting upgrade file", e);
                }
            }
            case FREEZE_ONLY -> upgradeActions.scheduleFreezeOnlyAt(requireNonNull(freezeStartTime));
                // UNKNOWN_FREEZE_TYPE will fail at preHandle, this code should never get called
            case UNKNOWN_FREEZE_TYPE -> throw new HandleException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
        }
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var op = feeContext.body();

        return feeContext.feeCalculator(SubType.DEFAULT).legacyCalculate(sigValueObj -> new FreezeResourceUsage()
                .usageGiven(fromPbj(op), sigValueObj, null));
    }

    /**
     * Performs checks that the entities related to this transaction exist and are valid
     */
    private static void validateSemantics(
            @NonNull final FreezeTransactionBody freezeTxn,
            @NonNull final ReadableFreezeStore freezeStore,
            @NonNull final ReadableUpgradeFileStore upgradeStore) {
        requireNonNull(freezeTxn);
        requireNonNull(freezeStore);
        requireNonNull(upgradeStore);

        if ((freezeTxn.freezeType() == PREPARE_UPGRADE || freezeTxn.freezeType() == TELEMETRY_UPGRADE)
                && (upgradeStore.peek() == null)) {
            throw new IllegalStateException("Update file not found");
        }
    }
    /**
     * For freeze types FREEZE_ONLY, FREEZE_UPGRADE, and TELEMETRY_UPGRADE, the startTime field must be set to
     * a time in the future, where future is defined as a time after the current consensus time.
     * @throws PreCheckException if startTime is not in the future
     */
    private static void verifyFreezeStartTimeIsInFuture(
            final @NonNull FreezeTransactionBody freezeTxn, final @NonNull Timestamp curConsensusTime)
            throws PreCheckException {
        requireNonNull(freezeTxn);
        requireNonNull(curConsensusTime);
        final Timestamp freezeStartTime = freezeTxn.startTime();
        if (freezeStartTime == null || (freezeStartTime.seconds() == 0 && freezeStartTime.nanos() == 0)) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
        }
        final Instant freezeStartTimeInstant =
                Instant.ofEpochSecond(freezeStartTime.seconds(), freezeStartTime.nanos());

        final Instant effectiveNowInstant = Instant.ofEpochSecond(curConsensusTime.seconds(), curConsensusTime.nanos());

        // make sure freezeStartTime is after current consensus time
        final boolean freezeStartTimeIsInFuture = freezeStartTimeInstant.isAfter(effectiveNowInstant);
        if (!freezeStartTimeIsInFuture) {
            throw new PreCheckException(ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE);
        }
    }

    /**
     * For freeze types PREPARE_UPGRADE, FREEZE_UPGRADE, and TELEMETRY_UPGRADE,
     * the updateFile and fileHash fields must be set.
     * @throws PreCheckException if updateFile or fileHash are not set or don't pass sanity checks
     */
    private static void verifyUpdateFileAndHash(
            final @NonNull FreezeTransactionBody freezeTxn,
            final @NonNull ReadableUpgradeFileStore upgradeStore,
            final long upgradeFileNumber)
            throws PreCheckException {
        requireNonNull(freezeTxn);
        requireNonNull(upgradeStore);

        // check that the updateFileID was set in the freeze transaction to the correct ID for upgrade files
        // this is the *only* place that the FileID is accessed
        // we subsequently ignore it
        final FileID updateFileID = freezeTxn.updateFile();
        if (updateFileID == null || updateFileID.fileNum() != upgradeFileNumber) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
        }
        if (upgradeStore.peek() == null) {
            throw new PreCheckException(ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST);
        }

        final Bytes fileHash = freezeTxn.fileHash();
        // don't verify the hash, just make sure it is not null or empty and is the correct length
        if (fileHash == null || Bytes.EMPTY.equals(fileHash) || fileHash.length() != UPDATE_FILE_HASH_LEN) {
            throw new PreCheckException(ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
        }
    }
}
