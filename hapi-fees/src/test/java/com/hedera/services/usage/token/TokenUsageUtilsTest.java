package com.hedera.services.usage.token;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.test.KeyUtils.B_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.usage.token.TokenEntitySizes.*;

@RunWith(JUnitPlatform.class)
public class TokenUsageUtilsTest {
	@Test
	public void usesExpectedEstimate() {
		// given:
		var op = TokenCreateTransactionBody.newBuilder().setAdminKey(B_COMPLEX_KEY).build();

		// when:
		var actual = TokenUsageUtils.keySizeIfPresent(op, TokenCreateTransactionBody::hasAdminKey, TokenCreateTransactionBody::getAdminKey);

		// then:
		assertEquals(FeeBuilder.getAccountKeyStorageSize(B_COMPLEX_KEY), actual);
	}
}
