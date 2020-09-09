package com.hedera.services.context.properties;

/*-
 * ‌
 * Hedera Services Node
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

public class NodeLocalProperties {
	private final PropertySource properties;

	private int port;
	private int tlsPort;
	private int precheckLookupRetries;
	private int precheckLookupRetryBackoffMs;
	private Profile activeProfile;

	public NodeLocalProperties(PropertySource properties) {
		this.properties = properties;

		reload();
	}

	public void reload() {
		port = properties.getIntProperty("grpc.port");
		tlsPort = properties.getIntProperty("grpc.tlsPort");
		precheckLookupRetries = properties.getIntProperty("precheck.account.maxLookupRetries");
		precheckLookupRetryBackoffMs = properties.getIntProperty("precheck.account.lookupRetryBackoffIncrementMs");
		activeProfile = properties.getProfileProperty("hedera.profiles.active");
	}

	public int port() {
		return port;
	}

	public int tlsPort() {
		return tlsPort;
	}

	public int precheckLookupRetries() {
		return precheckLookupRetries;
	}

	public int precheckLookupRetryBackoffMs() {
		return precheckLookupRetryBackoffMs;
	}

	public Profile activeProfile() {
		return activeProfile;
	}
}
