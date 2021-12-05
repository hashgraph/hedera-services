package com.hedera.services.state;

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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AutoAccountsManager;
import com.hedera.services.utils.EntityNum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AutoAccountCreationsManagerTest {

	@Test
	void settersAndGettersWork() {
		EntityNum a = new EntityNum(1);
		EntityNum b = new EntityNum(2);
		Map<ByteString, EntityNum> expectedMap = new HashMap<>() {{
			put(ByteString.copyFromUtf8("aaaa"), a);
			put(ByteString.copyFromUtf8("bbbb"), b);
		}};

		var subject = new AutoAccountsManager();
		assertTrue(subject.getAutoAccountsMap().isEmpty());

		subject.setAutoAccountsMap(expectedMap);
		assertEquals(expectedMap, subject.getAutoAccountsMap());
		assertEquals(b, subject.fetchEntityNumFor(ByteString.copyFromUtf8("bbbb")));
	}
}
