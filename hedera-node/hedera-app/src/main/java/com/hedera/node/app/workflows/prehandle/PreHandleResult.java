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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the {@link PreHandleResult}. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 *
 * @param txnBody Transaction that is being pre-handled
 * @param payer payer for the transaction
 * @param status {@link ResponseCodeEnum} status of the transaction
 * @param payerKey payer key required to sign the transaction. It is null if payer is missing
 * @param innerResult {@link PreHandleResult} of the inner transaction (where appropriate)
 */
public record PreHandleResult(
        @Nullable TransactionBody txnBody,
        @Nullable SignatureMap signatureMap,
        @Nullable AccountID payer,
        @NonNull ResponseCodeEnum status,
        @Nullable HederaKey payerKey,
        @NonNull List<HederaKey> otherPartyKeys,
        @Nullable List<TransactionSignature> cryptoSignatures,
        @Nullable PreHandleResult innerResult) {

    public PreHandleResult {
        requireNonNull(status);
    }

    public PreHandleResult(
            @NonNull final PreHandleContext context,
            @NonNull final SignatureMap signatureMap,
            @NonNull final List<TransactionSignature> cryptoSignatures,
            @Nullable final PreHandleResult innerResult) {
        this(
                requireNonNull(context).getTxn(),
                requireNonNull(signatureMap),
                context.getPayer(),
                context.getStatus(),
                context.getPayerKey(),
                context.getRequiredNonPayerKeys(),
                requireNonNull(cryptoSignatures),
                innerResult);
    }

    public PreHandleResult(@NonNull final ResponseCodeEnum status) {
        this(null, null, null, status, null, Collections.emptyList(), Collections.emptyList(), null);
    }

    public static <T> T preHandleFailure(AccountID payer, ResponseCodeEnum responseCodeEnum, TransactionInfo txInfo) {
        return null;
    }

    public static <T> T preHandleFailure(AccountID payer, Future<Boolean> payerVerificationFuture, ResponseCodeEnum responseCodeEnum, TransactionInfo txInfo) {
        return null;
    }

    public static <T> T nodeDueDiligenceFailure(AccountID creator, ResponseCodeEnum responseCodeEnum, TransactionInfo txInfo) {
        return null;
    }

    public static <T> T unknownFailure(AccountID creator, Throwable th) {
        return null;
    }

    public static <T> T nodeDueDiligenceFailure(AccountID creator, ResponseCodeEnum responseCodeEnum) {
        return null;
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
