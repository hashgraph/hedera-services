package com.hedera.services.context;

import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith({ LogCaptureExtension.class, MockitoExtension.class })
class NodeInfoTest {
	private final long nodeId = 0L;

	@Mock
	private Address address;
	@Mock
	private AddressBook book;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private NodeInfo subject;

	@BeforeEach
	void setUp() {
		subject = new NodeInfo(nodeId, () -> book);
	}

	@Test
	void understandsStaked() {
		givenEntryWithStake(nodeId, 1L);

		// expect:
		assertFalse(subject.isZeroStake(nodeId));
		assertFalse(subject.isSelfZeroStake());
	}

	@Test
	void understandsZeroStaked() {
		givenEntryWithStake(nodeId, 0L);

		// expect:
		assertTrue(subject.isZeroStake(nodeId));
		assertTrue(subject.isSelfZeroStake());
	}

	@Test
	void interpretsMissingAsZeroStake() {
		// expect:
		assertTrue(subject.isZeroStake(-1));
		assertTrue(subject.isZeroStake(1));
	}

	@Test
	void understandsAccountIsInMemo() {
		// setup:
		final var memo = "0.0.3";
		final var expectedAccount = IdUtils.asAccount(memo);

		givenEntryWithMemoAndStake(nodeId, memo, 1L);

		// expect:
		assertEquals(expectedAccount, subject.accountOf(nodeId));
		assertEquals(expectedAccount, subject.selfAccount());
		assertTrue(subject.hasSelfAccount());
	}

	@Test
	void logsErrorOnMissingAccountForNonZeroStake() {
		givenEntryWithMemoAndStake(nodeId, "Oops!", 1L);

		// when:
		subject.readBook();

		// then:
		assertThat(
				logCaptor.errorLogs(),
				contains(startsWith("Cannot parse account for staked node id 0, potentially fatal")));
		assertFalse(subject.hasSelfAccount());
	}

	@Test
	void doesNotLogErrorOnMissingAccountForZeroStake() {
		givenEntryWithMemoAndStake(nodeId, "Oops!", 0L);

		// when:
		subject.readBook();

		// then:
		assertTrue(logCaptor.errorLogs().isEmpty());
	}

	@Test
	void throwsIaeOnMissingNode() {
		givenEntryWithMemoAndStake(nodeId, "0.0.3", 1L);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.accountOf(-1L));
		assertThrows(IllegalArgumentException.class, () -> subject.accountOf(1L));
	}

	@Test
	void throwsIaeOnMissingAccount() {
		givenEntryWithMemoAndStake(nodeId, "ZERO-STAKE", 0L);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.accountOf(nodeId));
	}

	private void givenEntryWithStake(long id, long stake) {
		given(address.getStake()).willReturn(stake);
		given(address.getMemo()).willReturn("0.0." + (3 + id));
		given(book.getAddress(id)).willReturn(address);
		given(book.getSize()).willReturn(1);
	}

	private void givenEntryWithMemoAndStake(long id, String memo, long stake) {
		given(address.getStake()).willReturn(stake);
		given(address.getMemo()).willReturn(memo);
		given(book.getAddress(id)).willReturn(address);
		given(book.getSize()).willReturn(1);
	}


}