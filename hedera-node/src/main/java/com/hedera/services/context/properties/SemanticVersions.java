package com.hedera.services.context.properties;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.queries.meta.GetVersionInfoAnswer;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class SemanticVersions {
	private static final Logger log = LogManager.getLogger(SemanticVersions.class);

	/* From https://semver.org/#is-there-a-suggested-regular-expression-regex-to-check-a-semver-string */
	private static final Pattern SEMVER_SPEC_REGEX = Pattern.compile(
			"^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\." +
					"(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");

	private static final String HAPI_VERSION_KEY = "hapi.proto.version";
	private static final String HEDERA_VERSION_KEY = "hedera.services.version";

	private String versionInfoResource = "semantic-version.properties";

	private AtomicReference<ActiveVersions> knownActive = new AtomicReference<>(null);

	public Optional<ActiveVersions> getDeployed() {
		return Optional.ofNullable(knownActive.get())
				.or(() -> Optional.ofNullable(fromResource(
						versionInfoResource,
						HAPI_VERSION_KEY,
						HEDERA_VERSION_KEY)));
	}

	ActiveVersions fromResource(String propertiesFile, String protoKey, String servicesKey) {
		try (InputStream in = GetVersionInfoAnswer.class.getClassLoader().getResourceAsStream(propertiesFile)) {
			var props = new Properties();
			props.load(in);
			log.info("Discovered semantic versions {} from resource '{}'", props, propertiesFile);
			var protoSemVer = asSemVer((String) props.get(protoKey));
			var hederaSemVer = asSemVer((String) props.get(servicesKey));
			knownActive.set(new ActiveVersions(protoSemVer, hederaSemVer));
		} catch (Exception surprising) {
			log.warn(
					"Failed to parse resource '{}' (keys '{}' and '{}'). Version info will be unavailable!",
					propertiesFile,
					protoKey,
					servicesKey,
					surprising);
			var emptySemver = SemanticVersion.getDefaultInstance();
			knownActive.set(new ActiveVersions(emptySemver, emptySemver));
		}
		return knownActive.get();
	}

	SemanticVersion asSemVer(String value) {
		final var matcher = SEMVER_SPEC_REGEX.matcher(value);
		if (matcher.matches()) {
			final var builder = SemanticVersion.newBuilder()
					.setMajor(Integer.parseInt(matcher.group(1)))
					.setMinor(Integer.parseInt(matcher.group(2)))
					.setPatch(Integer.parseInt(matcher.group(3)));
			if (matcher.group(4) != null) {
				builder.setPreReleaseVersion(matcher.group(4));
			}
			if (matcher.group(5) != null) {
				builder.setBuildMetadata(matcher.group(5));
			}
			return builder.build();
		} else {
			throw new IllegalArgumentException("Argument value='" + value + "' is not a valid semver");
		}
	}

	void setVersionInfoResource(String versionInfoResource) {
		this.versionInfoResource = versionInfoResource;
	}
}
