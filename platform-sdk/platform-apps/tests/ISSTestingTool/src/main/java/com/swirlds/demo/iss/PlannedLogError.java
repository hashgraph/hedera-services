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

package com.swirlds.demo.iss;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;

/**
 * Describes an error which will be logged at a predetermined consensus time after genesis
 */
public class PlannedLogError implements SelfSerializable {
    private static final long CLASS_ID = 0xf0c6ba6c5da86ed4L;

    /**
     * The amount of time after genesis that the error will be written to the log at
     */
    private Duration timeAfterGenesis;

    /**
     * Zero arg constructor for the constructable registry.
     */
    public PlannedLogError() {}

    /**
     * Constructor
     *
     * @param timeAfterGenesis the time after genesis that the error should be written to the log at
     */
    public PlannedLogError(@NonNull final Duration timeAfterGenesis) {
        this.timeAfterGenesis = timeAfterGenesis;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeLong(timeAfterGenesis.toNanos());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        timeAfterGenesis = Duration.ofNanos(in.readLong());
    }

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * Input string will be of form "secondsAfterGenesis:message", where secondsAfterGenesis is an int
     *
     * @param plannedLogErrorString the string to parse
     * @return the parsed PlannedLogError
     */
    @NonNull
    public static PlannedLogError fromString(@NonNull final String plannedLogErrorString) {
        return new PlannedLogError(Duration.ofSeconds(Integer.parseInt(plannedLogErrorString)));
    }

    /**
     * Get the time after genesis when the log error is planned
     *
     * @return the time after genesis when the log error is planned
     */
    @NonNull
    public Duration getTimeAfterGenesis() {
        return timeAfterGenesis;
    }
}
