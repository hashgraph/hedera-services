package com.hedera.node.app.spi.key;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.Key;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class HederaKeyTest {
	@Test
	void canConvertToHederaKey() {
		final var id = IdUtils.asContract("1.2.3");
		final var input = Key.newBuilder().setDelegatableContractId(id).build();

		final var subject = HederaKey.asHederaKey(input);

		assertTrue(subject.isPresent());

		final var jkey = (JKey) subject.get();
		Assertions.assertTrue(jkey.hasDelegatableContractId());
	}
}
