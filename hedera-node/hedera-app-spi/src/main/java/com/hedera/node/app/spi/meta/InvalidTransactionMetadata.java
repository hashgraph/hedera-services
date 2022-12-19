/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * An implementation of {@link TransactionMetadata} for cases when a failure with a specific {@link
 * ResponseCodeEnum} has occurred. This is used instead of {@link SigTransactionMetadata} when other
 * payer key lookup information obtained in pre-handle is not needed in handle, since that need to
 * be re-computed.
 *
 * @param txnBody the {@link TransactionBody} if known, {@code null} otherwise
 * @param payer the payer for the transaction
 * @param status the {@link ResponseCodeEnum} of the error
 */
public record InvalidTransactionMetadata(
        @NonNull TransactionBody txnBody,
        @NonNull AccountID payer,
        @NonNull ResponseCodeEnum status)
        implements TransactionMetadata {
    public InvalidTransactionMetadata {
        requireNonNull(txnBody);
        requireNonNull(payer);
        requireNonNull(status);
    }

    @Override
    public List<HederaKey> requiredNonPayerKeys() {
        return List.of();
    }

    @Override
    public HederaKey payerKey() {
        return null;
    }
}
