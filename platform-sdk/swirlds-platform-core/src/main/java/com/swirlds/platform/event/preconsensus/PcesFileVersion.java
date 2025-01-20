/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus;

import java.util.Arrays;

/**
 * The version of the file format used to serialize preconsensus events.
 */
public enum PcesFileVersion {
    /** The version of the file format that serializes events as protobuf. */
    PROTOBUF_EVENTS(2);

    private final int versionNumber;

    PcesFileVersion(final int versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * @return the version number of the file format
     */
    public int getVersionNumber() {
        return versionNumber;
    }

    /**
     * @return the version number of the current file format
     */
    public static int currentVersionNumber() {
        return PROTOBUF_EVENTS.getVersionNumber();
    }

    /**
     * Get the file format version with the given version number.
     *
     * @param versionNumber the version number of the file format
     * @return the file format version with the given version number
     */
    public static PcesFileVersion fromVersionNumber(final int versionNumber) {
        return Arrays.stream(PcesFileVersion.values())
                .filter(v -> v.getVersionNumber() == versionNumber)
                .findFirst()
                .orElse(null);
    }
}
