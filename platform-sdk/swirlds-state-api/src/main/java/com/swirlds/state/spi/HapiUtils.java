/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.spi;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Comparator;

/**
 * Utility methods for the Hedera API.
 */
public final class HapiUtils {
    private static final String ALPHA_PREFIX = "alpha.";
    private static final int ALPHA_PREFIX_LENGTH = ALPHA_PREFIX.length();

    // FUTURE WORK: Add unit tests for this class.
    /**
     * A {@link Comparator} for {@link SemanticVersion}s that ignores
     * any semver part that cannot be parsed as an integer.
     */
    public static final Comparator<SemanticVersion> SEMANTIC_VERSION_COMPARATOR = Comparator.comparingInt(
                    SemanticVersion::major)
            .thenComparingInt(SemanticVersion::minor)
            .thenComparingInt(SemanticVersion::patch)
            .thenComparingInt(semVer -> HapiUtils.parsedAlphaIntOrMaxValue(semVer.pre()))
            .thenComparingInt(semVer -> HapiUtils.parsedIntOrZero(semVer.build()));

    private static int parsedAlphaIntOrMaxValue(@NonNull final String s) {
        if (s.isBlank() || !s.startsWith(ALPHA_PREFIX)) {
            return Integer.MAX_VALUE;
        } else {
            try {
                return Integer.parseInt(s.substring(ALPHA_PREFIX_LENGTH));
            } catch (NumberFormatException ignore) {
                return Integer.MAX_VALUE;
            }
        }
    }

    private static int parsedIntOrZero(@NonNull final String s) {
        if (s.isBlank() || "0".equals(s)) {
            return 0;
        } else {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignore) {
                return 0;
            }
        }
    }

    public static SemanticVersion deserializeSemVer(final SerializableDataInputStream in) throws IOException {
        final var ans = SemanticVersion.newBuilder();
        ans.major(in.readInt()).minor(in.readInt()).patch(in.readInt());
        if (in.readBoolean()) {
            ans.pre(in.readNormalisedString(Integer.MAX_VALUE));
        }
        if (in.readBoolean()) {
            ans.build(in.readNormalisedString(Integer.MAX_VALUE));
        }
        return ans.build();
    }

    public static void serializeSemVer(final SemanticVersion semVer, final SerializableDataOutputStream out)
            throws IOException {
        out.writeInt(semVer.major());
        out.writeInt(semVer.minor());
        out.writeInt(semVer.patch());
        serializeIfUsed(semVer.pre(), out);
        serializeIfUsed(semVer.build(), out);
    }

    private static void serializeIfUsed(final String semVerPart, final SerializableDataOutputStream out)
            throws IOException {
        if (semVerPart.isBlank()) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeNormalisedString(semVerPart);
        }
    }

    public static String asAccountString(final AccountID accountID) {
        return String.format("%d.%d.%d", accountID.shardNum(), accountID.realmNum(), accountID.accountNum());
    }
}
