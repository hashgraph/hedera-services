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
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.SplittableRandom;

class ExpirableTxnRecordSerdeTest {
	private static final SeededPropertySource propertySource = new SeededPropertySource(new SplittableRandom());

	@Test
	void serdeWorksWithMiscAllowances() throws IOException, ConstructableRegistryException {
		registerRecordConstructables();

		final var builder = ExpirableTxnRecord.newBuilder()
				.setTxnId(propertySource.nextTxnId())
				.setCryptoAllowances(propertySource.nextCryptoAllowances(
						10, 2, 3))
				.setFungibleTokenAllowances(propertySource.nextFungibleAllowances(
						10, 10, 2, 3));

		final var subject = builder.build();

		TxnUtils.assertSerdeWorks(subject, ExpirableTxnRecord::new, ExpirableTxnRecord.RELEASE_0250_VERSION);
	}

	public static EntityId randomEntityId() {
		return propertySource.nextEntityId();
	}

	public static byte[] randomBytes(int n) {
		return propertySource.nextBytes(n);
	}

	public static Address randomAddress() {
		return propertySource.nextAddress();
	}

	public static Pair<Bytes, Bytes> randomStateChangePair() {
		if (propertySource.nextBoolean()) {
			return Pair.of(propertySource.nextEvmWord(), null);
		} else {
			return Pair.of(propertySource.nextEvmWord(), propertySource.nextEvmWord());
		}
	}

	public static Bytes randomEvmWord() {
		if (propertySource.nextBoolean()) {
			return Bytes.ofUnsignedLong(propertySource.nextLong()).trimLeadingZeros();
		} else {
			return Bytes.wrap(randomBytes(32)).trimLeadingZeros();
		}
	}

	public static long randomNumInScope() {
		return propertySource.nextInRangeLong();
	}

	public static void registerRecordConstructables() throws ConstructableRegistryException {
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
