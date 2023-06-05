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

package com.hedera.node.app.info;

import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.mono.context.properties.SerializableSemVers;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.regex.Pattern;

/**
 * Implementation of {@link NetworkInfo} that delegates to the mono-service.
 */
public class MonoNetworkInfo implements NetworkInfo {
    private static final int MAX_VERSION_LENGTH = 100;
    // Arbitrary limit to prevent stack overflow when parsing unrealistically long versions

    /* From https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string */
    private static final Pattern SEMVER_SPEC_REGEX = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)"
                        + "(?:\\."
                    + "(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)"
                    + "*))?$");

    private final com.hedera.node.app.service.mono.config.NetworkInfo delegate;

    /**
     * Constructs a {@link MonoNetworkInfo} with the given delegate.
     *
     * @param delegate the delegate
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public MonoNetworkInfo(@NonNull com.hedera.node.app.service.mono.config.NetworkInfo delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    @NonNull
    public Bytes ledgerId() {
        final var ledgerId = delegate.ledgerId();
        return ledgerId != null ? Bytes.wrap(ledgerId.toByteArray()) : Bytes.EMPTY;
    }

    @NonNull
    public SemanticVersion servicesVersion() {
        final SerializableSemVers version = SEMANTIC_VERSIONS.deployedSoftwareVersion();
        return asSemanticVer(version.getProtoBuild());
    }

    @NonNull
    public SemanticVersion hapiVersion() {
        final SerializableSemVers version = SEMANTIC_VERSIONS.deployedSoftwareVersion();
        return asSemanticVer(version.getServicesBuild());
    }

    private static SemanticVersion asSemanticVer(final String value) {
        if (value.length() > MAX_VERSION_LENGTH) {
            throw new IllegalArgumentException("Semantic version '" + value + "' is too long");
        }
        final var matcher = SEMVER_SPEC_REGEX.matcher(value);
        if (matcher.matches()) {
            final var builder = SemanticVersion.newBuilder()
                    .major(Integer.parseInt(matcher.group(1)))
                    .minor(Integer.parseInt(matcher.group(2)))
                    .patch(Integer.parseInt(matcher.group(3)));
            if (matcher.group(4) != null) {
                builder.pre(matcher.group(4));
            }
            if (matcher.group(5) != null) {
                builder.build(matcher.group(5));
            }
            return builder.build();
        } else {
            throw new IllegalArgumentException("Argument value='" + value + "' is not a valid semver");
        }
    }
}
