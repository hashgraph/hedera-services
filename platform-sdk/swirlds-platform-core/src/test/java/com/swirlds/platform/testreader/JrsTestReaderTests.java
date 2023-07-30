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

package com.swirlds.platform.testreader;

import static com.swirlds.platform.testreader.JrsTestReader.parseTimestampFromDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.platform.util.VirtualTerminal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JrsTestReader Tests")
public class JrsTestReaderTests {

    @Test
    @DisplayName("Parse Remote Directory For Timestamp Test")
    void parseRemoteDirectoryForTimestampTest() {
        final Instant expectedTimestamp = Instant.parse("2023-06-30T05:36:33Z");

        final String dir1 = "gs://swirlds-circleci-jrs-results/swirlds-automation/develop/"
                + "4N/Basic/20230630-053633-GCP-Daily-Basic-4N/Crypto-LargeTx-50k-20m/regression.log";
        assertEquals(expectedTimestamp, parseTimestampFromDirectory(dir1));

        final String dir2 = "gs://swirlds-circleci-jrs-results/swirlds-automation/develop/"
                + "4N/Basic/20230630-053633-GCP-Daily-Basic-4N/Crypto-LargeTx-50k-20m";
        assertEquals(expectedTimestamp, parseTimestampFromDirectory(dir2));

        final String dir3 = "gs://swirlds-circleci-jrs-results/swirlds-automation/develop/"
                + "4N/Basic/20230630-053633-GCP-Daily-Basic-4N";
        assertEquals(expectedTimestamp, parseTimestampFromDirectory(dir3));

        final String dir4 =
                "gs://swirlds-circleci-jrs-results/swirlds-automation/develop/" + "4N/Basic/20230630-053633";
        assertEquals(expectedTimestamp, parseTimestampFromDirectory(dir4));

        final String dir5 = "gs://swirlds-circleci-jrs-results/swirlds-automation/develop";
        assertNull(parseTimestampFromDirectory(dir5));
    }

    @Test
    void test() {
        final VirtualTerminal terminal = new VirtualTerminal().setThrowOnError(true);

        final ExecutorService executor = Executors.newFixedThreadPool(12);

        final List<String> directories = JrsTestReader.findTestDirectories(
                terminal,
                executor,
                "gs://swirlds-circleci-jrs-results/swirlds-automation/develop/",
                Instant.now().minus(2, ChronoUnit.DAYS));

        for (final String directory : directories) {
            System.out.println("-  " + directory);
        }
    }
}
