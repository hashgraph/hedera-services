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

import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;

/**
 * A type which stores and generates textual report grid of timing values.
 *
 * @param <X>
 * 		the type of the x-coordinate value
 * @param <Y>
 * 		the type of the y-coordinate value
 */
public class TimingGridGraph<X extends Comparable<X>, Y extends Comparable<Y>> extends GridGraph<X, Y> {

    /**
     * The default format specifier for value entries
     */
    public static final String DEFAULT_FORMAT_SPEC = "%-6s";

    /**
     * A stop watch used for timing the generation of an entire grid
     */
    private final StopWatch watch;

    /**
     * The x-coordinate of the value current being generated
     */
    private X x;

    /**
     * The y-coordinate of the value current being generated
     */
    private Y y;

    /**
     * Construct a TimingGridGraph from a test panel name and axis label strings. The values are string-ized
     * using the default format specifier
     *
     * @param graphName
     * 		the test panel name
     * @param xAxisLabel
     * 		the x-axis label
     * @param yAxisLabel
     * 		the y-axis label
     */
    protected TimingGridGraph(final String graphName, final String xAxisLabel, final String yAxisLabel) {
        this(graphName, xAxisLabel, yAxisLabel, DEFAULT_FORMAT_SPEC);
    }

    /**
     * Construct a TimingGridGraph from a test panel name and axis label strings. The values are string-ized
     * using the default format specifier
     *
     * @param graphName
     * 		the test panel name
     * @param xAxisLabel
     * 		the x-axis label
     * @param yAxisLabel
     * 		the y-axis label
     * @param formatSpec
     * 		the format specifier, applied to every grid value
     */
    protected TimingGridGraph(
            final String graphName, final String xAxisLabel, final String yAxisLabel, final String formatSpec) {
        super(graphName, xAxisLabel, yAxisLabel, "Time (ms)", formatSpec);
        this.watch = new StopWatch();
    }

    /**
     * Construct a new Promise instance for one value entry, which will hold a timing value when either its `stop` or
     * `close` method is called.
     *
     * @param x
     * 		the x-coordinate of the value current being generated
     * @param y
     * 		the y-coordinate of the value current being generated
     * @return a new Promise object
     */
    public Promise<X, Y> defer(final X x, final Y y) {
        return new Promise<>(this, x, y);
    }

    /**
     * Start the timer at the current grid coordinates
     *
     * @param x
     * 		the x-coordinate of the value current being generated
     * @param y
     * 		the y-coordinate of the value current being generated
     */
    public void start(final X x, final Y y) {
        if (!watch.isStopped()) {
            clear();
        }

        this.x = x;
        this.y = y;
        watch.start();
    }

    /**
     * Stop the timer for the most recently start coordinates
     */
    public void stop() {
        if (!watch.isStarted()) {
            return;
        }

        watch.stop();
        insertResult(x, y, watch.getTime(TimeUnit.MILLISECONDS));
        clear();
    }

    /**
     * Reset the stopwatch, and set the value coordinates to `null`
     */
    public void clear() {
        watch.reset();
        x = null;
        y = null;
    }

    /**
     * A type which represents a future time duration value, in milliseconds.
     *
     * @param <X>
     * 		the type of the x-coordinate value
     * @param <Y>
     * 		the type of the y-coordinate value
     */
    public static class Promise<X extends Comparable<X>, Y extends Comparable<Y>> implements AutoCloseable {
        /**
         * The grid graph to be uppdated when the timing value is available
         */
        private final TimingGridGraph<X, Y> parent;

        /**
         * The x-coordinate of the value current being generated
         */
        private final X x;

        /**
         * The y-coordinate of the value current being generated
         */
        private final Y y;

        /**
         * The stopwatch used to get the data point for the grid coordinates
         */
        private final StopWatch watch;

        /**
         * Are we currently started, and waiting for a call to {@code stop} or {@code close}?
         */
        private boolean capturing;

        /**
         * Construct a promise for specific values coordinates in a grid graph
         *
         * @param parent
         * 		the grid graph to update
         * @param x
         * 		the x-coordinate of the value current being generated
         * @param y
         * 		the y-coordinate of the value current being generated
         */
        protected Promise(final TimingGridGraph<X, Y> parent, final X x, final Y y) {
            this.parent = parent;
            this.x = x;
            this.y = y;
            this.watch = new StopWatch();
            this.capturing = false;
        }

        /**
         * Start the stopwatch
         */
        public void start() {
            if (!watch.isStopped()) {
                clear();
            }

            capturing = true;
            watch.start();
        }

        /**
         * Stop the stopwatch. The reports the timing result to the parent.
         */
        public void stop() {
            if (!capturing || !watch.isStarted()) {
                return;
            }

            watch.stop();

            parent.insertResult(x, y, watch.getTime(TimeUnit.MILLISECONDS));
            clear();
        }

        /**
         * Reset the stopwatch and stop counting
         */
        public void clear() {
            capturing = false;
            watch.reset();
        }

        @Override
        public void close() {
            stop();
        }
    }
}
