/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.transactions.token;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiBaseTransfer;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TokenAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.function.Consumer;

public class HapiTokenAirdrop extends HapiBaseTransfer<HapiTokenAirdrop> {

    public HapiTokenAirdrop(final TokenMovement... sources) {
        this.tokenAwareProviders = List.of(sources);
    }

    @Override
    protected long feeFor(HapiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
        return 0;
    }

    @Override
    public HederaFunctionality type() {
        return HederaFunctionality.TokenAirdrop;
    }

    @Override
    protected HapiTokenAirdrop self() {
        return this;
    }

    @Override
    protected Consumer<TransactionBody.Builder> opBodyDef(HapiSpec spec) throws Throwable {
        final TokenAirdropTransactionBody opBody = spec.txns()
                .<TokenAirdropTransactionBody, TokenAirdropTransactionBody.Builder>body(
                        TokenAirdropTransactionBody.class, b -> {
                            final var xfers = transfersAllFor(spec);
                            for (final TokenTransferList scopedXfers : xfers) {
                                b.addTokenTransfers(scopedXfers);
                            }
                        });
        return builder -> builder.setTokenAirdrop(opBody);
    }
}
