/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * A basic implementation of {@link SoftwareVersion} that represents a version using a long value.
 */
public class BasicSoftwareVersion implements SoftwareVersion {

    private static final long CLASS_ID = 0x777ea397b73c9830L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long softwareVersion;

    /**
     * Zero arg constructor used for deserialization.
     */
    public BasicSoftwareVersion() {}

    /**
     * Create a new software version.
     *
     * @param softwareVersion
     * 		the version number
     */
    public BasicSoftwareVersion(final long softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    /**
     * Get the software version number. Distinct from {@link #getVersion()}, which returns the serialization version
     * for this object.
     *
     * @return the software version number
     */
    public long getSoftwareVersion() {
        return softwareVersion;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(softwareVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        softwareVersion = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(final SoftwareVersion that) {
        if (that == NO_VERSION) {
            // No version always comes before all other versions
            return 1;
        }

        if (that instanceof BasicSoftwareVersion thatVersion) {
            return Long.compare(softwareVersion, thatVersion.softwareVersion);
        } else {
            throw new IllegalArgumentException(
                    "Can not compare BasicSoftwareVersion to " + that.getClass().getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof BasicSoftwareVersion that)) {
            return false;
        }

        return softwareVersion == that.softwareVersion;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Long.hashCode(softwareVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return Long.toString(softwareVersion);
    }
}
