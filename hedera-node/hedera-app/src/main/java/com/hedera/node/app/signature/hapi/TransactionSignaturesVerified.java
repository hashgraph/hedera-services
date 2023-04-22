/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.signature.hapi;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.signatures.SignatureVerification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Contains the result of a List of signature verifications. This class aggregates each of those individual
 * verification tasks and returns a single boolean indicating whether the verifications succeeded, or failed.
 */
public class TransactionSignaturesVerified implements Future<SignatureVerificationResults> {
    /** The list of {@link Future}s for each individual verification */
    private final Future<SignatureVerification> payerFuture;

    private final List<Future<SignatureVerification>> nonPayerFutures;
    private final List<Future<SignatureVerification>> hollowFutures;
    private final List<Future<SignatureVerification>> allFutures;

    private boolean canceled = false;

    /** Create a new instance */
    public TransactionSignaturesVerified(
            @NonNull final Future<SignatureVerification> payerFuture,
            @NonNull final List<Future<SignatureVerification>> nonPayerFutures,
            @NonNull final List<Future<SignatureVerification>> hollowFutures) {
        this.payerFuture = requireNonNull(payerFuture);
        this.nonPayerFutures = requireNonNull(nonPayerFutures);
        this.hollowFutures = requireNonNull(hollowFutures);
        this.allFutures = new ArrayList<>();
        allFutures.add(payerFuture);
        allFutures.addAll(nonPayerFutures);
        allFutures.addAll(hollowFutures);
    }

    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        // If we're already done, then we can't cancel (including if we canceled previously)
        if (isDone()) {
            return false;
        }

        // Try to cancel each underlying future (I go ahead and try canceling all of them, even if one fails)
        boolean result = true;
        for (final var future : allFutures) {
            final var couldBeCanceled = future.cancel(mayInterruptIfRunning);
            if (!couldBeCanceled) {
                result = false;
            }
        }

        // Record that we have had "canceled" called already, so we don't do it again, and so that "done" is right.
        canceled = true;

        // We only return true if we got "true" from each sub-future
        return result;
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public boolean isDone() {
        if (canceled || allFutures.isEmpty()) {
            return true;
        }

        for (final var future : allFutures) {
            if (future.isDone()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public SignatureVerificationResults get() throws InterruptedException, ExecutionException {
        var passed = true;
        for (final var future : allFutures) {
            if (passed) {
                passed = future.get().passed();
            } else {
                future.cancel(true);
            }
        }

        final var listNonPayerFutures = new ArrayList<SignatureVerification>();
        for (final var future : nonPayerFutures) {
            listNonPayerFutures.add(future.get());
        }

        final var listHollowFutures = new ArrayList<SignatureVerification>();
        for (final var future : hollowFutures) {
            listHollowFutures.add(future.get());
        }

        return new SignatureVerificationResults(payerFuture.get(), listNonPayerFutures, listHollowFutures, passed);
    }

    // Should to millisRemaining or nanos remaining like completable future?
    // Should we rely on the underlying futures to throw the timeout if "millisRemaining" is negative?
    @Override
    public SignatureVerificationResults get(final long timeout, @NonNull final TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        var millisRemaining = unit.toMillis(timeout);
        var passed = true;
        for (final var future : allFutures) {
            if (passed) {
                final var now = System.currentTimeMillis();
                passed = future.get(millisRemaining, TimeUnit.MILLISECONDS).passed();
                millisRemaining -= System.currentTimeMillis() - now;
            } else {
                future.cancel(true);
            }
        }

        final var listNonPayerFutures = new ArrayList<SignatureVerification>();
        for (final var future : nonPayerFutures) {
            listNonPayerFutures.add(future.get());
        }

        final var listHollowFutures = new ArrayList<SignatureVerification>();
        for (final var future : hollowFutures) {
            listHollowFutures.add(future.get());
        }

        return new SignatureVerificationResults(payerFuture.get(), listNonPayerFutures, listHollowFutures, passed);
    }
}
