package com.hedera.services.txns.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

class TokenListChecksTest {
	@Test
	public void permitsAdminKeyRemoval() {
		// setup:
		Predicate<Key> adminKeyRemoval = mock(Predicate.class);
		TokenListChecks.ADMIN_KEY_REMOVAL = adminKeyRemoval;

		given(adminKeyRemoval.test(any())).willReturn(true);

		// when:
		var validity = TokenListChecks.checkKeys(
				true, Key.getDefaultInstance(),
				false, Key.getDefaultInstance(),
				false, Key.getDefaultInstance(),
				false, Key.getDefaultInstance(),
				false, Key.getDefaultInstance());

		// then:
		assertEquals(ResponseCodeEnum.OK, validity);

		// cleanup:
		TokenListChecks.ADMIN_KEY_REMOVAL = ImmutableKeyUtils::signalsKeyRemoval;
	}
}