package com.hedera.services.bdd.spec.props;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.SuiteRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.*;

public class MapPropertySource implements HapiPropertySource {
	private static final Logger log = LogManager.getLogger(MapPropertySource.class);
	private static final Set<String> KEYS_TO_CENSOR = Set.of(
		"startupAccounts.literal", "default.payer.pemKeyPassphrase"
	);

	private final Map props;

	public MapPropertySource(Map props) {
		Map<String, Object> typedProps = (Map<String, Object>)props;
		var filteredProps = typedProps.entrySet()
				.stream()
				.filter(entry -> !KEYS_TO_CENSOR.contains(entry.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		log.info("Initializing a MapPropertySource from " + filteredProps);
		this.props = props;
	}

	@Override
	public String get(String property) {
		return (String)props.get(property);
	}

	@Override
	public boolean has(String property) {
		return props.containsKey(property);
	}
}
