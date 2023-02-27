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

package com.swirlds.platform.chatter.protocol.processing;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * A message sent periodically that informs peers how long it takes for this node to process an event, from the time an
 * event is first received to the time it is validated.
 */
public class ProcessingTimeMessage implements SelfSerializable {

    private static final long CLASS_ID = 0x66489DCB3E5E440AL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long processingTimeInNanos;

    public ProcessingTimeMessage() {}

    public ProcessingTimeMessage(final long processingTimeInNanos) {
        this.processingTimeInNanos = processingTimeInNanos;
    }

    public long getProcessingTimeInNanos() {
        return processingTimeInNanos;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(processingTimeInNanos);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        processingTimeInNanos = in.readLong();
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
