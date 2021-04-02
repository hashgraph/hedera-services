package com.hedera.services.legacy.config;

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

import com.hedera.services.legacy.logic.CustomProperties;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class PropertiesLoader {
	public static final Logger log = LogManager.getLogger(PropertiesLoader.class);

	public static CustomProperties applicationProps;

	public static void populateApplicationPropertiesWithProto(ServicesConfigurationList serviceConfigList) {
		Properties properties = new Properties();
		serviceConfigList.getNameValueList().forEach(setting -> {
			properties.setProperty(setting.getName(), setting.getValue());
		});
		applicationProps = new CustomProperties(properties);
		AsyncPropertiesObject.loadAsynchProperties(applicationProps);
		log.info("Application Properties Populated with these values :: " + applicationProps.getCustomProperties());
	}

	public static String getDefaultListeningNodeAccount() {
		return AsyncPropertiesObject.getDefaultListeningNodeAccount();
	}

	public static int getUniqueListeningPortFlag() {
		return AsyncPropertiesObject.getUniqueListeningPortFlag();
	}

	public static String getSaveAccounts() {
		return AsyncPropertiesObject.getSaveAccounts();
	}

	public static String getExportedAccountPath() {
		return AsyncPropertiesObject.getExportedAccountPath();
	}
}
