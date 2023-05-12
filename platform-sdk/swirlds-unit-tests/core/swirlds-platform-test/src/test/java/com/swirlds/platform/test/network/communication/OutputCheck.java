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

package com.swirlds.platform.test.network.communication;

import java.io.OutputStream;
import org.junit.jupiter.api.Assertions;

public class OutputCheck extends OutputStream {
    private int[] expected;
    private int index = 0;

    public void setExpected(final int[] expected) {
        this.expected = expected;
        this.index = 0;
    }

    public void reset() {
        expected = null;
        index = 0;
    }

    @Override
    public void write(int b) {
        if (expected == null) {
            throw new IllegalStateException("No expected output set");
        }
        b &= 0xff; // because the contracts of read() and write() are terrible
        Assertions.assertEquals(
                expected[index],
                b,
                String.format("Expected byte %d but got %d at index %d", expected[index], b, index));
        index++;
    }

    @Override
    public void flush() {
        // nothing to do
    }
}
