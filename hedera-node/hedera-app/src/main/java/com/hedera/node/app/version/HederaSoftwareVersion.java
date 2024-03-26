/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.version;

import static com.hedera.node.app.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.spi.HapiUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * An implementation of {@link SoftwareVersion} which can be saved in state and holds information about the HAPI and
 * Services versions of the running software.
 *
 * <p>The HAPI version is the version of the Hedera API. It may be that the HAPI version is less than the services
 * version if we had multiple services releases without touching the HAPI. In theory, these two versions could be
 * completely different from each other.
 *
 * <p>The Services version is the version of the node software itself.
 */
public class HederaSoftwareVersion implements SoftwareVersion {

    public static final long CLASS_ID = 0x6f2b1bc2df8cbd0cL;
    public static final int RELEASE_027_VERSION = 1;
    public static final Pattern ALPHA_PRE_PATTERN = Pattern.compile("alpha[.](\\d+)");

    private int configVersion;
    private SemanticVersion hapiVersion;
    private SemanticVersion servicesVersion;

    public HederaSoftwareVersion() {
        // For ConstructableRegistry. Do not use.
    }

    public HederaSoftwareVersion(
            final SemanticVersion hapiVersion, final SemanticVersion servicesVersion, final int configVersion) {
        this.hapiVersion = hapiVersion;
        this.configVersion = configVersion;
        this.servicesVersion = servicesVersion;
    }

    public SemanticVersion getHapiVersion() {
        return hapiVersion;
    }

    public SemanticVersion getServicesVersion() {
        return servicesVersion;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return RELEASE_027_VERSION;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return RELEASE_027_VERSION;
    }

    @Override
    public int compareTo(@NonNull final SoftwareVersion other) {
        // If the other software version is a HederaSoftwareVersion, then we can compare them directly.
        // If however, the other is null, or is not a HederaSoftwareVersion, then we will always sort
        // it before this one.
        if (other instanceof HederaSoftwareVersion that) {
            final var servicesComparison = SEMANTIC_VERSION_COMPARATOR.compare(
                    toUpgradeComparableSemVer(this.configVersion, this.servicesVersion),
                    toUpgradeComparableSemVer(that.configVersion, that.servicesVersion));
            return servicesComparison != 0
                    ? servicesComparison
                    : SEMANTIC_VERSION_COMPARATOR.compare(hapiVersion, that.hapiVersion);
        } else {
            return 1;
        }
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        hapiVersion = deserializeSemVer(in);
        servicesVersion = deserializeSemVer(in);
    }

    private static SemanticVersion deserializeSemVer(final SerializableDataInputStream in) throws IOException {
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

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        serializeSemVer(hapiVersion, out);
        serializeSemVer(servicesVersion, out);
    }

    private static void serializeSemVer(final SemanticVersion semVer, final SerializableDataOutputStream out)
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

    public boolean isAfter(@Nullable final SoftwareVersion deserializedVersion) {
        if (deserializedVersion == null) {
            return true;
        }
        return compareTo(deserializedVersion) > 0;
    }

    public boolean isBefore(@Nullable final SoftwareVersion deserializedVersion) {
        if (deserializedVersion == null) {
            return false;
        }
        return compareTo(deserializedVersion) < 0;
    }

    @Override
    public String toString() {
        // This is called by the platform when printing information on saved states to logs
        return "HederaSoftwareVersion{" + "hapiVersion="
                + HapiUtils.toString(hapiVersion) + ", servicesVersion="
                + HapiUtils.toString(servicesVersion) + '}';
    }

    /**
     * Given a semantic version, returns a modified form of which every part is either
     * absent or a parsed that can be used to compare
     * versions when detecting software upgrades.
     *
     * @param configVersion the numeric version of the configuration
     * @param semVer the literal semantic version
     * @return a comparable form of the given semantic version
     */
    private static SemanticVersion toUpgradeComparableSemVer(
            final int configVersion, @NonNull final SemanticVersion semVer) {
        final var builder = semVer.copyBuilder().pre(alphaNumberOrMaxValue(semVer.pre()) + "");
        if (configVersion > 0) {
            builder.build(configVersion + "");
        } else {
            builder.build("");
        }
        return builder.build();
    }

    private static int alphaNumberOrMaxValue(@Nullable final String pre) {
        if (pre == null) {
            return Integer.MAX_VALUE;
        }
        final var alphaMatch = ALPHA_PRE_PATTERN.matcher(pre);
        // alpha versions come before everything else
        return alphaMatch.matches() ? Integer.parseInt(alphaMatch.group(1)) : Integer.MAX_VALUE;
    }
}
