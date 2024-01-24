/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the minimum event generation for particular rounds
 *
 * @param round             the round number
 * @param minimumGeneration the minimum event generation for a given round
 */
public record MinGenInfo(long round, long minimumGeneration) {

    /**
     * The maximum permissible size of a list of {@link MinGenInfo} lists as deserialized by
     * {@link #deserializeList(SerializableDataInputStream)}. Is an upper bound for
     * {@link com.swirlds.platform.consensus.ConsensusConfig#roundsNonAncient()}, choices for this config that exceed
     * this value will result in an exception being thrown when deserializeList is called.
     */
    public static int MAX_MINGEN_INFO_SIZE = 32;

    /**
     * Serialize a list of {@link MinGenInfo} objects
     *
     * @param minGenInfo the list of {@link MinGenInfo} objects to serialize
     * @param out        the stream to write to
     * @throws IOException thrown if an IO error occurs
     */
    public static void serializeList(
            @NonNull final List<MinGenInfo> minGenInfo, @NonNull final SerializableDataOutputStream out)
            throws IOException {
        out.writeInt(minGenInfo.size());
        for (final MinGenInfo info : minGenInfo) {
            out.writeLong(info.round());
            out.writeLong(info.minimumGeneration());
        }
    }

    /**
     * Deserialize a list of {@link MinGenInfo} objects
     *
     * @param in the stream to read from
     * @return the list of {@link MinGenInfo} objects
     * @throws IOException thrown if an IO error occurs
     */
    public static List<MinGenInfo> deserializeList(@NonNull final SerializableDataInputStream in) throws IOException {
        final int minGenInfoSize = in.readInt();
        if (minGenInfoSize > MAX_MINGEN_INFO_SIZE) {
            throw new IOException("Deserializing a list of MinGenInfo objects with size " + minGenInfoSize
                    + " exceeds the maximum permissible size of " + MAX_MINGEN_INFO_SIZE);
        }
        final List<MinGenInfo> minGenInfo = new ArrayList<>(minGenInfoSize);
        for (int i = 0; i < minGenInfoSize; i++) {
            minGenInfo.add(new MinGenInfo(in.readLong(), in.readLong()));
        }
        return minGenInfo;
    }
}
