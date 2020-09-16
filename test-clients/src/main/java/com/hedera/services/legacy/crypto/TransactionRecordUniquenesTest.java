package com.hedera.services.legacy.crypto;

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

import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Fetches the tx records for an account and asserts for uniqueness of the records
 *
 * @author Achal
 */
public class TransactionRecordUniquenesTest {

	private static final Logger log = LogManager.getLogger(TransactionRecordUniquenesTest.class);

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	private static ManagedChannel channel;


	public TransactionRecordUniquenesTest(int port, String host) {
		// connecting to the grpc server on the port
		channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		TransactionRecordUniquenesTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
	}

	public static void main(String args[])
			throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		TransactionRecordUniquenesTest transactionRecordUniquenesTest = new TransactionRecordUniquenesTest(
				port, host);
		for (int i = 0; i < 1; i++) {
			log.info("run number :: " + i);
			transactionRecordUniquenesTest.demo();
		}
	}

	public void demo() throws Exception {
		Map<String, List<AccountKeyListObj>> keyFromFile = null;
		try {
			keyFromFile = TestHelper.getKeyFromFile(fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		AccountID payerAccount = genesisAccount.get(0).getAccountId();

		AccountID defaultNodeAccount = RequestBuilder
				.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

		TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

//    // create 1st account by payer as genesis
		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper
				.createAccountWithFeeZeroThreshold(payerAccount, defaultNodeAccount, firstPair, 1000000l,
						genKeyPair);
		log.info(transaction + "is the crypto create account transaction");
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		AccountID newlyCreateAccountId1 = null;
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		try {
			newlyCreateAccountId1 = TestHelper
					.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		} catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
			invalidNodeTransactionPrecheckCode.printStackTrace();
		}
		Assert.assertNotNull(newlyCreateAccountId1);
		log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		// create 2nd account by genesis
		KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithFeeZeroThreshold(payerAccount, defaultNodeAccount, secondPair, 1000000l,
						genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info("Pre Check Response of Create second account :: " + response
				.getNodeTransactionPrecheckCode().name());
		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId2 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId2);
		Assert
				.assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");

		log.info("--------------------------------------");

		// transfer between 1st to 2nd account by using payer account as 3rd account
		Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
				newlyCreateAccountId2, newlyCreateAccountId1, firstPair, defaultNodeAccount, 1000l);

		log.info("Transferring 1000 coin from 1st account to 2nd account....");
		TransactionResponse transferRes = stub.cryptoTransfer(transfer1);
		Assert.assertNotNull(transferRes);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
		TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
		TransactionReceipt txReceipt = TestHelper
				.getTxReceipt(transferBody.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");

		getTransactionRecordsByAccountId(firstPair, newlyCreateAccountId1,
				defaultNodeAccount,
				newlyCreateAccountId1);

		getTransactionRecordsByAccountId(genKeyPair, payerAccount,
				defaultNodeAccount,
				payerAccount);
	}

	public static void getTransactionRecordsByAccountId(KeyPair genesisKeyPair,
			AccountID payerAccount,
			AccountID defaultNodeAccount, AccountID accountID) throws Exception {
		log.info("Get Tx records by account Id...");
		long fee = FeeClient.getFeeByID(HederaFunctionality.CryptoGetAccountRecords);
		Query query = TestHelper
				.getTxRecordByAccountId(accountID, payerAccount, genesisKeyPair, defaultNodeAccount,
						fee, ResponseType.COST_ANSWER);
		Response transactionRecord = stub.getAccountRecords(query);
		Assert.assertNotNull(transactionRecord);
		Assert.assertNotNull(transactionRecord.getCryptoGetAccountRecords());

		fee = transactionRecord.getCryptoGetAccountRecords().getHeader().getCost();
		query = TestHelper
				.getTxRecordByAccountId(accountID, payerAccount, genesisKeyPair, defaultNodeAccount, fee,
						ResponseType.ANSWER_ONLY);
		transactionRecord = stub.getAccountRecords(query);
		Assert.assertNotNull(transactionRecord);
		Assert.assertNotNull(transactionRecord.getCryptoGetAccountRecords());
		Assert.assertEquals(ResponseCodeEnum.OK,
				transactionRecord.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode());
		Assert.assertEquals(accountID, transactionRecord.getCryptoGetAccountRecords().getAccountID());
		List<TransactionRecord> recordList = transactionRecord.getCryptoGetAccountRecords()
				.getRecordsList();
		log.info(
				"Tx Records List for account ID " + accountID.getAccountNum() + " :: " + recordList.size());
		List<TransactionRecord> transactionRecords = new ArrayList<>();
		int j = recordList.size();
		for (int i = 0; i < j; i++) {
			transactionRecords.add(recordList.get(i));
		}

		Set<TransactionRecord> transactionRecordSet = new HashSet<>();
		j = recordList.size();
		for (int i = 0; i < j; i++) {
			transactionRecordSet.add(recordList.get(i));
		}

		log.info("Transaction Record Size is :: " + transactionRecords.size());
		log.info("Set Record Size is :: " + transactionRecordSet.size());
		Assert.assertEquals(transactionRecords.size(), transactionRecordSet.size());

		log.info("--------------------------------------");
	}


}
