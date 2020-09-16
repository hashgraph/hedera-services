package com.hedera.services.legacy.throttling;

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
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
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

import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


/**
 * Test class to test Crypto Throttling , by creating transactions more than throttled settings
 *
 * @author Tirupathi Mandala Created on 2019-08-29
 */
public class CryptoCreateThrottling {

	private static final Logger log = LogManager.getLogger(CryptoCreateThrottling.class);
	public static int MAX_TRIES = 3000; //maximum tries for receipts
	public static int MAX_CREATE_ACCOUNTS = 1;
	public static int MAX_TRANSFERS = 10;
	public static int MAX_UPDATE_ACCOUNTS = 10;

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	private static PrivateKey genesisPrivateKey;
	private static KeyPair genesisKeyPair;
	private static AccountID payerAccount;
	private static AccountID nodeAccount2;
	private static ManagedChannel channel;

	public CryptoCreateThrottling(int port, String host) {
		// connecting to the grpc server on the port
		channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		stub = CryptoServiceGrpc.newBlockingStub(channel);
	}

	public static void main(String args[])
			throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		InetAddress address = InetAddress.getByName("34.226.77.143");
		log.info(address.isReachable(1000));
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		CryptoCreateThrottling cryptoCreate = new CryptoCreateThrottling(port, host);
		cryptoCreate.demo();

	}

	public void demo() throws Exception {

		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);
		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		genesisPrivateKey = genKeyPairObj.getPrivateKey();
		genesisKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		payerAccount = genesisAccount.get(0).getAccountId();
		nodeAccount2 = RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
		log.info(payerAccount);
		TestHelper.initializeFeeClient(channel, payerAccount, genesisKeyPair, nodeAccount2);

		// create Account
		for (int i = 0; i < MAX_CREATE_ACCOUNTS; i++) {
			KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
			Transaction transaction = TestHelper
					.createAccountWithFee(payerAccount, nodeAccount2, firstPair,
							10000l, Collections.singletonList(genesisPrivateKey));
			AccountID newAccountID = createAccount(transaction);
			log.info("Account ID is ::" + newAccountID.getAccountNum());
		}

		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper.createAccountWithFee(payerAccount, nodeAccount2, firstPair,
				10000l, Collections.singletonList(genesisPrivateKey));
		AccountID newAccountID = createAccount(transaction);
		log.info("Account ID is ::" + newAccountID.getAccountNum());

		for (int i = 0; i < MAX_TRANSFERS; i++) {
			Transaction transferTransaction = TestHelper
					.createTransferSigMap(payerAccount, genesisKeyPair,
							newAccountID, payerAccount, genesisKeyPair, nodeAccount2, 1000l);
			Response transferResponse = doTransfer(transferTransaction);
			log.info(transferResponse.getTransactionGetReceipt().getReceipt().getStatus()
					+ ":: is the status of transfer");
		}

		firstPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper.createAccountWithFee(payerAccount, nodeAccount2, firstPair,
				10000l, Collections.singletonList(genesisPrivateKey));
		newAccountID = createAccount(transaction);
		log.info("Account ID is ::" + newAccountID.getAccountNum());

		for (int i = 0; i < MAX_UPDATE_ACCOUNTS; i++) {
			Duration autoRenew = RequestBuilder.getDuration(
					CustomPropertiesSingleton.getInstance().getAccountDuration());
			Transaction updateAccounttransaction = TestHelper.updateAccount(newAccountID, payerAccount,
					genesisPrivateKey, nodeAccount2, autoRenew);
			List<PrivateKey> privateKeyList = new ArrayList<>();
			privateKeyList.add(genesisPrivateKey);
			privateKeyList.add(firstPair.getPrivate());
			Transaction signUpdate = TransactionSigner
					.signTransaction(updateAccounttransaction, privateKeyList);
			log.info("updateAccount request=" + signUpdate);
			Response response = updateAccount(signUpdate);
			log.info(response.getTransactionGetReceipt().getReceipt().getStatus()
					+ "is the update Account response");
		}
		channel.shutdown();
	}

	public AccountID createAccount(Transaction transaction) throws InterruptedException {
		AccountID accountID = AccountID.newBuilder().setAccountNum(100000000).build();
		try {
			TransactionResponse response = stub.createAccount(transaction);
			int count = 0;
			while ((response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)) {
				log.info("in create account busy loop");
				log.info(response.getNodeTransactionPrecheckCode());
				Thread.sleep(10);
				response = stub.createAccount(transaction);
				if (count > MAX_TRIES) {
					break;
				}
				count++;
			}
			log.info(response.getNodeTransactionPrecheckCode() + ":: is the pre check code");
			TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
			accountID =
					getTxReceipt(body.getTransactionID()).getTransactionGetReceipt()
							.getReceipt().getAccountID();
			return accountID;

		} catch (Exception e) {
			e.printStackTrace();
			log.info("There was an error");
		}
		return accountID;

	}

	public Response getTxReceipt(TransactionID transactionID) throws InterruptedException {
		Query query = Query.newBuilder().setTransactionGetReceipt(
				RequestBuilder.getTransactionGetReceiptQuery(transactionID, ResponseType.ANSWER_ONLY))
				.build();
		Response transactionReceipts = Response.newBuilder()
				.setTransactionGetReceipt(TransactionGetReceiptResponse
						.newBuilder().setReceipt(TransactionReceipt.newBuilder().setStatusValue(0).build())
						.build()).build();
		try {
			transactionReceipts = stub.getTransactionReceipts(query);
			int count = 0;
			while ((transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
					!= ResponseCodeEnum.SUCCESS)
					|| (transactionReceipts.getTransactionGetReceipt().getHeader()
					.getNodeTransactionPrecheckCode()
					== ResponseCodeEnum.BUSY)) {
				Thread.sleep(10);
				log.info("in get receipt busy loop");
				transactionReceipts = stub.getTransactionReceipts(query);
				if (count > MAX_TRIES) {
					break;
				}
				count++;
			}
			log.info(transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
					+ ":: is the status");
			return transactionReceipts;

		} catch (Exception e) {
			e.printStackTrace();
			log.info("There was an error");
		}
		return transactionReceipts;
	}

	public Response doTransfer(Transaction transaction) throws Exception {
		TransactionResponse transferResponse = stub.cryptoTransfer(transaction);
		log.info(transferResponse.getNodeTransactionPrecheckCode());
		int count = 0;
		while ((transferResponse.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)
				|| (transferResponse.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK)
		) {
			Thread.sleep(1);
			transferResponse = stub.cryptoTransfer(transaction);
			if (count > MAX_TRIES) {
				break;
			}
			count++;
		}
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		Response transferReceipt = getTxReceipt(body.getTransactionID());
		return transferReceipt;
	}

	public Response getTransactionRecord(Transaction transaction)
			throws Exception {
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		Query query = TestHelper.getTxRecordByTxId(body.getTransactionID(),
				payerAccount, genesisKeyPair, nodeAccount2, TestHelper.getCryptoMaxFee(),
				ResponseType.ANSWER_ONLY);
		Response transactionRecord = stub.getTxRecordByTxID(query);
		int count = 0;
		while ((transactionRecord.getTransactionGetReceipt().getReceipt().getStatus()
				!= ResponseCodeEnum.SUCCESS)
				|| (
				transactionRecord.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode()
						== ResponseCodeEnum.BUSY)) {
			Thread.sleep(10);
			transactionRecord = stub.getTxRecordByTxID(query);
			if (count > MAX_TRIES) {
				break;
			}
			count++;
		}
		return transactionRecord;
	}

	public Response updateAccount(Transaction transaction) throws Exception {
		TransactionResponse updateAccountResponse = stub.updateAccount(transaction);
		log.info(updateAccountResponse.getNodeTransactionPrecheckCode());
		int count = 0;
		while ((updateAccountResponse.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY)
				|| (updateAccountResponse.getNodeTransactionPrecheckCode() != ResponseCodeEnum.OK)
		) {
			Thread.sleep(10);
			updateAccountResponse = stub.updateAccount(transaction);
			if (count > MAX_TRIES) {
				break;
			}
			count++;
		}
		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		Response transferReceipt = getTxReceipt(body.getTransactionID());
		return transferReceipt;
	}
}
