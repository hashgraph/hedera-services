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

import static com.swirlds.platform.test.grid.GridGraphResult.DASH;
import static com.swirlds.platform.test.grid.GridGraphResult.PIPE;
import static com.swirlds.platform.test.grid.GridGraphResult.repeatedChar;

import java.io.PrintWriter;
import java.util.HashMap;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A type which holds and renders the results of a single test
 *
 * @param <X>
 *     the type of the x-coordinate value
 * @param <Y>
 *     the type of the y-coordinate value
 */
public class GridTest<X extends Comparable<X>, Y extends Comparable<Y>>
        implements Comparable<GridTest<X, Y>>, GridRenderer {

    /**
     * The test name string
     */
    private final String testName;

    /**
     * GridGraph to collect, keyed by the graph name
     */
    private final HashMap<String, GridGraph<X, Y>> graphs;

    /**
     * Construct a grid test from a test name
     *
     * @param testName
     * 		the test name
     */
    protected GridTest(final String testName) {
        this.testName = testName;
        this.graphs = new HashMap<>();
    }

    /**
     * Get the name of the test name
     *
     * @return the test name string
     */
    public String getTestName() {
        return testName;
    }

    /**
     * Get the GridGraph for the given graph name if it exists, else create one.
     *
     * @param graphName
     * 		the name of the graph (typically the name of the property to be displayed)
     * @param xAxisLabel
     * 		the x-axis label
     * @param yAxisLabel
     * 		the y-axis label
     * @param valueLabel
     * 		the name of the value
     * @return a GridGraph
     */
    public GridGraph<X, Y> graphFor(
            final String graphName, final String xAxisLabel, final String yAxisLabel, final String valueLabel) {
        return graphs.computeIfAbsent(graphName, (n) -> new GridGraph<>(n, xAxisLabel, yAxisLabel, valueLabel));
    }

    /**
     * Get the TimingGridGraph for the given graph name if it exists, else create one.
     *
     * @param graphName
     * 		the name of the graph (typically the name of the property to be displayed)
     * @param xAxisLabel
     * 		the x-axis label
     * @param yAxisLabel
     * 		the y-axis label
     * @return a TimingGridGraph
     */
    public TimingGridGraph<X, Y> timingGraphFor(
            final String graphName, final String xAxisLabel, final String yAxisLabel) {
        return (TimingGridGraph<X, Y>)
                graphs.computeIfAbsent(graphName, (n) -> new TimingGridGraph<>(n, xAxisLabel, yAxisLabel));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void render(final PrintWriter writer) {
        writer.println(repeatedChar(DASH, 120));
        writer.printf("%s Begin Test: %s%n", PIPE, getTestName());
        writer.println(repeatedChar(DASH, 120));
        writer.println();
        writer.flush();

        for (final GridGraph<X, Y> graph : graphs.values()) {
            graph.render(writer);
        }

        writer.println();
        writer.println(repeatedChar(DASH, 120));
        writer.printf("%s End Test: %s%n", PIPE, getTestName());
        writer.println(repeatedChar(DASH, 120));
        writer.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final GridTest<?, ?> gridTest = (GridTest<?, ?>) o;

        return new EqualsBuilder().append(getTestName(), gridTest.getTestName()).isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(getTestName()).toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final GridTest<X, Y> o) {
        if (this == o) {
            return 0;
        }

        if (o == null || getClass() != o.getClass()) {
            return 1;
        }

        return new CompareToBuilder()
                .append(this.getTestName(), o.getTestName())
                .build();
    }
}
