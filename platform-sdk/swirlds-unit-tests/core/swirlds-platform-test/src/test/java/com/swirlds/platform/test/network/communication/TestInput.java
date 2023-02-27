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

import java.io.InputStream;

public class TestInput extends InputStream {
    private int[] input;
    private int index = 0;

    public void setInput(final int[] input) {
        this.input = input;
        this.index = 0;
    }

    public void reset() {
        input = null;
        index = 0;
    }

    @Override
    public int read() {
        if (input == null) {
            throw new IllegalStateException("No input set");
        }
        if (index >= input.length) {
            return -1; // end of stream
        }
        return input[index++];
    }
}
