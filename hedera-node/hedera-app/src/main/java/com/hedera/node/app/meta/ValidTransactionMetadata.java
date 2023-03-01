/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.meta;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

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
 * @param otherSignatures lit {@link TransactionSignature} of other keys that need to sign
 * @param innerMetadata {@link ValidTransactionMetadata} of the inner transaction (where appropriate)
 */
public record ValidTransactionMetadata(
        @NonNull TransactionBody txnBody,
        @NonNull AccountID payer,
        @NonNull SignatureMap signatureMap,
        @NonNull ResponseCodeEnum status,
        @Nullable HederaKey payerKey,
        @NonNull Map<HederaKey, TransactionSignature> otherSignatures,
        @Nullable TransactionMetadata innerMetadata)
        implements com.hedera.node.app.spi.meta.TransactionMetadata {

    public ValidTransactionMetadata {
        requireNonNull(txnBody);
        requireNonNull(payer);
        requireNonNull(signatureMap);
        requireNonNull(status);
        requireNonNull(otherSignatures);
    }

    public ValidTransactionMetadata(
            @NonNull final PreHandleContext context,
            @NonNull final SignatureMap signatureMap,
            @NonNull final Map<HederaKey, TransactionSignature> otherSignatures,
            @Nullable final TransactionMetadata innerMetadata) {
        this(
                requireNonNull(context).getTxn(),
                context.getPayer(),
                signatureMap,
                context.getStatus(),
                context.getPayerKey(),
                otherSignatures,
                innerMetadata);
    }

    @Override
    public boolean failed() {
        return status != ResponseCodeEnum.OK;
    }
}
