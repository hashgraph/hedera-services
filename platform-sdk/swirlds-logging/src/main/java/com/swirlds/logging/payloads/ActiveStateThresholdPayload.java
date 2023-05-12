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

package com.swirlds.logging.payloads;

/**
 * This payload is used when the active states in memory exceed one of the configured thresholds.
 */
public class ActiveStateThresholdPayload extends AbstractLogPayload {

    private String thresholdCategory;
    private double currentValue;
    private double maximumAllowable;

    public ActiveStateThresholdPayload() {}

    public enum ThresholdCategory {
        /**
         * A limit on the total number of State objects in memory.
         */
        TOTAL_NUMBER_OF_STATES,
        /**
         * A limit on the maximum age of a State in memory.
         */
        MAXIMUM_STATE_AGE
    }

    /**
     * @param message
     * 		a human readable message
     * @param thresholdCategory
     * 		a string describing the threshold that was violated
     * @param currentValue
     * 		the value of the metric that exceeded its threshold
     * @param maximumAllowable
     * 		the value of the threshold that was violated
     */
    public ActiveStateThresholdPayload(
            final String message,
            final String thresholdCategory,
            final double currentValue,
            final double maximumAllowable) {
        super(message);

        this.thresholdCategory = thresholdCategory;
        this.currentValue = currentValue;
        this.maximumAllowable = maximumAllowable;
    }

    /**
     * Get the name of the threshold that was violated.
     */
    public String getThresholdCategory() {
        return thresholdCategory;
    }

    /**
     * Set the name of the threshold that was violated.
     */
    public void setThresholdCategory(final String thresholdCategory) {
        this.thresholdCategory = thresholdCategory;
    }

    /**
     * Get the current value which exceeds the threshold.
     */
    public double getCurrentValue() {
        return currentValue;
    }

    /**
     * Set the current value which exceeds the threshold.
     */
    public void setCurrentValue(final double currentValue) {
        this.currentValue = currentValue;
    }

    /**
     * Get the configured threshold value.
     */
    public double getMaximumAllowable() {
        return maximumAllowable;
    }

    /**
     * Set the configured threshold value.
     */
    public void setMaximumAllowable(final double maximumAllowable) {
        this.maximumAllowable = maximumAllowable;
    }
}
