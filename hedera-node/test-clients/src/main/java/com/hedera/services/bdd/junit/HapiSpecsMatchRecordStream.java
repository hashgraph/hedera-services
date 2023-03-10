/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.services.bdd.junit;

import static com.hedera.services.bdd.spec.HapiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.onlyDefaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.awaitStreamAssertions;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordedCryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustInclude;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.spec.utilops.streams.assertions.RecordStreamAssertion;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HapiSpecsMatchRecordStream extends HapiSuite {
    private static final Logger log = LogManager.getLogger(HapiSpecsMatchRecordStream.class);

    List<HapiSpecOperation> hapiSpecSequence;
    public static void main(String... args) {
        HapiSpecOperation op = new HapiCryptoCreate("accountName");
        new HapiSpecsMatchRecordStream(List.of(op)).runSuiteSync();
    }

    public HapiSpecsMatchRecordStream(final List<HapiSpecOperation> hapiSpecSequence) {
        this.hapiSpecSequence = hapiSpecSequence;
    }
    @Override
    public List<HapiSpec> getSpecsInSuite() {
        List<HapiSpec> specs = new ArrayList<>();
        specs.add(assertFailureWhenOperationIsNotReflectedInRecordStream());
        specs.addAll(hapiSpecSequence.stream()
                .map(this::assertOperationIsReflectedInRecordStream)
                .toList());
        return specs;
    }

    /**
     * Take a HapiSpecOperation and create a validator that will check that the operation is reflected in the record stream.
     * @param hapiSpecOp a spec operation
     * @return a HapiSpec that validates that the operation is reflected in the record stream
     */
    private HapiSpec assertOperationIsReflectedInRecordStream(HapiSpecOperation hapiSpecOp) {
        Function<HapiSpec, RecordStreamAssertion> streamMustIncludeVerb;

        switch (hapiSpecOp.type()) {
            case CryptoCreate:
                HapiCryptoCreate cryptoCreate = (HapiCryptoCreate) hapiSpecOp;
                streamMustIncludeVerb = recordedCryptoCreate(cryptoCreate.getAccount());
                break;
                // TODO: add more cases for other operations
            default:
                throw new IllegalArgumentException("Unsupported operation type: " + hapiSpecOp.type());
        }
        return onlyDefaultHapiSpec("CryptoCreateIsReflectedInRecordStream")
                .given(streamMustInclude(streamMustIncludeVerb))
                .when(hapiSpecOp)
                .then(awaitStreamAssertions());
    }

    /**
     * Check that a CryptoCreate operation is not reflected in the record stream when no operation is executed.
     * @return a HapiSpec that validates that the operation is not reflected in the record stream
     */
    private HapiSpec assertFailureWhenOperationIsNotReflectedInRecordStream() {
        return defaultFailingHapiSpec("CryptoCreateIsNotReflectedInRecordStreamWhenNotSent")// expect failure
                .given(streamMustInclude(recordedCryptoCreate("accountName")))    // set up to check if the operation is reflected in the record stream
                .when(UtilVerbs.noOp())     // don't execute the operation
                .then(awaitStreamAssertions()); // assert that the operation is reflected in the record stream (this should fail)
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}

