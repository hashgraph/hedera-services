/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.SemanticVersion;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum SemanticVersions {
    SEMANTIC_VERSIONS;

    private static final Logger log = LogManager.getLogger(SemanticVersions.class);

    /* From https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string */
    private static final Pattern SEMVER_SPEC_REGEX =
            Pattern.compile(
                    "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\."
                        + "(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

    private static final String HAPI_VERSION_KEY = "hapi.proto.version";
    private static final String HEDERA_VERSION_KEY = "hedera.services.version";
    private static final String VERSION_INFO_RESOURCE = "semantic-version.properties";

    private final AtomicReference<ActiveVersions> knownActive = new AtomicReference<>(null);
    private final AtomicReference<SerializableSemVers> knownSerializable =
            new AtomicReference<>(null);

    @Nonnull
    public ActiveVersions getDeployed() {
        ensureLoaded();
        return knownActive.get();
    }

    @Nonnull
    public SerializableSemVers deployedSoftwareVersion() {
        ensureLoaded();
        return knownSerializable.get();
    }

    private void ensureLoaded() {
        if (knownActive.get() == null) {
            final var deployed =
                    fromResource(VERSION_INFO_RESOURCE, HAPI_VERSION_KEY, HEDERA_VERSION_KEY);
            knownActive.set(deployed);
            knownSerializable.set(
                    new SerializableSemVers(deployed.protoSemVer(), deployed.hederaSemVer()));
        }
    }

    @Nonnull
    static ActiveVersions fromResource(
            final String propertiesFile, final String protoKey, final String servicesKey) {
        try (final var in =
                SemanticVersions.class.getClassLoader().getResourceAsStream(propertiesFile)) {
            final var props = new Properties();
            props.load(in);
            log.info("Discovered semantic versions {} from resource '{}'", props, propertiesFile);
            final var protoSemVer = asSemVer((String) props.get(protoKey));
            final var hederaSemVer = asSemVer((String) props.get(servicesKey));
            return new ActiveVersions(protoSemVer, hederaSemVer);
        } catch (Exception surprising) {
            log.warn(
                    "Failed to parse resource '{}' (keys '{}' and '{}'). Version info will be"
                            + " unavailable!",
                    propertiesFile,
                    protoKey,
                    servicesKey,
                    surprising);
            final var emptySemver = SemanticVersion.getDefaultInstance();
            return new ActiveVersions(emptySemver, emptySemver);
        }
    }

    static SemanticVersion asSemVer(final String value) {
        final var matcher = SEMVER_SPEC_REGEX.matcher(value);
        if (matcher.matches()) {
            final var builder =
                    SemanticVersion.newBuilder()
                            .setMajor(Integer.parseInt(matcher.group(1)))
                            .setMinor(Integer.parseInt(matcher.group(2)))
                            .setPatch(Integer.parseInt(matcher.group(3)));
            if (matcher.group(4) != null) {
                builder.setPre(matcher.group(4));
            }
            if (matcher.group(5) != null) {
                builder.setBuild(matcher.group(5));
            }
            return builder.build();
        } else {
            throw new IllegalArgumentException(
                    "Argument value='" + value + "' is not a valid semver");
        }
    }
}
