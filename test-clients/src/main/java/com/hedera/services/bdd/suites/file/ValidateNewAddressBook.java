/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.suites.file.FetchSystemFiles.unchecked;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes.SYS_FILE_SERDES;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidateNewAddressBook extends HapiSuite {
    private static final Logger log = LogManager.getLogger(ValidateNewAddressBook.class);

    public static void main(String... args) {
        new ValidateNewAddressBook().runSuiteSync();
    }

    final String TARGET_DIR = "./remote-system-files";

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(fetchFiles());
    }

    private HapiSpec fetchFiles() {
        return defaultHapiSpec("ValidateNewAddressBook")
                .given()
                .when()
                .then(
                        getFileContents(NODE_DETAILS)
                                .saveTo(path("nodeDetails.bin"))
                                .saveReadableTo(
                                        unchecked(NodeAddressBook::parseFrom),
                                        path("nodeDetails.json")),
                        getFileContents(ADDRESS_BOOK)
                                .saveTo(path("addressBook.bin"))
                                .saveReadableTo(
                                        unchecked(NodeAddressBook::parseFrom),
                                        path("addressBook.txt")),
                        getFileContents(NODE_DETAILS)
                                .saveTo(path("nodeDetails.bin"))
                                .saveReadableTo(
                                        SYS_FILE_SERDES.get(102L)::fromRawFile,
                                        path("nodeDetails.json")));
    }

    private String path(String file) {
        return Path.of(TARGET_DIR, file).toString();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
