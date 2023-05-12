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

package com.swirlds.common.test.set;

/**
 * Describes a hotspot in a {@link HotspotHashSet}.
 */
public class Hotspot {

    /**
     * The weight that determines the frequency that an object from this hotspot is chosen.
     */
    private final double weight;

    /**
     * The desired size of this hotspot.
     */
    private final int size;

    /**
     * Create a new description of a hotspot.
     *
     * @param weight
     * 		the weight of this hotspot. A weight of 2 will cause this hotspot to be chosen twice as
     * 		often as a hotspot with a weight of 1.
     * @param size
     * 		the number of elements in this hotspot
     */
    public Hotspot(final double weight, final int size) {
        this.weight = weight;
        this.size = size;
    }

    /**
     * Get the weight of this hotspot.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Get the maximum size that this hotspot would like to be.
     */
    public int getHotspotSize() {
        return size;
    }
}
