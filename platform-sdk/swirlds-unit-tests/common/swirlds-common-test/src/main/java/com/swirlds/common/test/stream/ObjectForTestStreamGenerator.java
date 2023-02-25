/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.stream;

import java.time.Instant;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ObjectForTestStreamGenerator {
    /**
     * number of objects to be generated
     */
    private int totalNum;
    /**
     * interval in ms between each two adjacent objects to be generated
     */
    private int intervalMs;
    /**
     * timeStamp of the next object
     */
    private Instant nextTimestamp;
    /**
     * idx of next object;
     */
    private int idx;
    /**
     * @param totalNum
     */
    public ObjectForTestStreamGenerator(int totalNum, int intervalMs, Instant firstTimestamp) {
        this.totalNum = totalNum;
        this.intervalMs = intervalMs;
        nextTimestamp = firstTimestamp;
    }

    Iterator<ObjectForTestStream> getIterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return idx < totalNum;
            }

            @Override
            public ObjectForTestStream next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                ObjectForTestStream object = new ObjectForTestStream(idx, nextTimestamp);
                idx++;
                nextTimestamp = nextTimestamp.plusMillis(intervalMs);
                return object;
            }
        };
    }
}
