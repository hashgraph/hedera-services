package com.hedera.services.legacy.regression;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.legacy.CI.CryptoTests;

/**
 * Check records for crypto service transactions.
 *
 * @author Hua Li Created on 2019-06-10
 */
public class RecordTestsCryptoServices {

	public static void main(String[] args) throws Throwable {
		String testConfigFilePath = "config/umbrellaTest.properties";
		CryptoTests tester = new CryptoTests(testConfigFilePath);
		tester.init(args);
		tester.cryptoCreateRecordCheckTests();
	}
}
