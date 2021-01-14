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

import com.hedera.services.legacy.config.PropertiesLoader;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.context.properties.BootstrapProperties.BOOTSTRAP_PROP_NAMES;
import static com.hedera.services.legacy.config.PropertiesLoader.getSaveAccounts;
import static com.hedera.services.legacy.config.PropertiesLoader.getUniqueListeningPortFlag;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.API_THROTTLING_CONFIG_PREFIX;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.API_THROTTLING_PREFIX;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY;
import static com.hedera.services.throttling.ThrottlingPropsBuilder.DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_BURST_PROPERTY;
import static com.hedera.services.throttling.bucket.BucketConfig.DEFAULT_CAPACITY_PROPERTY;

/**
 * Implements a {@link PropertySources} that re-resolves every property
 * reference by delegating to a {@link PropertiesLoader} method or other
 * supplier.
 *
 * The main purpose of this implementation is standardize property naming
 * and access conventions across the codebase, which will greatly simplify
 * the task of refactoring {@link PropertiesLoader}.
 *
 * @author Michael Tinker
 */
public class StandardizedPropertySources implements PropertySources {
	public static final Logger log = LogManager.getLogger(StandardizedPropertySources.class);

	public static Supplier<ScreenedNodeFileProps> nodePropertiesSupplier = ScreenedNodeFileProps::new;
	public static Supplier<ScreenedSysFileProps> dynamicGlobalPropsSupplier = ScreenedSysFileProps::new;

	private static final int ISS_RESET_PERIOD_SECS = 30;
	private static final int ISS_ROUNDS_TO_DUMP = 5;

	private final PropertySource bootstrapProps;
	private final Predicate<String> fileSourceExists;
	private final Map<String, Object> throttlePropsFromSysFile = new HashMap<>();

	final ScreenedSysFileProps dynamicGlobalProps;
	final ScreenedNodeFileProps nodeProps;

	public StandardizedPropertySources(
			PropertySource bootstrapProps,
			Predicate<String> fileSourceExists
	) {
		this.bootstrapProps = bootstrapProps;
		this.fileSourceExists = fileSourceExists;

		nodeProps = nodePropertiesSupplier.get();
		dynamicGlobalProps = dynamicGlobalPropsSupplier.get();
	}

	public void reloadFrom(ServicesConfigurationList config) {
		log.info("Updating throttle props from {} candidates", config.getNameValueCount());
		throttlePropsFromSysFile.clear();
		for (Setting setting : config.getNameValueList())  {
			var name = setting.getName();
			if (!name.startsWith(API_THROTTLING_PREFIX)) {
				continue;
			}
			if (isDoubleProp(name)) {
				putDouble(name, setting.getValue());
			} else {
				throttlePropsFromSysFile.put(name, setting.getValue());
			}
		}
		dynamicGlobalProps.screenNew(config);
	}

	private static final Set<String> DEFAULT_DOUBLE_PROPS = Set.of(
			DEFAULT_BURST_PROPERTY,
			DEFAULT_CAPACITY_PROPERTY,
			DEFAULT_TXN_CAPACITY_REQUIRED_PROPERTY,
			DEFAULT_QUERY_CAPACITY_REQUIRED_PROPERTY);
	private boolean isDoubleProp(String name) {
		if (DEFAULT_DOUBLE_PROPS.contains(name)) {
			return true;
		}
		if (name.endsWith("burstPeriod") || name.endsWith("capacity") || name.endsWith("capacityRequired")) {
			return true;
		}
		return false;
	}
	private void putDouble(String name, String literal) {
		try {
			throttlePropsFromSysFile.put(name, Double.parseDouble(literal));
		} catch (NumberFormatException nfe) {
			log.warn("Ignoring config: {}={}", name, literal, nfe);
		}
	}

	@Override
	public void assertSourcesArePresent() {
		assertPropertySourcesExist();
	}

	private void assertPropertySourcesExist() {
		assertFileSourceExists(bootstrapProps.getStringProperty("bootstrap.networkProperties.path"));
		assertFileSourceExists(bootstrapProps.getStringProperty("bootstrap.hapiPermissions.path"));
	}

	private void assertFileSourceExists(String path) {
		if (!fileSourceExists.test(path)) {
			throw new IllegalStateException(String.format("Fatal error, no '%s' file exists!", path));
		}
	}

	@Override
	public PropertySource asResolvingSource() {
		var bootstrap = new SupplierMapPropertySource(sourceMap());
		var bootstrapPlusThrottleProps = new DeferringPropertySource(bootstrap, throttlePropsFromSysFile);
		var bootstrapPlusThrottlePlusNodeProps = new ChainedSources(nodeProps, bootstrapPlusThrottleProps);
		return new ChainedSources(dynamicGlobalProps, bootstrapPlusThrottlePlusNodeProps);
	}

	private Map<String, Supplier<Object>> sourceMap() {
		Map<String, Supplier<Object>> source = new HashMap<>();

		/* Bootstrap properties, which must include defaults for every system property. */
		BOOTSTRAP_PROP_NAMES.forEach(name -> source.put(name, () -> bootstrapProps.getProperty(name)));

		/* Global/dynamic properties. */
		source.put("hedera.recordStream.logDir", PropertiesLoader::getRecordLogDir);
		source.put("hedera.recordStream.logPeriod", PropertiesLoader::getRecordLogPeriod);

		source.put("binary.object.query.retry.times", PropertiesLoader::getBinaryObjectQueryRetryTimes);

		/* Node-local properties. */
		source.put("dev.defaultListeningNodeAccount", PropertiesLoader::getDefaultListeningNodeAccount);
		source.put("dev.onlyDefaultNodeListens", () -> getUniqueListeningPortFlag() != 1);
		source.put("hedera.accountsExportPath", PropertiesLoader::getExportedAccountPath);
		source.put("hedera.exportAccountsOnStartup", () -> getSaveAccounts().equals("YES"));
		source.put("iss.reset.periodSecs", () -> ISS_RESET_PERIOD_SECS);
		source.put("iss.roundsToDump", () -> ISS_ROUNDS_TO_DUMP);

		return source;
	}
}
