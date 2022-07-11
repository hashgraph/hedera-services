package com.hedera.services.state.migration;

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

import com.hedera.services.context.properties.SerializableSemVers;

import javax.annotation.Nullable;

import static com.hedera.services.context.properties.SerializableSemVers.forHapiAndHedera;

/**
 * Gives the versions of the current and previous world states.
 */
public final class StateVersions {
	// For the record,
	//   - Release 0.7.x was state version 1
	//   - Release 0.8.x was state version 2
	//   - Release 0.9.x was state version 3
	//   - Release 0.10.x was state version 4
	//   - Release 0.11.x was state version 5
	//   - Release 0.12.x was state version 6
	//   - Release 0.13.x was state version 7
	//   - Release 0.14.x was state version 8
	//   - Release 0.15.x was state version 9
	//   - Release 0.16.x was state version 10
	//   - Release 0.17.x was state version 11
	//   - Release 0.18.x was state version 12
	//   - Release 0.19.x and 0.20.x were state version 13
	//   - Release 0.21.x was state version 14
	//   - Release 0.22.x and 0.23.x were state version 15
	//   - Release 0.24.x was state version 17
	public static final int RELEASE_025X_VERSION = 18;
	public static final int RELEASE_0260_VERSION = 19;
	public static final int RELEASE_0270_VERSION = 20;
	public static final int RELEASE_0280_VERSION = 21;

	public static final SerializableSemVers LAST_025X_VERSION = forHapiAndHedera("0.25.1", "0.25.4");
	public static final SerializableSemVers FIRST_026X_VERSION = forHapiAndHedera("0.26.0", "0.26.0");
	public static final SerializableSemVers LAST_026X_VERSION = forHapiAndHedera("0.26.0", "0.26.3");
	public static final SerializableSemVers FIRST_027X_VERSION = forHapiAndHedera("0.27.0", "0.27.0");
	public static final SerializableSemVers LAST_027X_VERSION = forHapiAndHedera("0.27.0", "0.27.3");

	public static final int MINIMUM_SUPPORTED_VERSION = RELEASE_025X_VERSION;
	public static final int CURRENT_VERSION = RELEASE_0280_VERSION;

	@Nullable
	public static SerializableSemVers lastSoftwareVersionOf(final int stateVersion) {
		return switch (stateVersion) {
			case RELEASE_025X_VERSION -> LAST_025X_VERSION;
			case RELEASE_0260_VERSION -> LAST_026X_VERSION;
			case RELEASE_0270_VERSION -> LAST_027X_VERSION;
			default -> null;
		};
	}

	private StateVersions() {
		throw new UnsupportedOperationException("Utility Class");
	}
}
