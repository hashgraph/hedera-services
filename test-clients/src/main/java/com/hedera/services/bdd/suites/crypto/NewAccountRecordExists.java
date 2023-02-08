/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static com.hedera.services.bdd.spec.HapiSpec.onlyDefaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.SigControl.SECP256K1_ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertEventuallyPasses;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordedCryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustInclude;
import static com.hedera.services.bdd.suites.contract.Utils.asInstant;

import com.hedera.services.bdd.junit.validators.AccountExistenceValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewAccountRecordExists extends HapiSuite {

    private static final Logger log = LogManager.getLogger(NewAccountRecordExists.class);

    public static void main(String... args) {
        new NewAccountRecordExists().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                newAccountIsReflectedInRecordStream(), newAccountIsReflectedInRecordStreamV2());
    }

    private HapiSpec newAccountIsReflectedInRecordStream() {
        final var balance = 1_234_567L;
        final var novelKey = "novelKey";
        final var memo = "It was the best of times";
        final var account = "novel";
        final var creation = "creation";
        final AtomicReference<Instant> consensusTime = new AtomicReference<>();
        return defaultHapiSpec("NewAccountIsReflectedInRecordStream")
                .given(newKeyNamed(novelKey).shape(SECP256K1_ON))
                .when(
                        cryptoCreate(account)
                                .key(novelKey)
                                .balance(balance)
                                .entityMemo(memo)
                                .via(creation),
                        getTxnRecord(creation)
                                .exposingTo(
                                        protoRecord ->
                                                consensusTime.set(
                                                        asInstant(
                                                                        protoRecord
                                                                                .getConsensusTimestamp())
                                                                .plusNanos(0))))
                .then(
                        sourcing(
                                () ->
                                        assertEventuallyPasses(
                                                new AccountExistenceValidator(
                                                        account, consensusTime.get()),
                                                Duration.ofMillis(2_100))));
    }

    private HapiSpec newAccountIsReflectedInRecordStreamV2() {
        final var balance = 1_234_567L;
        final var memo = "It was the best of times";
        final var account = "novel";
        return onlyDefaultHapiSpec("NewAccountIsReflectedInRecordStream")
                .given(
                        streamMustInclude(
                                recordedCryptoCreate(
                                        account, a -> a.withMemo(memo).withBalance(balance))))
                .when(cryptoCreate(account).balance(balance).memo(memo))
                .then(
                        // HapiSpec automatically waits for the streamIncludes()
                        // expectation to pass, fail, or time out
                        );
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
