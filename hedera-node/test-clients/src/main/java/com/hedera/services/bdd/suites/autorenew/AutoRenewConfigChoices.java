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

package com.hedera.services.bdd.suites.autorenew;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import java.util.Map;

public class AutoRenewConfigChoices {
    static final int DEFAULT_HIGH_TOUCH_COUNT = 10_000;
    static final int HIGH_SCAN_CYCLE_COUNT = 10_000;

    static final String LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION = "ledger.autoRenewPeriod.minDuration";
    static final String DEFAULT_MIN_AUTO_RENEW_PERIOD =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION);
    static final String AUTORENEW_GRACE_PERIOD = "autorenew.gracePeriod";
    static final String DEFAULT_GRACE_PERIOD =
            HapiSpecSetup.getDefaultNodeProps().get(AUTORENEW_GRACE_PERIOD);
    static final String AUTORENEW_NUMBER_OF_ENTITIES_TO_SCAN = "autorenew.numberOfEntitiesToScan";
    static final String DEFAULT_NUM_TO_SCAN =
            HapiSpecSetup.getDefaultNodeProps().get(AUTORENEW_NUMBER_OF_ENTITIES_TO_SCAN);
    static final String AUTO_RENEW_TARGET_TYPES = "autoRenew.targetTypes";

    public static HapiSpecOperation enableContractAutoRenewWith(final long minAutoRenewPeriod, final long gracePeriod) {
        return enableContractAutoRenewWith(
                minAutoRenewPeriod, gracePeriod,
                HIGH_SCAN_CYCLE_COUNT, DEFAULT_HIGH_TOUCH_COUNT);
    }

    public static HapiSpecOperation enableContractAutoRenewWith(
            final long minAutoRenewPeriod,
            final long gracePeriod,
            final int maxScannedPerCycle,
            final int maxTouchedPerCycle) {
        return fileUpdate(APP_PROPERTIES)
                .payingWith(GENESIS)
                .overridingProps(propsForAutoRenewOnWith(
                        minAutoRenewPeriod, gracePeriod, maxScannedPerCycle, maxTouchedPerCycle, "CONTRACT"));
    }

    public static Map<String, String> propsForAccountAutoRenewOnWith(
            final long minAutoRenewPeriod, final long gracePeriod) {
        return propsForAccountAutoRenewOnWith(
                minAutoRenewPeriod, gracePeriod, HIGH_SCAN_CYCLE_COUNT, DEFAULT_HIGH_TOUCH_COUNT);
    }

    public static Map<String, String> propsForAccountAutoRenewOnWith(
            final long minAutoRenew, final long gracePeriod, final int maxScan, final int maxTouch) {
        return propsForAutoRenewOnWith(minAutoRenew, gracePeriod, maxScan, maxTouch, "ACCOUNT");
    }

    public static Map<String, String> propsForAutoRenewOnWith(
            final long minAutoRenew,
            final long gracePeriod,
            final int maxScan,
            final int maxTouch,
            final String targetTypes) {
        return Map.of(
                LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
                "" + minAutoRenew,
                AUTO_RENEW_TARGET_TYPES,
                targetTypes,
                AUTORENEW_GRACE_PERIOD,
                "" + gracePeriod,
                AUTORENEW_NUMBER_OF_ENTITIES_TO_SCAN,
                "" + maxScan,
                "autorenew.maxNumberOfEntitiesToRenewOrDelete",
                "" + maxTouch);
    }

    static Map<String, String> leavingAutoRenewDisabledWith(long minAutoRenewPeriod) {
        return Map.of(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, "" + minAutoRenewPeriod);
    }

    public static Map<String, String> disablingAutoRenewWithDefaults() {
        return Map.of(
                LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, DEFAULT_MIN_AUTO_RENEW_PERIOD,
                AUTO_RENEW_TARGET_TYPES, "",
                AUTORENEW_GRACE_PERIOD, DEFAULT_GRACE_PERIOD,
                AUTORENEW_NUMBER_OF_ENTITIES_TO_SCAN, DEFAULT_NUM_TO_SCAN);
    }

    public static Map<String, String> disablingAutoRenewWith(long minAutoRenew) {
        return Map.of(
                LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
                "" + minAutoRenew,
                AUTO_RENEW_TARGET_TYPES,
                "",
                AUTORENEW_GRACE_PERIOD,
                DEFAULT_GRACE_PERIOD,
                AUTORENEW_NUMBER_OF_ENTITIES_TO_SCAN,
                DEFAULT_NUM_TO_SCAN);
    }
}
