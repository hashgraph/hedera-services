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

package com.swirlds.demo.merkle.map;

import com.swirlds.demo.platform.HotspotConfiguration;
import com.swirlds.demo.platform.PAYLOAD_TYPE;
import java.io.Serializable;

public class FCMSequential implements Serializable {

    private PAYLOAD_TYPE sequentialType;
    private int sequentialAmount;
    private int sequentialSize;

    /**
     * An optional configuration for a hotspot. Not all {@link PAYLOAD_TYPE}s treat hotspots in the same way.
     */
    private HotspotConfiguration hotspot;

    public PAYLOAD_TYPE getSequentialType() {
        return sequentialType;
    }

    public void setSequentialType(PAYLOAD_TYPE sequentialType) {
        this.sequentialType = sequentialType;
    }

    public int getSequentialAmount() {
        return sequentialAmount;
    }

    public void setSequentialAmount(int sequentialAmount) {
        this.sequentialAmount = sequentialAmount;
    }

    public int getSequentialSize() {
        return sequentialSize;
    }

    public void setSequentialSize(int sequentialSize) {
        this.sequentialSize = sequentialSize;
    }

    /**
     * Get the hotspot configuration for this group of transactions, or null if there is no configured hotspot.
     */
    public HotspotConfiguration getHotspot() {
        return hotspot;
    }

    /**
     * Set the hotspot configuration for this group of transactions.
     */
    public void setHotspot(final HotspotConfiguration hotspot) {
        this.hotspot = hotspot;
    }
}
