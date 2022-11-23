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

import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

public class CryptoDeleteAllowanceFactory extends SignedTxnFactory<CryptoDeleteAllowanceFactory> {
    private CryptoDeleteAllowanceFactory() {}

    List<NftRemoveAllowance> nftAllowances;

    public static CryptoDeleteAllowanceFactory newSignedDeleteAllowance() {
        return new CryptoDeleteAllowanceFactory();
    }

    public CryptoDeleteAllowanceFactory withNftAllowances(List<NftRemoveAllowance> nftAllowances) {
        this.nftAllowances = nftAllowances;
        return this;
    }

    @Override
    protected CryptoDeleteAllowanceFactory self() {
        return this;
    }

    @Override
    protected long feeFor(final Transaction signedTxn, final int numPayerKeys) {
        return 0;
    }

    @Override
    protected void customizeTxn(final TransactionBody.Builder txn) {
        final var op =
                CryptoDeleteAllowanceTransactionBody.newBuilder()
                        .addAllNftAllowances(nftAllowances)
                        .build();
        txn.setCryptoDeleteAllowance(op);
    }
}
