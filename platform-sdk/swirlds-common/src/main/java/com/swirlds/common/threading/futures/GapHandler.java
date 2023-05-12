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

package com.swirlds.common.threading.futures;

/**
 * Describes a method that is called when a gap in a {@link SequentialFutures} is encountered.
 *
 * @param <T>
 * 		the type of the object held by the future
 */
@FunctionalInterface
public interface GapHandler<T> {

    /**
     * Called when there is a gap in the sequence. It's ok to throw an exception if gaps are not tolerated,
     * or to simply cancel the future.
     *
     * @param index
     * 		the index of the gap
     * @param future
     * 		the future representing an index, should be completed or cancelled if no exception is thrown
     * 		by this method
     * @param previousValue
     * 		the value that was most recently {@link SequentialFutures#complete(long, Object)} completed,
     * 		may not be from the previous index if the complete call from the previous index threw an exception
     */
    void handleGap(final long index, final StandardFuture<T> future, final T previousValue);
}
