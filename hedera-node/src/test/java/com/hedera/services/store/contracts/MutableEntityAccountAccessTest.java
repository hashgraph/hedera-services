package com.hedera.services.store.contracts;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MutableEntityAccountAccessTest {
	@Mock
	private HederaLedger ledger;
	@Mock
	private Supplier<VirtualMap<ContractKey, ContractValue>> storage;
	@Mock
	private Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> bytecode;

	private MutableEntityAccess subject;

	private AccountID id = IdUtils.asAccount("0.0.1234");

	@BeforeEach
	void setUp() {
		subject = new MutableEntityAccess(ledger, storage, bytecode);
		when(ledger.getBalance(id)).thenReturn(1234L);
	}

	// need a lot of test coverage. Adding test to resolve sonar issues
	@Test
	void testBalance() {
		final var balance = subject.getBalance(id);
		assertEquals(ledger.getBalance(id), balance);
	}
}