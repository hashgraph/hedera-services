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

import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature
 * verification.
 *
 * @param txnBody Transaction that is being pre-handled
 * @param payer payer for the transaction
 * @param status {@link ResponseCodeEnum} status of the transaction
 * @param payerKey payer key required to sign the transaction. It is null if payer is missing
 * @param requiredNonPayerKeys list of keys that are required to sign the transaction, in addition
 *     to payer key
 */
public record SigTransactionMetadata(
        @NonNull TransactionBody txnBody,
        @NonNull AccountID payer,
        ResponseCodeEnum status,
        @Nullable HederaKey payerKey,
        List<HederaKey> requiredNonPayerKeys)
        implements TransactionMetadata {
    public SigTransactionMetadata {
        Objects.requireNonNull(txnBody);
        Objects.requireNonNull(payer);
    }
}
