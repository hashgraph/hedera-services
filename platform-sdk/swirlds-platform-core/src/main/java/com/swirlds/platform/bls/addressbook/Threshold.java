/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.bls.addressbook;

import java.util.function.LongUnaryOperator;

/** Represents different thresholds that have to be met */
public enum Threshold {
    /** >=1/3 */
    STRONG_MINORITY((final long whole) -> whole / 3 + ((whole % 3 == 0) ? 0 : 1)),
    /** >1/2 */
    MAJORITY((final long whole) -> whole / 2 + 1),
    /** >2/3 */
    SUPERMAJORITY((final long whole) -> whole / 3 * 2 + (whole % 3) * 2 / 3 + 1),
    /** ==1 */
    UNANIMOUS((final long whole) -> whole);

    /**
     * The method which calculates the minimum value which meets the threshold
     */
    private final LongUnaryOperator minValueMeetingThresholdMethod;

    /**
     * Constructor
     *
     * @param minValueMeetingThresholdMethod the method which calculates the minimum value that meets the threshold
     */
    Threshold(final LongUnaryOperator minValueMeetingThresholdMethod) {
        this.minValueMeetingThresholdMethod = minValueMeetingThresholdMethod;
    }

    /**
     * Gets the minimum value which meets a certain threshold
     *
     * @param whole the total value we are meeting a threshold of
     * @return the minimum value which satisfies a threshold of the whole value
     */
    public long getMinValueMeetingThreshold(final long whole) {
        return minValueMeetingThresholdMethod.applyAsLong(whole);
    }
}
