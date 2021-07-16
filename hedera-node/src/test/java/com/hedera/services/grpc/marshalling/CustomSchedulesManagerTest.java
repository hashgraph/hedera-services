package com.hedera.services.grpc.marshalling;

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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class CustomSchedulesManagerTest {
	@Mock
	private CustomFeeSchedules customFeeSchedules;

	private CustomSchedulesManager subject;

	@BeforeEach
	void setUp() {
		subject = new CustomSchedulesManager(customFeeSchedules);
	}

	@Test
	void usesDelegateForMissing() {
		given(customFeeSchedules.lookupScheduleFor(a.asEntityId())).willReturn(aSchedule);

		// when:
		final var ans = subject.managedSchedulesFor(a.asEntityId());

		// then:
		assertSame(aSchedule, ans);
	}

	@Test
	void reusesExtantScheduleIfPresent() {
		given(customFeeSchedules.lookupScheduleFor(a.asEntityId()))
				.willReturn(aSchedule)
				.willThrow(AssertionError.class);

		// when:
		final var firstAns = subject.managedSchedulesFor(a.asEntityId());
		final var secondAns = subject.managedSchedulesFor(a.asEntityId());

		// then:
		assertSame(firstAns, secondAns);
	}

	@Test
	void enumeratesAllManagedSchedules() {
		given(customFeeSchedules.lookupScheduleFor(a.asEntityId())).willReturn(aSchedule);
		given(customFeeSchedules.lookupScheduleFor(b.asEntityId())).willReturn(bSchedule);

		// when:
		subject.managedSchedulesFor(a.asEntityId());
		subject.managedSchedulesFor(b.asEntityId());
		// and:
		final var all = subject.finalManagedSchedules();

		// then:
		assertEquals(2, all.size());
		// and:
		final var first = all.get(0);
		assertEquals(a, first.getLeft());
		assertSame(aSchedule, first.getRight());
		// and:
		final var second = all.get(1);
		assertEquals(b, second.getLeft());
		assertSame(bSchedule, second.getRight());
	}

	private final long amountOfHbarFee = 100_000L;
	private final Id hbarFeeCollectorId = new Id(1, 2, 3);
	private final EntityId hbarFeeCollector = hbarFeeCollectorId.asEntityId();
	private final FcCustomFee hbarFee = FcCustomFee.fixedFee(amountOfHbarFee, null, hbarFeeCollector);
	private final long amountOfHtsFee = 100_000L;
	private final Id htsFeeCollectorId = new Id(1, 2, 3);
	private final EntityId feeDenom = new EntityId(6, 6, 6);
	private final EntityId htsFeeCollector = htsFeeCollectorId.asEntityId();
	private final FcCustomFee htsFee = FcCustomFee.fixedFee(amountOfHtsFee, feeDenom, htsFeeCollector);
	final Id a = new Id(1, 2, 3);
	final Id b = new Id(2, 3, 4);
	final List<FcCustomFee> aSchedule = List.of(hbarFee);
	final List<FcCustomFee> bSchedule = List.of(htsFee);
}
