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

/** Represents different kinds of thresholds that have to be met */
public enum Threshold {
    /** >=1/3 */
    STRONG_MINORITY {
        @Override
        long getMinSatisfyingValue(long whole) {
            return whole / 3 + ((whole % 3 == 0) ? 0 : 1);
        }
    },
    /** >1/2 */
    MAJORITY {
        @Override
        long getMinSatisfyingValue(long whole) {
            return whole / 2 + 1;
        }
    },
    /** >2/3 */
    SUPERMAJORITY {
        @Override
        long getMinSatisfyingValue(long whole) {
            return whole / 3 * 2 + (whole % 3) * 2 / 3 + 1;
        }
    },
    /** ==1 */
    UNANIMOUS {
        @Override
        long getMinSatisfyingValue(long whole) {
            return whole;
        }
    };

    /**
     * Gets the minimum value which meets a certain threshold
     *
     * @param whole the total value we are meeting a threshold of
     * @return the minimum value which satisfies a certain threshold of the whole value
     */
    abstract long getMinSatisfyingValue(final long whole);
}
