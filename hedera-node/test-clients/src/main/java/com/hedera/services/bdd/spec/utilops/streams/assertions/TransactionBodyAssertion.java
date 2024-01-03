/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;
import org.opentest4j.AssertionFailedError;

/**
 * Implementation support for a {@link RecordStreamAssertion} that filters to a single
 * record stream item, and passes as long as a given assertion does not throw an exception.
 */
public class TransactionBodyAssertion extends BaseIdScreenedAssertion {
    private final Predicate<TransactionID> idFilter;
    private final AssertingBiConsumer<HapiSpec, TransactionBody> bodyAssertion;

    public TransactionBodyAssertion(
            @NonNull final String specTxnId,
            @NonNull final HapiSpec spec,
            @NonNull final Predicate<TransactionID> idFilter,
            @NonNull final AssertingBiConsumer<HapiSpec, TransactionBody> bodyAssertion) {
        super(specTxnId, spec);
        this.idFilter = idFilter;
        this.bodyAssertion = bodyAssertion;
    }

    @Override
    protected boolean filter(@NonNull final TransactionID txnId) {
        return idFilter.test(txnId);
    }

    @Override
    public boolean test(@NonNull final RecordStreamItem item) throws AssertionError {
        try {
            final var txn = CommonUtils.extractTransactionBody(item.getTransaction());
            bodyAssertion.accept(spec, txn);
            return true;
        } catch (InvalidProtocolBufferException e) {
            throw new AssertionFailedError("Transaction body could not be parsed from item " + item);
        }
    }
}
