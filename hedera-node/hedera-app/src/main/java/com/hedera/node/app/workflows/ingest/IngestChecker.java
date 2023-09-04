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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.swirlds.common.system.status.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.node.app.info.CurrentPlatformStatus;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.solvency.SolvencyPreCheck;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.DeduplicationCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
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
    private final ThrottleAccumulator throttleAccumulator;
    private final SolvencyPreCheck solvencyPreCheck;
    private final SignatureVerifier signatureVerifier;
    private final SignatureExpander signatureExpander;
    private final DeduplicationCache deduplicationCache;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param currentPlatformStatus the {@link CurrentPlatformStatus} that contains the current status of the platform
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the bytes of a transaction
     * @param throttleAccumulator the {@link ThrottleAccumulator} for throttling
     * @param solvencyPreCheck the {@link SolvencyPreCheck} that checks payer balance
     * @param signatureExpander the {@link SignatureExpander} that expands signatures
     * @param signatureVerifier the {@link SignatureVerifier} that verifies signature data
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestChecker(
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final ThrottleAccumulator throttleAccumulator,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final DeduplicationCache deduplicationCache) {
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.solvencyPreCheck = solvencyPreCheck;
        this.signatureVerifier = requireNonNull(signatureVerifier);
        this.signatureExpander = requireNonNull(signatureExpander);
        this.deduplicationCache = requireNonNull(deduplicationCache);
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
     * @param tx the {@link Transaction} to check
     * @return the {@link TransactionInfo} with the extracted information
     * @throws PreCheckException if a check fails
     */
    public TransactionInfo runAllChecks(@NonNull final HederaState state, @NonNull final Transaction tx)
            throws PreCheckException {
        // 1. Check the syntax
        final var txInfo = transactionChecker.check(tx);
        final var txBody = txInfo.txBody();
        final var functionality = txInfo.functionality();

        // 2. Check the time box of the transaction using wall clock time as an approximation
        transactionChecker.checkTimeBox(txBody, Instant.now());

        // This should never happen, because HapiUtils#checkFunctionality() will throw
        // UnknownHederaFunctionality if it cannot map to a proper value, and WorkflowOnset
        // will convert that to INVALID_TRANSACTION_BODY.
        assert functionality != HederaFunctionality.NONE;

        // 3. Deduplicate
        if (deduplicationCache.contains(txBody.transactionIDOrThrow())) {
            throw new PreCheckException(DUPLICATE_TRANSACTION);
        }

        // 4. Check throttles
        if (throttleAccumulator.shouldThrottle(txInfo.txBody())) {
            throw new PreCheckException(BUSY);
        }

        // 5. Get payer account
        final AccountID payerId =
                txBody.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT);

        solvencyPreCheck.checkPayerAccountStatus(state, payerId);

        // 6. Check account balance
        solvencyPreCheck.checkSolvencyOfVerifiedPayer(state, tx);

        // 7. Verify payer's signatures
        verifyPayerSignature(state, txInfo, payerId);

        return txInfo;
    }

    private void verifyPayerSignature(
            @NonNull final HederaState state, @NonNull final TransactionInfo txInfo, @NonNull final AccountID payerId)
            throws PreCheckException {

        // Get the payer account
        final var stores = new ReadableStoreFactory(state);
        final var payerAccount = stores.getStore(ReadableAccountStore.class).getAccountById(payerId);

        // If there is no payer account for this ID, then the transaction is invalid
        if (payerAccount == null) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }

        // There should, absolutely, be a key for this account. If there isn't, then something is wrong in
        // state. So we will log this with a warning. We will also have to do something about the fact that
        // the key is missing -- so we will fail with unauthorized.
        if (!payerAccount.hasKey()) {
            // FUTURE: Have an alert and metric in our monitoring tools to make sure we are aware if this happens
            logger.warn("Payer account {} has no key, indicating a problem with state", payerId);
            throw new PreCheckException(UNAUTHORIZED);
        }

        // Expand the signatures
        final var expandedSigs = new HashSet<ExpandedSignaturePair>();
        signatureExpander.expand(
                payerAccount.keyOrThrow(), txInfo.signatureMap().sigPairOrThrow(), expandedSigs);

        // Verify the signatures
        final var results = signatureVerifier.verify(txInfo.signedBytes(), expandedSigs);
        final var future = results.get(payerAccount.keyOrThrow());

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
