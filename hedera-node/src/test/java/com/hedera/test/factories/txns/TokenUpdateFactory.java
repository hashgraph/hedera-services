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

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_REPLACE_KT;

import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;

public class TokenUpdateFactory extends SignedTxnFactory<TokenUpdateFactory> {
    private TokenID id;
    private Optional<KeyTree> newAdminKt = Optional.empty();
    private Optional<AccountID> newTreasury = Optional.empty();
    private Optional<AccountID> newAutoRenew = Optional.empty();
    private boolean replaceFreeze, replaceSupply, replaceWipe, replaceKyc;

    private TokenUpdateFactory() {}

    public static TokenUpdateFactory newSignedTokenUpdate() {
        return new TokenUpdateFactory();
    }

    public TokenUpdateFactory updating(TokenID id) {
        this.id = id;
        return this;
    }

    public TokenUpdateFactory newAdmin(KeyTree kt) {
        newAdminKt = Optional.of(kt);
        return this;
    }

    public TokenUpdateFactory newAutoRenew(AccountID account) {
        newAutoRenew = Optional.of(account);
        return this;
    }

    public TokenUpdateFactory newTreasury(AccountID account) {
        newTreasury = Optional.of(account);
        return this;
    }

    public TokenUpdateFactory replacingFreeze() {
        replaceFreeze = true;
        return this;
    }

    public TokenUpdateFactory replacingSupply() {
        replaceSupply = true;
        return this;
    }

    public TokenUpdateFactory replacingWipe() {
        replaceWipe = true;
        return this;
    }

    public TokenUpdateFactory replacingKyc() {
        replaceKyc = true;
        return this;
    }

    @Override
    protected TokenUpdateFactory self() {
        return this;
    }

    @Override
    protected long feeFor(Transaction signedTxn, int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(TransactionBody.Builder txn) {
        var op = TokenUpdateTransactionBody.newBuilder();
        op.setToken(id);
        newAdminKt.ifPresent(kt -> op.setAdminKey(kt.asKey()));
        if (replaceFreeze) {
            op.setFreezeKey(TOKEN_REPLACE_KT.asKey());
        }
        if (replaceKyc) {
            op.setKycKey(TOKEN_REPLACE_KT.asKey());
        }
        if (replaceSupply) {
            op.setSupplyKey(TOKEN_REPLACE_KT.asKey());
        }
        if (replaceWipe) {
            op.setWipeKey(TOKEN_REPLACE_KT.asKey());
        }
        newAutoRenew.ifPresent(a -> op.setAutoRenewAccount(a));
        newTreasury.ifPresent(op::setTreasury);
        txn.setTokenUpdate(op);
    }
}
