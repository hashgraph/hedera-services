/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.lambda;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.LambdaSStore;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.LambdaOwnerID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.LambdaSStoreTransactionBody;
import com.hederahashgraph.api.proto.java.LambdaStorageSlot;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HapiLambdaSStore extends HapiTxnOp<HapiLambdaSStore> {
    private List<LambdaStorageSlot> slots = new ArrayList<>();

    @NonNull
    private final LambdaOwnerID.OwnerIdOneOfType ownerType;

    @NonNull
    private final String ownerName;

    public static HapiLambdaSStore storeAccountLambda(@NonNull final String account) {
        return new HapiLambdaSStore(LambdaOwnerID.OwnerIdOneOfType.ACCOUNT_ID, account);
    }

    public HapiLambdaSStore slots(@NonNull final Bytes... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("Slots must be key-value pairs");
        }
        for (int i = 0; i < kv.length; i += 2) {
            slots.add(LambdaStorageSlot.newBuilder()
                    .setKey(fromPbj(kv[i]))
                    .setValue(fromPbj(kv[i + 1]))
                    .build());
        }
        return this;
    }

    private HapiLambdaSStore(@NonNull final LambdaOwnerID.OwnerIdOneOfType ownerType, @NonNull final String ownerName) {
        this.ownerType = requireNonNull(ownerType);
        this.ownerName = requireNonNull(ownerName);
    }

    @Override
    public HederaFunctionality type() {
        return LambdaSStore;
    }

    @Override
    protected HapiLambdaSStore self() {
        return this;
    }

    @Override
    protected long feeFor(@NonNull final HapiSpec spec, @NonNull final Transaction txn, final int numPayerKeys)
            throws Throwable {
        return 1L;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        final var op = spec.txns()
                .<LambdaSStoreTransactionBody, LambdaSStoreTransactionBody.Builder>body(
                        LambdaSStoreTransactionBody.class, b -> b.addAllStorageSlots(slots));
        return b -> b.setLambdaSstore(op);
    }
}
