package com.hedera.services.state;

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

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.swirlds.common.utility.CommonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.Charset;

import static com.hedera.services.state.StateModule.provideStateViews;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class StateModuleTest {
	@Mock
	private TokenStore tokenStore;
	@Mock
	private ScheduleStore scheduleStore;
	@Mock
	private MutableStateChildren workingState;
	@Mock
	private LegacyEd25519KeyReader b64KeyReader;
	@Mock
	private PropertySource properties;
	@Mock
	private NetworkInfo networkInfo;

	@Test
	void providesDefaultCharset() {
		// expect:
		assertEquals(Charset.defaultCharset(), StateModule.provideNativeCharset().get());
	}

	@Test
	void canGetSha384() {
		// expect:
		assertDoesNotThrow(() -> StateModule.provideDigestFactory().forName("SHA-384"));
	}

	@Test
	void notificationEngineAvail() {
		// expect:
		assertDoesNotThrow(() -> StateModule.provideNotificationEngine().get());
	}

	@Test
	void viewUsesWorkingStateChildren() {
		final var viewFactory = provideStateViews(scheduleStore, workingState, networkInfo);

		assertDoesNotThrow(viewFactory::get);
	}

	@Test
	void looksUpExpectedKey() {
		// setup:
		final var keystoreLoc = "somewhere";
		final var storeName = "far";
		final var keyBytes = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();

		given(properties.getStringProperty("bootstrap.genesisB64Keystore.path")).willReturn(keystoreLoc);
		given(properties.getStringProperty("bootstrap.genesisB64Keystore.keyName")).willReturn(storeName);
		given(b64KeyReader.hexedABytesFrom(keystoreLoc, storeName)).willReturn(CommonUtils.hex(keyBytes));

		// when:
		final var keySupplier = StateModule.provideSystemFileKey(b64KeyReader, properties);
		// and:
		final var key = keySupplier.get();

		// then:
		assertArrayEquals(keyBytes, key.getEd25519());
	}
}
