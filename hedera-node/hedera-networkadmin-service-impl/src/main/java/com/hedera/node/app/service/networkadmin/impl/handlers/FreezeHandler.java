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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.PREPARE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.TELEMETRY_UPGRADE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.networkadmin.ReadableSpecialFileStore;
import com.hedera.node.app.service.networkadmin.impl.config.NetworkAdminServiceConfig;
import com.hedera.node.app.spi.state.WritableFreezeStore;
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
        final FreezeTransactionBody freezeTxn = context.body().freeze();
        final Timestamp txValidStart = context.body().transactionIDOrThrow().transactionValidStartOrThrow();

        pureChecks(freezeTxn, txValidStart);

        final FreezeType freezeType = freezeTxn.freezeType();

        // from proto specs, it looks like update file not required for FREEZE_UPGRADE and TELEMETRY_UPGRADE
        // but specs aren't very clear
        // current code in FreezeTransitionLogic checks for the file in specialFiles
        // so we will do the same
        if (Arrays.asList(FREEZE_UPGRADE, TELEMETRY_UPGRADE, PREPARE_UPGRADE).contains(freezeType)) {
            final ReadableSpecialFileStore specialFileStore = context.createStore(ReadableSpecialFileStore.class);
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
    private static void pureChecks(final FreezeTransactionBody freezeTxn, @NonNull final Timestamp txValidStart)
            throws PreCheckException {
        // freeze.proto properties startHour, startMin, endHour, endMin are deprecated in the protobuf
        // reject any freeze transactions that set these properties
        if (freezeTxn == null
                || freezeTxn.startHour() != 0
                || freezeTxn.startMin() != 0
                || freezeTxn.endHour() != 0
                || freezeTxn.endMin() != 0) {
            throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);
        }

        final FreezeType freezeType = freezeTxn.freezeType();
        switch (freezeType) {
                // default value for freezeType is UNKNOWN_FREEZE_TYPE
                // reject any freeze transactions that do not set freezeType or set it to UNKNOWN_FREEZE_TYPE
            case UNKNOWN_FREEZE_TYPE -> throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);

                // FREEZE_ONLY requires a valid start_time
                // FREEZE_UPGRADE and TELEMETRY_UPGRADE require a valid start_time and valid update_file and
                // file_hash values
            case FREEZE_ONLY, FREEZE_UPGRADE, TELEMETRY_UPGRADE -> verifyFreezeStartTimeIsInFuture(
                    freezeTxn, txValidStart);

                // PREPARE_UPGRADE, FREEZE_ABORT do not require any additional checks
            default -> {
                // do nothing
            }
        }
    }

    public void handle(
            @NonNull final TransactionBody txn,
            @NonNull final NetworkAdminServiceConfig adminServiceConfig,
            @NonNull final ReadableSpecialFileStore specialFileStore,
            @NonNull final WritableFreezeStore freezeStore) {
        requireNonNull(txn);
        final FreezeTransactionBody freezeTxn = txn.freezeOrThrow();
        final FileID updateFileNum =
                freezeTxn.updateFile(); // only some freeze types require this, it may be null for others

        validateSemantics(freezeTxn, specialFileStore, updateFileNum);

        final FreezeUpgradeActions upgradeActions = new FreezeUpgradeActions(adminServiceConfig, freezeStore);
        final Timestamp freezeStartTime = freezeTxn.startTime(); // may be null for some freeze types
        final Instant freezeStartTimeInstant = freezeStartTime == null
                ? null
                : Instant.ofEpochSecond(freezeStartTime.seconds(), freezeStartTime.nanos());

        switch (freezeTxn.freezeType()) {
            case PREPARE_UPGRADE ->
            // by the time we get here, we've already checked that updateFileNum is non-null in validateSemantics()
            upgradeActions.extractSoftwareUpgrade(specialFileStore
                    .get(requireNonNull(updateFileNum).fileNum())
                    .orElseThrow(() -> new IllegalStateException("Update file not found")));
                // TODO: call networkCtx.recordPreparedUpgrade(freezeTxn); (issue #6201)
            case FREEZE_UPGRADE -> upgradeActions.scheduleFreezeUpgradeAt(requireNonNull(freezeStartTimeInstant));
            case FREEZE_ABORT -> upgradeActions.abortScheduledFreeze();
                // TODO: call networkCtx.discardPreparedUpgradeMeta(); (issue #6201)
            case TELEMETRY_UPGRADE -> upgradeActions.extractTelemetryUpgrade(
                    specialFileStore
                            .get(requireNonNull(updateFileNum).fileNum())
                            .orElseThrow(() -> new IllegalStateException("Telemetry update file not found")),
                    requireNonNull(freezeStartTimeInstant));
                // case FREEZE_ONLY is default
            default -> upgradeActions.scheduleFreezeOnlyAt(requireNonNull(freezeStartTimeInstant));
        }
    }

    /**
     * Performs checks that the entities related to this transaction exist and are valid
     */
    //    @NonNull
    private static void validateSemantics(
            @NonNull final FreezeTransactionBody freezeTxn,
            @NonNull final ReadableSpecialFileStore specialFileStore,
            @Nullable final FileID updateFileNum) {
        requireNonNull(freezeTxn);
        requireNonNull(specialFileStore);

        if (freezeTxn.freezeType() == PREPARE_UPGRADE || freezeTxn.freezeType() == TELEMETRY_UPGRADE) {
            requireNonNull(updateFileNum);
            final Optional<byte[]> updateFileZip = specialFileStore.get(updateFileNum.fileNum());
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
    private static void verifyFreezeStartTimeIsInFuture(FreezeTransactionBody freezeTxn, Timestamp curConsensusTime)
            throws PreCheckException {
        final Timestamp freezeStartTime = freezeTxn.startTime();
        if (freezeStartTime == null || (freezeStartTime.seconds() == 0 && freezeStartTime.nanos() == 0)) {
            throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);
        }
        final Instant freezeStartTimeInstant =
                Instant.ofEpochSecond(freezeStartTime.seconds(), freezeStartTime.nanos());

        final Instant effectiveNowInstant = Instant.ofEpochSecond(curConsensusTime.seconds(), curConsensusTime.nanos());

        // make sure freezeStartTime is after current consensus time
        final boolean freezeStartTimeIsInFuture = freezeStartTimeInstant.isAfter(effectiveNowInstant);
        if (!freezeStartTimeIsInFuture) {
            throw new PreCheckException(FREEZE_START_TIME_MUST_BE_FUTURE);
        }
    }

    /**
     * For freeze types PREPARE_UPGRADE, FREEZE_UPGRADE, and TELEMETRY_UPGRADE,
     * the updateFile and fileHash fields must be set.
     * @throws PreCheckException if updateFile or fileHash are not set or don't pass sanity checks
     */
    private static void verifyUpdateFileAndHash(
            FreezeTransactionBody freezeTxn, ReadableSpecialFileStore specialFileStore) throws PreCheckException {
        final FileID updateFile = freezeTxn.updateFile();

        if (updateFile == null || specialFileStore.get(updateFile.fileNum()).isEmpty()) {
            throw new PreCheckException(FREEZE_UPDATE_FILE_DOES_NOT_EXIST);
        }

        final Bytes fileHash = freezeTxn.fileHash();
        // don't verify the hash, just make sure it is not null or empty and is the correct length
        if (fileHash == null || Bytes.EMPTY.equals(fileHash) || fileHash.length() != UPDATE_FILE_HASH_LEN) {
            throw new PreCheckException(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
        }
    }
}
