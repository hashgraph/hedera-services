package com.hedera.services.bdd.suites.utils.sysfiles;

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

import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

public class KeyValueConfigPojo {
	Map<String, String> properties;

	public static KeyValueConfigPojo configFrom(ServicesConfigurationList proto) {
		var pojo = new KeyValueConfigPojo();
		pojo.setProperties(new TreeMap<>());
		proto.getNameValueList()
				.stream()
				.sorted(Comparator.comparing(Setting::getName))
				.forEach(setting -> pojo.getProperties().put(setting.getName(), setting.getValue()));
		return pojo;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}
}
