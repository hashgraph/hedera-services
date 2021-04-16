package com.hedera.services.context.properties;

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

import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.context.properties.BootstrapProperties.BOOTSTRAP_PROP_NAMES;

/**
 * Implements a {@link PropertySources} that re-resolves every property
 * reference by delegating to a supplier.
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
		log.info("Reloading global dynamic properties from {} candidates", config.getNameValueCount());
		dynamicGlobalProps.screenNew(config);
	}

	@Override
	public void assertSourcesArePresent() {
		assertPropertySourcesExist();
	}

	private void assertPropertySourcesExist() {
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
		var bootstrapPlusNodeProps = new ChainedSources(nodeProps, bootstrap);
		return new ChainedSources(dynamicGlobalProps, bootstrapPlusNodeProps);
	}

	private Map<String, Supplier<Object>> sourceMap() {
		Map<String, Supplier<Object>> source = new HashMap<>();

		/* Bootstrap properties, which must include defaults for every system property. */
		BOOTSTRAP_PROP_NAMES.forEach(name -> source.put(name, () -> bootstrapProps.getProperty(name)));

		/* Node-local properties. */
		source.put("iss.resetPeriod", () -> ISS_RESET_PERIOD_SECS);
		source.put("iss.roundsToDump", () -> ISS_ROUNDS_TO_DUMP);

		return source;
	}
}
