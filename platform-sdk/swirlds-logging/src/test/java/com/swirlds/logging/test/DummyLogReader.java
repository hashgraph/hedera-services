/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.test;

import com.swirlds.logging.SwirldsLogReader;
import com.swirlds.logging.json.JsonLogEntry;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A log reader that emits "canned" data.
 */
public class DummyLogReader extends SwirldsLogReader<JsonLogEntry> {

    private final List<JsonLogEntry> entryList;
    private final Queue<JsonLogEntry> entryQueue;

    public DummyLogReader(final List<JsonLogEntry> entryList) throws FileNotFoundException {
        this.entryList = entryList;
        this.entryQueue = new LinkedList<>(entryList);
    }

    private DummyLogReader(final DummyLogReader that) {
        entryList = that.entryList;
        entryQueue = new LinkedList<>(entryList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JsonLogEntry readNextEntry() throws IOException {
        if (entryQueue.size() > 0) {
            return entryQueue.remove();
        } else {
            return null;
        }
    }

    public DummyLogReader copy() {
        return new DummyLogReader(this);
    }
}
