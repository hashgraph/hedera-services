/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.txns;

import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class TokenUnfreezeFactory extends SignedTxnFactory<TokenUnfreezeFactory> {
    private TokenUnfreezeFactory() {}

    private TokenID id;

    public static TokenUnfreezeFactory newSignedTokenUnfreeze() {
        return new TokenUnfreezeFactory();
    }

    public TokenUnfreezeFactory unfreezing(TokenID id) {
        this.id = id;
        return this;
    }

    @Override
    protected TokenUnfreezeFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        var op = TokenUnfreezeAccountTransactionBody.newBuilder().setToken(id);
        txn.setTokenUnfreeze(op);
    }
}
