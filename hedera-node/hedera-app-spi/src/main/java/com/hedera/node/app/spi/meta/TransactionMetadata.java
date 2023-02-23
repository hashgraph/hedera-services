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

package com.hedera.node.app.spi.meta;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Metadata collected when transactions are handled as part of "pre-handle". This happens with
 * multiple background threads. Any state read or computed as part of this pre-handle, including any
 * errors, are captured in the TransactionMetadata. This is then made available to the transaction
 * during the "handle" phase as part of the HandleContext.
 *
 * @param txnBody Transaction that is being pre-handled
 * @param payer payer for the transaction
 * @param status {@link ResponseCodeEnum} status of the transaction
 * @param payerKey payer key required to sign the transaction. It is null if payer is missing
 * @param requiredNonPayerKeys list of keys that are required to sign the transaction, in addition
 *     to payer key
 * @param payerSignature {@link TransactionSignature} of the payer
 * @param otherSignatures lit {@link TransactionSignature} of other keys that need to sign
 * @param innerMetadata {@link TransactionMetadata} of the inner transaction (where appropriate)
 */
public record TransactionMetadata(
        @Nullable TransactionBody txnBody,
        @Nullable AccountID payer,
        @Nullable SignatureMap signatureMap,
        @NonNull ResponseCodeEnum status,
        @Nullable HederaKey payerKey,
        @NonNull List<HederaKey> requiredNonPayerKeys,
        @Nullable TransactionSignature payerSignature,
        @NonNull List<TransactionSignature> otherSignatures,
        @Nullable TransactionMetadata innerMetadata) {

    public TransactionMetadata {
        requireNonNull(status);
        requireNonNull(requiredNonPayerKeys);
        requireNonNull(otherSignatures);
    }

    public TransactionMetadata(
            @NonNull final PreHandleContext context,
            @NonNull final SignatureMap signatureMap,
            @Nullable final TransactionSignature payerSignature,
            @NonNull final List<TransactionSignature> otherSignatures,
            @Nullable final TransactionMetadata innerMetadata) {
        this(
                requireNonNull(context).getTxn(),
                context.getPayer(),
                requireNonNull(signatureMap),
                context.getStatus(),
                context.getPayerKey(),
                context.getRequiredNonPayerKeys(),
                payerSignature,
                otherSignatures,
                innerMetadata);
    }

    public TransactionMetadata(@NonNull final ResponseCodeEnum status) {
        this(null, null, null, status, null, List.of(), null, List.of(), null);
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
