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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;

public class TokenDissociateFactory extends SignedTxnFactory<TokenDissociateFactory> {
    private AccountID target;
    private List<TokenID> dissociations = new ArrayList<>();

    private TokenDissociateFactory() {}

    public static TokenDissociateFactory newSignedTokenDissociate() {
        return new TokenDissociateFactory();
    }

    public TokenDissociateFactory targeting(AccountID target) {
        this.target = target;
        return this;
    }

    public TokenDissociateFactory dissociating(TokenID token) {
        dissociations.add(token);
        return this;
    }

    @Override
    protected TokenDissociateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        txn.setTokenDissociate(
                        TokenDissociateTransactionBody.newBuilder()
                                .setAccount(target)
                                .addAllTokens(dissociations))
                .build();
    }
}
