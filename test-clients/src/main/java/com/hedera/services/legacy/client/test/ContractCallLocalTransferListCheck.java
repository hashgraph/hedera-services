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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * Extended from ContractCall, by setting isCheckTransferList to true before thread start
 * to enable balance check feature
 */
public class ContractCallLocalTransferListCheck extends ContractCallLocal {

	private static final Logger log = LogManager.getLogger(ContractCallLocalTransferListCheck.class);

	/**
	 * This is the parent thread, it will kick off multiple instances
	 * crypto, smart contract file test threads.
	 *
	 * At the end of submitting transactions, pull events file and records file from node
	 * and compare again saved transactionID and transactionRecord
	 */
	public ContractCallLocalTransferListCheck(String host, int port, long nodeAccountNumber, boolean useSigMap,
			String[] args, int index) {
		super(host, port, nodeAccountNumber, useSigMap, args, index);
		this.isCheckTransferList = true;
	}
}
