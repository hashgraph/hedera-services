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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link Future} that waits on a {@link List} of {@link TransactionSignature}s to complete signature checks, and
 * yields a {@link SignatureVerification}.
 */
public class SignatureVerificationFuture implements Future<SignatureVerification> {
    /**
     * The Key we verified. This will *never* be null, because we would not have attempted signature verification
     * without having a key. If an EVM address was used, we would have already extracted the key, so it can be
     * provided here.
     */
    private final Key key;
    /**
     * Optional: used only with hollow accounts, the EVM address associated with the hollow account. The corresponding
     * {@link #key} was extracted from the transaction (either because it was a "full" prefix on the signature map,
     * or because we used an "ecrecover" like process to extract the key from the signature and signed bytes).
     */
    private final Bytes evmAddress;
    /**
     * The map of {@link TransactionSignature}s. This map cannot be empty. Each {@link TransactionSignature} contains
     * a {@link Future} indicating when then status code (verified, or not) is available on the transaction signature.
     */
    private final Map<Key, TransactionSignature> sigs;
    /**
     * Whether *this* future has been canceled. Used for properly implementing {@link Future} semantics.
     */
    private boolean canceled = false;

    /**
     * Create a new instance.
     *
     * @param key The key associated with this sig check. Cannot be null.
     * @param evmAddress When used with hollow accounts ONLY, the EVM address associated with that hollow account.
     * @param sigs The {@link TransactionSignature}s, from which the pass/fail status of the
     * {@link SignatureVerification} is derived. This list must contain at least one element.
     */
    public SignatureVerificationFuture(
            @NonNull Key key, @Nullable Bytes evmAddress, @NonNull Map<Key, TransactionSignature> sigs) {
        this.key = requireNonNull(key);
        this.evmAddress = evmAddress;
        this.sigs = requireNonNull(sigs);

        if (sigs.isEmpty()) {
            throw new IllegalArgumentException("The TransactionSignature list cannot be empty");
        }
    }

