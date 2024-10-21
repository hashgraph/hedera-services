/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip423;

import static com.hedera.services.bdd.suites.freeze.UpgradeSuite.poeticUpgradeLoc;

import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class LongTermScheduleUtils {

    static final String PAYING_ACCOUNT_2 = "payingAccount2";
    static final String SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED = "Scheduled transaction must not succeed";
    static final String SCHEDULE_CREATE_FEE = "scheduleCreateFee";
    static final String SENDER_1 = "sender1";
    static final String SENDER_2 = "sender2";
    static final String SENDER_3 = "sender3";
    static final String SIMPLE_UPDATE = "SimpleUpdate";
    static final String SUCCESS_TXN = "successTxn";
    static final String TRANSACTION_NOT_SCHEDULED = "Transaction not scheduled!";
    static final String VALID_SCHEDULE = "validSchedule";
    static final String WEIRDLY_POPULAR_KEY = "weirdlyPopularKey";
    static final String WRONG_CONSENSUS_TIMESTAMP = "Wrong consensus timestamp!";
    static final String WRONG_RECORD_ACCOUNT_ID = "Wrong record account ID!";
    static final String WRONG_SCHEDULE_ID = "Wrong schedule ID!";
    static final String WRONG_TRANSACTION_VALID_START = "Wrong transaction valid start!";
    static final String WRONG_TRANSFER_LIST = "Wrong transfer list!";

    static final byte[] ORIG_FILE = "SOMETHING".getBytes();

    private LongTermScheduleUtils() {}

    static boolean transferListCheck(
            HapiGetTxnRecord triggered,
            AccountID givingAccountID,
            AccountID receivingAccountID,
            AccountID payingAccountID,
            Long amount) {
        AccountAmount givingAmount = AccountAmount.newBuilder()
                .setAccountID(givingAccountID)
                .setAmount(-amount)
                .build();

        AccountAmount receivingAmount = AccountAmount.newBuilder()
                .setAccountID(receivingAccountID)
                .setAmount(amount)
                .build();

        var accountAmountList = triggered.getResponseRecord().getTransferList().getAccountAmountsList();
        System.out.println("accountAmountList: " + accountAmountList);
        boolean payerHasPaid =
                accountAmountList.stream().anyMatch(a -> a.getAccountID().equals(payingAccountID) && a.getAmount() < 0);
        System.out.println("payerHasPaid: " + payerHasPaid);
        boolean amountHasBeenTransferred =
                accountAmountList.contains(givingAmount) && accountAmountList.contains(receivingAmount);
        System.out.println("amountHasBeenTransferred: " + amountHasBeenTransferred);

        return amountHasBeenTransferred && payerHasPaid;
    }

    static byte[] getPoeticUpgradeHash() {
        final byte[] poeticUpgradeHash;
        try {
            final var sha384 = MessageDigest.getInstance("SHA-384");
            final var poeticUpgrade = Files.readAllBytes(Paths.get(poeticUpgradeLoc));
            poeticUpgradeHash = sha384.digest(poeticUpgrade);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("scheduledFreezeWorksAsExpected environment is unsuitable", e);
        }
        return poeticUpgradeHash;
    }
}
