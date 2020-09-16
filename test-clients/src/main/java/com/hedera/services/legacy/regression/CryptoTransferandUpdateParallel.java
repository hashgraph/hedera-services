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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * MultiThreaded transfer and cryptoupdate test
 *
 * @author Achal
 */
public class CryptoTransferandUpdateParallel {

	private static final Logger log = LogManager.getLogger(CryptoTransferandUpdateParallel.class);

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;

	public CryptoTransferandUpdateParallel() {

	}

	public static void main(String args[])
			throws Exception {

		log.info("create account");
		log.info(args.length);
		int numberOfRepeats = 10;
		if ((args.length) > 0) {
			numberOfRepeats = Integer.parseInt(args[0]);
		}
		for (int i = 0; i < numberOfRepeats; i++) {
			CryptoTransferandUpdateParallel cryptoTransferParallel = new CryptoTransferandUpdateParallel();

			log.info("**** repeat # " + (i + 1));
			cryptoTransferParallel.demo();
		}


	}


	public void demo()
			throws Exception {

		Random r = new Random();
		int Low = 1;
		int High = 10;
		int result = 1;
		result = r.nextInt(High - Low) + Low;

		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoTransferandUpdateParallel.stub = CryptoServiceGrpc.newBlockingStub(channel);


		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		AccountID payerAccount = genesisAccount.get(0).getAccountId();

		AccountID nodeAccount3 = RequestBuilder
				.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

		TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, nodeAccount3);

// create 1st account by payer as genesis
		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, firstPair, 1000000l, genKeyPair);
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
						.name());
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId1 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId1);
		log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		// create 2nd account by payer as genesis
		KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, secondPair, 100000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info("Pre Check Response of Create second account :: " + response
				.getNodeTransactionPrecheckCode().name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId2 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId2);
		Assert
				.assertTrue(newlyCreateAccountId2.getAccountNum() > newlyCreateAccountId1.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId2.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		// create 3rd account by payer as genesis
		KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, thirdPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create Third account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId3 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId3);
		Assert
				.assertTrue(newlyCreateAccountId3.getAccountNum() > newlyCreateAccountId2.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		// create 4th account by payer as genesis
		KeyPair fourthPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, fourthPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info("Pre Check Response of Create fourth account :: " + response
				.getNodeTransactionPrecheckCode().name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId4 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId4);
		Assert
				.assertTrue(newlyCreateAccountId4.getAccountNum() > newlyCreateAccountId3.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId4.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		// create 5th account by payer as genesis
		KeyPair fifthPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, fifthPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create fifth account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId5 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId5);
		Assert
				.assertTrue(newlyCreateAccountId5.getAccountNum() > newlyCreateAccountId4.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId5.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		KeyPair sixthPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, sixthPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create sixth account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId6 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId6);
		Assert
				.assertTrue(newlyCreateAccountId6.getAccountNum() > newlyCreateAccountId5.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId6.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		KeyPair seventhPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, seventhPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create seventh account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId7 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId7);
		Assert
				.assertTrue(newlyCreateAccountId7.getAccountNum() > newlyCreateAccountId6.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId7.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		KeyPair eightPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, eightPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create eighth account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId8 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId8);
		Assert
				.assertTrue(newlyCreateAccountId8.getAccountNum() > newlyCreateAccountId7.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId8.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		KeyPair ninthPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, ninthPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create ninth account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId9 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId9);
		Assert
				.assertTrue(newlyCreateAccountId9.getAccountNum() > newlyCreateAccountId8.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId9.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		KeyPair tenthPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, nodeAccount3, tenthPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create tenth account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId10 = TestHelper
				.getTxReceipt(body.getTransactionID(), stub, log, host).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId10);
		Assert
				.assertTrue(newlyCreateAccountId10.getAccountNum() > newlyCreateAccountId9.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId10.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		// parallel transfers
		Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
				newlyCreateAccountId2, newlyCreateAccountId3,
				thirdPair, nodeAccount3, 1000l);

		Transaction transfer2 = TestHelper
				.createTransferSigMap(newlyCreateAccountId2, secondPair, newlyCreateAccountId3,
						newlyCreateAccountId4, fourthPair, nodeAccount3, 1000l);

		Transaction transfer3 = TestHelper.createTransferSigMap(newlyCreateAccountId3, thirdPair,
				newlyCreateAccountId4, newlyCreateAccountId5, fifthPair, nodeAccount3, 1000l);

		Transaction transfer4 = TestHelper
				.createTransferSigMap(newlyCreateAccountId4, fourthPair,
						newlyCreateAccountId5, newlyCreateAccountId6, sixthPair, nodeAccount3, 1000l);

		Transaction transfer5 = TestHelper.createTransferSigMap(newlyCreateAccountId5, fifthPair,
				newlyCreateAccountId6, newlyCreateAccountId7, seventhPair, nodeAccount3, 1000l);

		Transaction transfer6 = TestHelper.createTransferSigMap(newlyCreateAccountId6, sixthPair,
				newlyCreateAccountId7, newlyCreateAccountId8, eightPair, nodeAccount3, 1000l);

		Transaction transfer7 = TestHelper
				.createTransferSigMap(newlyCreateAccountId7, seventhPair,
						newlyCreateAccountId8, newlyCreateAccountId9, ninthPair, nodeAccount3, 1000l);

		Transaction transfer8 = TestHelper.createTransferSigMap(newlyCreateAccountId8, eightPair,
				newlyCreateAccountId9, newlyCreateAccountId10,
				tenthPair, nodeAccount3, 1000l);

		Transaction transfer9 = TestHelper.createTransferSigMap(newlyCreateAccountId9, ninthPair,
				newlyCreateAccountId10, newlyCreateAccountId1, firstPair, nodeAccount3, 1000l);

		Transaction transfer10 = TestHelper
				.createTransferSigMap(newlyCreateAccountId10, tenthPair,
						newlyCreateAccountId2, newlyCreateAccountId3, thirdPair, nodeAccount3, 1000l);

		int size = 5;
		ExecutorService threads1 = Executors.newFixedThreadPool(size);
		List<Callable<TransactionResponse>> torun1 = new ArrayList<>(size);
		torun1.add(() -> stub.cryptoTransfer(transfer1));
		torun1.add(() -> stub.cryptoTransfer(transfer2));
		torun1.add(() -> stub.cryptoTransfer(transfer3));
		torun1.add(() -> stub.cryptoTransfer(transfer4));
		torun1.add(() -> stub.cryptoTransfer(transfer5));
		torun1.add(() -> stub.cryptoTransfer(transfer6));
		torun1.add(() -> stub.cryptoTransfer(transfer7));
		torun1.add(() -> stub.cryptoTransfer(transfer8));
		torun1.add(() -> stub.cryptoTransfer(transfer9));
		torun1.add(() -> stub.cryptoTransfer(transfer10));
		// all tasks executed in different threads, at 'once'.
		List<Future<TransactionResponse>> futures1 = threads1.invokeAll(torun1);

		log.info("Transfering between 10 accounts in parallel");

		// transfer result

		TransactionResponse transferRes = futures1.get(0).get();
		System.out.println("The traansfer response is ::");
		System.out.println(transferRes);
		Assert.assertNotNull(transferRes);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer1 = TransactionBody.parseFrom(transfer1.getBodyBytes());
		TransactionReceipt txReceipt = TestHelper
				.getTxReceipt(bodyTransfer1.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// transfer result

		TransactionResponse transferRes2 = futures1.get(1).get();
		System.out.println("The traansfer response is ::");
		System.out.println(transferRes2);
		log.info(transferRes2);
		Assert.assertNotNull(transferRes);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes2.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes2.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer2 = TransactionBody.parseFrom(transfer2.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer2.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// transfer result

		TransactionResponse transferRes3 = futures1.get(2).get();
		System.out.println("The traansfer response is ::");
		System.out.println(transferRes3);
		Assert.assertNotNull(transferRes3);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes3.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes3.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer3 = TransactionBody.parseFrom(transfer3.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer3.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// transfer result

		TransactionResponse transferRes4 = futures1.get(3).get();
		System.out.println("The traansfer response is ::");
		System.out.println(transferRes4);
		Assert.assertNotNull(transferRes4);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes4.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes4.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer4 = TransactionBody.parseFrom(transfer4.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer4.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// transfer result

		TransactionResponse transferRes5 = futures1.get(4).get();
		System.out.println("The traansfer response is ::");
		System.out.println(transferRes5);
		Assert.assertNotNull(transferRes5);

		Assert.assertEquals(ResponseCodeEnum.OK, transferRes5.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes5.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer5 = TransactionBody.parseFrom(transfer5.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer5.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		TransactionResponse transferRes6 = futures1.get(5).get();
		Assert.assertNotNull(transferRes6);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes6.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes6.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer6 = TransactionBody.parseFrom(transfer6.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer6.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// transfer result
		TransactionResponse transferRes7 = futures1.get(6).get();
		Assert.assertNotNull(transferRes7);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes7.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes7.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer7 = TransactionBody.parseFrom(transfer7.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer7.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// transfer test
		TransactionResponse transferRes8 = futures1.get(7).get();
		Assert.assertNotNull(transferRes8);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes8.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes8.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer8 = TransactionBody.parseFrom(transfer8.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer8.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		TransactionResponse transferRes9 = futures1.get(8).get();
		Assert.assertNotNull(transferRes9);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes9.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes9.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer9 = TransactionBody.parseFrom(transfer9.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer9.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		log.info("-----------------------------------------");

		// transfer result
		TransactionResponse transferRes10 = futures1.get(9).get();
		Assert.assertNotNull(transferRes10);
		Assert.assertEquals(ResponseCodeEnum.OK, transferRes10.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response transfer :: " + transferRes10.getNodeTransactionPrecheckCode().name());
		TransactionBody bodyTransfer10 = TransactionBody.parseFrom(transfer10.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(bodyTransfer10.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);

		log.info("Transfer parallel success");
		log.info("-----------------------------------------");
		log.info("-----------------------------------------");

		// updating 10 accounts

		Transaction updateaccount1 = getSIgnedUpfate(newlyCreateAccountId1, firstPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount2 = getSIgnedUpfate(newlyCreateAccountId2, secondPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount3 = getSIgnedUpfate(newlyCreateAccountId3, thirdPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount4 = getSIgnedUpfate(newlyCreateAccountId4, fourthPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount5 = getSIgnedUpfate(newlyCreateAccountId5, fifthPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount6 = getSIgnedUpfate(newlyCreateAccountId6, sixthPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount7 = getSIgnedUpfate(newlyCreateAccountId7, seventhPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount8 = getSIgnedUpfate(newlyCreateAccountId8, eightPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount9 = getSIgnedUpfate(newlyCreateAccountId9, ninthPair, payerAccount,
				genesisPrivateKey, nodeAccount3);
		Transaction updateaccount10 = getSIgnedUpfate(newlyCreateAccountId10, tenthPair, payerAccount,
				genesisPrivateKey, nodeAccount3);

		size = 5;
		ExecutorService threads2 = Executors.newFixedThreadPool(size);
		List<Callable<TransactionResponse>> torun2 = new ArrayList<>(size);
		torun2.add(() -> stub.updateAccount(updateaccount1));
		torun2.add(() -> stub.updateAccount(updateaccount2));
		torun2.add(() -> stub.updateAccount(updateaccount3));
		torun2.add(() -> stub.updateAccount(updateaccount4));
		torun2.add(() -> stub.updateAccount(updateaccount5));
		torun2.add(() -> stub.updateAccount(updateaccount6));
		torun2.add(() -> stub.updateAccount(updateaccount7));
		torun2.add(() -> stub.updateAccount(updateaccount8));
		torun2.add(() -> stub.updateAccount(updateaccount9));
		torun2.add(() -> stub.updateAccount(updateaccount10));

		// all tasks executed in different threads, at 'once'.
		List<Future<TransactionResponse>> futures2 = threads2.invokeAll(torun2);
		response = futures2.get(0).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());

		TransactionBody updateAccBody1 = TransactionBody.parseFrom(updateaccount1.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody1.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		Response accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId1, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(1).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody2 = TransactionBody.parseFrom(updateaccount2.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody2.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId2, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		log.info("updating successful" + "\n");

		response = futures2.get(2).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody3 = TransactionBody.parseFrom(updateaccount3.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody3.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId3, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(3).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody4 = TransactionBody.parseFrom(updateaccount4.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody4.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId4, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(4).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody5 = TransactionBody.parseFrom(updateaccount5.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody5.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId5, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(5).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody6 = TransactionBody.parseFrom(updateaccount6.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody6.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId6, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(6).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody7 = TransactionBody.parseFrom(updateaccount7.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody7.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId7, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(7).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody8 = TransactionBody.parseFrom(updateaccount8.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody8.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId8, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(8).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody9 = TransactionBody.parseFrom(updateaccount9.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody9.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId9, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		response = futures2.get(9).get();
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response account update :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccBody10 = TransactionBody.parseFrom(updateaccount10.getBodyBytes());
		txReceipt = TestHelper.getTxReceipt(updateAccBody10.getTransactionID(), stub);
		Assert.assertNotNull(txReceipt);
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId10, payerAccount, genKeyPair,
						nodeAccount3);
		Assert.assertNotNull(accountInfoResponse);
		log.info(accountInfoResponse.getCryptoGetInfo().getAccountInfo());

		log.info("updating successful" + "\n");

		threads1.shutdown();
		threads2.shutdown();
		channel.shutdown();

	}

	public Transaction getSIgnedUpfate(AccountID newlycreatedAccountID, KeyPair firstPair,
			AccountID payerAccount, PrivateKey genesisPrivateKey, AccountID nodeAccount) {

		Duration autoRenew = RequestBuilder.getDuration(
				CustomPropertiesSingleton.getInstance().getAccountDuration());
		Transaction updateaccount1 = TestHelper.updateAccount(newlycreatedAccountID, payerAccount,
				genesisPrivateKey, nodeAccount, autoRenew);

		List<PrivateKey> privateKeyList = new ArrayList<>();
		privateKeyList.add(genesisPrivateKey);
		privateKeyList.add(firstPair.getPrivate());
		Transaction signUpdate = TransactionSigner.signTransaction(updateaccount1, privateKeyList);
		return signUpdate;

	}

	public Transaction createsignTransaction(KeyPair pair, AccountID payerAccount,
			AccountID nodeAccount, PrivateKey payerAccountPrivateKey) throws Exception {

		Transaction transaction = TestHelper
				.createAccountWithFee(payerAccount, nodeAccount, pair, 100000l,
						Collections.singletonList(payerAccountPrivateKey));
		return transaction;
	}

	private void assertAccountInfoDetails(AccountID newlyCreateAccountId1,
			Response accountInfoResponse) {
		Assert.assertNotNull(accountInfoResponse);
		Assert.assertNotNull(accountInfoResponse.getCryptoGetInfo());
		CryptoGetInfoResponse.AccountInfo accountInfo1 = accountInfoResponse.getCryptoGetInfo()
				.getAccountInfo();
		log.info("Account Info of Account ID " + newlyCreateAccountId1.getAccountNum());
		log.info(accountInfo1);
		Assert.assertNotNull(accountInfo1);
		Assert.assertEquals(newlyCreateAccountId1, accountInfo1.getAccountID());
		Assert.assertEquals(100l, accountInfo1.getGenerateReceiveRecordThreshold());
		Assert.assertEquals(100l, accountInfo1.getGenerateSendRecordThreshold());
		Assert.assertFalse(accountInfo1.getReceiverSigRequired());
		Duration renewal = RequestBuilder.getDuration(5000);
	}

}
