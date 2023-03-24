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
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ONLY;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.PREPARE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.TELEMETRY_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.UNKNOWN_FREEZE_TYPE;
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
     * @see <a href="https://hashgraph.github.io/hedera-protobufs/#freeze.proto">Protobuf freeze documentation</a>
     */
    @SuppressWarnings("java:S1874") // disable the warnings for use of deprecated code
        // it is necessary to check getStartHour, getStartMin, getEndHour, getEndMin, all of which are deprecated
        // because if they are present then we set a status of INVALID_FREEZE_TRANSACTION_BODY
    public void preHandle(@NonNull final PreHandleContext context, @NonNull final ReadableSpecialFileStore specialFileStore) {
        requireNonNull(context);

        FreezeTransactionBody freezeTxn = context.getTxn().freezeOrThrow();
        final FreezeType freezeType = freezeTxn.freezeType();

        try {
            // default value for freezeType is UNKNOWN_FREEZE_TYPE
            // reject any freeze transactions that do not set freezeType or set it to UNKNOWN_FREEZE_TYPE
            if (freezeType == UNKNOWN_FREEZE_TYPE) {
                throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);
            }

            // freeze.proto properties startHour, startMin, endHour, endMin are deprecated
            // reject any freeze transactions that set these properties
            if (freezeTxn.startHour() != 0 || freezeTxn.startMin() != 0 || freezeTxn.endHour() != 0
                    || freezeTxn.endMin() != 0) {
                throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);
            }

            // for these freeze types, freezeStartTime is required and it must be in the future
            if (freezeType == FREEZE_ONLY || freezeType == FREEZE_UPGRADE || freezeType == TELEMETRY_UPGRADE) {
                final Timestamp freezeStartTime = freezeTxn.startTime();
                if (!freezeTxn.hasStartTime() || Timestamp.DEFAULT.equals(freezeStartTime)) {
                    throw new PreCheckException(INVALID_FREEZE_TRANSACTION_BODY);
                }
                final Instant freezeStartTimeInstant = Instant.ofEpochSecond(freezeStartTime.seconds(),
                        freezeStartTime.nanos());

                // get current consensus time
                final Timestamp ts = context.getTxn().transactionID().transactionValidStart();
                final Instant effectiveNowInstant = Instant.ofEpochSecond(ts.seconds(), ts.nanos());

                // make sure freezeStartTime is after current consensus time
                final boolean freezeStartTimeIsInFuture = freezeStartTimeInstant.isAfter(effectiveNowInstant);
                if (!freezeStartTimeIsInFuture) {
                    throw new PreCheckException(FREEZE_START_TIME_MUST_BE_FUTURE);
                }
            }

            // for these freeze types, updateFile and fileHash are required
            if (freezeType == PREPARE_UPGRADE || freezeType == FREEZE_UPGRADE || freezeType == TELEMETRY_UPGRADE) {
                final FileID updateFile = freezeTxn.updateFile();
                // from proto specs, it looks like update file not required for FREEZE_UPGRADE and TELEMETRY_UPGRADE
                // but specs aren't very clear
                // current code in FreezeTransitionLogic checks for the file in specialFiles
                // so we will do the same here

                if (!freezeTxn.hasUpdateFile() || updateFile == null || specialFileStore.get(updateFile.fileNum())
                        .isEmpty()) {
                    throw new PreCheckException(FREEZE_UPDATE_FILE_DOES_NOT_EXIST);
                }

                final Bytes fileHash = freezeTxn.fileHash();
                if (fileHash == null || Bytes.EMPTY.equals(fileHash) || fileHash.length() != UPDATE_FILE_HASH_LEN) {
                    throw new PreCheckException(FREEZE_UPDATE_FILE_HASH_DOES_NOT_MATCH);
                }

                // FREEZE_ABORT is the last freeze type. It requires no additional checks.
                // FREEZE_ABORT can be run multiple times to allow DevOps to submit
                // multiple such transactions if disconnected nodes missed the previous

                // no need to add any keys to the context because this transaction does not require any signatures
                // it must be submitted by an account with superuser privileges, that is checked during ingest

                // all checks have passed
                context.status(OK);
            }
        } catch (PreCheckException e) {
            // instead of catching this exception, would like to allow it to propagate up
            // this will be implemented in issue #5880
            context.status(e.responseCode());
        }
    }
}
