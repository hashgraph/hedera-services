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

import static com.swirlds.platform.test.grid.GridGraphResult.ASTERISK;
import static com.swirlds.platform.test.grid.GridGraphResult.SPACE;
import static com.swirlds.platform.test.grid.GridGraphResult.TAB;
import static com.swirlds.platform.test.grid.GridGraphResult.repeatedChar;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A class which generates a TSV text grid with the results given. A header and a footer are also included.
 *
 * @param <X>
 * 		the type of the index for the x-coordinate
 * @param <Y>
 * 		the type of the index for the y-coordinate
 */
public class GridGraph<X extends Comparable<X>, Y extends Comparable<Y>>
        implements Comparable<GridGraph<X, Y>>, GridRenderer {

    /**
     * the graph name
     */
    private final String graphName;

    /**
     * the x-axis label string
     */
    private final String xAxisLabel;

    /**
     * the y-axis label string
     */
    private final String yAxisLabel;

    /**
     * the table value label string
     */
    private final String valueLabel;

    /**
     * the results set for a textual grid
     */
    private final Set<GridGraphResult<X, Y>> results;

    /**
     * the format spec for value entries
     */
    private final String formatSpec;

    /**
     * Construct a new GridGraph
     *
     * @param graphName
     * 		the name of the graph
     * @param xAxisLabel
     * 		the x-axis label
     * @param yAxisLabel
     * 		the y-axis label
     * @param valueLabel
     * 		the value label
     */
    protected GridGraph(
            final String graphName, final String xAxisLabel, final String yAxisLabel, final String valueLabel) {
        this(graphName, xAxisLabel, yAxisLabel, valueLabel, null);
    }

    /**
     * Construct a new GridGraph
     *
     * @param graphName
     * 		the name of the graph
     * @param xAxisLabel
     * 		the x-axis label
     * @param yAxisLabel
     * 		the y-axis label
     * @param valueLabel
     * 		the value label
     * @param formatSpec
     * 		the string format specifier for value entries
     */
    protected GridGraph(
            final String graphName,
            final String xAxisLabel,
            final String yAxisLabel,
            final String valueLabel,
            final String formatSpec) {
        this.graphName = graphName;
        this.xAxisLabel = xAxisLabel;
        this.yAxisLabel = yAxisLabel;
        this.valueLabel = valueLabel;
        this.results = new HashSet<>();
        this.formatSpec = formatSpec;
    }

    /**
     * Get the graph name
     *
     * @return the graph name
     */
    public String getGraphName() {
        return graphName;
    }

    /**
     * Get the x-axis name
     *
     * @return the x-axis name
     */
    public String getXAxisLabel() {
        return xAxisLabel;
    }

    /**
     * Get the y-axis name
     *
     * @return the y-axis name
     */
    public String getYAxisLabel() {
        return yAxisLabel;
    }

    /**
     * Get the value name
     *
     * @return the value name
     */
    public String getValueLabel() {
        return valueLabel;
    }

    /**
     * Insert a single value
     *
     * @param x
     * 		the x-coordinate for the value
     * @param y
     * 		the y-coordinate for the value
     * @param value
     * 		the value
     * @return true iff the value at the given coordinates was inserted (i.e., was not already present)
     */
    public boolean insertResult(final X x, final Y y, final Object value) {
        return results.add(new GridGraphResult<>(x, y, value, formatSpec));
    }

    /**
     * {@inheritDoc}
     *
     * @param writer
     */
    @Override
    public void render(final PrintWriter writer) {
        final Map<Y, Map<X, GridGraphResult<X, Y>>> matrix = createResultMatrix();
        final List<Y> yValues = matrix.keySet().stream().distinct().sorted().collect(Collectors.toList());
        final List<X> xValues = matrix.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        writer.println(repeatedChar(ASTERISK, 80));
        writer.printf("%s Graph: %s%n", ASTERISK, getGraphName());
        writer.printf("%s \tX-Axis Label: \t\t%s%n", ASTERISK, getXAxisLabel());
        writer.printf("%s \tY-Axis Label: \t\t%s%n", ASTERISK, getYAxisLabel());
        writer.printf("%s \tValue Label: \t\t%s%n", ASTERISK, getValueLabel());
        writer.println(repeatedChar(ASTERISK, 80));
        writer.println();

        int idx = 0;
        writer.print(TAB);
        for (final X header : xValues) {
            writer.printf("%-6s", header);
            writer.print((idx < xValues.size() - 1) ? TAB : System.lineSeparator());
            idx++;
        }

        for (final Y colHeader : yValues) {
            final Map<X, GridGraphResult<X, Y>> row = matrix.getOrDefault(colHeader, new HashMap<>());
            writer.print(colHeader);
            writer.print(TAB);

            if (row.isEmpty()) {
                writer.println();
                continue;
            }

            idx = 0;
            for (final X header : xValues) {
                GridGraphResult<X, Y> value = row.get(header);

                if (value != null) {
                    value.render(writer);
                } else {
                    writer.printf(formatSpec, SPACE);
                }

                writer.print((idx < xValues.size() - 1) ? TAB : System.lineSeparator());
                idx++;
            }
        }

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

        final GridGraph<?, ?> gridGraph = (GridGraph<?, ?>) o;

        return new EqualsBuilder()
                .append(getGraphName(), gridGraph.getGraphName())
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(getGraphName()).toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final GridGraph<X, Y> o) {
        if (this == o) {
            return 0;
        }

        if (o == null || getClass() != o.getClass()) {
            return 1;
        }

        return new CompareToBuilder()
                .append(this.getGraphName(), o.getGraphName())
                .build();
    }

    /**
     * Create a map of maps to represent the table values
     *
     * @return the value matrix
     */
    private Map<Y, Map<X, GridGraphResult<X, Y>>> createResultMatrix() {
        final Map<Y, Map<X, GridGraphResult<X, Y>>> matrix = new HashMap<>();

        for (final GridGraphResult<X, Y> r : results) {
            matrix.compute(r.getY(), (k, v) -> {
                if (v == null) {
                    v = new HashMap<>();
                }

                v.putIfAbsent(r.getX(), r);
                return v;
            });
        }

        return matrix;
    }
}
