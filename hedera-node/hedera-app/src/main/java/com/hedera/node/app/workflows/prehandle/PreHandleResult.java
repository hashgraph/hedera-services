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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.UNKNOWN;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This is then made available to the
 * handle workflow.
 *
 * @param payer payer for the transaction, which could be from the transaction body, or could be a node account.
 *              The payer **may be null** only in the event of a catastrophic unknown exception. That is, if the
 *              status is {@link Status#UNKNOWN_FAILURE}, then the payer will be null. If the status is
 *              {@link Status#NODE_DUE_DILIGENCE_FAILURE}, then the payer will be the node account. In all other cases,
 *              the payer is extracted from the transaction body.
 * @param status {@link Status} of this pre-handle. Will always be set.
 * @param responseCode {@link ResponseCodeEnum} to the transaction as determined during pre-handle. Will always be set.
 * @param txInfo Information about the transaction that is being handled. If the transaction was not parseable, then
 *               this will be null, and an appropriate error status will be set.
 * @param verificationResults A map of {@link Future<SignatureVerificationFuture>} yielding the
 *                            {@link SignatureVerificationFuture} for a given cryptographic key. Ony cryptographic keys
 *                            are used as the key of this map.
 * @param innerResult {@link PreHandleResult} of the inner transaction (where appropriate)
 */
public record PreHandleResult(
        @Nullable AccountID payer,
        @Nullable Key payerKey,
        @NonNull Status status,
        @NonNull ResponseCodeEnum responseCode,
        @Nullable TransactionInfo txInfo,
        @Nullable Map<Key, SignatureVerificationFuture> verificationResults,
        @Nullable PreHandleResult innerResult) {

    /**
     * An enumeration of all possible types of pre-handle results.
     */
    public enum Status {
        /** When the pre-handle fails for completely unknown reasons. This indicates a bug in our software. */
        UNKNOWN_FAILURE,
        /** When the pre-handle fails due to node due diligence failures. */
        NODE_DUE_DILIGENCE_FAILURE,
        /**
         * When the pre-handle fails because the combination of state and transaction is invalid, such as insufficient
         * balance, missing account, or invalid transaction body data.
         */
        PRE_HANDLE_FAILURE,
        /**
         * The pre-handle ended successfully. Since signature verification is asynchronous, it may be that the
         * transaction is still invalid, but we have done all we can to verify it at this point. The handle code
         * will need to check the signature verification results to see what the true status is.
         */
        SO_FAR_SO_GOOD
    }

    /** Create a new instance. */
    public PreHandleResult {
        requireNonNull(status);
        requireNonNull(responseCode);
    }

    /**
     * Get a {@link Future<SignatureVerification>} for the given key.
     *
     * <p>If the key is a cryptographic key (i.e. a basic key like ED25519 or ECDSA_SECP256K1), and the cryptographic
     * key was in the signature map of the transaction, then a {@link Future} will be returned that will yield the
     * {@link SignatureVerification} for that key. If there was no such cryptographic key in the signature map, then
     * a completed, failed future is returned.
     *
     * <p>If the key is a key list, then a {@link Future} will be returned that aggregates the results of each key in
     * the key list, possibly nested.
     *
     * <p>If the key is a threshold key, then a {@link Future} will be returned that aggregates the results of each key
     * in the threshold key, possibly nested, based on the threshold for that key.
     *
     * @param key The key to check on the verification results for.
     * @return A {@link Future} that will yield the {@link SignatureVerification} for the given key.
     */
    @NonNull
    public Future<SignatureVerification> verificationFor(@NonNull final Key key) {
        requireNonNull(key);
        if (verificationResults == null) return failedVerificationFuture(key);
        return switch (key.key().kind()) {
            case ED25519, ECDSA_SECP256K1 -> {
                final var result = verificationResults.get(key);
                yield result == null ? failedVerificationFuture(key) : result;
            }
            case KEY_LIST -> {
                final var keys = key.keyListOrThrow().keysOrElse(emptyList());
                yield verificationFor(key, keys, 0);
            }
            case THRESHOLD_KEY -> {
                final var thresholdKey = key.thresholdKeyOrThrow();
                final var keyList = thresholdKey.keysOrElse(KeyList.DEFAULT);
                final var keys = keyList.keysOrElse(emptyList());
                final var threshold = thresholdKey.threshold();
                final var clampedThreshold = Math.min(Math.max(1, threshold), keys.size());
                yield verificationFor(key, keys, keys.size() - clampedThreshold);
            }
            case CONTRACT_ID, DELEGATABLE_CONTRACT_ID, ECDSA_384, RSA_3072, UNSET -> failedVerificationFuture(key);
        };
    }

    /**
     * Utility method that converts the keys into a list of {@link Future<SignatureVerification>} and then aggregates
     * them into a single {@link Future<SignatureVerification>}.
     *
     * @param key The key that is being verified.
     * @param keys The sub-keys of the key being verified
     * @param numCanFail The number of sub-keys that can fail verification before the key itself does
     * @return A {@link Future<SignatureVerification>}
     */
    @NonNull
    private Future<SignatureVerification> verificationFor(
            @NonNull final Key key, @NonNull final List<Key> keys, final int numCanFail) {
        // If there are no keys, then we always fail. There must be at least one key in a key list or threshold key
        // for it to be a valid key and to pass any form of verification.
        if (keys.isEmpty() || numCanFail < 0) return failedVerificationFuture(key);
        final var futures = keys.stream().map(this::verificationFor).toList();
        return new CompoundSignatureVerificationFuture(key, null, futures, numCanFail);
    }

    /**
     * Look for a {@link SignatureVerification} that applies to the given hollow account.
     * @param hollowAccount The hollow account to lookup verification for.
     * @return The {@link SignatureVerification} for the given hollow account.
     */
    @NonNull
    public Future<SignatureVerification> verificationFor(@NonNull final Account hollowAccount) {
        requireNonNull(hollowAccount);
        if (verificationResults != null) {
            for (final var result : verificationResults.values()) {
                final var account = result.hollowAccount();
                if (account != null && account.accountNumber() == hollowAccount.accountNumber()) {
                    return result;
                }
            }
        }
        return failedVerificationFuture(hollowAccount);
    }

    /**
     * Creates a new {@link PreHandleResult} in the event of a random failure that should not be automatically
     * charged to the node. Instead, during the handle phase, we will try again and charge the node if it fails again.
     * The {@link #responseCode()} will be set to {@link ResponseCodeEnum#UNKNOWN} and {@link #status()} will be set
     * to {@link Status#UNKNOWN_FAILURE}.
     *
     * @return A new {@link PreHandleResult} with the given parameters.
     */
    @NonNull
    public static PreHandleResult unknownFailure() {
        return new PreHandleResult(null, null, Status.UNKNOWN_FAILURE, UNKNOWN, null, null, null);
    }

    /**
     * Creates a new {@link PreHandleResult} in the event of node due diligence failure. The node itself will be
     * charged for the transaction. If the {@link TransactionInfo} is not available because the failure happened while
     * parsing the bytes, then it may be omitted as {@code null}. The {@link #status()} will be set to
     * {@link Status#NODE_DUE_DILIGENCE_FAILURE}.
     *
     * @param node The node that is responsible for paying for this due diligence failure.
     * @param responseCode The response code of the failure.
     * @param txInfo The transaction info, if available.
     * @return A new {@link PreHandleResult} with the given parameters.
     */
    @NonNull
    public static PreHandleResult nodeDueDiligenceFailure(
            @NonNull final AccountID node,
            @NonNull final ResponseCodeEnum responseCode,
            @Nullable final TransactionInfo txInfo) {
        return new PreHandleResult(node, null, Status.NODE_DUE_DILIGENCE_FAILURE, responseCode, txInfo, null, null);
    }

    /**
     * Creates a new {@link PreHandleResult} in the event of a failure that should be charged to the payer.
     *
     * @param payer The account that will pay for this transaction.
     * @param responseCode The responseCode code of the failure.
     * @param txInfo The transaction info
     * @param verificationResults A map of {@link Future<SignatureVerificationFuture>} yielding the
     *                            {@link SignatureVerificationFuture} for a given cryptographic key. Ony cryptographic keys
     *                            are used as the key of this map.
     * @return A new {@link PreHandleResult} with the given parameters.
     */
    @NonNull
    public static PreHandleResult preHandleFailure(
            @NonNull final AccountID payer,
            @Nullable final Key payerKey,
            @NonNull final ResponseCodeEnum responseCode,
            @NonNull final TransactionInfo txInfo,
            @Nullable Map<Key, SignatureVerificationFuture> verificationResults) {
        return new PreHandleResult(
                payer, payerKey, Status.PRE_HANDLE_FAILURE, responseCode, txInfo, verificationResults, null);
    }

    /** Convenience method to create a SignatureVerification that failed */
    @NonNull
    private static Future<SignatureVerification> failedVerificationFuture(@NonNull final Key key) {
        return completedFuture(new SignatureVerification() {
            @NonNull
            @Override
            public Key key() {
                return key;
            }

            @Override
            public boolean passed() {
                return false;
            }
        });
    }

    /** Convenience method to create a SignatureVerification for a hollow account that failed */
    @NonNull
    private static Future<SignatureVerification> failedVerificationFuture(@NonNull final Account hollowAccount) {
        return completedFuture(new SignatureVerification() {
            @Nullable
            @Override
            public Key key() {
                return null;
            }

            @NonNull
            @Override
            public Account hollowAccount() {
                return hollowAccount;
            }

            @Override
            public boolean passed() {
                return false;
            }
        });
    }
}
