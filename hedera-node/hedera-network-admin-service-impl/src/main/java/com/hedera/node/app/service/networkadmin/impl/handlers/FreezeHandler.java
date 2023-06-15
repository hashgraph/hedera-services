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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.networkadmin.ReadableUpdateFileStore;
import com.hedera.node.app.service.networkadmin.impl.WritableUpdateFileStore;
import com.hedera.node.app.service.networkadmin.impl.config.NetworkAdminServiceConfig;
import com.hedera.node.app.spi.state.WritableFreezeStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#FREEZE}.
 */
@Singleton
public class FreezeHandler implements TransactionHandler {
    // length of the hash of the update file included in the FreezeTransactionBody
    // used for a quick sanity check that the file hash is not invalid
    public static final int UPDATE_FILE_HASH_LEN = 48;

    @Inject
    public FreezeHandler() {
        // Dagger2
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

        // from proto specs, it looks like update file not required for FREEZE_UPGRADE and TELEMETRY_UPGRADE
        // but specs aren't very clear
        // current code in FreezeTransitionLogic checks for the file in specialFiles
        // so we will do the same
        final FreezeTransactionBody freezeTxn = context.body().freezeOrThrow();
        final FreezeType freezeType = freezeTxn.freezeType();
        if (Arrays.asList(FREEZE_ONLY, FREEZE_UPGRADE, TELEMETRY_UPGRADE).contains(freezeType)) {
            final Timestamp txValidStart = context.body().transactionIDOrThrow().transactionValidStartOrThrow();
            verifyFreezeStartTimeIsInFuture(freezeTxn, txValidStart);
        }
        if (Arrays.asList(FREEZE_UPGRADE, TELEMETRY_UPGRADE, PREPARE_UPGRADE).contains(freezeType)) {
            final ReadableUpdateFileStore specialFileStore = context.createStore(ReadableUpdateFileStore.class);
            verifyUpdateFileAndHash(freezeTxn, specialFileStore);
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
        final NetworkAdminServiceConfig adminServiceConfig =
                context.configuration().getConfigData(NetworkAdminServiceConfig.class);
        final WritableUpdateFileStore specialFileStore = context.writableStore(WritableUpdateFileStore.class);
        final WritableFreezeStore freezeStore = context.writableStore(WritableFreezeStore.class);

        final FreezeTransactionBody freezeTxn = txn.freezeOrThrow();
        final FileID updateFileID =
                freezeTxn.updateFile(); // only some freeze types require this, it may be null for others

        validateSemantics(freezeTxn, specialFileStore, updateFileID);

        final FreezeUpgradeActions upgradeActions = new FreezeUpgradeActions(adminServiceConfig, freezeStore);
        final Timestamp freezeStartTime = freezeTxn.startTime(); // may be null for some freeze types
        final Instant freezeStartTimeInstant = freezeStartTime == null
                ? null
                : Instant.ofEpochSecond(freezeStartTime.seconds(), freezeStartTime.nanos());

        // @todo('Issue #6761') - the below switch returns a CompletableFuture, need to use this with an ExecutorService
        switch (freezeTxn.freezeType()) {
            case PREPARE_UPGRADE -> {
                // by the time we get here, we've already checked that updateFileID is non-null in validateSemantics()
                // and that fileHash is non-null in preHandle()
                specialFileStore.updateFileHash(freezeTxn.fileHash());
                specialFileStore.updateFileID(updateFileID);
                upgradeActions.extractSoftwareUpgrade(specialFileStore
                        .get(requireNonNull(updateFileID))
                        .orElseThrow(() -> new IllegalStateException("Update file not found")));
            }
            case FREEZE_UPGRADE -> upgradeActions.scheduleFreezeUpgradeAt(requireNonNull(freezeStartTimeInstant));
            case FREEZE_ABORT -> {
                upgradeActions.abortScheduledFreeze();
                specialFileStore.updateFileHash(null);
                specialFileStore.updateFileID(null);
            }
            case TELEMETRY_UPGRADE -> upgradeActions.extractTelemetryUpgrade(
                    specialFileStore
                            .get(requireNonNull(updateFileID))
                            .orElseThrow(() -> new IllegalStateException("Telemetry update file not found")),
                    requireNonNull(freezeStartTimeInstant));
            case FREEZE_ONLY -> upgradeActions.scheduleFreezeOnlyAt(requireNonNull(freezeStartTimeInstant));
                // UNKNOWN_FREEZE_TYPE will fail at preHandle, this code should never get called
            case UNKNOWN_FREEZE_TYPE -> throw new HandleException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
        }
    }

    /**
     * Performs checks that the entities related to this transaction exist and are valid
     */
    private static void validateSemantics(
            @NonNull final FreezeTransactionBody freezeTxn,
            @NonNull final ReadableUpdateFileStore specialFileStore,
            @Nullable final FileID updateFileID) {
        requireNonNull(freezeTxn);
        requireNonNull(specialFileStore);

        if (freezeTxn.freezeType() == PREPARE_UPGRADE || freezeTxn.freezeType() == TELEMETRY_UPGRADE) {
            if (updateFileID == null) {
                throw new HandleException(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY);
            }
            final Optional<byte[]> updateFileZip = specialFileStore.get(updateFileID);
            if (updateFileZip.isEmpty()) {
                throw new IllegalStateException("Update file not found");
            }
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
            final @NonNull FreezeTransactionBody freezeTxn, final @NonNull ReadableUpdateFileStore specialFileStore)
            throws PreCheckException {
        requireNonNull(freezeTxn);
        requireNonNull(specialFileStore);
        final FileID updateFile = freezeTxn.updateFile();

        if (updateFile == null || specialFileStore.get(updateFile).isEmpty()) {
            throw new PreCheckException(ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST);
        }

        final Bytes fileHash = freezeTxn.fileHash();
        // don't verify the hash, just make sure it is not null or empty and is the correct length
        if (fileHash == null || Bytes.EMPTY.equals(fileHash) || fileHash.length() != UPDATE_FILE_HASH_LEN) {
            throw new PreCheckException(ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
        }
    }
}
