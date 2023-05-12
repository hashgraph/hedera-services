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

package com.swirlds.common.utility;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * A standard representation of a semantic version number.
 */
public final class SemanticVersion implements Comparable<SemanticVersion>, SelfSerializable {

    /**
     * Constant value representing a zero version number.
     */
    public static final SemanticVersion ZERO = new SemanticVersion(0, 0, 0, StringUtils.EMPTY, StringUtils.EMPTY);

    /**
     * Constant value representing an ASCII period.
     */
    private static final String PERIOD = ".";

    /**
     * Constant value representing an ASCII dash.
     */
    private static final String DASH = "-";

    /**
     * Constant value representing an ASCII plus sign.
     */
    private static final String PLUS = "+";

    /**
     * A precompiled regular expression used to parse a semantic version string and extract the individual components.
     */
    private static final Pattern SEMVER_PATTERN = Pattern.compile('^'
            + "((\\d+)\\.(\\d+)\\.(\\d+))" // version string
            + "(?:-([\\dA-Za-z]+(?:\\.[\\dA-Za-z]+)*))?" // prerelease suffix (optional)
            + "(?:\\+([\\dA-Za-z\\-]+(?:\\.[\\dA-Za-z\\-]+)*))?" // build suffix (optional)
            + '$');

    private static final int MAX_STRING_LENGTH = 1000;
    private static final long CLASS_ID = 0xa65ed81ceb0a9dc0L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private int major;
    private int minor;
    private int patch;
    private String prerelease;
    private String build;

    /**
     * Needed for {@link com.swirlds.common.constructable.RuntimeConstructable}
     */
    public SemanticVersion() {}

    /**
     * @param major
     * 		the major version.
     * @param minor
     * 		the minor version.
     * @param patch
     * 		the patch version.
     * @param prerelease
     * 		the optional prerelease specifier.
     * @param build
     * 		the optional build specifier.
     */
    public SemanticVersion(
            final int major, final int minor, final int patch, final String prerelease, final String build) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        if (prerelease != null && prerelease.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Invalid length for prerelease, max is " + MAX_STRING_LENGTH);
        }
        this.prerelease = prerelease;
        if (build != null && build.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("Invalid length for build, max is " + MAX_STRING_LENGTH);
        }
        this.build = build;
    }

    /**
     * Parses a semantic version string into the individual components.
     *
     * @param version
     * 		a semantic version number in string form.
     * @return an instance of a {@link SemanticVersion} containing the individual components.
     * @throws InvalidSemanticVersionException
     * 		if the supplied string cannot be parsed as a semantic version number.
     * @throws IllegalArgumentException
     * 		if the {@code version} argument is a {@code null} reference.
     */
    public static SemanticVersion parse(final String version) {
        throwArgNull(version, "version");
        final Matcher matcher = SEMVER_PATTERN.matcher(version);

        if (!matcher.matches()) {
            throw new InvalidSemanticVersionException(
                    String.format("The supplied version '%s' is not a valid semantic version", version));
        }

        try {
            final int major = Integer.parseInt(matcher.group(2));
            final int minor = Integer.parseInt(matcher.group(3));
            final int patch = Integer.parseInt(matcher.group(4));
            final String prerelease = CommonUtils.nullToBlank(matcher.group(5));
            final String build = CommonUtils.nullToBlank(matcher.group(6));

            return new SemanticVersion(major, minor, patch, prerelease, build);
        } catch (final NumberFormatException e) {
            throw new InvalidSemanticVersionException(
                    String.format("The supplied version '%s' is not a valid semantic version", version), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("NullableProblems")
    public int compareTo(final SemanticVersion that) {
        if (this == that) return 0;

        return new CompareToBuilder()
                .append(major, that.major)
                .append(minor, that.minor)
                .append(patch, that.patch)
                .append(prerelease, that.prerelease)
                .append(build, that.build)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;

        if (!(o instanceof final SemanticVersion that)) return false;

        return new EqualsBuilder()
                .append(major, that.major)
                .append(minor, that.minor)
                .append(patch, that.patch)
                .append(prerelease, that.prerelease)
                .append(build, that.build)
                .isEquals();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(major)
                .append(minor)
                .append(patch)
                .append(prerelease)
                .append(build)
                .toHashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(String.format("%d", major()))
                .append(PERIOD)
                .append(String.format("%d", minor()))
                .append(PERIOD)
                .append(String.format("%d", patch()));

        if (prerelease() != null && !prerelease().isBlank()) {
            builder.append(DASH).append(prerelease());
        }

        if (build() != null && !build().isBlank()) {
            builder.append(PLUS).append(build());
        }

        return builder.toString();
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String prerelease() {
        return prerelease;
    }

    public String build() {
        return build;
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
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeInt(major);
        out.writeInt(minor);
        out.writeInt(patch);
        out.writeNormalisedString(prerelease);
        out.writeNormalisedString(build);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        major = in.readInt();
        minor = in.readInt();
        patch = in.readInt();
        prerelease = in.readNormalisedString(MAX_STRING_LENGTH);
        build = in.readNormalisedString(MAX_STRING_LENGTH);
    }
}
