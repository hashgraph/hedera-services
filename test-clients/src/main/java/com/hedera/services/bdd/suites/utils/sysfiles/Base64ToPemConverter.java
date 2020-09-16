package com.hedera.services.bdd.suites.utils.sysfiles;

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

import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519KeyStore;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519PrivateKey;

import java.io.File;

public class Base64ToPemConverter {
	public static void main(String... args) throws Exception {
		var ocKeystoreLoc = "test-clients/src/main/resource/mainnet-account950.txt";
		var pemLoc = "test-clients/devops-utils/validation-scenarios/keys/mainnet-account950.pem";
		var passphrase = "swirlds";

		var ocKeyPair = KeyFactory.firstListedKp(ocKeystoreLoc, "START_ACCOUNT");
		var privateKey = ocKeyPair.getPrivateKey();

		var store = new Ed25519KeyStore.Builder().withPassword(passphrase.toCharArray()).build();
		store.insertNewKeyPair(Ed25519PrivateKey.fromBytes(privateKey.getEncoded()));
		store.write(new File(pemLoc));
	}
}
