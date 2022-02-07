package com.hedera.services.utils;

import com.hedera.services.ledger.accounts.AliasManager;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AddressComparatorTest {
	@Mock
	private AliasManager aliasManager;

	@Test
	void compareTest() {
		var subject = AddressComparator.INSTANCE;
		subject.aliasManager = aliasManager;

		final var first = Address.fromHexString("0x0001");
		final var second = Address.fromHexString("0x0002");
		final var third = Address.fromHexString("0x00ef");
		final var fourth = Address.fromHexString("0x00ff");

		given(aliasManager.isInUse(first)).willReturn(false);
		given(aliasManager.isInUse(second)).willReturn(false);
		given(aliasManager.isInUse(third)).willReturn(true);
		given(aliasManager.isInUse(fourth)).willReturn(true);

		var result = subject.compare(first, second);
		assertEquals(-1, result);

		result = subject.compare(first, third);
		assertEquals(-1, result);

		result = subject.compare(first, fourth);
		assertEquals(-1, result);

		result = subject.compare(second, fourth);
		assertEquals(-1, result);

		result = subject.compare(second, third);
		assertEquals(-1, result);

		result = subject.compare(third, fourth);
		assertEquals(-1, result);
	}

}