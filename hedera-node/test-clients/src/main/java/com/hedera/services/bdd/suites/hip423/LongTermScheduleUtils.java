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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUppercase;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.buildUpgradeZipFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.prepareUpgrade;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateSpecialFile;
import static com.hedera.services.bdd.spec.utilops.upgrade.BuildUpgradeZipOp.FAKE_UPGRADE_ZIP_LOC;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.DEFAULT_UPGRADE_FILE_ID;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.FAKE_ASSETS_LOC;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileAppendsPerBurst;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileHashAt;

import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public final class LongTermScheduleUtils {

    static final String SENDER = "sender";
    static final String PAYER = "payer";
    static final String ADMIN = "admin";
    static final String EXTRA_KEY = "extraKey";
    static final String SHARED_KEY = "sharedKey";
    static final String NEW_SENDER_KEY = "newSenderKey";
    static final String SENDER_TXN = "senderTxn";
    static final String CREATE_TXN = "createTxn";
    static final String RECEIVER = "receiver";
    static final String BASIC_XFER = "basicXfer";
    static final String TWO_SIG_XFER = "twoSigXfer";
    static final String DEFERRED_XFER = "deferredXfer";
    static final String THREE_SIG_XFER = "threeSigXfer";
    static final String TOKEN_A = "tokenA";
    static final String CREATION = "creation";
    static final String BEFORE = "before";
    static final String DEFERRED_FALL = "deferredFall";
    static final String DEFERRED_CREATION = "deferredCreation";
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

    static SpecOperation[] scheduleFakeUpgrade(
            @NonNull final String payer, final long lifetime, @NonNull final String via) {
        final var operations = List.of(
                buildUpgradeZipFrom(FAKE_ASSETS_LOC),
                // Upload it to file 0.0.150; need sourcing() here because the operation reads contents eagerly
                sourcing(() -> updateSpecialFile(
                        GENESIS,
                        DEFAULT_UPGRADE_FILE_ID,
                        FAKE_UPGRADE_ZIP_LOC,
                        TxnUtils.BYTES_4K,
                        upgradeFileAppendsPerBurst())),
                // Issue PREPARE_UPGRADE; need sourcing() here because we want to hash only after creating the ZIP
                sourcing(() -> scheduleCreate(
                                VALID_SCHEDULE,
                                prepareUpgrade()
                                        .withUpdateFile(DEFAULT_UPGRADE_FILE_ID)
                                        .havingHash(upgradeFileHashAt(FAKE_UPGRADE_ZIP_LOC)))
                        .withEntityMemo(randomUppercase(100))
                        .designatingPayer(GENESIS)
                        .payingWith(payer)
                        .recordingScheduledTxn()
                        .expiringIn(lifetime)
                        .via(via)));
        return operations.toArray(SpecOperation[]::new);
    }
}
