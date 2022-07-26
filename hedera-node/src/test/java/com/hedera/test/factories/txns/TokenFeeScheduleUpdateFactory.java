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

import com.hedera.services.state.submerkle.FcCustomFee;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TokenFeeScheduleUpdateFactory extends SignedTxnFactory<TokenFeeScheduleUpdateFactory> {
    private TokenID id;
    private List<FcCustomFee> newFcCustomFees = new ArrayList<>();

    private TokenFeeScheduleUpdateFactory() {}

    public static TokenFeeScheduleUpdateFactory newSignedTokenFeeScheduleUpdate() {
        return new TokenFeeScheduleUpdateFactory();
    }

    public TokenFeeScheduleUpdateFactory updating(TokenID id) {
        this.id = id;
        return this;
    }

    public TokenFeeScheduleUpdateFactory withCustom(FcCustomFee fee) {
        newFcCustomFees.add(fee);
        return this;
    }

    @Override
    protected TokenFeeScheduleUpdateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        var op = TokenFeeScheduleUpdateTransactionBody.newBuilder();
        op.setTokenId(id);
        op.addAllCustomFees(
                newFcCustomFees.stream().map(FcCustomFee::asGrpc).collect(Collectors.toList()));
        txn.setTokenFeeScheduleUpdate(op);
    }
}
