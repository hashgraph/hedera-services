package com.hedera.services.state.initialization;

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

public interface SystemFilesManager {
	void createAddressBookIfMissing();
	void createNodeDetailsIfMissing();
	void createUpdateZipFileIfMissing();

	/* Ensure files 0.0.111 and 0.0.112 exist in state, creating them
	 * from the FeeSchedules.json and bootstrap.properties resources/files
	 * if they are missing. (The {@code HfsSystemFilesManager} will signal interested
	 * components of the loaded files via a callback.) */
	void loadExchangeRates();
	void loadFeeSchedules();

	/* Ensure files 0.0.121 and 0.0.122 exist in state, creating them from
	 * the application.properties and api-permission.properties assets if they
	 * are missing. (The {@code HfsSystemFilesManager} will signal interested
	 * components of the loaded files via a callback.) */
	void loadApiPermissions();
	void loadApplicationProperties();

	default void loadAllSystemFiles() {
		loadApplicationProperties();
		loadApiPermissions();
		loadFeeSchedules();
		loadExchangeRates();

		setFilesLoaded();
	}
	void setFilesLoaded();
	boolean areFilesLoaded();
}
