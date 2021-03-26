package com.hedera.services.files.interceptors;

import com.hedera.services.config.FileNumbers;
import com.hedera.services.sysfiles.domain.throttling.ThrottleBucket;
import com.hedera.services.sysfiles.logic.ErrorCodeUtils;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.swirlds.common.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_THROTTLE_DEFINITIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ThrottleDefsManagerTest {
	FileNumbers fileNums = new MockFileNumbers();
	FileID throttleDefs = fileNums.toFid(123L);

	@Mock
	AddressBook book;
	@Mock
	ThrottleBucket bucket;
	@Mock
	Function<ThrottleDefinitions, com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions> mockToPojo;
	@Mock
	Consumer<ThrottleDefinitions> postUpdateCb;

	ThrottleDefsManager subject;

	@BeforeEach
	void setUp() {
		subject = new ThrottleDefsManager(fileNums, () -> book, postUpdateCb);
	}

	@Test
	void rubberstampsAllUpdates() {
		// expect:
		assertEquals(ThrottleDefsManager.YES_VERDICT, subject.preAttrChange(throttleDefs, null));
	}

	@Test
	void throwsUnsupportedOnDelete() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.preDelete(throttleDefs));
	}

	@Test
	void invokesPostUpdateCbAsExpected() {
		// given:
		var newDef = ThrottleDefinitions.getDefaultInstance();

		// when:
		subject.postUpdate(throttleDefs, newDef.toByteArray());

		// then:
		verify(postUpdateCb).accept(newDef);
	}

	@Test
	public void reusesResponseCodeFromMapperFailure() {
		// setup:
		int nodes = 7;
		var pojoDefs = new com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions();
		pojoDefs.getBuckets().add(bucket);

		given(book.getSize()).willReturn(nodes);
		given(bucket.asThrottleMapping(nodes)).willThrow(new IllegalStateException(
				ErrorCodeUtils.exceptionMsgFor(
						NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION, "YIKES!"
				)));
		given(mockToPojo.apply(any())).willReturn(pojoDefs);
		// and:
		subject.toPojo = mockToPojo;

		// when:
		var verdict = subject.preUpdate(throttleDefs, ThrottleDefinitions.getDefaultInstance().toByteArray());

		// then:
		assertEquals(NODE_CAPACITY_NOT_SUFFICIENT_FOR_OPERATION, verdict.getKey());
		assertFalse(verdict.getValue());
	}

	@Test
	public void fallsBackToDefaultInvalidIfNoDetailsFromMapperFailure() {
		// setup:
		int nodes = 7;
		var pojoDefs = new com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions();
		pojoDefs.getBuckets().add(bucket);

		given(book.getSize()).willReturn(nodes);
		given(bucket.asThrottleMapping(nodes)).willThrow(new IllegalStateException("YIKES!"));
		given(mockToPojo.apply(any())).willReturn(pojoDefs);
		// and:
		subject.toPojo = mockToPojo;

		// when:
		var verdict = subject.preUpdate(throttleDefs, ThrottleDefinitions.getDefaultInstance().toByteArray());

		// then:
		assertEquals(INVALID_THROTTLE_DEFINITIONS, verdict.getKey());
		assertFalse(verdict.getValue());
	}

	@Test
	public void rejectsInvalidBytes() {
		byte[] invalidBytes = "NONSENSE".getBytes();

		// when:
		var verdict = subject.preUpdate(throttleDefs, invalidBytes);

		// then:
		assertEquals(ThrottleDefsManager.UNPARSEABLE_VERDICT, verdict);
	}

	@Test
	void returnsMaximumPriorityForThrottleDefsUpdate() {
		// given:
		var priority = subject.priorityForCandidate(fileNums.toFid(123L));

		// expect:
		assertEquals(OptionalInt.of(ThrottleDefsManager.APPLICABLE_PRIORITY), priority);
	}

	@Test
	void returnsNoPriorityIfNoThrottleDefs() {
		// given:
		var priority = subject.priorityForCandidate(fileNums.toFid(124L));

		// expect:
		assertTrue(priority.isEmpty());
	}
}