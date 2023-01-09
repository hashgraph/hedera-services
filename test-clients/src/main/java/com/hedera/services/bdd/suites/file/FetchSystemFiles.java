/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FetchSystemFiles extends HapiSuite {
    private static final Logger log = LogManager.getLogger(FetchSystemFiles.class);

    public static void main(String... args) {
        new FetchSystemFiles().runSuiteSync();
    }

    private static final String TARGET_DIR = "./remote-system-files";

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(fetchFiles());
    }

    /** Fetches the system files from a running network and saves them to the local file system. */
    private HapiSpec fetchFiles() {
        return customHapiSpec("FetchFiles")
                .withProperties(
                        Map.of(
                                "fees.useFixedOffer", "true",
                                "fees.fixedOffer", "100000000"))
                .given()
                .when()
                .then(
                        getFileContents(NODE_DETAILS)
                                .saveTo(path("nodeDetails.bin"))
                                .saveReadableTo(
                                        unchecked(NodeAddressBook::parseFrom),
                                        path("nodeDetails.txt")),
                        getFileContents(ADDRESS_BOOK)
                                .saveTo(path("addressBook.bin"))
                                .saveReadableTo(
                                        unchecked(NodeAddressBook::parseFrom),
                                        path("addressBook.txt")),
                        getFileContents(NODE_DETAILS)
                                .saveTo(path("nodeDetails.bin"))
                                .saveReadableTo(
                                        unchecked(NodeAddressBook::parseFrom),
                                        path("nodeDetails.txt")),
                        getFileContents(EXCHANGE_RATES)
                                .saveTo(path("exchangeRates.bin"))
                                .saveReadableTo(
                                        unchecked(ExchangeRateSet::parseFrom),
                                        path("exchangeRates.txt")),
                        getFileContents(APP_PROPERTIES)
                                .saveTo(path("appProperties.bin"))
                                .saveReadableTo(
                                        unchecked(ServicesConfigurationList::parseFrom),
                                        path("appProperties.txt")),
                        getFileContents(API_PERMISSIONS)
                                .saveTo(path("apiPermissions.bin"))
                                .saveReadableTo(
                                        unchecked(ServicesConfigurationList::parseFrom),
                                        path("appPermissions.txt")),
                        getFileContents(FEE_SCHEDULE)
                                .saveTo(path("feeSchedule.bin"))
                                .fee(300_000L)
                                .nodePayment(40L)
                                .saveReadableTo(
                                        unchecked(CurrentAndNextFeeSchedule::parseFrom),
                                        path("feeSchedule.txt")));
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

    private String path(String file) {
        return Path.of(TARGET_DIR, file).toString();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
