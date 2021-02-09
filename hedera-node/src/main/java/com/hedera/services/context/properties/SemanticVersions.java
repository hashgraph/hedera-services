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
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.utils.EntityIdUtils.asDotDelimitedLongArray;

public class SemanticVersions {
	private static final Logger log = LogManager.getLogger(SemanticVersions.class);

	static String HAPI_VERSION_KEY = "hapi.proto.version";
	static String HEDERA_VERSION_KEY = "hedera.services.version";
	static String VERSION_INFO_RESOURCE = "semantic-version.properties";

	static AtomicReference<ActiveVersions> knownActive = new AtomicReference<>(null);

	public Optional<ActiveVersions> getDeployed() {
		return Optional.ofNullable(knownActive.get())
				.or(() -> Optional.ofNullable(fromResource(
						VERSION_INFO_RESOURCE,
						HAPI_VERSION_KEY,
						HEDERA_VERSION_KEY)));
	}

	private static ActiveVersions fromResource(String propertiesFile, String protoKey, String servicesKey) {
		try (InputStream in = GetVersionInfoAnswer.class.getClassLoader().getResourceAsStream(propertiesFile)) {
			var props = new Properties();
			props.load(in);
			log.info("Discovered semantic versions {} from resource '{}'", props, propertiesFile);
			knownActive.set(new ActiveVersions(
					asSemVer((String)props.get(protoKey)),
					asSemVer((String)props.get(servicesKey))));
		} catch (Exception surprising) {
			log.warn(
					"Failed to read versions from resource '{}' (keys '{}' and '{}')",
					propertiesFile,
					protoKey,
					servicesKey,
					surprising);
		}
		return knownActive.get();
	}

	public static SemanticVersion asSemVer(String value) {
		long[] parts = asDotDelimitedLongArray(value);
		return SemanticVersion.newBuilder()
				.setMajor((int)parts[0])
				.setMinor((int)parts[1])
				.setPatch((int)parts[2])
				.build();
	}
}
