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

package com.hedera.node.app.throttle;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HapiThrottle;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayOutputStream;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@link ThrottleAccumulator} that delegates to a {@link FunctionalityThrottling} instance to
 * support query throttling only.
 */
@Singleton
public class MonoThrottleAccumulator implements ThrottleAccumulator {
    private final FunctionalityThrottling hapiThrottling;

    @Inject
    public MonoThrottleAccumulator(@HapiThrottle final FunctionalityThrottling hapiThrottling) {
        this.hapiThrottling = hapiThrottling;
    }

    @Override
    public boolean shouldThrottle(@NonNull final TransactionBody txn) {
        try {
            // This is wildly inefficient. We need to rework the fee system, so we are not
            // creating temporary objects like this and doing so much protobuf serialization
            // for no good reason!
            var out = new ByteArrayOutputStream();
            TransactionBody.PROTOBUF.write(txn, new WritableStreamingData(out));
            final var txnBytes = out.toByteArray();

            final var signedTx = SignedTransaction.newBuilder()
                    .bodyBytes(Bytes.wrap(txnBytes))
                    .build();

            out = new ByteArrayOutputStream();
            SignedTransaction.PROTOBUF.write(signedTx, new WritableStreamingData(out));

            final var adapter = SignedTxnAccessor.uncheckedFrom(Transaction.newBuilder()
                    .signedTransactionBytes(Bytes.wrap(out.toByteArray()))
                    .build());

            return hapiThrottling.shouldThrottleTxn(adapter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean shouldThrottleQuery(final @NonNull HederaFunctionality functionality, final @NonNull Query query) {
        final var monoFunctionality = PbjConverter.fromPbj(functionality);
        final var monoQuery = PbjConverter.fromPbj(query);
        return hapiThrottling.shouldThrottleQuery(monoFunctionality, monoQuery);
    }
}
