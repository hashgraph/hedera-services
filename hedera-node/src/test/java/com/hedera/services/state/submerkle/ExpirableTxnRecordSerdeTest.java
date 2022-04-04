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

import com.google.common.primitives.Longs;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.test.utils.SerdeUtils;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.CommonUtils;
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
	private static final SplittableRandom r = new SplittableRandom();

	@Test
	void serdeWorksWithMiscAllowances() throws IOException, ConstructableRegistryException {
		registerRecordConstructables();

		final var builder = ExpirableTxnRecord.newBuilder()
				.setTxnId(randomTxnId())
				.setCryptoAllowances(SerdeUtils.randomCryptoAllowances(
						r, 10, 2, 3))
				.setFungibleTokenAllowances(SerdeUtils.randomFungibleAllowances(
						r, 10, 10, 2, 3));

		final var subject = builder.build();

		TxnUtils.assertSerdeWorks(subject, ExpirableTxnRecord::new, ExpirableTxnRecord.RELEASE_0250_VERSION);
	}

	public static EntityId randomEntityId() {
		return new EntityId(0, 0, r.nextLong(Long.MAX_VALUE));
	}

	public static byte[] randomBytes(int n) {
		final var ans = new byte[n];
		r.nextBytes(ans);
		return ans;
	}

	public static Address randomAddress() {
		byte[] ans = new byte[20];
		if (r.nextBoolean()) {
			r.nextBytes(ans);
		} else {
			System.arraycopy(Longs.toByteArray(randomNumInScope()), 0, ans, 12, 8);
		}
		return Address.fromHexString(CommonUtils.hex(ans));
	}

	public static Pair<Bytes, Bytes> randomStateChangePair() {
		if (r.nextBoolean()) {
			return Pair.of(randomEvmWord(), null);
		} else {
			return Pair.of(randomEvmWord(), randomEvmWord());
		}
	}

	public static Bytes randomEvmWord() {
		if (r.nextBoolean()) {
			return Bytes.ofUnsignedLong(r.nextLong()).trimLeadingZeros();
		} else {
			return Bytes.wrap(randomBytes(32)).trimLeadingZeros();
		}
	}

	public static long randomNumInScope() {
		return r.nextLong(BitPackUtils.MAX_NUM_ALLOWED);
	}

	public static TxnId randomTxnId() {
		return new TxnId(
				randomEntityId(),
				new RichInstant(r.nextLong(Long.MAX_VALUE), r.nextInt(1_000_000)),
				r.nextBoolean(),
				r.nextInt(1000));
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
