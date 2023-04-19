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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.service.mono.Utils.asHederaKeys;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.signature.SignatureVerificationResult;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This is then made available to the
 * transaction during the "handle" phase as part of the {@link HandleContext}.
 *
 * @param payer payer for the transaction, which could be from the transaction body, or could be a node account.
 *              The payer will always be set.
 * @param status {@link ResponseCodeEnum} status of the pre-handle result. The status will always be set.
 * @param txInfo Information about the transaction that is being handled. If the transaction was not parseable, then
 *               this will be null, and an appropriate error status will be set.
 * @param payerSignatureVerification Result of the payer signature verification. If the payer refers to a node account,
 *                                   then this field will be null. Signature verification is performed asynchronously,
 *                                   so you may have to block on this field while getting the result.
 * @param nonPayerSignatureVerification Result of the non-payer signature verification. It may be that there were no
 *                                      non-payer signatures, in which case this field may or may not be null. If there
 *                                      were any signatures to verify, then this field will give a final result
 *                                      to whether all signatures were valid.
 * @param innerResult                   {@link PreHandleResult} of the inner transaction (where appropriate)
 */
public record PreHandleResult(
        @NonNull AccountID payer,
        @NonNull ResponseCodeEnum status,
        @Nullable TransactionInfo txInfo,
        @Nullable SignatureVerificationResult payerSignatureVerification,
        @Nullable SignatureVerificationResult nonPayerSignatureVerification,
        @Nullable PreHandleResult innerResult) {

    public PreHandleResult {
        requireNonNull(status);
        requireNonNull(payer);
    }

    /**
     * Creates a new {@link PreHandleResult} in the event of a random failure that should not be automatically
     * charged to the node. Instead, during the handle phase, we will try again and charge the node if it fails again.
     * The {@link #status()} will be set to {@link ResponseCodeEnum#UNKNOWN}.
     *
     * @param node The node that was responsible for this transaction.
     * @return A new {@link PreHandleResult} with the given parameters.
     */
    public static PreHandleResult unknownFailure(@NonNull final AccountID node) {
        return new PreHandleResult(node, ResponseCodeEnum.UNKNOWN, null, null, null, null);
    }

    /**
     * Creates a new {@link PreHandleResult} in the event of node due diligence failure. The node itself will be
     * charged for the transaction. If the {@link TransactionInfo} is not available because the failure happened while
     * parsing the bytes, then it may be omitted as {@code null}.
     *
     * @param node The node that is responsible for paying for this due diligence failure.
     * @param status The status code of the failure.
     * @param txInfo The transaction info, if available.
     * @return A new {@link PreHandleResult} with the given parameters.
     */
    public static PreHandleResult nodeDueDiligenceFailure(
            @NonNull final AccountID node,
            @NonNull final ResponseCodeEnum status,
            @Nullable final TransactionInfo txInfo) {
        return new PreHandleResult(node, status, txInfo, null, null, null);
    }

    /**
     * Creates a new {@link PreHandleResult} in the event of a failure that should be charged to the payer.
     *
     * @param payer The account that will pay for this transaction.
     * @param status The status code of the failure.
     * @param txInfo The transaction info
     * @param payerVerificationResult The result of the payer signature verification. May be null if the payer account was not found in state.
     * @return A new {@link PreHandleResult} with the given parameters.
     */
    public static PreHandleResult preHandleFailure(
            @NonNull final AccountID payer,
            @NonNull final ResponseCodeEnum status,
            @NonNull final TransactionInfo txInfo,
            @Nullable final SignatureVerificationResult payerVerificationResult) {
        return new PreHandleResult(payer, status, txInfo, payerVerificationResult, null, null);
    }

    /**
     * Checks the failure by validating the status is not {@link ResponseCodeEnum OK}
     *
     * @return returns true if status is not OK
     */
    public boolean failed() {
        return status != ResponseCodeEnum.OK;
    }
}
