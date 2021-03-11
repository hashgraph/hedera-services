package com.hedera.services.yahcli.output;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;

public enum CommonMessages {
	COMMON_MESSAGES;

	public void printGlobalInfo(ConfigManager config) {
		var msg = String.format("Targeting %s, paying with %s", config.getTargetName(), ConfigUtils.asId(config.getDefaultPayer()));
		System.out.println(msg);
	}

	public String fq(Integer num) {
		return "0.0." + num;
	}
}
