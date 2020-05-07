package com.hedera.services.legacy.services.context.properties;

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

import com.hedera.services.ServicesMain;
import com.hedera.services.context.properties.PropertySanitizer;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.logic.ApplicationConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DefaultPropertySanitizer implements PropertySanitizer {
	public static final Logger log = LogManager.getLogger(ServicesMain.class);

	@Override
	public void sanitize(PropertySources propertySources) {
		if (!PropertiesLoader.validExchangeRateAllowedPercentage()) {
			log.warn("Limited exchange rate percentage change to {}%!", ApplicationConstants.DEFAULT_EXCHANGE_RATE_ALLOWED_PERCENTAGE);
		}
	}
}
