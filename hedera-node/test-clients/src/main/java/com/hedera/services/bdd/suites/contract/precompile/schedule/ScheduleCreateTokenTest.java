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

package com.hedera.services.bdd.suites.contract.precompile.schedule;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.OrderedInIsolation;
import com.hedera.services.bdd.spec.dsl.annotations.Account;
import com.hedera.services.bdd.spec.dsl.annotations.Contract;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

@OrderedInIsolation
public class ScheduleCreateTokenTest {

    @Contract(contract = "HIP756Contract", creationGas = 4_000_000L)
    static SpecContract contract;

    @Account
    static SpecAccount treasury;

    @Account
    static SpecAccount autoRenew;

    @HapiTest
    @DisplayName("Can successfully schedule a create fungible token operation")
    public Stream<DynamicTest> scheduledCreateToken() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var scheduleAddress = new AtomicReference<Address>();
            allRunFor(
                    spec,
                    contract.call("scheduleCreateFT", autoRenew, treasury)
                            .gas(1_000_000L)
                            .exposingResultTo(res -> scheduleAddress.set((Address) res[1])));
            final var scheduleID = ConversionUtils.asScheduleId(scheduleAddress.get());
            spec.registry().saveScheduleId("scheduledCreateFT", scheduleID);
            allRunFor(spec, getScheduleInfo("scheduledCreateFT").hasScheduleId("scheduledCreateFT"));
        }));
    }

    @HapiTest
    @DisplayName("Can successfully schedule a create non fungible token operation")
    public Stream<DynamicTest> scheduledCreateNonFungibleToken() {
        return hapiTest(withOpContext((spec, opLog) -> {
            final var scheduleAddress = new AtomicReference<Address>();
            allRunFor(
                    spec,
                    contract.call("scheduleCreateNFT", autoRenew, treasury)
                            .gas(1_000_000L)
                            .exposingResultTo(res -> scheduleAddress.set((Address) res[1])));
            final var scheduleID = ConversionUtils.asScheduleId(scheduleAddress.get());
            spec.registry().saveScheduleId("scheduledCreateNFT", scheduleID);
            allRunFor(spec, getScheduleInfo("scheduledCreateNFT").hasScheduleId("scheduledCreateNFT"));
        }));
    }
}
