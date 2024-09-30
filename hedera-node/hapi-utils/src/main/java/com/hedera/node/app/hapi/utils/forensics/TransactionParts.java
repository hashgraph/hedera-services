/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.forensics;

import static com.hedera.node.app.hapi.utils.CommonUtils.extractTransactionBody;
import static com.hedera.node.app.hapi.utils.CommonUtils.functionOf;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Encapsulates the parts of a transaction we typically care about
 * for forensics purposes.
 */
public record TransactionParts(
        @NonNull Transaction wrapper, @NonNull TransactionBody body, @NonNull HederaFunctionality function) {
    public TransactionParts {
        requireNonNull(wrapper);
        requireNonNull(body);
        requireNonNull(function);
    }

    public static TransactionParts from(@NonNull final Transaction txn) {
        try {
            final var body = extractTransactionBody(txn);
            return new TransactionParts(txn, body, functionOf(body));
        } catch (InvalidProtocolBufferException | UnknownHederaFunctionality e) {
            // Fail immediately with invalid transactions that should not be
            // in any production record stream
            throw new IllegalArgumentException(e);
        }
    }
}
