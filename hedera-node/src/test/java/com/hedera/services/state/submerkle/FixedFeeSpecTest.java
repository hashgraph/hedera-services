package com.hedera.services.state.submerkle;

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