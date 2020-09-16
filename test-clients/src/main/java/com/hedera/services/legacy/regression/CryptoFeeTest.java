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
import com.hederahashgraph.fee.FeeBuilder;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Crypto Fee Test
 *
 * @author Tirupathi Mandala
 * @Date : 4/3/2019
 */

public class CryptoFeeTest {

	private static final Logger log = LogManager.getLogger(CryptoFeeTest.class);

	public static String fileName = TestHelper.getStartUpFile();
	public static String feeScheduleFileName = "feeSchedule.txt";
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	private static ManagedChannel channel;

	public CryptoFeeTest(int port, String host) {
		// connecting to the grpc server on the port
		channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoFeeTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
	}

	public CryptoFeeTest() {
	}

	public static void main(String args[])
			throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		CryptoFeeTest multipleCryptoTransfers = new CryptoFeeTest(port, host);
		multipleCryptoTransfers.demo();
	}

	public void demo()
			throws Exception {


		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		AccountID payerAccount = genesisAccount.get(0).getAccountId();

		AccountID defaultNodeAccount = RequestBuilder
				.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

		TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, defaultNodeAccount);

		// create 1st account by payer as genesis
		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper
				.createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 1000000l, genKeyPair);
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		TransactionReceipt txReceipt1 = TestHelper.getTxReceipt(body.getTransactionID(), stub);
		AccountID newlyCreateAccountId1 = txReceipt1.getAccountID();
		Assert.assertNotNull(newlyCreateAccountId1);
		log.info("Account ID " + newlyCreateAccountId1.getAccountNum() + " created successfully.");
		log.info("getHbarEquiv -->" + txReceipt1.getExchangeRate().getCurrentRate().getHbarEquiv());
		Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getHbarEquiv() > 0);
		Assert.assertTrue(txReceipt1.getExchangeRate().getCurrentRate().getCentEquiv() > 0);
		Assert.assertTrue(
				txReceipt1.getExchangeRate().getCurrentRate().getExpirationTime().getSeconds() > 0);
		Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getHbarEquiv() > 0);
		Assert.assertTrue(txReceipt1.getExchangeRate().getNextRate().getCentEquiv() > 0);
		Assert.assertTrue(
				txReceipt1.getExchangeRate().getNextRate().getExpirationTime().getSeconds() > 0);
		log.info("--------------------------------------");

		// get tx record of payer account by txId
		log.info("Get Tx record by Tx Id...");
		long queryFeeForTxRecord = FeeClient.getCostForGettingTxRecord();

		Query query = TestHelper
				.getTxRecordByTxId(body.getTransactionID(), payerAccount,
						genKeyPair, defaultNodeAccount, queryFeeForTxRecord,
						ResponseType.COST_ANSWER);
		Response transactionRecord = stub.getTxRecordByTxID(query);
		Assert.assertNotNull(transactionRecord);
		Assert.assertEquals(ResponseCodeEnum.OK,
				transactionRecord.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode());
		log.info("The cost of getting Transaction Record ===> " +
				transactionRecord.getTransactionGetRecord().getHeader().getCost());

		long txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
		query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
				genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
		transactionRecord = stub.getTxRecordByTxID(query);
		Assert.assertNotNull(transactionRecord);
		TransactionRecord transactionRecordResponse = transactionRecord.getTransactionGetRecord()
				.getTransactionRecord();
		Assert
				.assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
		Assert.assertTrue(
				transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
						> 0);
		Assert.assertTrue(
				transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
						> 0);
		Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
				.getExpirationTime().getSeconds() > 0);
		Assert.assertEquals(body.getTransactionID(),
				transactionRecordResponse.getTransactionID());
		Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
		log.info("Tx Record is successfully retrieve and asserted.");
		log.info("--------------------------------------");

		// create 2nd account by payer as genesis
		KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, defaultNodeAccount, secondPair, 1000000l,
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

		log.info("Get Tx record by Tx Id...");
		query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
				genKeyPair,
				defaultNodeAccount, queryFeeForTxRecord, ResponseType.COST_ANSWER);
		transactionRecord = stub.getTxRecordByTxID(query);
		Assert.assertNotNull(transactionRecord);

		log.info("The cost of getting Transaction Record ===>" +
				transactionRecord.getTransactionGetRecord().getHeader().getCost());

		txRecordFee = transactionRecord.getTransactionGetRecord().getHeader().getCost();
		query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
				genKeyPair, defaultNodeAccount, txRecordFee, ResponseType.ANSWER_ONLY);
		transactionRecord = stub.getTxRecordByTxID(query);

		Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
		transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
		Assert
				.assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
		Assert.assertTrue(
				transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
						> 0);
		Assert.assertTrue(
				transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
						> 0);
		Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
				.getExpirationTime().getSeconds() > 0);
		Assert.assertEquals(body.getTransactionID(),
				transactionRecordResponse.getTransactionID());
		Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
		log.info("Tx Record is successfully retrieve and asserted.");
		log.info("--------------------------------------");

		// create 3rd account by payer as genesis
		KeyPair thirdPair = new KeyPairGenerator().generateKeyPair();

		transaction = TestHelper
				.createAccountWithSigMap(payerAccount, defaultNodeAccount, thirdPair, 1000000l, genKeyPair);
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create Third account :: " + response.getNodeTransactionPrecheckCode()
						.name());
		body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newlyCreateAccountId3 =
				TestHelper.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		Assert.assertNotNull(newlyCreateAccountId3);
		Assert
				.assertTrue(newlyCreateAccountId3.getAccountNum() > newlyCreateAccountId2.getAccountNum());
		log.info("Account ID " + newlyCreateAccountId3.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");

		log.info("Get Tx record by Tx Id...");

		query = TestHelper.getTxRecordByTxId(body.getTransactionID(), payerAccount,
				genKeyPair, defaultNodeAccount, TestHelper.getCryptoMaxFee(),
				ResponseType.ANSWER_ONLY);
		transactionRecord = stub.getTxRecordByTxID(query);
		Assert.assertNotNull(transactionRecord);
		Assert.assertNotNull(transactionRecord.getTransactionGetRecord());
		transactionRecordResponse = transactionRecord.getTransactionGetRecord().getTransactionRecord();
		Assert
				.assertEquals(ResponseCodeEnum.SUCCESS, transactionRecordResponse.getReceipt().getStatus());
		Assert.assertTrue(
				transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getHbarEquiv()
						> 0);
		Assert.assertTrue(
				transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate().getCentEquiv()
						> 0);
		Assert.assertTrue(transactionRecordResponse.getReceipt().getExchangeRate().getCurrentRate()
				.getExpirationTime().getSeconds() > 0);
		Assert.assertEquals(body.getTransactionID(),
				transactionRecordResponse.getTransactionID());
		Assert.assertEquals(body.getMemo(), transactionRecordResponse.getMemo());
		log.info("Tx Record is successfully retrieve and asserted.");
		log.info("--------------------------------------");


    /* Get account Info of 3rd Account , before transfer between 1st and 2nd for Fee validation
       Use payerAccount for paying GetInfo Fee.
    */
		Response accountInfoResponseBefore = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId3, payerAccount,
						genKeyPair, defaultNodeAccount);
		log.info("Balance ac-3 Before Transfer Fee Deduction = " + accountInfoResponseBefore
				.getCryptoGetInfo().getAccountInfo().getBalance());
		long beforeTransferExpBal = 1000000L;
		Assert.assertEquals(beforeTransferExpBal,
				accountInfoResponseBefore.getCryptoGetInfo().getAccountInfo().getBalance());
		// transfer between 1st to 2nd account by using payer account as 3rd account
		Transaction transfer1 = TestHelper.createTransferSigMap(newlyCreateAccountId1, firstPair,
				newlyCreateAccountId2, newlyCreateAccountId3, thirdPair, defaultNodeAccount, 1000l);

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

		query = TestHelper.getTxRecordByTxId(transferBody.getTransactionID(), payerAccount,
				genKeyPair, defaultNodeAccount, TestHelper.getCryptoMaxFee(),
				ResponseType.ANSWER_ONLY);
		transactionRecord = stub.getTxRecordByTxID(query);
		log.info("transactionRecord-->TransactionFee = " + transactionRecord.getTransactionGetRecord()
				.getTransactionRecord().getTransactionFee());
		long convertedTransactionFee = FeeBuilder
				.getTinybarsFromTinyCents(TestHelper.getExchangeRate(), 100000);
		Assert.assertEquals(convertedTransactionFee,
				transactionRecord.getTransactionGetRecord().getTransactionRecord().getTransactionFee());

    /* Get account Info of 3rd Account , after transfer between 1st and 2nd for Fee validation
       Use payerAccount for paying GetInfo Fee.
    */
		Response accountInfoResponseAfter = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId3, payerAccount,
						genKeyPair, defaultNodeAccount);
		log.info(
				"Balance ac-3 After Transfer Fee Deduction = " + accountInfoResponseAfter.getCryptoGetInfo()
						.getAccountInfo().getBalance());
		long afterTransferExpBal =
				beforeTransferExpBal - transactionRecord.getTransactionGetRecord().getTransactionRecord()
						.getTransactionFee();
		Assert.assertEquals(afterTransferExpBal,
				accountInfoResponseAfter.getCryptoGetInfo().getAccountInfo().getBalance());
		log.info("-----------------------------------------");

		// Validate accounts after transfer transaction
		Response accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId1, newlyCreateAccountId3,
						thirdPair, defaultNodeAccount);
		log.info(
				"Balance ac-1 = " + accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());

		Assert
				.assertEquals(999000, accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
		assertAccountInfoDetails(newlyCreateAccountId1, accountInfoResponse);

		// Get account Info of 2nd Account
		accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, newlyCreateAccountId2, newlyCreateAccountId3,
						thirdPair, defaultNodeAccount);
		log.info(
				"Balance ac-2= " + accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
		Assert.assertEquals(1001000,
				accountInfoResponse.getCryptoGetInfo().getAccountInfo().getBalance());
		assertAccountInfoDetails(newlyCreateAccountId2, accountInfoResponse);
		log.info("-----------------------------------------");

	}


	private void assertAccountInfoDetails(AccountID newlyCreateAccountId1,
			Response accountInfoResponse) {
		Assert.assertNotNull(accountInfoResponse);
		Assert.assertNotNull(accountInfoResponse.getCryptoGetInfo());
		CryptoGetInfoResponse.AccountInfo accountInfo1 = accountInfoResponse.getCryptoGetInfo()
				.getAccountInfo();
		Assert.assertNotNull(accountInfo1);
		Assert.assertEquals(newlyCreateAccountId1, accountInfo1.getAccountID());
		Assert.assertEquals(5000000000000000000l, accountInfo1.getGenerateReceiveRecordThreshold());
		Assert.assertEquals(5000000000000000000l, accountInfo1.getGenerateSendRecordThreshold());
		Assert.assertFalse(accountInfo1.getReceiverSigRequired());
	}


}
