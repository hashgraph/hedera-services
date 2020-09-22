package com.hedera.services.legacy.client.util;

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
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hedera.services.legacy.client.core.BuildQuery;
import com.hedera.services.legacy.client.core.BuildTransaction;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.TestHelper;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import static com.hedera.services.legacy.core.TestHelper.DEFAULT_SEND_RECV_RECORD_THRESHOLD;
import static org.junit.Assert.fail;

public class Common {
	private final static Logger log = LogManager.getLogger(Common.class);

	private static final int MAX_RECEIPT_RETRIES = 120;
	public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup

	//based on current transaction account and time stamp to control TPS by waiting
	public static float tpsControl(long startTime, long currentTranAmount, float targetTPS) {
		long waitTimeMS = (long) Math.ceil(1000.0f / targetTPS);
		long endTimeMS = System.currentTimeMillis();
		float currentTPS = getTPS(startTime, endTimeMS, currentTranAmount);
		if (currentTPS > targetTPS) { // too fast then wait
			try {
				Thread.sleep(waitTimeMS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return currentTPS;
	}

	// calculate latest TPS
	public static float getTPS(long startTimeMS, long endTimeMS, long count) {
		return count / ((float) (endTimeMS - startTimeMS) / 1000.0f);
	}

	public static ContractID getContractIDfromReceipt(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID payerAccount, TransactionID txId) throws Exception {
		TransactionGetReceiptResponse contractCreateReceipt = getReceiptByTransactionId(stub, txId);
		if (contractCreateReceipt.getReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
			log.warn("Create contract receipt status was " +
					contractCreateReceipt.getReceipt().getStatus());
			return null;
		}
		return contractCreateReceipt.getReceipt().getContractID();
	}

	public static AccountID getAccountIDfromReceipt(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			TransactionID txId) throws Exception {
		TransactionGetReceiptResponse contractCreateReceipt = getReceiptByTransactionId(stub, txId);
		if (contractCreateReceipt.getReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
			log.warn("Create contract receipt status was " +
					contractCreateReceipt.getReceipt().getStatus());
			return null;
		}
		return contractCreateReceipt.getReceipt().getAccountID();
	}

	public static FileID getFileIDfromReceipt(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			TransactionID txId) throws Exception {
		TransactionGetReceiptResponse Receipt = getReceiptByTransactionId(stub, txId);
		if (Receipt.getReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
			log.warn("Create contract receipt status was " +
					Receipt.getReceipt().getStatus());
			return null;
		}
		return Receipt.getReceipt().getFileID();
	}

	public static TransactionGetReceiptResponse getReceiptByTransactionId(
			CryptoServiceGrpc.CryptoServiceBlockingStub stub, TransactionID transactionId)
			throws Exception {
		while (true){
			Response response = querySubmit(() -> {
				try {
					return Query.newBuilder()
							.setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
									transactionId, ResponseType.ANSWER_ONLY)).build();
				} catch (Exception e) {
					return null;
				}
			}, stub::getTransactionReceipts);

			ResponseCodeEnum code = response.getTransactionGetReceipt().getReceipt().getStatus();
			ResponseCodeEnum preCheck =  response.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode();

			if(preCheck == ResponseCodeEnum.RECEIPT_NOT_FOUND){
				return null;
			}else if (code == ResponseCodeEnum.UNKNOWN ){
				//retry
				Thread.sleep(200);
			}else if (code == ResponseCodeEnum.SUCCESS){
				return response.getTransactionGetReceipt();
			}else{
				log.warn("Unexpected receipt response {} ", response);
				return response.getTransactionGetReceipt();
			}
		}
	}


	public static Response getRawReceiptByTransactionId(
			CryptoServiceGrpc.CryptoServiceBlockingStub stub, TransactionID transactionId)
			throws Exception {
		while (true){
			Response response = querySubmit(() -> {
				try {
					return Query.newBuilder()
							.setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
									transactionId, ResponseType.ANSWER_ONLY)).build();
				} catch (Exception e) {
					return null;
				}
			}, stub::getTransactionReceipts);

			ResponseCodeEnum code = response.getTransactionGetReceipt().getReceipt().getStatus();
			ResponseCodeEnum preCheck =  response.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode();

			if(preCheck == ResponseCodeEnum.RECEIPT_NOT_FOUND){
				return null;
			}else if (code == ResponseCodeEnum.UNKNOWN ){
				//retry
				Thread.sleep(200);
			}else{
				return response;
			}
		}
	}
	/**
	 * A utility function used to submit transaction to different stub whether response handling and retry.
	 * If response is BUSY or PLATFORM_TRANSACTION_NOT_CREATED then try build transaction again and resubmit,
	 * otherwise assert as unexpected error (insufficient fee, invalid signature, etc)
	 *
	 * @param builder the function call to create transaction to be submitted
	 * @param stubFunc the stub function entry to submit the request
	 * @return return the successfully submitted transactions.
	 */
	public static Transaction tranSubmit(BuildTransaction builder, Function<Transaction, TransactionResponse> stubFunc)
			throws StatusRuntimeException
	{
		Transaction transaction;
		while (true) {
			try {
				transaction = builder.callBuilder();
				Assert.assertNotEquals(transaction, null);
				TransactionResponse response = stubFunc.apply(transaction);

				if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.OK) {
					break;
				} else if (response.getNodeTransactionPrecheckCode() == ResponseCodeEnum.BUSY ||
						response.getNodeTransactionPrecheckCode()
								== ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED) {
					// Try again
					Thread.sleep(50);
				} else {
					log.error("Unexpected response {}", response);
					break;
				}
			}catch (InterruptedException e){
				log.error("Exception ", e);
				return null;
			} catch (io.grpc.StatusRuntimeException e){
				throw e;
			}
		}
		return transaction;
	}

	/**
	 * Keep send query if platform is busy
	 * @param builder the function call to build query to be submitted
	 * @param stubFunc the stub function entry to submit the query
	 * @return response from server
	 */
	public static Response querySubmit(BuildQuery builder, Function<Query, Response> stubFunc)
	{
		Response response;
		while (true) {
			try {
				Query query = builder.callBuilder();
				response = stubFunc.apply(query);

				ResponseCodeEnum preCheckCode = ResponseCodeEnum.UNKNOWN;

				if (query.hasTransactionGetRecord() ){
					preCheckCode = response.getTransactionGetRecord()
							.getHeader().getNodeTransactionPrecheckCode();
				}
				if (preCheckCode == ResponseCodeEnum.BUSY ||
						preCheckCode == ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED) {
					// Try again
					Thread.sleep(50);
				} else {
					return response;
				}
			}catch (InterruptedException e) {
				log.error("Exception ", e);
				return null;
			}
		}
	}

	public static void addKeyMap(KeyPair pair, Map<String, PrivateKey> pubKey2privKeyMap) {
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		String pubKeyHex = bytes2Hex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
	}

	public static Key PrivateKeyToKey(PrivateKey privateKey){
		byte[] pubKey = ((EdDSAPrivateKey) privateKey).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		return key;
	}

	public static Key keyPairToKey(KeyPair pair){
		byte[] pubKey = ((EdDSAPrivateKey) pair.getPrivate()).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		return key;
	}

	public static Key keyPairToPubKey(KeyPair pair){
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		return key;
	}

	public static Transaction getCreateContractRequestWithSigMap(Long payerAccountNum, Long payerRealmNum,
			Long payerShardNum,
			Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
			long transactionFee, Timestamp timestamp, Duration txDuration,
			boolean generateRecord, String txMemo, long gas, FileID fileId,
			ByteString constructorParameters, long initialBalance,
			Duration autoRenewalPeriod, List<PrivateKey> keys, String contractMemo,
			Key adminKey, Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {

		Transaction transaction = RequestBuilder
				.getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
						autoRenewalPeriod, contractMemo, adminKey);

		transaction = TransactionSigner.signTransaction(transaction, keys);

		transactionFee = FeeClient.getContractCreateFee(transaction, keys.size());

		transaction = RequestBuilder
				.getCreateContractRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, generateRecord, txMemo, gas, fileId, constructorParameters, initialBalance,
						autoRenewalPeriod, contractMemo, adminKey);

		List<Key> keyList = new ArrayList<>();
		for (PrivateKey pk : keys) {
			keyList.add(PrivateKeyToKey(pk));
		}
		transaction = TransactionSigner.signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);
		return transaction;
	}

	public static Transaction getContractCallRequestWithSigMap(Long payerAccountNum, Long payerRealmNum,
			Long payerShardNum,
			Long nodeAccountNum, Long nodeRealmNum, Long nodeShardNum,
			long transactionFee, Timestamp timestamp,
			Duration txDuration, long gas, ContractID contractId,
			ByteString functionData, long value,
			List<PrivateKey> keys, Map<String, PrivateKey> pubKey2privKeyMap) throws Exception {

		Transaction transaction = RequestBuilder
				.getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, gas, contractId, functionData, value);

		transaction = TransactionSigner.signTransaction(transaction, keys);

		transactionFee = FeeClient.getCostContractCallFee(transaction, keys.size());

		transaction = RequestBuilder
				.getContractCallRequest(payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum,
						nodeRealmNum, nodeShardNum, transactionFee, timestamp,
						txDuration, gas, contractId, functionData, value);

		List<Key> keyList = new ArrayList<>();
		for (PrivateKey pk : keys) {
			keyList.add(PrivateKeyToKey(pk));
		}

		return TransactionSigner.signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap);

	}

	/**
	 * Creates an account with complex keys with max tx fee.
	 */
	public static Transaction createAccountComplex(AccountID payerAccount, Key payerKey, AccountID nodeAccount,
			Key key, long initialBalance, Map<String, PrivateKey> pubKey2privKeyMap) {
		long transactionFee = TestHelper.getCryptoMaxFee();
		Properties properties = TestHelper.getApplicationProperties();
		long accountDuration = Long.parseLong(properties.getProperty("ACCOUNT_DURATION"));
		Duration autoRenewDuration = RequestBuilder.getDuration(accountDuration);
		boolean receiverSigRequired = false;

		Transaction transaction = createAccountComplex(payerAccount, payerKey, nodeAccount, key,
				initialBalance, transactionFee, receiverSigRequired, autoRenewDuration, pubKey2privKeyMap);
		try {
			transactionFee = FeeClient.getCreateAccountFee(transaction, 1);
		} catch (Exception e) {
			log.error("Exception ", e);
		}

		return createAccountComplex(payerAccount, payerKey, nodeAccount, key,
				initialBalance, transactionFee, receiverSigRequired, autoRenewDuration, pubKey2privKeyMap);
	}


	/**
	 * Creates an account with complex keys.
	 */
	public static Transaction createAccountComplex(AccountID payerAccount, Key payerKey, AccountID nodeAccount,
			Key key, long initialBalance, long transactionFee, boolean receiverSigRequired,
			Duration autoRenewPeriod, Map<String, PrivateKey> pubKey2privKeyMap) {
		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Duration transactionDuration = RequestBuilder.getDuration(TX_DURATION_SEC);

		boolean generateRecord = true;
		String memo = "Create Account Test";
		long sendRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;
		long receiveRecordThreshold = DEFAULT_SEND_RECV_RECORD_THRESHOLD;

		Transaction createAccountRequest = RequestBuilder
				.getCreateAccountBuilder(payerAccount.getAccountNum(),
						payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
						nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transactionFee, timestamp,
						transactionDuration,
						generateRecord, memo, key, initialBalance, sendRecordThreshold, receiveRecordThreshold,
						receiverSigRequired, autoRenewPeriod);
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		if (receiverSigRequired) {
			keys.add(key);
		}

		Transaction transaction = null;
		try {
			transaction = TransactionSigner
					.signTransactionComplexWithSigMap(createAccountRequest, keys, pubKey2privKeyMap);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return transaction;
	}

	public static Transaction buildCryptoDelete(AccountID payer, Key payerKey,
			AccountID deleteAccount, Key accKey,
			AccountID transferAccount,
			AccountID nodeAccount, Map<String, PrivateKey> pubKey2privKeyMap){
		Duration transactionValidDuration = RequestBuilder.getDuration(100);
		CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
				.newBuilder().setDeleteAccountID(deleteAccount).setTransferAccountID(transferAccount)
				.build();
		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		TransactionID  transactionID = TransactionID.newBuilder().setAccountID(payer)
				.setTransactionValidStart(timestamp).build();
		TransactionBody transactionBody = TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setNodeAccountID(AccountID.newBuilder().setAccountNum(nodeAccount.getAccountNum()).build())
				.setTransactionFee(TestHelper.getContractMaxFee())
				.setTransactionValidDuration(transactionValidDuration)
				.setMemo("Crypto Delete")
				.setCryptoDelete(cryptoDeleteTransactionBody)
				.build();

		byte[] bodyBytesArr = transactionBody.toByteArray();
		ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);
		Transaction deletetx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();

		List<Key> keys = new ArrayList<>();
		keys.add(payerKey);
		keys.add(accKey);
		Transaction signDelete = null;
		try {
			signDelete = TransactionSigner
					.signTransactionComplexWithSigMap(deletetx, keys, pubKey2privKeyMap);
		} catch (Exception e) {
			return null;
		}
		return signDelete;
	}

	public static byte[] encodeSetWithFuncStr(final int valueToAdd, final String FunctionStr) {
		String funcJson = FunctionStr.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function.encode(valueToAdd);
	}


	public static byte[] encodeGetValue(final String FunctionStr) {
		String funcJson = FunctionStr.replaceAll("'", "\"");
		CallTransaction.Function function = CallTransaction.Function.fromJsonInterface(funcJson);
		return function.encode();
	}

	public static Transaction createTransfer(AccountID fromAccount, PrivateKey fromKey,
			AccountID toAccount,
			AccountID payerAccount, PrivateKey payerAccountKey, AccountID nodeAccount,
			long amount, String memo) {
		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Duration transactionDuration = RequestBuilder.getDuration(30);

		Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 0, timestamp, transactionDuration,
				false,
				memo, fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
				amount);
		// sign the tx
		List<PrivateKey> privKeysList = new ArrayList<>();
		privKeysList.add(payerAccountKey);
		privKeysList.add(fromKey);

		Transaction signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);

		long transferFee = 0;
		try {
			transferFee = FeeClient.getTransferFee(signedTx, privKeysList.size());
		} catch (Exception e) {
			e.printStackTrace();
		}
		transferTx = RequestBuilder.getCryptoTransferRequest(payerAccount.getAccountNum(),
				payerAccount.getRealmNum(), payerAccount.getShardNum(), nodeAccount.getAccountNum(),
				nodeAccount.getRealmNum(), nodeAccount.getShardNum(), transferFee, timestamp,
				transactionDuration, false,
				memo, fromAccount.getAccountNum(), -amount, toAccount.getAccountNum(),
				amount);

		signedTx = TransactionSigner.signTransaction(transferTx, privKeysList);
		return signedTx;
	}

	public static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID accountID,
			AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount) throws Exception {
		Response accountInfoResponse = TestHelper.getCryptoGetBalance(stub, accountID, payerAccount,
				payerKeyPair, nodeAccount);

		Assert.assertNotNull(accountInfoResponse.getCryptogetAccountBalance());
		return accountInfoResponse.getCryptogetAccountBalance().getBalance();
	}

	public static Map<AccountID, Long> createBalanceMap(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			List<AccountID> accountIDList, AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount){
		Map<AccountID, Long> balanceMap = new HashMap<>();
		for(AccountID entry : accountIDList){
			long currentBalance = 0;
			try {
				currentBalance = getAccountBalance(stub,
						entry,
						payerAccount, payerKeyPair, nodeAccount);
				balanceMap.put(entry, currentBalance);
			} catch (Exception e) {
				log.error("Exception ", e);
			}
		}
		return balanceMap;
	}

	/**
	 * Verify a list of account balance, whether changed according to transferList
	 * @param preBalance
	 * @param transferList
	 * @return
	 */
	public static boolean verifyAccountBalance(Map<AccountID, Long> preBalance, TransferList transferList,
			Map<AccountID, Long> actualAfterBalance)
	{
		boolean checkResult = true;
		Map<AccountID, Long> expectedAfterBalance = new HashMap<>();
		expectedAfterBalance.putAll(preBalance);
		List<AccountAmount> amountList = transferList.getAccountAmountsList();

		// update account balance according to transferList
		for(AccountAmount item : amountList){
			AccountID accountID = item.getAccountID();
			long value = item.getAmount();
			if(expectedAfterBalance.get(accountID)!=null){
				expectedAfterBalance.put(accountID, expectedAfterBalance.get(accountID).longValue() + value);
			}else{
				log.error("Account {} not found in expectedAfterBalance, TransferList = {}", accountID, transferList);
				fail();
			}
		}

		for (Map.Entry<AccountID, Long> entry : expectedAfterBalance.entrySet()){
			try {
				long currentBalance = actualAfterBalance.get(entry.getKey());
				long expectedBalance = entry.getValue();
				if (expectedBalance != currentBalance){
					log.error("Error Account {}", entry);
					log.error("Balance mismatch for Account {}, {} vs {}, diff = {}", entry.getKey(),
							expectedBalance, currentBalance, (expectedBalance-currentBalance));
					log.error("preBalance {} ", preBalance.get(entry.getKey()));
					log.error("expectedAfterBalance {} ", expectedAfterBalance.get(entry.getKey()));

					checkResult = false;
				}
			}catch (Exception e){
				log.error("Exception ", e);
			}
		}
		if (!checkResult){
			log.error("transferList {} ", transferList);
			log.error("ERROR");
			fail();
		}
		return checkResult;
	}

	public static void accountDiff(Map<AccountID, Long> before, Map<AccountID, Long> after){
		for (Map.Entry<AccountID, Long> entry : before.entrySet()){
			try {
				long afterBalance = after.get(entry.getKey());
				long beforeBalance = entry.getValue();
				if (beforeBalance != afterBalance){
					log.error("Balance change for Account {} = {}", entry.getKey(), (afterBalance-beforeBalance));
				}
			}catch (Exception e){
				log.error("Exception ", e);
			}

		}
	}

	/**
	 * Encodes bytes to a hex string.
	 *
	 * @param bytes data to be encoded
	 * @return hex string
	 */
	public static String bytes2Hex(byte[] bytes) {
	  String str = Hex.encodeHexString(bytes);
	  return str;
	}
}
