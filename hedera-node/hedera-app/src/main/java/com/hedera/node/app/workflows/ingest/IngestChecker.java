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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.solvency.SolvencyPreCheck;
import com.hedera.node.app.spi.info.CurrentPlatformStatus;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * The {@code IngestChecker} contains checks that are specific to the ingest workflow
 */
public class IngestChecker {

    private final AccountID nodeAccountID;
    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final TransactionChecker transactionChecker;
    private final ThrottleAccumulator throttleAccumulator;
    private final SolvencyPreCheck solvencyPreCheck;
    private final SignaturePreparer signaturePreparer;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param nodeAccountID the {@link AccountID} of the <em>node</em>
     * @param nodeInfo the {@link NodeInfo} that contains information about the node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus} that contains the current status of the platform
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the bytes of a transaction
     * @param throttleAccumulator the {@link ThrottleAccumulator} for throttling
     * @param solvencyPreCheck the {@link SolvencyPreCheck} that checks payer balance
     * @param signaturePreparer the {@link SignaturePreparer} that prepares signature data
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestChecker(
            @NonNull @NodeSelfId final AccountID nodeAccountID,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final ThrottleAccumulator throttleAccumulator,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final SignaturePreparer signaturePreparer) {
        this.nodeAccountID = requireNonNull(nodeAccountID);
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.solvencyPreCheck = solvencyPreCheck;
        this.signaturePreparer = requireNonNull(signaturePreparer);
    }

    /**
     * Checks the general state of the node
     *
     * @throws PreCheckException if the node is unable to process queries
     */
    public void checkNodeState() throws PreCheckException {
        if (nodeInfo.isSelfZeroStake()) {
            // Zero stake nodes are currently not supported
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }
        if (currentPlatformStatus.get() != ACTIVE) {
            throw new PreCheckException(PLATFORM_NOT_ACTIVE);
        }
    }

    /**
     * Runs all the ingest checks on a {@link Transaction}
     *
     * @param tx the {@link Transaction} to check
     * @return the {@link TransactionInfo} with the extracted information
     * @throws PreCheckException if a check fails
     */
    public TransactionInfo runAllChecks(@NonNull final HederaState state, @NonNull final Transaction tx) throws PreCheckException {
        // 1. Check the syntax
        final var transactionInfo = transactionChecker.check(tx);
        final var txBody = transactionInfo.txBody();
        final var functionality = transactionInfo.functionality();

        // This should never happen, because HapiUtils#checkFunctionality() will throw
        // UnknownHederaFunctionality if it cannot map to a proper value, and WorkflowOnset
        // will convert that to INVALID_TRANSACTION_BODY.
        assert functionality != HederaFunctionality.NONE;

        // 2. Check throttles
        if (throttleAccumulator.shouldThrottle(transactionInfo.txBody())) {
            throw new PreCheckException(ResponseCodeEnum.BUSY);
        }

        // 3. Check payer's signature
        signaturePreparer.syncGetPayerSigStatus(tx);

        // 4. Check account balance
        final AccountID payerID =
                txBody.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT);
        solvencyPreCheck.checkPayerAccountStatus(state, payerID);
        solvencyPreCheck.checkSolvencyOfVerifiedPayer(state, tx);

        return transactionInfo;
    }
}
