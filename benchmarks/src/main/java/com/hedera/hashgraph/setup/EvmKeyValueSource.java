package com.hedera.hashgraph.setup;

/*-
 * ‌
 * Hedera Services JMH benchmarks
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

import com.google.common.primitives.Ints;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.SplittableRandom;

public class EvmKeyValueSource {
	private static final int numKeys = 163_840;
	private static final long entropySeed = 42_424_242L;
	private static final UInt256[] keys = new UInt256[numKeys];
	private static final SplittableRandom r = new SplittableRandom(entropySeed);
	static {
		final var keyBytes = new byte[32];
		for (int i = 0; i < numKeys; i++) {
			r.nextBytes(keyBytes);
			keys[i] = UInt256.fromBytes(Bytes.wrap(keyBytes));
		}
	}

	public static KvMutationBatch randomMutationBatch(
			final int size,
			final int maxContractNum,
			final int maxKvPerContract,
			final double removalProb
	) {
		final var contracts = new AccountID[size];
		final var keys = new UInt256[size];
		final var values = new UInt256[size];

		for (int i = 0; i < size; i++) {
			contracts[i] = AccountID.newBuilder()
					.setAccountNum(r.nextInt(maxContractNum) + 1)
					.build();
			keys[i] = uniqueKey(r.nextInt(maxKvPerContract));
			values[i] = (r.nextDouble() < removalProb) ? UInt256.ZERO : keys[i];
		}

		return new KvMutationBatch(contracts, keys, values);
	}

	public static UInt256 uniqueKey(final int n) {
		if (n < numKeys) {
			return keys[n % numKeys];
		} else {
			final var baseKey = keys[n % numKeys];
			final var keyBytes = new byte[32];
			System.arraycopy(baseKey.toArrayUnsafe(), 0, keyBytes, 0, keyBytes.length);
			final var noise = Ints.toByteArray(n);
			for (int i = 0; i < keyBytes.length; i++) {
				keyBytes[i] ^= noise[i % noise.length];
			}
			return UInt256.fromBytes(Bytes.wrap(keyBytes));
		}
	}
}
