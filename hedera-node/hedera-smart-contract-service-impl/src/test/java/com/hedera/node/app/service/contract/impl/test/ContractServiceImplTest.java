/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.contract.impl.test;

import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ContractServiceImplTest {

	@Test
	void testSpi() {
		// when
		final ContractService service = ContractService.getInstance();

		// then
		Assertions.assertNotNull(service, "We must always receive an instance");
		Assertions.assertEquals(
				ContractServiceImpl.class,
				service.getClass(),
				"We must always receive an instance of type "
						+ ContractServiceImpl.class.getName());
		Assertions.assertEquals(
				ContractService.class.getSimpleName(),
				service.getServiceName(),
				"Service must have a reasonable name");
	}
}
