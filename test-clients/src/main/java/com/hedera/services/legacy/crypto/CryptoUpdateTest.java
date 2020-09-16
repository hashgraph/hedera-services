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

import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.UInt64Value;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
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
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CryptoUpdateTest {

	private static final Logger log = LogManager.getLogger(CryptoUpdateTest.class);

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	private static PrivateKey genesisPrivateKey;
	private static KeyPair genKeyPair;
	private static AccountID payerAccount;
	private static AccountID nodeAccount;
	private static KeyPair keyPair;
	private static AccountID accountID;

	public CryptoUpdateTest(int port, String host) throws Exception {
		// connecting to the grpc server on the port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext()
				.build();
		stub = CryptoServiceGrpc.newBlockingStub(channel);

		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		genesisPrivateKey = genKeyPairObj.getPrivateKey();
		genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		payerAccount = genesisAccount.get(0).getAccountId();
		// default node account
		nodeAccount = RequestBuilder
				.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

		TestHelper.initializeFeeClient(channel, payerAccount, genKeyPair, nodeAccount);

		// create an account by payer as genesis
		keyPair = new KeyPairGenerator().generateKeyPair();
		accountID = createAccount();
	}

	public static void main(String args[])
			throws Exception {
		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		CryptoUpdateTest cryptoUpdateTest = new CryptoUpdateTest(port, host);
		cryptoUpdateTest.update_WrapperTest();
		cryptoUpdateTest.update_PrimitiveTest();
		log.info("-------------Finished-----------------");
	}

	public void update_WrapperTest()
			throws Exception {
		// Get account Info of the Account
		CryptoGetInfoResponse.AccountInfo accountInfo = getAccountInfo();
		Assert.assertFalse(accountInfo.getReceiverSigRequired());
		long receiveRecordThreshold = accountInfo.getGenerateReceiveRecordThreshold();
		long sendRecordThreshold = accountInfo.getGenerateSendRecordThreshold();

		// update account' receiverSigRequired from false to be true
		// And update account' receiveRecordThreshold and sendRecordThreshold with wrapper types
		log.info("updating receiverSigRequired from false to be true of account :: " + accountID);
		log.info("updating receiveRecordThreshold and sendRecordThreshold of account (UInt64Value):: " + accountID);
		updateAccount_Wrapper(BoolValue.newBuilder().setValue(true).build(),
				UInt64Value.newBuilder().setValue(sendRecordThreshold - 500).build(),
				UInt64Value.newBuilder().setValue(receiveRecordThreshold - 700).build());

		// Get account Info of the Account After Updated
		accountInfo = getAccountInfo();
		Assert.assertTrue(accountInfo.getReceiverSigRequired());
		log.info("updating receiverSigRequired from false to be true successful" + "\n");
		Assert.assertEquals(sendRecordThreshold - 500, accountInfo.getGenerateSendRecordThreshold());
		Assert.assertEquals(receiveRecordThreshold - 700, accountInfo.getGenerateReceiveRecordThreshold());
		log.info("updating receiveRecordThreshold and sendRecordThreshold (UInt64Value) successful" + "\n");
		log.info("--------------------------------------");

		// update account' receiverSigRequired from true to be false
		log.info("updating receiverSigRequired from true to be false of account :: " + accountID);
		updateAccount_Wrapper(BoolValue.newBuilder().setValue(false).build(),
				null,null);

		// Get account Info of the Account After Updated
		accountInfo = getAccountInfo();
		Assert.assertFalse(accountInfo.getReceiverSigRequired());
		log.info("updating receiverSigRequired from true to be false successful" + "\n");
	}

	public void update_PrimitiveTest() throws Exception {
		// Get account Info of the Account
		CryptoGetInfoResponse.AccountInfo accountInfo = getAccountInfo();
		boolean receiverSigRequired = accountInfo.getReceiverSigRequired();
		long receiveRecordThreshold = accountInfo.getGenerateReceiveRecordThreshold();
		long sendRecordThreshold = accountInfo.getGenerateSendRecordThreshold();

		// update account' receiveRecordThreshold and sendRecordThreshold with primitive types
		log.info("updating receiveRecordThreshold and sendRecordThreshold of account (long):: " + accountID);
		updateAccount_Primitive(sendRecordThreshold - 1000,
				receiveRecordThreshold - 1050);

		// Get account Info of the Account After Updated
		accountInfo = getAccountInfo();
		Assert.assertEquals(receiverSigRequired, accountInfo.getReceiverSigRequired());
		Assert.assertEquals(sendRecordThreshold - 1000, accountInfo.getGenerateSendRecordThreshold());
		Assert.assertEquals(receiveRecordThreshold - 1050, accountInfo.getGenerateReceiveRecordThreshold());
		log.info("updating receiveRecordThreshold and sendRecordThreshold (long) successful" + "\n");
		log.info("--------------------------------------");
	}

	static AccountID createAccount() throws Exception {
		Transaction transaction = TestHelper
				.createAccountWithFee(payerAccount, nodeAccount, keyPair, 1000000l,
						Collections.singletonList(genesisPrivateKey));
		TransactionResponse response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response of Create Account :: " + response.getNodeTransactionPrecheckCode()
						.name());

		TransactionBody body = TransactionBody.parseFrom(transaction.getBodyBytes());
		AccountID newAccountId = TestHelper
				.getTxReceipt(body.getTransactionID(), stub).getAccountID();
		Assert.assertNotNull(newAccountId);
		log.info("Account ID " + newAccountId.getAccountNum() + " created successfully.");
		log.info("--------------------------------------");
		return newAccountId;
	}

	static CryptoGetInfoResponse.AccountInfo getAccountInfo() throws Exception {
		Response accountInfoResponse = TestHelper
				.getCryptoGetAccountInfo(stub, accountID, payerAccount,
						genKeyPair, nodeAccount);
		return accountInfoResponse.getCryptoGetInfo().getAccountInfo();
	}

	static void updateAccount_Wrapper(BoolValue receiverSigRequired,
			UInt64Value sendRecordThreshold, UInt64Value receiveRecordThreshold) throws Exception {
		CryptoUpdateTransactionBody.Builder cryptoUpdateBuilder = updateAccountTxBuilder_wrapper(
				sendRecordThreshold, receiveRecordThreshold, receiverSigRequired);
		Transaction transaction = updateAccountTxBuilder(
				null, null, null, null,
				cryptoUpdateBuilder);
		signAndSubmitTx(transaction);
	}

	static void updateAccount_Primitive(long sendRecordThreshold, long receiveRecordThreshold) throws Exception {
		CryptoUpdateTransactionBody.Builder cryptoUpdateBuilder = updateAccountTxBuilder_primitive(
				sendRecordThreshold, receiveRecordThreshold);
		Transaction transaction = updateAccountTxBuilder(null, null,
				null, null, cryptoUpdateBuilder);
		System.out.println(TransactionBody.parseFrom(transaction.getBodyBytes()));
		signAndSubmitTx(transaction);
	}

	static void signAndSubmitTx(Transaction transaction) throws Exception {
		List<PrivateKey> privateKeyList = new ArrayList<>();
		privateKeyList.add(genesisPrivateKey);
		privateKeyList.add(keyPair.getPrivate());
		Transaction signUpdate = TransactionSigner.signTransaction(transaction, privateKeyList);
		log.info("updateAccount request=" + signUpdate);
		TransactionResponse response = stub.updateAccount(signUpdate);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info(
				"Pre Check Response updateAccount :: " + response.getNodeTransactionPrecheckCode().name());
		TransactionBody updateAccountBody = TransactionBody.parseFrom(transaction.getBodyBytes());
		TransactionReceipt txReceipt = TestHelper.getTxReceipt(updateAccountBody.getTransactionID(), stub);
		Assert.assertTrue(ResponseCodeEnum.OK == txReceipt.getStatus() ||
				ResponseCodeEnum.SUCCESS == txReceipt.getStatus());
	}

	public static CryptoUpdateTransactionBody.Builder updateAccountTxBuilder_wrapper(
			UInt64Value sendRecordThreshold, UInt64Value receiveRecordThreshold,
			BoolValue receiverSigRequired) {
		CryptoUpdateTransactionBody.Builder cryptoUpdateBuilder = CryptoUpdateTransactionBody.newBuilder();
		if (receiverSigRequired != null) {
			cryptoUpdateBuilder.setReceiverSigRequiredWrapper(receiverSigRequired);
		}
		if (sendRecordThreshold != null) {
			cryptoUpdateBuilder.setSendRecordThresholdWrapper(sendRecordThreshold);
		}
		if (receiveRecordThreshold != null) {
			cryptoUpdateBuilder.setReceiveRecordThresholdWrapper(receiveRecordThreshold);
		}
		return cryptoUpdateBuilder;
	}

	public static CryptoUpdateTransactionBody.Builder updateAccountTxBuilder_primitive(
			long sendRecordThreshold, long receiveRecordThreshold) {
		CryptoUpdateTransactionBody.Builder cryptoUpdateBuilder = CryptoUpdateTransactionBody.newBuilder();

		if (sendRecordThreshold != 0) {
			cryptoUpdateBuilder.setSendRecordThreshold(sendRecordThreshold);
		}
		if (receiveRecordThreshold != 0) {
			cryptoUpdateBuilder.setReceiveRecordThreshold(receiveRecordThreshold);
		}
		return cryptoUpdateBuilder;
	}

	public static Transaction updateAccountTxBuilder(Key newKey, AccountID proxyAccountID,
			Duration autoRenewPeriod, Timestamp expirationTime,
			CryptoUpdateTransactionBody.Builder cryptoUpdateBuilder) {

		TransactionID transactionID = RequestBuilder.getTransactionID(
				TestHelper.getDefaultCurrentTimestampUTC(), payerAccount);
		TransactionBody.Builder bodyBuilder = TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setNodeAccountID(nodeAccount)
				.setTransactionFee(FeeClient.getMaxFee())
				.setTransactionValidDuration(RequestBuilder.getDuration(30))
				.setGenerateRecord(true)
				.setMemo("Update Account " + accountID.getAccountNum());

		cryptoUpdateBuilder.setAccountIDToUpdate(accountID);
		if (newKey != null) {
			cryptoUpdateBuilder.setKey(newKey);
		}
		if (proxyAccountID != null) {
			cryptoUpdateBuilder.setProxyAccountID(proxyAccountID);
		}
		if (autoRenewPeriod != null) {
			cryptoUpdateBuilder.setAutoRenewPeriod(autoRenewPeriod);
		}
		if (expirationTime != null) {
			cryptoUpdateBuilder.setExpirationTime(expirationTime);
		}

		bodyBuilder.setCryptoUpdateAccount(cryptoUpdateBuilder.build());
		byte[] bodyBytesArr = bodyBuilder.build().toByteArray();
		ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
		return Transaction.newBuilder().setBodyBytes(bodyBytes).build();
	}
}
