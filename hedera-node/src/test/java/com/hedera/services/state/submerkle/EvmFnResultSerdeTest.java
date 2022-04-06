package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.SplittableRandom;

import static com.hedera.services.state.submerkle.EvmFnResult.RELEASE_0250_VERSION;
import static com.hedera.test.utils.TxnUtils.assertSerdeWorks;

class EvmFnResultSerdeTest {
	private static final SeededPropertySource propertySource = new SeededPropertySource(new SplittableRandom());

	@Test
	void serdeWorksWithNoStateChanges() throws IOException, ConstructableRegistryException {
		registerResultConstructable();

		final var subject = propertySource.nextEvmFnResult();
		subject.setStateChanges(Collections.emptyMap());

		assertSerdeWorks(subject, EvmFnResult::new, RELEASE_0250_VERSION);
	}

	@Test
	void serdeWorksWithStateChanges() throws IOException, ConstructableRegistryException {
		registerResultConstructable();

		for (int i = 0; i < 10; i++) {
			final var subject = propertySource.nextEvmFnResult();

			assertSerdeWorks(subject, EvmFnResult::new, RELEASE_0250_VERSION);
		}
	}

	private void registerResultConstructable() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(TxnId.class, TxnId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowance.class, FcTokenAllowance::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowanceId.class, FcTokenAllowanceId::new));
	}
}
