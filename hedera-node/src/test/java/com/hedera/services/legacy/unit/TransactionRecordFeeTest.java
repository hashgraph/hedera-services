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
package com.hedera.services.legacy.unit;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRecordFeeTest {
    private static TransactionReceipt transactionReceipt;
    private static TransactionRecord transactionRecord;
    private static final int receiptStorageTime = 180;

    @BeforeEach
    void init() {
        final var currentRate = ExchangeRate.newBuilder().setCentEquiv(12).setHbarEquiv(1);
        final var nextRate = ExchangeRate.newBuilder().setCentEquiv(15).setHbarEquiv(1);
        final var exchangeRateSet =
                ExchangeRateSet.newBuilder().setCurrentRate(currentRate).setNextRate(nextRate);
        final var firstAccount =
                AccountID.newBuilder().setAccountNum(1l).setRealmNum(0l).setShardNum(0l);
        final var secondAccount =
                AccountID.newBuilder().setAccountNum(2l).setRealmNum(0l).setShardNum(0l);
        transactionReceipt =
                TransactionReceipt.newBuilder()
                        .setStatus(ResponseCodeEnum.OK)
                        .setAccountID(firstAccount)
                        .setExchangeRate(exchangeRateSet)
                        .build();

        final var transactionHash = TxnUtils.randomUtf8ByteString(48);
        final var commonTimeStamp = Timestamp.newBuilder().setSeconds(10000000l);
        final var txId =
                TransactionID.newBuilder()
                        .setAccountID(firstAccount)
                        .setTransactionValidStart(commonTimeStamp);
        final String memo = "TestTransactionRecord";
        final long transactionFee = 10000l;
        final var accountAmount1 =
                AccountAmount.newBuilder().setAccountID(firstAccount).setAmount(10000);
        final var accountAmount2 =
                AccountAmount.newBuilder().setAccountID(secondAccount).setAmount(10000);

        final var transferList =
                TransferList.newBuilder()
                        .addAccountAmounts(accountAmount1)
                        .addAccountAmounts(accountAmount2);

        transactionRecord =
                TransactionRecord.newBuilder()
                        .setReceipt(transactionReceipt)
                        .setTransactionHash(transactionHash)
                        .setConsensusTimestamp(commonTimeStamp)
                        .setTransactionID(txId)
                        .setMemo(memo)
                        .setTransactionFee(transactionFee)
                        .setTransferList(transferList)
                        .build();
    }

    @Test
    void testTransactionRecordRBH() {
        final long transactionRecordRbh =
                FeeBuilder.getTxRecordUsageRBH(transactionRecord, receiptStorageTime);
        assertNotEquals(0, transactionRecordRbh);
    }
}
