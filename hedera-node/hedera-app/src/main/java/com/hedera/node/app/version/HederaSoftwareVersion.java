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

import static com.swirlds.state.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static com.swirlds.state.spi.HapiUtils.deserializeSemVer;
import static com.swirlds.state.spi.HapiUtils.serializeSemVer;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.util.HapiUtils;
import com.swirlds.common.SoftwareVersion;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link SoftwareVersion} which can be saved in state and holds information about the HAPI and
 * Services versions of the running software.
 *
 * <p>The HAPI version is the version of the Hedera API. It may be that the HAPI version is less than the services
 * version if we had multiple services releases without touching the HAPI. In theory, these two versions could be
 * completely different from each other.
 *
 * <p>The Services version is the version of the node software itself.
 * This will be removed once we stop supporting 0.53.0 and earlier versions.
 */
@Deprecated(forRemoval = true)
public class HederaSoftwareVersion implements SoftwareVersion {

    public static final long CLASS_ID = 0x6f2b1bc2df8cbd0cL;
    public static final int RELEASE_027_VERSION = 1;
    public static final int RELEASE_048_VERSION = 2;

    private int configVersion;
    private SemanticVersion hapiVersion;
    private SemanticVersion servicesVersion;

    /**
     * The version of this object that was deserialized. When serializing a software version, we need to write it
     * to the stream using the same format as when it was deserialized.
     */
    private int deserializedVersion = RELEASE_048_VERSION;

    public HederaSoftwareVersion() {
        // For ConstructableRegistry. Do not use.
    }

    /**
     * Constructs a new instance with the given versions.
     * @param hapiVersion HAPI version
     * @param servicesVersion services version
     * @param configVersion config version
     */
    public HederaSoftwareVersion(
            final SemanticVersion hapiVersion,
            @NonNull final SemanticVersion servicesVersion,
            final int configVersion) {
        this.hapiVersion = hapiVersion;
        this.configVersion = configVersion;
        this.servicesVersion = Objects.requireNonNull(servicesVersion, "servicesVersion must not be null");
    }

    public SemanticVersion servicesVersion() {
        return servicesVersion;
    }

    public int configVersion() {
        return configVersion;
    }

    @Override
    @NonNull
    public SemanticVersion getPbjSemanticVersion() {
        return toUpgradeComparableSemVer(configVersion, servicesVersion);
    }

    public SemanticVersion getHapiVersion() {
        return hapiVersion;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return deserializedVersion;
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
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        deserializedVersion = version;

        hapiVersion = deserializeSemVer(in);
        servicesVersion = deserializeSemVer(in);
        if (version >= RELEASE_048_VERSION) {
            configVersion = in.readInt();
        }
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        serializeSemVer(hapiVersion, out);
        serializeSemVer(servicesVersion, out);
        if (deserializedVersion >= RELEASE_048_VERSION) {
            out.writeInt(configVersion);
        }
    }

    @Override
    public String toString() {
        // This is called by the platform when printing information on saved states to logs
        return "HederaSoftwareVersion{" + "hapiVersion="
                + HapiUtils.toString(hapiVersion) + ", servicesVersion="
                + readableServicesVersion() + '}';
    }

    /**
     * Returns a readable form of the services version with the config version as the semver build part if non-zero.
     *
     * @return a readable form of the services version
     */
    public String readableServicesVersion() {
        return HapiUtils.toString(toUpgradeComparableSemVer(configVersion, servicesVersion));
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

    /**
     * Given a pre-release version, returns the numeric part of the version or {@link Integer#MAX_VALUE} if the
     * pre-release version is not a number. (Which implies the version is not an alpha version, and comes after
     * any alpha version.)
     *
     * @param pre the pre-release version
     * @return the numeric part of the pre-release version or {@link Integer#MAX_VALUE}
     */
    private static int alphaNumberOrMaxValue(@Nullable final String pre) {
        if (pre == null) {
            return Integer.MAX_VALUE;
        }
        final var alphaMatch = HapiUtils.ALPHA_PRE_PATTERN.matcher(pre);
        // alpha versions come before everything else
        return alphaMatch.matches() ? Integer.parseInt(alphaMatch.group(1)) : Integer.MAX_VALUE;
    }
}
