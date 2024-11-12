/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.streams;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.streams.StreamValidationOp.readMaybeRecordStreamDataFor;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;

import com.hedera.services.bdd.junit.support.validators.ContractSelfAdminKeyValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Assertions;

/**
 * A {@link UtilOp} that validates that the selected nodes' application or platform log contains or
 * does not contain a given pattern.
 */
public class ContractSelfAdminKeyValidationOp extends UtilOp {
    private static final long BUFFER_MS = 500L;
    private final long newContractNum;

    public ContractSelfAdminKeyValidationOp(final long newContractNum) {
        this.newContractNum = newContractNum;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        // Freeze the network
        allRunFor(
                spec,
                freezeOnly().payingWith(GENESIS).startingIn(2).seconds(),
                // Wait for the final stream files to be created
                sleepFor(10 * BUFFER_MS));
        readMaybeRecordStreamDataFor(spec)
                .ifPresentOrElse(
                        data -> new ContractSelfAdminKeyValidator(newContractNum)
                                .validationErrorsIn(data)
                                .map(Throwable::getMessage)
                                .forEach(Assertions::fail),
                        () -> Assertions.fail("No record stream data found"));
        return false;
    }
}
