package com.hedera.services.sigs.factories;

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

import com.google.protobuf.ByteString;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.*;

@RunWith(JUnitPlatform.class)
public class BodySigningSigFactoryTest {
	@Test
	public void createsExpectedSig() {
		// given:
		BodySigningSigFactory subject = new BodySigningSigFactory(data);

		// when:
		TransactionSignature actualSig = subject.create(ByteString.copyFrom(pk), ByteString.copyFrom(sig));

		// then:
		Assertions.assertEquals(EXPECTED_SIG, actualSig);
	}
}
