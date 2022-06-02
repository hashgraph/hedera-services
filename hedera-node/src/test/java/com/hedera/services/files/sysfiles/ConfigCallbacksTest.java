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
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ConfigCallbacksTest {
	private static final String literalBlockValues =
			"c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666";
	private static final KnownBlockValues blockValues = KnownBlockValues.from(literalBlockValues);

	@Mock
	private GlobalDynamicProperties dynamicProps;
	@Mock
	private PropertySources propertySources;
	@Mock
	private HapiOpPermissions hapiOpPermissions;
	@Mock
	private FunctionalityThrottling functionalityThrottling;
	@Mock
	private MerkleNetworkContext networkCtx;

	private ConfigCallbacks subject;

	@BeforeEach
	void setUp() {
		subject = new ConfigCallbacks(
				hapiOpPermissions, dynamicProps,
				propertySources, functionalityThrottling, functionalityThrottling, functionalityThrottling, () -> networkCtx);
	}

	@Test
	void propertiesCbAsExpected() {
		given(dynamicProps.knownBlockValues()).willReturn(blockValues);
		var config = ServicesConfigurationList.getDefaultInstance();

		// when:
		subject.propertiesCb().accept(config);

		// then:
		verify(propertySources).reloadFrom(config);
		verify(dynamicProps).reload();
		verify(functionalityThrottling, times(3)).applyGasConfig();
		verify(networkCtx).renumberBlocksToMatch(blockValues);
	}

	@Test
	void permissionsCbAsExpected() {
		var config = ServicesConfigurationList.getDefaultInstance();

		// when:
		subject.permissionsCb().accept(config);

		// then:
		verify(hapiOpPermissions).reloadFrom(config);
	}
}
