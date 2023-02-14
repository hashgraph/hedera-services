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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertEventuallyPasses;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.Utils.asInstant;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.validators.ContractExistenceValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewContractRecordExists extends HapiSuite {
    private static final String EMPTY_CONTRACT = "EmptyConstructor";
    private static final Logger log = LogManager.getLogger(NewContractRecordExists.class);

    public static void main(String... args) {
        new NewContractRecordExists().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(newContractIsReflectedInRecordStream());
    }

    private HapiSpec newContractIsReflectedInRecordStream() {
        final var creation = "creation";
        final AtomicReference<Instant> consensusTime = new AtomicReference<>();
        return defaultHapiSpec(EMPTY_CONTRACT)
                .given(uploadInitCode(EMPTY_CONTRACT))
                .when(
                        contractCreate(EMPTY_CONTRACT).hasKnownStatus(SUCCESS).via(creation),
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
                                                new ContractExistenceValidator(
                                                        EMPTY_CONTRACT, consensusTime.get()),
                                                Duration.ofMillis(2_100))));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
