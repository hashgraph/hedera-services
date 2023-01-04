/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAutoCreatedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.propsForAccountAutoRenewOnWith;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.updateSpecFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AutoAccountUpdateSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(AutoAccountUpdateSuite.class);
    public static final long INITIAL_BALANCE = 1000L;

    private static final String PAYER = "payer";
    private static final String ALIAS = "testAlias";
    private static final String TRANSFER_TXN = "transferTxn";
    public static final String TRANSFER_TXN_2 = "transferTxn2";
    private static final String TRANSFER_TXN_3 = "transferTxn3";

    public static void main(String... args) {
        new AutoAccountUpdateSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                updateKeyOnAutoCreatedAccount(),
                accountCreatedAfterAliasAccountExpires(),
                modifySigRequiredAfterAutoAccountCreation());
    }

    private HapiSpec modifySigRequiredAfterAutoAccountCreation() {
        return defaultHapiSpec("modifySigRequiredAfterAutoAccountCreation")
                .given(newKeyNamed(ALIAS), cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ALIAS)),
                        /* validate child record has no alias set and has fields as expected */
                        getTxnRecord(TRANSFER_TXN)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(1)
                                .hasNoAliasInChildRecord(0)
                                .logged(),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .autoRenew(THREE_MONTHS_IN_SECONDS)
                                                .receiverSigReq(false)
                                                .expectedBalanceWithChargedUsd(
                                                        (ONE_HUNDRED_HBARS), 0, 0)))
                .then(
                        /* change receiverSigRequired to false and validate */
                        cryptoUpdateAliased(ALIAS)
                                .receiverSigRequired(true)
                                .signedBy(ALIAS, PAYER, DEFAULT_PAYER),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .autoRenew(THREE_MONTHS_IN_SECONDS)
                                                .receiverSigReq(true)
                                                .expectedBalanceWithChargedUsd(
                                                        (ONE_HUNDRED_HBARS), 0, 0)),

                        /* transfer without receiver sig fails */
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN_2)
                                .signedBy(PAYER, DEFAULT_PAYER)
                                .hasKnownStatus(INVALID_SIGNATURE),

                        /* transfer with receiver sig passes */
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN_3)
                                .signedBy(ALIAS, PAYER, DEFAULT_PAYER),
                        getTxnRecord(TRANSFER_TXN_3)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(0),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        (2 * ONE_HUNDRED_HBARS), 0, 0)));
    }

    private HapiSpec accountCreatedAfterAliasAccountExpires() {
        final var briefAutoRenew = 3L;
        return defaultHapiSpec("AccountCreatedAfterAliasAccountExpires")
                .given(
                        newKeyNamed(ALIAS),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        propsForAccountAutoRenewOnWith(
                                                briefAutoRenew, 20 * briefAutoRenew)),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        /* auto account is created */
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ALIAS)),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .autoRenew(THREE_MONTHS_IN_SECONDS)
                                                .expectedBalanceWithChargedUsd(
                                                        (ONE_HUNDRED_HBARS), 0, 0)))
                .then(
                        /* update auto renew period */
                        cryptoUpdateAliased(ALIAS)
                                .autoRenewPeriod(briefAutoRenew)
                                .signedBy(ALIAS, PAYER, DEFAULT_PAYER),
                        sleepFor(2 * briefAutoRenew * 1_000L + 500L),
                        getAutoCreatedAccountBalance(ALIAS),

                        /* account is expired but not deleted and validate the transfer succeeds*/
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .via(TRANSFER_TXN_2),
                        getTxnRecord(TRANSFER_TXN_2)
                                .andAllChildRecords()
                                .hasNonStakingChildRecordCount(0),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        (2 * ONE_HUNDRED_HBARS), 0, 0)),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(disablingAutoRenewWithDefaults()));
    }

    private HapiSpec updateKeyOnAutoCreatedAccount() {
        final var complexKey = "complexKey";

        SigControl ENOUGH_UNIQUE_SIGS =
                KeyShape.threshSigs(
                        2,
                        KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
                        KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));

        return defaultHapiSpec("updateKeyOnAutoCreatedAccount")
                .given(
                        newKeyNamed(ALIAS),
                        newKeyNamed(complexKey).shape(ENOUGH_UNIQUE_SIGS),
                        cryptoCreate(PAYER).balance(INITIAL_BALANCE * ONE_HBAR))
                .when(
                        /* auto account is created */
                        cryptoTransfer(tinyBarsFromToWithAlias(PAYER, ALIAS, ONE_HUNDRED_HBARS))
                                .payingWith(PAYER)
                                .via(TRANSFER_TXN),
                        withOpContext((spec, opLog) -> updateSpecFor(spec, ALIAS)),
                        getTxnRecord(TRANSFER_TXN).andAllChildRecords().logged(),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        ONE_HUNDRED_HBARS, 0, 0)
                                                .alias(ALIAS)))
                .then(
                        /* validate the key on account can be updated to complex key, and has no relation to alias*/
                        cryptoUpdateAliased(ALIAS)
                                .key(complexKey)
                                .payingWith(PAYER)
                                .signedBy(ALIAS, complexKey, PAYER, DEFAULT_PAYER),
                        getAliasedAccountInfo(ALIAS)
                                .has(
                                        accountWith()
                                                .expectedBalanceWithChargedUsd(
                                                        (ONE_HUNDRED_HBARS), 0, 0)
                                                .key(complexKey)));
    }
}
