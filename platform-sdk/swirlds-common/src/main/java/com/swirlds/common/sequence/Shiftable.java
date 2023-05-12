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

package com.swirlds.common.sequence;

/**
 * A data structure with a shifting window of acceptable values. When a new value is added, if the value
 * falls outside the currently accepted window then it is ignored.
 */
public interface Shiftable {
    /**
     * Purge all data with a generation older (lower number) than the specified generation
     *
     * @param firstSequenceNumberInWindow
     * 		the first sequence number in the window after this operation completes, all data older than
     * 		this value is removed
     */
    void shiftWindow(long firstSequenceNumberInWindow);
}
