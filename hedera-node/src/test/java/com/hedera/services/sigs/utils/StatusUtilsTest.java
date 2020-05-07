package com.hedera.services.sigs.utils;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.legacy.crypto.SignatureStatusCode;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static com.hedera.test.factories.txns.SystemDeleteFactory.*;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;

@RunWith(JUnitPlatform.class)
public class StatusUtilsTest {
	@Test
	public void usesTxnIdForStatus() throws Throwable {
		// given:
		PlatformTxnAccessor platformTxn = new PlatformTxnAccessor(from(newSignedSystemDelete().get()));
		SignatureStatus expectedStatus = new SignatureStatus(
				SignatureStatusCode.SUCCESS, ResponseCodeEnum.OK,
				true, platformTxn.getTxn().getTransactionID(),
				null, null, null, null);

		// when:
		SignatureStatus status = StatusUtils.successFor(true, platformTxn);

		// then:
		assertEquals(expectedStatus.toString(), status.toString());
	}
}