    /**
     * {@inheritDoc}
     *
     * Attempts to cancel all {@link TransactionSignature} futures. Due to the asynchronous nature of those objects,
     * it is possible that the future has not yet even been created, and we do not currently have any way to prevent
     * it from creating a future. The best we can do is to wait for the future to be created and then cancel it.
     * However, waiting for the future is a blocking operation, so this call may block for some time, which is not
     * very nice.
     *
     * <p>Alternatively, we can just do a "best effort" cancel, which is what we will do. If the future exists, we will
     * cancel it. If it does not exist, we will let it be. Canceling of futures is, on the whole, a best effort
     * operation anyway.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // If we're already done, then we can't cancel again
        if (isDone()) {
            return false;
        }

        // Try to cancel each underlying future (I go ahead and try canceling all of them, even if one fails).
        // If the underlying Future doesn't exist, then we just skip it. Best effort.
        boolean result = true;
        for (final var txSig : sigs.values()) {
            final var future = txSig.getFuture();
            if (future != null) {
                final var couldBeCanceled = future.cancel(mayInterruptIfRunning);
                if (!couldBeCanceled) {
                    result = false;
                }
            }
        }

        // Record that we have had "canceled" called already, so we don't do it again, and so that "done" is right.
        canceled = true;

        // We only return true if we got "true" from each sub-future
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCancelled() {
        return canceled;
    }

    /**
     * {@inheritDoc}
     *
     * Due to the asynchronous nature of {@link TransactionSignature}, it is possible that at the time this method
     * is called, one or more {@link TransactionSignature}s do not yet have a {@link Future}. If that is the case,
     * then we are clearly not done!
     */
    @Override
    public boolean isDone() {
        if (canceled) {
            return true;
        }

        for (final var txSig : sigs.values()) {
            final var future = txSig.getFuture();
            if (future == null || !future.isDone()) {
                return false;
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     *
     * Blocks on each {@link Future}. Due to the asynchronous nature of {@link TransactionSignature}, it is possible
     * that a {@link Future} has not been assigned to one or more {@link TransactionSignature}s. In that case, we will
     * wait for the {@link Future} before proceeding.
     */
    @Override
    public SignatureVerification get() throws InterruptedException, ExecutionException {
        for (final var txSig : sigs.values()) {
            // Unfortunately, the implementation of this is a busy loop. This could be improved, and if it were,
            // we could not burn CPU waiting. In practice that should never actually happen, because the future
            // should have been created and the result finalized long before this "get" method is called.
            txSig.waitForFuture();
            txSig.getFuture().get();
        }

        return new SignatureVerificationImpl(key, evmAddress, checkIfPassed(key, sigs));
    }

    /**
     * {@inheritDoc}
     *
     * Blocks on each {@link Future}. Due to the asynchronous nature of {@link TransactionSignature}, it is possible
     * that a {@link Future} has not been assigned to one or more {@link TransactionSignature}s. In that case, we will
     * wait for the {@link Future} before proceeding (up to the timeout).
     */
    @Override
    public SignatureVerification get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        var millisRemaining = unit.toMillis(timeout);
        for (final var txSig : sigs.values()) {
            // Figure out how many milliseconds we still have remaining before timing out
            final var now = System.currentTimeMillis();
            millisRemaining -= System.currentTimeMillis() - now;
            if (millisRemaining <= 0) {
                // If there was no time left, then TimeoutException
                throw new TimeoutException("Timed out waiting for signature verification to complete");
            } else if (txSig.getFuture() == null) {
                // If there was time left, but we didn't yet have a Future on the TransactionSignature, then
                // rather than blocking in a tight loop (like we do for "get"), we have to just wait a bit and
                // check for timeout and try again. That way, we can still time out.
                Thread.sleep(1);
            } else {
                // We now the maximum number of millis we can wait, so we block for at most that long. If it takes
                // longer, the future we delegate to here will throw. If it takes less, we recompute how much is
                // remaining on the next iteration.
                txSig.getFuture().get(millisRemaining, TimeUnit.MILLISECONDS);
            }
        }

        return new SignatureVerificationImpl(key, evmAddress, checkIfPassed(key, sigs));
    }

    /**
     * Determines whether the signature verification as a whole was successful. Called only <strong>AFTER</strong> we
     * have waited for all {@link TransactionSignature} {@link Future}s, so we know we have either
     * {@link VerificationStatus#VALID} or {@link VerificationStatus#INVALID} as a final result for each key.
     *
     * <p>Each {@link TransactionSignature} represents the signature check for a SINGLE concrete key type. There is
     * no {@link TransactionSignature} created for a {@link KeyList} or {@link ThresholdKey}, or for "internal" keys
     * like for contract IDs.
     *
     * <p>It is possible that in a complex multi-sig threshold-key scenario that some {@link TransactionSignature}s
     * may be {@link VerificationStatus#INVALID}, and yet the overall result is still valid. More specifically:
     *
     * <ol>
     *     <li>If the key is a {@link KeyList}, every single key within it must be valid</li>
     *     <li>If the key is a {@link ThresholdKey}, then only {@link ThresholdKey#threshold()} keys must be valid</li>
     *     <li>For all supported primitive keys, they must be valid</li>
     *     <li>Any unsupported primitive keys or "internal" keys are always invalid</li>
     * </ol>
     *
     * @param key The key to verify
     * @param map A Map of {@link Key} to {@link TransactionSignature}
     * @return {@code true} if the {@code key} has been validated, {@code false} otherwise.
     */
    private boolean checkIfPassed(@NonNull final Key key, @NonNull final Map<Key, TransactionSignature> map) {
        return switch (key.key().kind()) {
            case ED25519, ECDSA_SECP256K1 -> {
                final var txSig = map.get(key);
                yield txSig != null && txSig.getSignatureStatus() == VerificationStatus.VALID;
            }
            case ECDSA_384, RSA_3072, CONTRACT_ID, DELEGATABLE_CONTRACT_ID, UNSET -> false;
            case KEY_LIST -> checkIfPassed(key.keyListOrThrow(), map);
            case THRESHOLD_KEY -> checkIfPassed(key.thresholdKeyOrThrow(), map);
        };
    }

    /**
     * Checks whether the given {@link ThresholdKey} has been verified. This kind of key only requires a subset of
     * the keys to be valid. The subset that are not valid may not be valid because they are just the wrong kind of
     * key (for example, a contract ID key), or because there was corresponding signature in the signature map for
     * the key, or because there was a key but it failed key verification.
     *
     * @param thresholdKey The {@link ThresholdKey} to verify. If the threshold is less than or equal to zero, then
     *                     the threshold is clamped to 1. If the threshold is greater than the number of keys, then
     *                     it is clamped to the number of keys. There must be at least one key to be valid.
     * @param map A Map of {@link Key} to {@link TransactionSignature}
     * @return {@code true} if at least {@link ThresholdKey#threshold()} keys within the {@code key} have been
     * validated, {@code false} otherwise.
     */
    private boolean checkIfPassed(
            @NonNull final ThresholdKey thresholdKey, @NonNull final Map<Key, TransactionSignature> map) {
        if (thresholdKey.hasKeys()) {
            final var keyList = thresholdKey.keysOrThrow();
            if (keyList.hasKeys()) {
                final var list = keyList.keysOrThrow();
                if (list.isEmpty()) return false;
                final var threshold = Math.max(Math.min(list.size(), thresholdKey.threshold()), 1);
                var numCanFail = list.size() - threshold;
                for (final var subKey : list) {
                    final var passed = checkIfPassed(subKey, map);
                    if (!passed && --numCanFail < 0) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given {@link KeyList} has been verified. Such a key is valid if, and only if, every key
     * within the {@link KeyList} has been validated.
     *
     * @param keyList The {@link KeyList} to verify. Must have at least one key to be valid.
     * @param map A Map of {@link Key} to {@link TransactionSignature}
     * @return {@code true} if at least {@link ThresholdKey#threshold()} keys within the {@code key} have been
     * validated, {@code false} otherwise.
     */
    private boolean checkIfPassed(@NonNull final KeyList keyList, @NonNull final Map<Key, TransactionSignature> map) {
        if (keyList.hasKeys()) {
            final var list = keyList.keysOrThrow();
            if (list.isEmpty()) return false;
            for (final var subKey : list) {
                final var passed = checkIfPassed(subKey, map);
                if (!passed) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
