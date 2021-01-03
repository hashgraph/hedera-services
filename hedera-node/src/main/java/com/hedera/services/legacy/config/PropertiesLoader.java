package com.hedera.services.legacy.config;

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

import com.hedera.services.context.domain.security.PermissionedAccountsRange;
import com.hedera.services.legacy.logic.CustomProperties;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PropertiesLoader {
	public static final Logger log = LogManager.getLogger(PropertiesLoader.class);

	public static CustomProperties applicationProps;
	public static CustomProperties apiProperties;
	public static List<Runnable> updateCallbacks = new ArrayList<>();

	public static void registerUpdateCallback(Runnable cb) {
		updateCallbacks.add(cb);
	}

	public static void populateApplicationPropertiesWithProto(ServicesConfigurationList serviceConfigList) {
		Properties properties = new Properties();
		serviceConfigList.getNameValueList().forEach(setting -> {
			properties.setProperty(setting.getName(), setting.getValue());
		});
		applicationProps = new CustomProperties(properties);
		AsyncPropertiesObject.loadAsynchProperties(applicationProps);
		log.info("Application Properties Populated with these values :: " + applicationProps.getCustomProperties());
		updateCallbacks.forEach(Runnable::run);
	}

	public static void populateAPIPropertiesWithProto(ServicesConfigurationList serviceConfigList) {
		Properties properties = new Properties();
		serviceConfigList.getNameValueList().forEach(setting -> {
			properties.setProperty(setting.getName(), setting.getValue());
		});
		apiProperties = new CustomProperties(properties);
		AsyncPropertiesObject.loadApiProperties(apiProperties);
		log.info("API Properties Populated with these values :: " + apiProperties.getCustomProperties());
	}

	public static long getRecordLogPeriod() {
		return AsyncPropertiesObject.getRecordLogPeriod();
	}

	public static String getRecordLogDir() {
		return AsyncPropertiesObject.getRecordLogDir();
	}

	public static int getRecordStreamQueueCapacity() {
		return AsyncPropertiesObject.getRecordStreamQueueCapacity();
	}

	public static boolean isEnableRecordStreaming() {
		return AsyncPropertiesObject.isEnableRecordStreaming();
	}

	public static Map<String, PermissionedAccountsRange> getApiPermission() {
		return AsyncPropertiesObject.getApiPermission();
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

	public static long getNettyKeepAliveTime() {
		return AsyncPropertiesObject.getNettyKeepAliveTime();
	}

	public static long getNettyKeepAliveTimeOut() {
		return AsyncPropertiesObject.getNettyKeepAliveTimeOut();
	}

	public static long getNettyMaxConnectionAge() {
		return AsyncPropertiesObject.getNettyMaxConnectionAge();
	}

	public static long getNettyMaxConnectionAgeGrace() {
		return AsyncPropertiesObject.getNettyMaxConnectionAgeGrace();
	}

	public static long getNettyMaxConnectionIdle() {
		return AsyncPropertiesObject.getNettyMaxConnectionIdle();
	}

	public static int getNettyMaxConcurrentCalls() {
		return AsyncPropertiesObject.getNettyMaxConcurrentCalls();
	}

	public static int getNettyFlowControlWindow() {
		return AsyncPropertiesObject.getNettyFlowControlWindow();
	}

	public static String getNettyMode() {
		return AsyncPropertiesObject.getNettyMode();
	}

	public static int getBinaryObjectQueryRetryTimes() {
		return AsyncPropertiesObject.getBinaryObjectQueryRetryTimes();
	}
}
