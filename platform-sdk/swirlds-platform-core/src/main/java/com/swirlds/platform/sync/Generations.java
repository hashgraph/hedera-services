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

package com.swirlds.platform.sync;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.consensus.GraphGenerations;
import java.io.IOException;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Generations implements GraphGenerations, SelfSerializable {
    /** The generations at genesis */
    public static final GraphGenerations GENESIS_GENERATIONS = new Generations(
            GraphGenerations.FIRST_GENERATION, GraphGenerations.FIRST_GENERATION, GraphGenerations.FIRST_GENERATION);

    private static final long CLASS_ID = 0x2d745f265302ccfbL;
    /** The minimum famous witness generation number from the minimum (oldest) non-expired round. */
    private long minRoundGeneration;
    /** the minimum generation of all the judges that are not ancient */
    private long minGenNonAncient;
    /**
     * The minimum famous witness generation number from the maximum round which for which the fame of all witnesses has
     * been decided.
     */
    private long maxRoundGeneration;

    /**
     * No-args constructor for RuntimeConstructable
     */
    public Generations() {}

    public Generations(final GraphGenerations generations) {
        this(
                generations.getMinRoundGeneration(),
                generations.getMinGenerationNonAncient(),
                generations.getMaxRoundGeneration());
    }

    public Generations(final long minRoundGeneration, final long minGenNonAncient, final long maxRoundGeneration) {
        this.minRoundGeneration = minRoundGeneration;
        this.minGenNonAncient = minGenNonAncient;
        this.maxRoundGeneration = maxRoundGeneration;
        checkGenerations();
    }

    @Override
    public long getMinRoundGeneration() {
        return minRoundGeneration;
    }

    @Override
    public long getMinGenerationNonAncient() {
        return minGenNonAncient;
    }

    @Override
    public long getMaxRoundGeneration() {
        return maxRoundGeneration;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(minRoundGeneration);
        out.writeLong(minGenNonAncient);
        out.writeLong(maxRoundGeneration);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        minRoundGeneration = in.readLong();
        minGenNonAncient = in.readLong();
        maxRoundGeneration = in.readLong();
        checkGenerations();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("minRoundGeneration", minRoundGeneration)
                .append("minGenNonAncient", minGenNonAncient)
                .append("maxRoundGeneration", maxRoundGeneration)
                .toString();
    }

    /**
     * Check if the generation numbers conform to constraints
     */
    private void checkGenerations() {
        if (minRoundGeneration < GraphGenerations.FIRST_GENERATION) {
            throw new IllegalArgumentException(
                    "minRoundGeneration cannot be smaller than " + GraphGenerations.FIRST_GENERATION + "! " + this);
        }
        if (minGenNonAncient < minRoundGeneration) {
            throw new IllegalArgumentException("minGenNonAncient cannot be smaller than minRoundGeneration! " + this);
        }

        if (maxRoundGeneration < minGenNonAncient) {
            throw new IllegalArgumentException("maxRoundGeneration cannot be smaller than minGenNonAncient! " + this);
        }
    }

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }
}
