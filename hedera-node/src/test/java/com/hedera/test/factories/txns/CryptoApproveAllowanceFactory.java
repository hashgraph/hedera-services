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
package com.hedera.test.factories.txns;

import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

public class CryptoApproveAllowanceFactory extends SignedTxnFactory<CryptoApproveAllowanceFactory> {
    private CryptoApproveAllowanceFactory() {}

    List<CryptoAllowance> cryptoAllowances;
    List<TokenAllowance> tokenAllowances;
    List<NftAllowance> nftAllowances;

    public static CryptoApproveAllowanceFactory newSignedApproveAllowance() {
        return new CryptoApproveAllowanceFactory();
    }

    public CryptoApproveAllowanceFactory withCryptoAllowances(
            List<CryptoAllowance> cryptoAllowances) {
        this.cryptoAllowances = cryptoAllowances;
        return this;
    }

    public CryptoApproveAllowanceFactory withTokenAllowances(List<TokenAllowance> tokenAllowances) {
        this.tokenAllowances = tokenAllowances;
        return this;
    }

    public CryptoApproveAllowanceFactory withNftAllowances(List<NftAllowance> nftAllowances) {
        this.nftAllowances = nftAllowances;
        return this;
    }

    @Override
    protected CryptoApproveAllowanceFactory self() {
        return this;
    }

    @Override
    protected long feeFor(final Transaction signedTxn, final int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(final TransactionBody.Builder txn) {
        final var op =
                CryptoApproveAllowanceTransactionBody.newBuilder()
                        .addAllCryptoAllowances(cryptoAllowances)
                        .addAllTokenAllowances(tokenAllowances)
                        .addAllNftAllowances(nftAllowances)
                        .build();
        txn.setCryptoApproveAllowance(op);
    }
}
