/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import java.security.SecureRandom;
import java.util.Random;

/**
 * Configures a hotspot for sequence of PTT transactions. Each transaction type may handle
 * hotspots differently.
 */
public class HotspotConfiguration {

    private static final Random RANDOM = new SecureRandom();

    private int size;
    private double frequency;

    /**
     * Get the size of the configured hotspot.
     */
    public int getSize() {
        return size;
    }

    /**
     * Set the size of the configured hotspot.
     */
    public void setSize(final int size) {
        this.size = size;
    }

    /**
     * Get the frequency of the configured hotspot, a number between 0.0 and 1.0.
     */
    public double getFrequency() {
        return frequency;
    }

    /**
     * Set the frequency of the configured hotspot, a number between 0.0 and 1.0.
     */
    public void setFrequency(final double frequency) {
        this.frequency = frequency;
    }

    /**
     *  Determines whether the hotspot configuration should be used
     *  based on the specified frequency
     *
     * @return Whether the hotspot configuration should be used
     */
    public boolean shouldBeUsed() {
        final double choice = RANDOM.nextDouble();
        return choice < frequency;
    }
}
