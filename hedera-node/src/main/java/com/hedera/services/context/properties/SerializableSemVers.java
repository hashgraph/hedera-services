/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.SemanticVersions.asSemVer;

import com.google.common.annotations.VisibleForTesting;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.SoftwareVersion;
import java.io.IOException;
import java.util.Comparator;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

public class SerializableSemVers implements SoftwareVersion {
    private static final String IS_INCOMPARABLE_MSG = " cannot be compared to ";
    private static final Pattern ALPHA_PRE_PATTERN = Pattern.compile("alpha[.](\\d+)");
    private static boolean currentVersionHasPatchMigrationRecords = false;
    public static final int RELEASE_027_VERSION = 1;
    public static final long CLASS_ID = 0x6f2b1bc2df8cbd0bL;

    public static final Comparator<SemanticVersion> SEM_VER_COMPARATOR =
            Comparator.comparingInt(SemanticVersion::getMajor)
                    .thenComparingInt(SemanticVersion::getMinor)
                    .thenComparingInt(SemanticVersion::getPatch)
                    .thenComparingInt(semver -> alphaNumberOf(semver.getPre()))
                    // We never deploy versions with `build` parts to prod envs, so
                    // just give precedence to the version that doesn't have one
                    .thenComparingInt(semver -> semver.getBuild().isBlank() ? 1 : 0);
    public static final Comparator<SerializableSemVers> FULL_COMPARATOR =
            Comparator.comparing(SerializableSemVers::getServices, SEM_VER_COMPARATOR)
                    .thenComparing(SerializableSemVers::getProto, SEM_VER_COMPARATOR);

    private SemanticVersion proto;
    private SemanticVersion services;

    public SerializableSemVers() {
        // RuntimeConstructable
    }

    public SemanticVersion getProto() {
        return proto;
    }

    public SemanticVersion getServices() {
        return services;
    }

    public SerializableSemVers(
            @NotNull final SemanticVersion proto, @NotNull final SemanticVersion services) {
        this.proto = proto;
        this.services = services;
    }

    public static SerializableSemVers forHapiAndHedera(
            @NotNull final String proto, @NotNull final String services) {
        return new SerializableSemVers(asSemVer(proto), asSemVer(services));
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

    public boolean isAfter(@Nullable final SoftwareVersion other) {
        return compareTo(other) > 0;
    }

    public boolean isBefore(@Nullable final SoftwareVersion other) {
        return compareTo(other) < 0;
    }

    public boolean hasMigrationRecordsFrom(@Nullable final SoftwareVersion other) {
        return isNonPatchUpgradeFrom(other)
                || (this.isAfter(other) && currentVersionHasPatchMigrationRecords);
    }

    @VisibleForTesting
    boolean isNonPatchUpgradeFrom(@Nullable final SoftwareVersion other) {
        if (other == null) {
            return true;
        }
        if (other instanceof SerializableSemVers that) {
            return this.isAfter(that) && haveDifferentMajorAndMinorVersions(this, that);
        } else {
            throw new IllegalArgumentException("Version " + this + IS_INCOMPARABLE_MSG + other);
        }
    }

    private boolean haveDifferentMajorAndMinorVersions(
            @NotNull final SerializableSemVers a, @NotNull final SerializableSemVers b) {
        return a.services.getMajor() != b.services.getMajor()
                || a.services.getMinor() != b.services.getMinor();
    }

    @Override
    public int compareTo(@Nullable final SoftwareVersion other) {
        if (proto == null || services == null) {
            throw new IllegalStateException(
                    "Uninitialized version " + this + IS_INCOMPARABLE_MSG + other);
        }
        if (other == SoftwareVersion.NO_VERSION) {
            // We are later than any unknown version
            return 1;
        }
        if (other instanceof SerializableSemVers that) {
            return FULL_COMPARATOR.compare(this, that);
        } else {
            throw new IllegalArgumentException("Version " + this + IS_INCOMPARABLE_MSG + other);
        }
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        proto = deserializeSemVer(in);
        services = deserializeSemVer(in);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        serializeSemVer(proto, out);
        serializeSemVer(services, out);
    }

    @Override
    public String toString() {
        return "Services @ " + readable(services) + " | HAPI @ " + readable(proto);
    }

    private static void serializeSemVer(
            final SemanticVersion semVer, final SerializableDataOutputStream out)
            throws IOException {
        out.writeInt(semVer.getMajor());
        out.writeInt(semVer.getMinor());
        out.writeInt(semVer.getPatch());
        serializeIfUsed(semVer.getPre(), out);
        serializeIfUsed(semVer.getBuild(), out);
    }

    private static void serializeIfUsed(
            final String semVerPart, final SerializableDataOutputStream out) throws IOException {
        if (semVerPart.isBlank()) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeNormalisedString(semVerPart);
        }
    }

    private static SemanticVersion deserializeSemVer(final SerializableDataInputStream in)
            throws IOException {
        final var ans = SemanticVersion.newBuilder();
        ans.setMajor(in.readInt()).setMinor(in.readInt()).setPatch(in.readInt());
        if (in.readBoolean()) {
            ans.setPre(in.readNormalisedString(Integer.MAX_VALUE));
        }
        if (in.readBoolean()) {
            ans.setBuild(in.readNormalisedString(Integer.MAX_VALUE));
        }
        return ans.build();
    }

    private static String readable(@Nullable final SemanticVersion semVer) {
        if (semVer == null) {
            return "<N/A>";
        }
        final var sb =
                new StringBuilder()
                        .append(semVer.getMajor())
                        .append(".")
                        .append(semVer.getMinor())
                        .append(".")
                        .append(semVer.getPatch());
        if (!semVer.getPre().isBlank()) {
            sb.append("-").append(semVer.getPre());
        }
        if (!semVer.getBuild().isBlank()) {
            sb.append("+").append(semVer.getBuild());
        }
        return sb.toString();
    }

    private static int alphaNumberOf(@NotNull final String pre) {
        final var alphaMatch = ALPHA_PRE_PATTERN.matcher(pre);
        // alpha versions come before everything else
        return alphaMatch.matches() ? Integer.parseInt(alphaMatch.group(1)) : Integer.MAX_VALUE;
    }

    @VisibleForTesting
    public void setProto(final SemanticVersion proto) {
        this.proto = proto;
    }

    @VisibleForTesting
    public void setServices(final SemanticVersion services) {
        this.services = services;
    }

    @VisibleForTesting
    static void setCurrentVersionHasPatchMigrationRecords(
            boolean currentVersionHasPatchMigrationRecords) {
        SerializableSemVers.currentVersionHasPatchMigrationRecords =
                currentVersionHasPatchMigrationRecords;
    }
}
