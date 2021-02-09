package com.hedera.services.ledger.properties;

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

import com.hedera.services.ledger.accounts.TestAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.ledger.properties.TestAccountProperty.*;

public class ChangeSummaryManagerTest {
	private ChangeSummaryManager<TestAccount, TestAccountProperty> subject = new ChangeSummaryManager<>();
	private EnumMap<TestAccountProperty, Object> changes = new EnumMap<>(TestAccountProperty.class);

	@BeforeEach
	private void setup() {
		changes.clear();
	}

	@Test
	public void persistsExpectedChanges() {
		// given:
		Object thing = new Object();
		TestAccount a = new TestAccount(1L, thing, false);

		// when:
		subject.update(changes, LONG, 5L);
		subject.update(changes, FLAG, true);
		// and:
		subject.persist(changes, a);

		// then:
		assertEquals(new TestAccount(5L, thing, true), a);
	}

	@Test
	public void setsFlagWithPrimitiveArg() {
		// when:
		subject.update(changes, FLAG, true);

		// then:
		assertEquals(Boolean.TRUE, changes.get(FLAG));
	}

	@Test
	public void setsValueWithPrimitiveArg() {
		// when:
		subject.update(changes, LONG, 5L);

		// then:
		assertEquals(Long.valueOf(5L), changes.get(LONG));
	}

	@Test
	public void setsThing() {
		// given:
		Object thing = new Object();

		// when:
		subject.update(changes, OBJ, thing);

		// then:
		assertEquals(thing, changes.get(OBJ));
	}
}
