package com.hedera.services.legacy.client.test;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.legacy.client.core.GrpcStub;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all test case
 */
public class ClientBaseThread extends Thread {
	private static final Logger log = LogManager.getLogger(ClientBaseThread.class);

	SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;
	CryptoServiceGrpc.CryptoServiceBlockingStub stub;

	AccountID nodeAccount;

	Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();

	GrpcStub grpcStub;

	public ClientBaseThread(String host, int port, long nodeAccountNumber, String[] args, int index) {
		grpcStub = new GrpcStub(host, port);
	}

	/**
	 * Convert hex string to bytes.
	 *
	 * @param data to be converted
	 * @return converted bytes
	 */
	public static byte[] hexToBytes(String data) throws DecoderException {
	  byte[] rv = Hex.decodeHex(data);
	  return rv;
	}

	@Override
	public void run() {
		try {
			demo();
		} catch (Exception e) {
			log.error(getName() + " died due to error ", e);
		}
		log.info(getName() + " thread finished \n\n\n\n");
	}

	// Will be override by derived children class
	void demo() throws Exception {
	}
}
