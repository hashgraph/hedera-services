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

import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
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
import java.util.Timer;
import java.util.TimerTask;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

/**
 * Creates accounts in a batch and then fetches receipt for last account created. This test is
 * TPS controlled.
 *
 * @author Achal
 */
public class BatchThreadCreate {

	private static final Logger log = LogManager.getLogger(BatchThreadTransfer.class);

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	private static int BATCH_SIZE;
	private static int COUNT_SIZE;
	// BATCH_SIZE * COUNT_SIZE gives the total number of transfers as well as TPS
	private static long TPS; // Transactions per second
	private ManagedChannel channel;
	private List<TransactionID> txList = new ArrayList<>();


	private BatchThreadCreate(int port, String host) {
		// connecting to the grpc server on the port
		channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		this.stub = CryptoServiceGrpc.newBlockingStub(channel);
	}


	public static void main(String args[])
			throws Exception {
		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		BATCH_SIZE = Integer.parseInt(properties.getProperty("BATCH_SIZE"));
		COUNT_SIZE = Integer.parseInt(properties.getProperty("COUNT_SIZE"));
		TPS = Integer.parseInt(properties.getProperty("TPS"));
		int port = Integer.parseInt(properties.getProperty("port"));
		log.info("Connecting host = " + host + "; port = " + port);

		BatchThreadCreate batch =
				new BatchThreadCreate(port, host);

		KeyPair keyPair = new KeyPairGenerator().generateKeyPair();
		AccountID accountID = batch.createAccountInitial(keyPair);

		Timer timer = new Timer("Timer1");
		Timer timer1 = new Timer("Timer2");
		TimerTask timerTask = new TimerTask() {
			int count = 0;

			public void run() {
				if (count > (COUNT_SIZE - 2)) {
					timer.cancel();
					timer.purge();
				}
				try {
					batch.createAccount(keyPair, accountID);
					count++;
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		};

		TimerTask timerTask1 = new TimerTask() {
			int count = 0;

			public void run() {
				if (count > ((COUNT_SIZE) * BATCH_SIZE) - 2) {
					timer1.cancel();
					timer1.purge();
				}
				batch.receiptCreate(count);
				count++;
			}
		};

		Thread thread = new Thread(() -> timer.scheduleAtFixedRate(timerTask, 10l, TPS / 10));

		Thread thread1 = new Thread(() -> timer1.scheduleAtFixedRate(timerTask1, 100l, 100l));

		thread.start();
		Thread.sleep(20000L);
		thread1.start();
	}

	private void receiptCreate(int i) {
		TransactionReceipt txReceipt = null;

		try {
			txReceipt = TestHelper.getTxReceipt(txList.get(i), stub);
			Assert.assertEquals(txReceipt.getStatus(), ResponseCodeEnum.SUCCESS);
		} catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
			invalidNodeTransactionPrecheckCode.printStackTrace();
		}
		Assert.assertNotNull(txReceipt);
	}

	public void createAccount(KeyPair pair, AccountID accountID) throws Exception {

		for (int i = 0; i < BATCH_SIZE; i++) {
			// get Private Key
			AccountID nodeAccount3 = RequestBuilder
					.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);
			KeyPair firstPair = new KeyPairGenerator().generateKeyPair();

			Transaction transaction = TestHelper
					.createAccount(accountID, nodeAccount3, firstPair, 1l, 1000000l,
							TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
							TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
			Transaction signTransaction = TransactionSigner
					.signTransaction(transaction, Collections.singletonList(pair.getPrivate()));

			long transactionFee = FeeClient.getCreateAccountFee(signTransaction, 1);

			transaction = TestHelper
					.createAccount(accountID, nodeAccount3, firstPair, 1l, transactionFee,
							TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
							TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
			signTransaction = TransactionSigner
					.signTransaction(transaction, Collections.singletonList(pair.getPrivate()));

			TransactionResponse response = stub.createAccount(signTransaction);
			Assert.assertNotNull(response);
			Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
			log.info("Pre Check Response of Create first account :: " + response
					.getNodeTransactionPrecheckCode().name());
			TransactionBody body = TransactionBody.parseFrom(signTransaction.getBodyBytes());
			txList.add(i, body.getTransactionID());
		}
	}

	private AccountID createAccountInitial(KeyPair firstPair)
			throws Exception {


		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		AccountID payerAccount = genesisAccount.get(0).getAccountId();
		AccountID nodeAccount3 = RequestBuilder.getAccountIdBuild(Utilities.getDefaultNodeAccount(),
				0L, 0L);

		TestHelper.initializeFeeClient(channel, payerAccount, genesisKeyPair, nodeAccount3);

		Transaction transaction = TestHelper.createAccount(payerAccount, nodeAccount3, firstPair,
				100000000000000000L, 1000000L, TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
				TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
		Transaction signTransaction = TransactionSigner.signTransaction(transaction,
				Collections.singletonList(genesisPrivateKey));

		long transactionFee = FeeClient.getCreateAccountFee(signTransaction, 1);

		transaction = TestHelper.createAccount(payerAccount, nodeAccount3, firstPair,
				100000000000000000L, transactionFee, TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD,
				TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD);
		signTransaction = TransactionSigner.signTransaction(transaction,
				Collections.singletonList(genesisPrivateKey));

		TransactionResponse response = stub.createAccount(signTransaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create first account :: " + response.getNodeTransactionPrecheckCode()
						.name());
		stub = CryptoServiceGrpc.newBlockingStub(channel);
		AccountID newlyCreateAccountId1 = null;
		Thread.sleep(5000);
		try {
			TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
			newlyCreateAccountId1 = TestHelper
					.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		} catch (InvalidNodeTransactionPrecheckCode invalidNodeTransactionPrecheckCode) {
			invalidNodeTransactionPrecheckCode.printStackTrace();
		}
		return newlyCreateAccountId1;
	}

}
