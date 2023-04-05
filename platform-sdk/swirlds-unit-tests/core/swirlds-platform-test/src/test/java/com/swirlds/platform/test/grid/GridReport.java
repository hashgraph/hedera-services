/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.grid;

import static com.swirlds.platform.test.grid.GridGraphResult.GREATER_THAN;
import static com.swirlds.platform.test.grid.GridGraphResult.LESS_THAN;
import static com.swirlds.platform.test.grid.GridGraphResult.repeatedChar;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * A type which emits a string to stdout that displays a grid of test results. A header and a footer for the results
 * table is also printed.
 */
public class GridReport implements GridRenderer {

    /**
     * The name of the test panel
     */
    private final String panelName;

    /**
     * The tests for which results are to be printed
     */
    private final Map<String, GridTest<? extends Comparable<?>, ? extends Comparable<?>>> tests;

    /**
     * Construct a grid report for a given test panel
     *
     * @param panelName
     * 		the panel name
     */
    public GridReport(final String panelName) {
        this.panelName = panelName;
        this.tests = new HashMap<>();
    }

    /**
     * Get the name of the test panel
     *
     * @return the panel name string
     */
    public String getPanelName() {
        return panelName;
    }

    /**
     * Get the GridTest object for the given test name, if it exists, else create a new one and return it.
     *
     * @param testName
     * 		the name of the test
     * @param <X>
     * 		the type of the x-coordinate
     * @param <Y>
     * 		the type of the y-coordinate
     * @return the GridTest instance for the given test name
     */
    @SuppressWarnings("unchecked")
    public <X extends Comparable<X>, Y extends Comparable<Y>> GridTest<X, Y> testFor(final String testName) {
        return (GridTest<X, Y>) tests.computeIfAbsent(testName, GridTest::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(final PrintWriter writer) {
        writer.println(repeatedChar(GREATER_THAN, 120));
        writer.printf("%s Begin Panel Report: %s%n", repeatedChar(GREATER_THAN, 3), getPanelName());
        writer.println(repeatedChar(LESS_THAN, 120));

        for (final GridTest<?, ?> test : tests.values()) {
            test.render(writer);
        }

        writer.println(repeatedChar(GREATER_THAN, 120));
        writer.printf("%s End Panel Report: %s%n", repeatedChar(GREATER_THAN, 3), getPanelName());
        writer.println(repeatedChar(LESS_THAN, 120));
    }
}
