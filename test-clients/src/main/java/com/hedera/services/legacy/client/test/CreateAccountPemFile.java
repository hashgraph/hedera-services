package com.hedera.services.legacy.client.test;

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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.client.util.Ed25519KeyStore;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.util.Collections;

import static com.hedera.services.legacy.client.util.Common.createAccountComplex;

public class CreateAccountPemFile extends ClientBaseThread {

	private static final Logger log = LogManager.getLogger(CryptoCreate.class);

	private String DEFAULT_PASSWORD = "password";
	private long initialBalance = 100000L;
	private int numberOfIterations = 1;
	private AccountID payerAccount;

	/**
	 * Each client runs with two thread, one sending account creation request, one checking for its
	 * results.
	 *
	 * Create a batch of account, then verify transaction success or not, then repeat
	 */
	public CreateAccountPemFile(String host, int port, long nodeAccountNumber, boolean useSigMap, String[] args,
			int index) {
		super(host, port, nodeAccountNumber, useSigMap, args, index);
		this.useSigMap = useSigMap;
		this.nodeAccountNumber = nodeAccountNumber;
		this.host = host;
		this.port = port;

		if ((args.length) > 0) {
			numberOfIterations = Integer.parseInt(args[0]);
		}
		if ((args.length) > 1) {
			initialBalance = Long.parseLong(args[1]);
		}
		if ((args.length) > 2) {
			DEFAULT_PASSWORD = args[2];
		}

		try {
			initAccountsAndChannels();
			payerAccount = genesisAccount;

		} catch (URISyntaxException | IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	@Override
	void demo() throws Exception {
		try {
			for(int i=0; i<numberOfIterations; i++) {
				final Ed25519KeyStore keyStore = new Ed25519KeyStore(DEFAULT_PASSWORD.toCharArray());
				final KeyPair newKeyPair = keyStore.insertNewKeyPair();

				if (useSigMap) {
					Common.addKeyMap(newKeyPair, pubKey2privKeyMap);
				}

				// send create account transaction only, confirm later
				AccountID newAccount = RequestBuilder.getAccountIdBuild(nodeAccountNumber, 0l, 0l);

				try {
					Transaction transaction = Common.tranSubmit(() -> {
						Transaction createRequest;
						try {
							if (useSigMap) {
								byte[] pubKey = ((EdDSAPublicKey) newKeyPair.getPublic()).getAbyte();
								Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
								Key payerKey = acc2ComplexKeyMap.get(payerAccount);
								createRequest = Common.createAccountComplex(payerAccount, payerKey, newAccount, key,
										initialBalance,
										pubKey2privKeyMap);
							} else {
								createRequest = TestHelper
										.createAccountWithFee(payerAccount, newAccount, newKeyPair, initialBalance,
												Collections.singletonList(genesisPrivateKey));
							}
						} catch (Exception e) {
							e.printStackTrace();
							return null;
						}
						return createRequest;
					}, stub::createAccount);

					AccountID createdAccount = Common.getAccountIDfromReceipt(stub,
							TransactionBody.parseFrom(transaction.getBodyBytes())
									.getTransactionID());
					String pemFileName = "account_" + createdAccount.getAccountNum() + ".pem";
					keyStore.write(pemFileName);

					long balance = Common.getAccountBalance(stub, createdAccount,
							payerAccount, genesisKeyPair, nodeAccount);

					Assert.assertEquals(balance, initialBalance);
					log.info("----------------------------------------");
					log.info("ATTENTION !!  Account {} created with balance {} and its pem file is {}, password is:{}",
							createdAccount.getAccountNum(), initialBalance,
							pemFileName, DEFAULT_PASSWORD);
					log.info("----------------------------------------");

					final Ed25519KeyStore restoreKeyStore = new Ed25519KeyStore(DEFAULT_PASSWORD.toCharArray(),
							pemFileName);
					KeyPair restoredKeyPair = restoreKeyStore.get(0);
					log.info("Read back generated pem file successfully ");


				} catch (StatusRuntimeException e) {
					if (!tryReconnect(e)) return;
				} catch (Exception e) {
					log.error("Unexpected error ", e);
					return;
				}
			}
		} finally {
			channel.shutdown();
		}
	}
}
