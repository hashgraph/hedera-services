/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.file;

import static com.hedera.services.bdd.spec.HapiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.suites.HapiSuite.ADDRESS_BOOK;
import static com.hedera.services.bdd.suites.HapiSuite.API_PERMISSIONS;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.EXCHANGE_RATES;
import static com.hedera.services.bdd.suites.HapiSuite.FEE_SCHEDULE;
import static com.hedera.services.bdd.suites.HapiSuite.NODE_DETAILS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class FetchSystemFiles {
    /**
     * Fetches the system files from a running network and validates they can be
     * parsed as the expected protobuf messages. */
    @HapiTest
    final Stream<DynamicTest> fetchFiles() {
        return customHapiSpec("FetchFiles")
                .withProperties(Map.of(
                        "fees.useFixedOffer", "true",
                        "fees.fixedOffer", "100000000"))
                .given()
                .when()
                .then(
                        getFileContents(NODE_DETAILS).andValidate(unchecked(NodeAddressBook::parseFrom)::apply),
                        getFileContents(ADDRESS_BOOK).andValidate(unchecked(NodeAddressBook::parseFrom)::apply),
                        getFileContents(EXCHANGE_RATES).andValidate(unchecked(ExchangeRateSet::parseFrom)::apply),
                        getFileContents(APP_PROPERTIES)
                                .andValidate(unchecked(ServicesConfigurationList::parseFrom)::apply),
                        getFileContents(API_PERMISSIONS)
                                .andValidate(unchecked(ServicesConfigurationList::parseFrom)::apply),
                        getFileContents(FEE_SCHEDULE)
                                .fee(300_000L)
                                .nodePayment(40L)
                                .andValidate(unchecked(CurrentAndNextFeeSchedule::parseFrom)::apply));
    }

    @FunctionalInterface
    public interface CheckedParser {
        Object parseFrom(byte[] bytes) throws Exception;
    }

    static Function<byte[], String> unchecked(CheckedParser parser) {
        return bytes -> {
            try {
                return parser.parseFrom(bytes).toString();
            } catch (Exception e) {
                e.printStackTrace();
                return "<N/A> due to " + e.getMessage() + "!";
            }
        };
    }
}
