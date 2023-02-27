/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.platform.dispatch.DispatchBuilder;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.dispatch.flowchart.DispatchFlowchart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dispatch Flowchart Tests")
class DispatchFlowchartTests {

    private final class Class1 {}

    private final class Class2 {}

    private final class Trigger1 {}

    private final class Trigger2 {}

    @Test
    @DisplayName("Linkage Test")
    void linkageTest() {
        final DispatchFlowchart flowchart = new DispatchFlowchart(new DispatchConfiguration(true, "", "", "", ""));

        flowchart.registerDispatcher(Class1.class, Trigger1.class, null);

        flowchart.registerObserver(Class2.class, Trigger1.class, null);

        final String data = flowchart.buildFlowchart();

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");
        assertFalse(data.contains("    Trigger2{{Trigger2}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("    Class1 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class1 --> Trigger2\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger2\n"), "data lacking expected line");

        // Links from triggers to observers
        assertFalse(data.contains("Trigger1 -.-> Class1\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger1 -.-> Class2\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class2\n"), "    data lacking expected line");
    }

    @Test
    @DisplayName("Linkage With Comments Test")
    void linkageWithCommentsTest() {

        final DispatchFlowchart flowchart = new DispatchFlowchart(new DispatchConfiguration(true, "", "", "", ""));

        final String dispatchComment = "this is a comment";
        flowchart.registerDispatcher(Class1.class, Trigger1.class, dispatchComment);

        final String observerComment = "this is another comment";
        flowchart.registerObserver(Class2.class, Trigger1.class, observerComment);

        final String data = flowchart.buildFlowchart();

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("flowchart TD\n"), "    Class1 -- \"this is a comment\" --> Trigger1\n");

        // Links from triggers to observers
        assertTrue(
                data.contains("    Trigger1 -. \"this is another comment\" .-> Class2\n"),
                "data lacking expected line");
    }

    @Test
    @DisplayName("Multi-Linkage Test")
    void multiLinkageTest() {
        final DispatchFlowchart flowchart = new DispatchFlowchart(new DispatchConfiguration(true, "", "", "", ""));

        flowchart.registerDispatcher(Class1.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class1.class, Trigger2.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger2.class, null);

        flowchart.registerObserver(Class1.class, Trigger1.class, null);
        flowchart.registerObserver(Class2.class, Trigger1.class, null);
        flowchart.registerObserver(Class1.class, Trigger2.class, null);
        flowchart.registerObserver(Class2.class, Trigger2.class, null);

        final String data = flowchart.buildFlowchart();

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");
        assertTrue(data.contains("    Trigger2{{Trigger2}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("    Class1 --> Trigger1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class1 --> Trigger2\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2 --> Trigger1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2 --> Trigger2\n"), "data lacking expected line");

        // Links from triggers to observers
        assertTrue(data.contains("Trigger1 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger2 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger1 -.-> Class2\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger2 -.-> Class2\n"), "    data lacking expected line");
    }

    @Test
    @DisplayName("Whitelist Trigger Test")
    void whitelistTriggerTest() {

        final DispatchFlowchart flowchart =
                new DispatchFlowchart(new DispatchConfiguration(true, "Trigger1", "", "", ""));

        flowchart.registerDispatcher(Class1.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class1.class, Trigger2.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger2.class, null);

        flowchart.registerObserver(Class1.class, Trigger1.class, null);
        flowchart.registerObserver(Class2.class, Trigger1.class, null);
        flowchart.registerObserver(Class1.class, Trigger2.class, null);
        flowchart.registerObserver(Class2.class, Trigger2.class, null);

        final String data = flowchart.buildFlowchart();
        System.out.println(data);

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");
        assertFalse(data.contains("    Trigger2{{Trigger2}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("    Class1 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class1 --> Trigger2\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger2\n"), "data lacking expected line");

        // Links from triggers to observers
        assertTrue(data.contains("Trigger1 -.-> Class1\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger1 -.-> Class2\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class2\n"), "    data lacking expected line");
    }

    @Test
    @DisplayName("Whitelist Trigger Test")
    void blacklistTriggerTest() {

        final DispatchFlowchart flowchart =
                new DispatchFlowchart(new DispatchConfiguration(true, "", "Trigger2", "", ""));

        flowchart.registerDispatcher(Class1.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class1.class, Trigger2.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger2.class, null);

        flowchart.registerObserver(Class1.class, Trigger1.class, null);
        flowchart.registerObserver(Class2.class, Trigger1.class, null);
        flowchart.registerObserver(Class1.class, Trigger2.class, null);
        flowchart.registerObserver(Class2.class, Trigger2.class, null);

        final String data = flowchart.buildFlowchart();

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");
        assertFalse(data.contains("    Trigger2{{Trigger2}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("    Class1 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class1 --> Trigger2\n"), "data lacking expected line");
        assertTrue(data.contains("    Class2 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger2\n"), "data lacking expected line");

        // Links from triggers to observers
        assertTrue(data.contains("Trigger1 -.-> Class1\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger1 -.-> Class2\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class2\n"), "    data lacking expected line");
    }

    @Test
    @DisplayName("Whitelist Object Test")
    void whitelistObjectTest() {

        final DispatchFlowchart flowchart =
                new DispatchFlowchart(new DispatchConfiguration(true, "", "", "Class1", ""));

        flowchart.registerDispatcher(Class1.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class1.class, Trigger2.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger2.class, null);

        flowchart.registerObserver(Class1.class, Trigger1.class, null);
        flowchart.registerObserver(Class2.class, Trigger1.class, null);
        flowchart.registerObserver(Class1.class, Trigger2.class, null);
        flowchart.registerObserver(Class2.class, Trigger2.class, null);

        final String data = flowchart.buildFlowchart();

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");
        assertTrue(data.contains("    Trigger2{{Trigger2}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("    Class1 --> Trigger1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class1 --> Trigger2\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger2\n"), "data lacking expected line");

        // Links from triggers to observers
        assertTrue(data.contains("Trigger1 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger2 -.-> Class1\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger1 -.-> Class2\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class2\n"), "    data lacking expected line");
    }

    @Test
    @DisplayName("Blacklist Object Test")
    void blacklistObjectTest() {

        final DispatchFlowchart flowchart =
                new DispatchFlowchart(new DispatchConfiguration(true, "", "", "", "Class2"));

        flowchart.registerDispatcher(Class1.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class1.class, Trigger2.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger1.class, null);
        flowchart.registerDispatcher(Class2.class, Trigger2.class, null);

        flowchart.registerObserver(Class1.class, Trigger1.class, null);
        flowchart.registerObserver(Class2.class, Trigger1.class, null);
        flowchart.registerObserver(Class1.class, Trigger2.class, null);
        flowchart.registerObserver(Class2.class, Trigger2.class, null);

        final String data = flowchart.buildFlowchart();

        // Header
        assertTrue(data.contains("flowchart TD\n"), "data lacking expected line");

        // Class definitions
        assertTrue(data.contains("    Class1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2\n"), "data lacking expected line");

        // Trigger definitions
        assertTrue(data.contains("    Trigger1{{Trigger1}}\n"), "data lacking expected line");
        assertTrue(data.contains("    Trigger2{{Trigger2}}\n"), "data lacking expected line");

        // Links from dispatchers to triggers
        assertTrue(data.contains("    Class1 --> Trigger1\n"), "data lacking expected line");
        assertTrue(data.contains("    Class1 --> Trigger2\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger1\n"), "data lacking expected line");
        assertFalse(data.contains("    Class2 --> Trigger2\n"), "data lacking expected line");

        // Links from triggers to observers
        assertTrue(data.contains("Trigger1 -.-> Class1\n"), "    data lacking expected line");
        assertTrue(data.contains("Trigger2 -.-> Class1\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger1 -.-> Class2\n"), "    data lacking expected line");
        assertFalse(data.contains("Trigger2 -.-> Class2\n"), "    data lacking expected line");
    }

    @Test
    @DisplayName("Catch Illegal Object Whitelist & Blacklist")
    void catchIllegalObjectWhitelistAndBlacklistTest() {
        // It's illegal to define a simultaneous whitelist and blacklist
        assertThrows(
                IllegalStateException.class,
                () -> new DispatchBuilder(new DispatchConfiguration(true, "", "", "Class1", "Class2")),
                "should be unable to construct flowchart with given configuration");
    }

    @Test
    @DisplayName("Catch Illegal Trigger Whitelist & Blacklist")
    void catchTriggerObjectWhitelistAndBlacklistTest() {
        // It's illegal to define a simultaneous whitelist and blacklist
        assertThrows(
                IllegalStateException.class,
                () -> new DispatchBuilder(new DispatchConfiguration(true, "Trigger1", "Trigger2", "", "")),
                "should be unable to construct flowchart with given configuration");
    }
}
