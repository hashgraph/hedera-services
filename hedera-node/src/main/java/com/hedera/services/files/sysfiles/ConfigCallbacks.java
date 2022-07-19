package com.hedera.services.files.sysfiles;

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

import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.hedera.services.throttling.annotations.ScheduleThrottle;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Singleton
public class ConfigCallbacks {
	private final PropertySources propertySources;
	private final HapiOpPermissions hapiOpPermissions;
	private final GlobalDynamicProperties dynamicProps;
	private final FunctionalityThrottling hapiThrottling;
	private final FunctionalityThrottling handleThrottling;
	private final FunctionalityThrottling scheduleThrottling;
	private final Supplier<MerkleNetworkContext> networkCtx;

	@Inject
	public ConfigCallbacks(
			final HapiOpPermissions hapiOpPermissions,
			final GlobalDynamicProperties dynamicProps,
			final PropertySources propertySources,
			final @HapiThrottle FunctionalityThrottling hapiThrottling,
			final @HandleThrottle FunctionalityThrottling handleThrottling,
			final @ScheduleThrottle FunctionalityThrottling scheduleThrottling,
			final Supplier<MerkleNetworkContext> networkCtx
	) {
		this.dynamicProps = dynamicProps;
		this.propertySources = propertySources;
		this.hapiOpPermissions = hapiOpPermissions;
		this.hapiThrottling = hapiThrottling;
		this.handleThrottling = handleThrottling;
		this.scheduleThrottling = scheduleThrottling;
		this.networkCtx = networkCtx;
	}

	public Consumer<ServicesConfigurationList> propertiesCb() {
		return config -> {
			propertySources.reloadFrom(config);
			dynamicProps.reload();
			hapiThrottling.applyGasConfig();
			handleThrottling.applyGasConfig();
			scheduleThrottling.applyGasConfig();
			networkCtx.get().renumberBlocksToMatch(dynamicProps.knownBlockValues());
		};
	}

	public Consumer<ServicesConfigurationList> permissionsCb() {
		return hapiOpPermissions::reloadFrom;
	}
}
