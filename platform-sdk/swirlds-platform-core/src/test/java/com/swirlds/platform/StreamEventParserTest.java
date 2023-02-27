/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.system.events.Event;
import java.io.File;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamEventParserTest {
    static {
        System.setProperty("log4j.configurationFile", "log4j2-test.xml");
    }

    // should be true because settings is not loaded
    private static final boolean populateSettingsCommon = true;
    private static int eventCount = 0;
    private static final String PARSE_FAIL_MSG = "Failed to parse the file";

    @BeforeAll
    static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.common.system.transaction");
        Settings.populateSettingsCommon();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // Event file version 5 with new Transaction class
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_27_00.004964000Z.evts",
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_26_00.021395000Z.evts",
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_25_00.051650000Z.evts",
                "src/test/resources/eventFiles/v5withTransactionV1/2021-04-18T04_24_00.033462000Z.evts",
            })
    void parseEvent(final String fileName) {
        eventCount = 0;
        final boolean result = StreamEventParser.parseEventStreamFile(
                new File(fileName), StreamEventParserTest::eventHandler, populateSettingsCommon);
        assertTrue(result, PARSE_FAIL_MSG);
        assertTrue(eventCount > 0, PARSE_FAIL_MSG);
    }

    private static boolean eventHandler(final Event event) {
        // System.out.println(event);
        eventCount++;
        return true;
    }
}
