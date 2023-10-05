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

import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.swirlds.common.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.fees.FeeContextImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.info.CurrentPlatformStatus;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The {@code IngestChecker} contains checks that are specific to the ingest workflow
 */
public final class IngestChecker {
    private static final Logger logger = LogManager.getLogger(IngestChecker.class);

    private final CurrentPlatformStatus currentPlatformStatus;
    private final TransactionChecker transactionChecker;
    private final SolvencyPreCheck solvencyPreCheck;
    private final SignatureVerifier signatureVerifier;
    private final SignatureExpander signatureExpander;
    private final DeduplicationCache deduplicationCache;
    private final TransactionDispatcher dispatcher;
    private final FeeManager feeManager;
    private final AccountID nodeAccount;
    private final Authorizer authorizer;
    private final SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param nodeAccount the {@link AccountID} of the node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus} that contains the current status of the platform
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the bytes of a transaction
     * @param solvencyPreCheck the {@link SolvencyPreCheck} that checks payer balance
     * @param signatureExpander the {@link SignatureExpander} that expands signatures
     * @param signatureVerifier the {@link SignatureVerifier} that verifies signature data
     * @param dispatcher the {@link TransactionDispatcher} that dispatches transactions
     * @param feeManager the {@link FeeManager} that manages {@link com.hedera.node.app.spi.fees.FeeCalculator}s
     * @param synchronizedThrottleAccumulator the {@link SynchronizedThrottleAccumulator} that checks transaction should be throttled
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestChecker(
            @NodeSelfId @NonNull final AccountID nodeAccount,
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final DeduplicationCache deduplicationCache,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final FeeManager feeManager,
            @NonNull final Authorizer authorizer,
            @NonNull final SynchronizedThrottleAccumulator synchronizedThrottleAccumulator) {
        this.nodeAccount = requireNonNull(nodeAccount, "nodeAccount must not be null");
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus, "currentPlatformStatus must not be null");
        this.transactionChecker = requireNonNull(transactionChecker, "transactionChecker must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
        this.signatureVerifier = requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.signatureExpander = requireNonNull(signatureExpander, "signatureExpander must not be null");
        this.deduplicationCache = requireNonNull(deduplicationCache, "deduplicationCache must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.authorizer = requireNonNull(authorizer, "authorizer must not be null");
        this.synchronizedThrottleAccumulator = requireNonNull(synchronizedThrottleAccumulator);
    }

    /**
     * Checks the general state of the node
     *
     * @throws PreCheckException if the node is unable to process queries
     */
    public void checkNodeState() throws PreCheckException {
        if (currentPlatformStatus.get() != ACTIVE) {
            throw new PreCheckException(PLATFORM_NOT_ACTIVE);
        }
    }

    /**
     * Runs all the ingest checks on a {@link Transaction}
     *
     * @param state the {@link HederaState} to use
     * @param tx the {@link Transaction} to check
     * @param configuration the {@link Configuration} to use
     * @return the {@link TransactionInfo} with the extracted information
     * @throws PreCheckException if a check fails
     */
    public TransactionInfo runAllChecks(
            @NonNull final HederaState state, @NonNull final Transaction tx, @NonNull final Configuration configuration)
            throws PreCheckException {
        // During ingest we approximate consensus time with wall clock time
        final var consensusTime = Instant.now();

        // 1. Check the syntax
        final var txInfo = transactionChecker.check(tx);
        final var txBody = txInfo.txBody();
        final var functionality = txInfo.functionality();

        // 1a. Verify the transaction has been sent to *this* node
        if (!nodeAccount.equals(txBody.nodeAccountID())) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }

        // 2. Check the time box of the transaction
        transactionChecker.checkTimeBox(txBody, consensusTime);

        // This should never happen, because HapiUtils#checkFunctionality() will throw
        // UnknownHederaFunctionality if it cannot map to a proper value, and WorkflowOnset
        // will convert that to INVALID_TRANSACTION_BODY.
        assert functionality != HederaFunctionality.NONE;

        // 3. Deduplicate
        if (deduplicationCache.contains(txInfo.transactionID())) {
            throw new PreCheckException(DUPLICATE_TRANSACTION);
        }

        // 4. Check throttles
        if (synchronizedThrottleAccumulator.shouldThrottle(txInfo, state)) {
            throw new PreCheckException(BUSY);
        }

        // 5. Get payer account
        final var storeFactory = new ReadableStoreFactory(state);
        final var payer = solvencyPreCheck.getPayerAccount(storeFactory, txInfo.payerID());
        final var payerKey = payer.key();
        // There should, absolutely, be a key for this account. If there isn't, then something is wrong in
        // state. So we will log this with a warning. We will also have to do something about the fact that
        // the key is missing -- so we will fail with unauthorized.
        if (payerKey == null) {
            // FUTURE: Have an alert and metric in our monitoring tools to make sure we are aware if this happens
            logger.warn("Payer account {} has no key, indicating a problem with state", txInfo.payerID());
            throw new PreCheckException(UNAUTHORIZED);
        }

        // 6. Verify payer's signatures
        verifyPayerSignature(txInfo, payerKey);

        // 7. Check payer solvency
        final FeeContext feeContext = new FeeContextImpl(
                consensusTime, txInfo, payerKey, feeManager, storeFactory, configuration, authorizer);
        final var fees = dispatcher.dispatchComputeFees(feeContext);
        solvencyPreCheck.checkSolvency(txInfo, payer, fees);

        return txInfo;
    }

    private void verifyPayerSignature(@NonNull final TransactionInfo txInfo, @NonNull final Key payerKey)
            throws PreCheckException {
        // Expand the signatures
        final var expandedSigs = new HashSet<ExpandedSignaturePair>();
        signatureExpander.expand(payerKey, txInfo.signatureMap().sigPairOrThrow(), expandedSigs);

        // Verify the signatures
        final var results = signatureVerifier.verify(txInfo.signedBytes(), expandedSigs);
        final var future = results.get(payerKey);

        // This can happen if the signature map was missing a signature for the payer account.
        if (future == null) {
            throw new PreCheckException(INVALID_SIGNATURE);
        }

        // Wait for the verification to complete. We have a timeout here of 1 second, which is WAY more time
        // than it should take (maybe three orders of magnitude more time). Even if this happens spuriously
        // (like, for example, if there was a really long GC pause), the worst case is the client will get an
        // internal error and retry. We just want to log it.
        try {
            final var verificationResult = future.get(1, TimeUnit.SECONDS);
            if (!verificationResult.passed()) {
                throw new PreCheckException(INVALID_SIGNATURE);
            }
        } catch (TimeoutException e) {
            // FUTURE: Have an alert and metric in our monitoring tools to make sure we are aware if this happens
            logger.warn("Signature verification timed out during ingest");
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            // FUTURE: Have an alert and metric in our monitoring tools to make sure we are aware if this happens
            logger.warn("Signature verification failed during ingest", e);
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            // This might not be a warn / error situation, if we were interrupted, it means that someone
            // is trying to shut down the server. So we can just throw and get out of here.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
