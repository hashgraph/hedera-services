package com.hedera.services.state.submerkle;

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

import com.hederahashgraph.api.proto.java.FixedFee;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedFeeSpecTest {
	@Test
	void factoryWorksForHbar() {
		// setup:
		final var hbarGrpc = FixedFee.newBuilder()
				.setAmount(123)
				.build();
		final var expected = new FixedFeeSpec(123, null);

		// when
		final var actual = FixedFeeSpec.fromGrpc(hbarGrpc);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void factoryWorksForHts() {
		// setup:
		final var denom = new EntityId(1, 2, 3);
		final var htsGrpc = FixedFee.newBuilder()
				.setAmount(123)
				.setDenominatingTokenId(denom.toGrpcTokenId())
				.build();
		final var expected = new FixedFeeSpec(123, denom);

		// when
		final var actual = FixedFeeSpec.fromGrpc(htsGrpc);

		// then:
		assertEquals(expected, actual);
	}

	@Test
	void reprWorksForHbar() {
		// setup:
		final var hbarGrpc = FixedFee.newBuilder()
				.setAmount(123)
				.build();

		// given:
		final var subject = new FixedFeeSpec(123, null);

		// when:
		final var repr = subject.asGrpc();

		// then:
		assertEquals(hbarGrpc, repr);
	}

	@Test
	void reprWorksForHts() {
		// setup:
		final var denom = new EntityId(1, 2, 3);
		final var htsGrpc = FixedFee.newBuilder()
				.setAmount(123)
				.setDenominatingTokenId(denom.toGrpcTokenId())
				.build();

		// given:
		final var subject = new FixedFeeSpec(123, denom);

		// when:
		final var repr = subject.asGrpc();

		// then:
		assertEquals(htsGrpc, repr);
	}
}
