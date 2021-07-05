package com.hedera.services.pricing;

import com.hederahashgraph.api.proto.java.FeeComponents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsableResourceTest {
	private final FeeComponents comps = FeeComponents.newBuilder()
			.setConstant(1)
			.setBpt(2)
			.setVpt(3)
			.setRbh(4)
			.setSbh(5)
			.setGas(6)
			.setBpr(7)
			.setSbpr(8)
			.build();

	@Test
	void noGetterTypos() {
		assertEquals(1, UsableResource.CONSTANT.getter().applyAsLong(comps));
		assertEquals(2, UsableResource.BPT.getter().applyAsLong(comps));
		assertEquals(3, UsableResource.VPT.getter().applyAsLong(comps));
		assertEquals(4, UsableResource.RBH.getter().applyAsLong(comps));
		assertEquals(5, UsableResource.SBH.getter().applyAsLong(comps));
		assertEquals(6, UsableResource.GAS.getter().applyAsLong(comps));
		assertEquals(7, UsableResource.BPR.getter().applyAsLong(comps));
		assertEquals(8, UsableResource.SBPR.getter().applyAsLong(comps));
	}
}