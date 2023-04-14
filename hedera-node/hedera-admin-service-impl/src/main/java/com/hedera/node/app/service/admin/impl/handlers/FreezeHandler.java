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

package com.hedera.node.app.service.admin.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_UPDATE_FILE_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.freeze.FreezeType;
import com.hedera.node.app.service.admin.impl.ReadableSpecialFileStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
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
     * @see <a href="https://hashgraph.github.io/hedera-protobufs/#freeze.proto">Protobuf freeze
     *     documentation</a>
     */
    @SuppressWarnings("java:S1874") // disable the warnings for use of deprecated code
    // it is necessary to check getStartHour, getStartMin, getEndHour, getEndMin, all of which are
    // deprecated
    // because if any are present then we set a status of INVALID_FREEZE_TRANSACTION_BODY
    public void preHandle(
            @NonNull final PreHandleContext context, @NonNull final ReadableSpecialFileStore specialFileStore)
            throws PreCheckException {
        requireNonNull(context);

        FreezeTransactionBody freezeTxn = context.body().freeze();

        // freeze.proto properties startHour, startMin, endHour, endMin are deprecated in the
        // protobuf
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
                // reject any freeze transactions that do not set freezeType or set it to
                // UNKNOWN_FREEZE_TYPE
            case UNKNOWN_FREEZE_TYPE -> throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);

                // FREEZE_ONLY requires a valid start_time
            case FREEZE_ONLY -> verifyFreezeStartTimeIsInFuture(
                    freezeTxn, context.body().transactionID().transactionValidStart());

                // PREPARE_UPGRADE requires valid update_file and file_hash values
            case PREPARE_UPGRADE -> verifyUpdateFileAndHash(freezeTxn, specialFileStore);

                // FREEZE_UPGRADE and TELEMETRY_UPGRADE require a valid start_time and valid
                // update_file and
                // file_hash values
            case FREEZE_UPGRADE, TELEMETRY_UPGRADE -> {
                verifyFreezeStartTimeIsInFuture(
                        freezeTxn, context.body().transactionID().transactionValidStart());

                // from proto specs, it looks like update file not required for FREEZE_UPGRADE and
                // TELEMETRY_UPGRADE
                // but specs aren't very clear
                // current code in FreezeTransitionLogic checks for the file in specialFiles
                // so we will do the same
                verifyUpdateFileAndHash(freezeTxn, specialFileStore);
            }

                // FREEZE_ABORT does not require any additional checks
            case FREEZE_ABORT -> {
                // do nothing
            }
        }

        // no need to add any keys to the context because this transaction does not require any
        // signatures
        // it must be submitted by an account with superuser privileges, that is checked during
        // ingest
    }

    /**
     * For freeze types FREEZE_ONLY, FREEZE_UPGRADE, and TELEMETRY_UPGRADE, the startTime field must be set to
     * a time in the future, where future is defined as a time after the current consensus time.
     * @throws PreCheckException if startTime is not in the future
     */
    private void verifyFreezeStartTimeIsInFuture(FreezeTransactionBody freezeTxn, Timestamp curConsensusTime)
            throws PreCheckException {
        final Timestamp freezeStartTime = freezeTxn.startTime();
        if (freezeStartTime == null || freezeStartTime.seconds() == 0 && freezeStartTime.nanos() == 0) {
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
     * For freeze types PREPARE_UPGRADE, FREEZE_UPGRADE, and TELEMETRY_UPGRADE, the updateFile and fileHash fields must be set.
     * @throws PreCheckException if updateFile or fileHash are not set or don't pass sanity checks
     */
    private void verifyUpdateFileAndHash(FreezeTransactionBody freezeTxn, ReadableSpecialFileStore specialFileStore)
            throws PreCheckException {
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
