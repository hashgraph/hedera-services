/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.service.token.impl.itest;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.impl.CryptoServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CryptoServiceImplTest {

	@Test
	void testSpi() {
		// when
		final CryptoService service = CryptoService.getInstance();

		// then
		Assertions.assertNotNull(service, "We must always receive an instance");
		Assertions.assertEquals(
				CryptoServiceImpl.class,
				service.getClass(),
				"We must always receive an instance of type " + CryptoServiceImpl.class.getName());
	}
}
