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

package com.hedera.node.app.service.token.impl.test.fixtures;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Fake Crypto Create Record Builder
 */
public class FakeCryptoCreateRecordBuilder {
    /**
     * Constructs a {@link FakeCryptoCreateRecordBuilder} instance.
     */
    public FakeCryptoCreateRecordBuilder() {}

    /**
     * Creates a {@link CryptoCreateRecordBuilder} instance.
     * @return a {@link CryptoCreateRecordBuilder} instance
     */
    public CryptoCreateRecordBuilder create() {
        return new CryptoCreateRecordBuilder() {

            private AccountID accountID;
            private Bytes evmAddress;
            private long transactionFee;
            private String memo;

            @NonNull
            @Override
            public TransactionBody transactionBody() {
                return TransactionBody.DEFAULT;
            }

            @Override
            public long transactionFee() {
                return 0;
            }

            @NonNull
            @Override
            public ResponseCodeEnum status() {
                return ResponseCodeEnum.SUCCESS;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder accountID(@NonNull final AccountID accountID) {
                this.accountID = accountID;
                return this;
            }

            @Override
            public SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status) {
                return this;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder evmAddress(@NonNull final Bytes evmAddress) {
                this.evmAddress = evmAddress;
                return this;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder transactionFee(@NonNull final long transactionFee) {
                this.transactionFee = transactionFee;
                return this;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder memo(@NonNull final String memo) {
                this.memo = memo;
                return this;
            }
        };
    }
}
