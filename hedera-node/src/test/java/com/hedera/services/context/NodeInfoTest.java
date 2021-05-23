package com.hedera.services.context;

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NodeInfoTest {
	private final long nodeId = 0L;

	@Mock
	private Address address;
	@Mock
	private AddressBook book;

	private NodeInfo subject;

	@BeforeEach
	void setUp() {
		subject = new NodeInfo(() -> book);
	}

	@Test
	void understandsStaked() {
		givenEntry(nodeId, 1L);

		// expect:
		assertFalse(subject.isZeroStake(nodeId));
	}

	@Test
	void understandsZeroStaked() {
		givenEntry(nodeId, 0L);

		// expect:
		assertTrue(subject.isZeroStake(nodeId));
	}

	@Test
	void understandsMissing() {
		// expect:
		assertTrue(subject.isZeroStake(-1));
		assertTrue(subject.isZeroStake(1));
	}

	private void givenEntry(long id, long stake) {
		given(address.getStake()).willReturn(stake);
		given(book.getAddress(id)).willReturn(address);
		given(book.getSize()).willReturn(1);
	}
}