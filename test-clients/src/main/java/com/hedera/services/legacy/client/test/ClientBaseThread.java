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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.legacy.client.core.GrpcStub;
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.AllProxyStakers;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoGetStakersQuery;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.GetBySolidityIDResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hedera.services.legacy.client.util.Common.createAccountComplex;
import static org.junit.Assert.fail;

/**
 * Base class for all test case
 */
public class ClientBaseThread extends Thread {
	private static final Logger log = LogManager.getLogger(ClientBaseThread.class);
	private static int MAX_RETRY_TIMES = 10;

	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();

	public static AccountID DEFAULT_FEE_COLLECTION_ACCOUNT = RequestBuilder.getAccountIdBuild(98L, 0L, 0L);
	;

	boolean useSigMap;
	long nodeAccountNumber = 3;
	String host;
	int port;

	SmartContractServiceGrpc.SmartContractServiceBlockingStub sCServiceStub;
	CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	ManagedChannel channel;

	AccountID genesisAccount;
	PrivateKey genesisPrivateKey;
	KeyPair genesisKeyPair;
	AccountID nodeAccount;

	Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();
	Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
	Map<AccountID, Key> acc2ComplexKeyMap = new LinkedHashMap<>();

	/** all successfully submitted transactions' ID */
	List<TransactionID> submittedTxID = new LinkedList<>();

	/** all confirmed, reached consensus transaction record */
	List<TransactionRecord> confirmedTxRecord = new LinkedList<>();

	/** whether save all transaction ID and tx record to be later used for checking */
	boolean isBackupTxIDRecord;

	/** whether use transferList from transaction record to check against current balance */
	boolean isCheckTransferList = false;

	GrpcStub grpcStub;


	public ClientBaseThread() {
		this("localhost", 50211, 3, false, null, 0);
	}

	public ClientBaseThread(String host, int port, long nodeAccountNumber, boolean useSigMap, String[] args,
			int index) {
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

	void enableBackupTxIDRecord() {
		isBackupTxIDRecord = true;
	}

	// Will be override by derived children class
	void demo() throws Exception {
	}


	public void reConnectChannel(Exception prevException) throws Exception {

		if (prevException.getCause() != null && prevException.getCause().getMessage().contains("Operation timed out")) {
			throw new Exception("Operation time out, server seems down, no need to reconnect");
		}
		if (prevException.getCause() != null && prevException.getCause().getMessage().contains("Connection refused")) {
			throw new Exception("Connection refused, server seems down, no need to reconnect");
		}

		// reference https://github.com/census-instrumentation/opencensus-java/issues/869
		// 		max_age is the server saying the connection is too old
		// 		issue a new RPC and things should still be functioning.
		if (prevException.toString().contains("max_age") && prevException.toString().contains("NO_ERROR")) {
			// GRPC server broke the connection with error HTTP/2 error code: NO_ERROR Received Goaway max_age
			// do nothing just reissue rpc request
			log.info("{} max_age happened， no need to reconnect ", getName());
			return;
		}
		long stTime = System.currentTimeMillis();
		log.warn("{} is reconnecting...", getName());
		int retry = 0;

		log.info("{} channel state {}", getName(), channel.getState(false));

		while (channel == null || (channel.getState(false) != ConnectivityState.READY
				&& channel.getState(false) != ConnectivityState.IDLE)) {

			channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
			if (retry < MAX_RETRY_TIMES) {
				log.info("{} Retry times {} state {}", getName(), retry, channel.getState(false));
				retry++;
			} else {
				// reach max retry throw error to avoid endless retrying
				throw new Exception("reconnect try failed ");
			}
			Thread.sleep(50);
		}
		stub = CryptoServiceGrpc.newBlockingStub(channel);
		sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channel);

		long endTime = System.currentTimeMillis() - stTime;
		log.info("{} Reconnect took {} milliseconds {}", getName(), endTime, channel.getState(false));
	}


	boolean tryReconnect(Exception prevException) {
		log.error("{} GRPC error", getName(), prevException);
		try {
			reConnectChannel(prevException);
		} catch (Exception ex) {
			log.error("{} Reconnect error, quitting", getName(), ex);
			return false;
		}
		return true;
	}

	void initAccountsAndChannels() throws Exception {
		Map<String, List<AccountKeyListObj>> hederaAccounts = null;
		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(INITIAL_ACCOUNTS_FILE);

		// Get Genesis Account key Pair
		List<AccountKeyListObj> genesisAccountList = keyFromFile.get("START_ACCOUNT");

		// get Private Key
		genesisPrivateKey = genesisAccountList.get(0).getKeyPairList().get(0).getPrivateKey();

		genesisKeyPair = new KeyPair(genesisAccountList.get(0).getKeyPairList().get(0).getPublicKey(),
				genesisPrivateKey);

		// get the Account Object
		genesisAccount = genesisAccountList.get(0).getAccountId();
		List<PrivateKey> genesisKeyList = new ArrayList<PrivateKey>(1);
		genesisKeyList.add(genesisPrivateKey);
		accountKeys.put(genesisAccount, genesisKeyList);


		KeyPairObj genesisKeyPair = genesisAccountList.get(0).getKeyPairList().get(0);
		String pubKeyHex = genesisKeyPair.getPublicKeyAbyteStr();
		Key akey = null;

		if (KeyExpansion.USE_HEX_ENCODED_KEY) {
			akey = Key.newBuilder().setEd25519(ByteString.copyFromUtf8(pubKeyHex)).build();
		} else {
			akey = Key.newBuilder().setEd25519(ByteString.copyFrom(hexToBytes(pubKeyHex)))
					.build();
		}

		pubKey2privKeyMap.put(pubKeyHex, genesisKeyPair.getPrivateKey());
		acc2ComplexKeyMap.put(genesisAccount,
				Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(akey)).build());


		nodeAccount = RequestBuilder.getAccountIdBuild(nodeAccountNumber, 0l, 0l);

		channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		stub = CryptoServiceGrpc.newBlockingStub(channel);
		sCServiceStub = SmartContractServiceGrpc.newBlockingStub(channel);
	}

	Transaction createQueryHeaderTransfer(AccountID payer, long transferAmt)
			throws Exception {

		Transaction transferTx = Common.createTransfer(payer, accountKeys.get(payer).get(0),
				nodeAccount, payer,
				accountKeys.get(payer).get(0), nodeAccount, transferAmt, "queryFrom" + payer.getAccountNum());
		return transferTx;

	}

	TransactionID createContractOnly(AccountID payerAccount, FileID contractFile, byte[] constructorData,
			long durationInSeconds, Key adminKey) throws Exception {
		return createContractOnly(payerAccount, contractFile, constructorData, durationInSeconds, adminKey, 0);
	}

	TransactionID createContractOnly(AccountID payerAccount, FileID contractFile, byte[] constructorData,
			long durationInSeconds, Key adminKey, long initialBalance) throws Exception {
		Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		final ByteString dataToPass = (constructorData != null) ? ByteString.copyFrom(
				constructorData) : ByteString.EMPTY;

		Transaction createContractRequest = Common.tranSubmit(() -> {
			Transaction request;
			try {
				if (useSigMap) {
					request = Common
							.getCreateContractRequestWithSigMap(payerAccount.getAccountNum(),
									payerAccount.getRealmNum(),
									payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
									nodeAccount.getShardNum(), 100l, timestamp,
									transactionDuration, true, "", 2500000, contractFile, dataToPass, initialBalance,
									contractAutoRenew, accountKeys.get(payerAccount), "", adminKey, pubKey2privKeyMap);
				} else {
					request = TestHelper
							.getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
									payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
									nodeAccount.getShardNum(), 100l, timestamp,
									transactionDuration, true, "", 2500000, contractFile, dataToPass, initialBalance,
									contractAutoRenew, accountKeys.get(payerAccount), "", adminKey);
				}
			} catch (Exception e) {
				log.error("Build request exception ", e);
				return null;
			}
			return request;
		}, sCServiceStub::createContract);

		TransactionBody createContractBody = TransactionBody
				.parseFrom(createContractRequest.getBodyBytes());
		TransactionID txId = createContractBody.getTransactionID();
		return txId;
	}

	TransactionID callContract(AccountID payerAccount, ContractID contractToCall, byte[] data, int value, long gasValue)
			throws Exception {

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Duration transactionDuration = RequestBuilder.getDuration(180);
		//payerAccountNum, payerRealmNum, payerShardNum, nodeAccountNum, nodeRealmNum, nodeShardNum, transactionFee,
		// timestamp, txDuration, gas, contractId, functionData, value, signatures
		final ByteString dataBstr = (data != null) ? ByteString.copyFrom(data) : ByteString.EMPTY;

		Transaction result = Common.tranSubmit(() -> {
			Transaction request;
			try {
				if (useSigMap) {
					request = Common
							.getContractCallRequestWithSigMap(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
									payerAccount.getShardNum(), nodeAccountNumber, 0l, 0l,
									TestHelper.getContractMaxFee(), timestamp,
									transactionDuration, 25_000_000_000L, contractToCall, dataBstr, value,
									accountKeys.get(payerAccount), pubKey2privKeyMap);
				} else {
					request = TestHelper
							.getContractCallRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
									payerAccount.getShardNum(), nodeAccountNumber, 0l, 0l,
									TestHelper.getContractMaxFee(), timestamp,
									transactionDuration, 25_000_000_000L, contractToCall, dataBstr, value,
									accountKeys.get(payerAccount));
				}
			} catch (Exception e) {
				log.error("Call contract generating transaction error ", e);
				return null;
			}
			return request;
		}, sCServiceStub::contractCallMethod);

		TransactionID txId = TransactionBody.parseFrom(result.getBodyBytes())
				.getTransactionID();
		return txId;
	}

	TransactionID deleteContract(AccountID payerAccount, KeyPair payerKeyPair, ContractID contractID,
			AccountID transferAccount) throws Exception {

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);

		List<Key> keyList = new ArrayList<>();
		keyList.add(Common.keyPairToPubKey(payerKeyPair));

		Transaction transaction = Common.tranSubmit(() -> {
			Transaction deleteRequest;
			try {
				deleteRequest = RequestBuilder
						.getDeleteContractRequest(payerAccount, nodeAccount, TestHelper.getContractMaxFee(), timestamp,
								transactionDuration, contractID, transferAccount, null, true,
								"deleteContract", SignatureList.getDefaultInstance());

				deleteRequest = TransactionSigner
						.signTransactionComplexWithSigMap(deleteRequest, keyList, pubKey2privKeyMap);

			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return deleteRequest;
		}, sCServiceStub::deleteContract);
		if (transaction != null) {
			return TransactionBody.parseFrom(transaction.getBodyBytes())
					.getTransactionID();
		} else {
			return null;
		}

	}


	TransactionID updateContract(AccountID payerAccount, KeyPair payerKeyPair, ContractID contractToUpdate,
			Duration autoRenewPeriod, Timestamp expirationTime, Key adminKey) throws Exception {

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);

		List<Key> keyList = new ArrayList<>();
		keyList.add(Common.keyPairToPubKey(payerKeyPair));

		Transaction transaction = Common.tranSubmit(() -> {
			Transaction updateRequest;
			try {
				updateRequest = RequestBuilder
						.getContractUpdateRequest(payerAccount, nodeAccount, TestHelper.getContractMaxFee(), timestamp,
								transactionDuration, true, "updateContract", contractToUpdate,
								autoRenewPeriod, adminKey, null,
								expirationTime, SignatureList.getDefaultInstance(), "newContract");

				updateRequest = TransactionSigner
						.signTransactionComplexWithSigMap(updateRequest, keyList, pubKey2privKeyMap);


			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return updateRequest;
		}, sCServiceStub::updateContract);
		if (transaction != null) {
			return TransactionBody.parseFrom(transaction.getBodyBytes())
					.getTransactionID();
		} else {
			return null;
		}

	}

	TransactionID callCreateAccount(AccountID payerAccount, KeyPair newAccountKeyPair,
			long initialBalance) throws InvalidProtocolBufferException {
		Transaction transaction = Common.tranSubmit(() -> {
			Transaction createRequest;
			try {
				if (useSigMap) {
					byte[] pubKey = ((EdDSAPublicKey) newAccountKeyPair.getPublic()).getAbyte();
					Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
					Key newAccountKeyList = Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(key).build()).build();
					Key payerKey = acc2ComplexKeyMap.get(payerAccount);
					createRequest = Common.createAccountComplex(payerAccount, payerKey, nodeAccount, newAccountKeyList, initialBalance,
							pubKey2privKeyMap);
				} else {
					createRequest = TestHelper
							.createAccountWithFee(payerAccount, nodeAccount, newAccountKeyPair, initialBalance,
									accountKeys.get(payerAccount));
				}
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return createRequest;
		}, stub::createAccount);
		if (transaction != null) {
			return TransactionBody.parseFrom(transaction.getBodyBytes())
					.getTransactionID();
		} else {
			return null;
		}
	}

	// Ask for ANSWER ONLY
	TransactionRecord getTransactionRecordANSWER(AccountID payerAccount,
			TransactionID transactionId) throws Exception {
		long fee = FeeClient.getCostForGettingTxRecord();
		Pair<Transaction, Response> pair = executeQueryForTxRecord(payerAccount, transactionId, fee,
				ResponseType.ANSWER_ONLY, false);
		Response recordResp = pair.getRight();
		if (recordResp == null) {
			return null;
		}
		if (recordResp.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode() == RECORD_NOT_FOUND) {
			return null;
		}
		TransactionRecord txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
		if (txRecord.toString().isEmpty()) {
			log.error("Empty txRecord recordResp == {} ", recordResp);
		}
		return txRecord;
	}

	long getTransactionRecordFee(AccountID payerAccount,
			TransactionID transactionId){
		long fee = FeeClient.getCostForGettingTxRecord();
		Pair<Transaction, Response> pair = executeQueryForTxRecord(payerAccount, transactionId, fee,
				ResponseType.COST_ANSWER, false);
		Response recordResp = pair.getRight();
		fee = recordResp.getTransactionGetRecord().getHeader().getCost();
		return fee;
	}

	// Ask for cost of answer first, then ask for ANSWER
	// if status is unknown, keep trying again if retryUnknown is true
	TransactionRecord getTransactionRecord(AccountID payerAccount,
			TransactionID transactionId, boolean retryUnknown) throws Exception {

		Pair<TransactionRecord, TransactionID> result = getTransactionRecordAndQueryTransactionID(payerAccount,
				transactionId, retryUnknown);
		if (result != null) {
			return result.getLeft();
		} else {
			return null;
		}
	}

	Pair<TransactionRecord, TransactionID> getTransactionRecordAndQueryTransactionID(AccountID payerAccount,
			TransactionID transactionId, boolean retryUnknown) throws Exception {
		long fee = FeeClient.getCostForGettingTxRecord();
		Pair<Transaction, Response> pair = executeQueryForTxRecord(payerAccount, transactionId, fee,
				ResponseType.COST_ANSWER, false);
		Response recordResp = pair.getRight();
		if (recordResp == null) {
			return null;
		}
		if (recordResp.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode() == RECORD_NOT_FOUND) {
			return null;
		}
		fee = recordResp.getTransactionGetRecord().getHeader().getCost();
		TransactionRecord txRecord = null;
		while (true) {
			pair = executeQueryForTxRecord(payerAccount, transactionId, fee,
					ResponseType.ANSWER_ONLY, true);
			recordResp = pair.getRight();
			if (recordResp == null) {
				return null;
			}
			if (recordResp.getTransactionGetRecord().getHeader().getNodeTransactionPrecheckCode() == RECORD_NOT_FOUND) {
				return null;
			}

			txRecord = recordResp.getTransactionGetRecord().getTransactionRecord();
			if (txRecord.toString().isEmpty()) {
				log.error("Empty txRecord recordResp == {} ", recordResp);
			}
			ResponseCodeEnum responseCode = txRecord.getReceipt().getStatus();
			if (retryUnknown && responseCode.equals(ResponseCodeEnum.UNKNOWN)) {
				log.info("Status unknown, keep retry");
				Thread.sleep(300);
			} else {
				break;
			}
		}
		TransactionID queryTranID = TransactionBody.parseFrom(pair.getLeft().getBodyBytes())
				.getTransactionID();
		return Pair.of(txRecord, queryTranID);
	}

	private Transaction queryPaymentTx;

	Pair<Transaction, Response> executeQueryForTxRecord(AccountID payerAccount, TransactionID transactionId,
			long fee, ResponseType responseType, boolean trackTxID) {
		Response response = Common.querySubmit(() -> {
			try {
				queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				Query getRecordQuery = RequestBuilder
						.getTransactionGetRecordQuery(transactionId, queryPaymentTx, responseType);
				return getRecordQuery;
			} catch (Exception e) {
				return null;
			}
		}, stub::getTxRecordByTxID);
		return Pair.of(queryPaymentTx, response);
	}

	public ContractCallLocalResponse callContractLocal(AccountID payerAccount,
			ContractID contractToCall, byte[] data, long gas, long localCallGas) throws Exception {
		ByteString callData = ByteString.EMPTY;
		int callDataSize = 0;
		if (data != null) {
			callData = ByteString.copyFrom(data);
			callDataSize = callData.size();
		}
		long fee = FeeClient.getCostContractCallLocalFee(callDataSize);
		Response callResp;
		callResp = executeContractCall(payerAccount, contractToCall, callData, fee,
				gas, ResponseType.COST_ANSWER);
		fee = callResp.getContractCallLocal().getHeader().getCost() + localCallGas;


		callResp = executeContractCall(payerAccount, contractToCall, callData, fee,
				gas, ResponseType.ANSWER_ONLY);
		Assert.assertTrue(callResp.hasContractCallLocal());
		ContractCallLocalResponse response = callResp.getContractCallLocal();
		return response;
	}

	public Pair<List<Transaction>, ContractCallLocalResponse> callContractLocal2(AccountID payerAccount,
			ContractID contractToCall, byte[] data, long gas, long localCallGas) {
		ByteString callData =  (data != null) ? ByteString.copyFrom(
				data) : ByteString.EMPTY;
		long fee = FeeClient.getCostContractCallLocalFee(callData.size());
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getContractCallLocalQuery(contractToCall, gas,
						callData, 0, 5000, queryPaymentTx, ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::contractCallLocalMethod);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());

		long actualFee = response.getContractCallLocal().getHeader().getCost() + localCallGas;
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, actualFee);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getContractCallLocalQuery(contractToCall, gas,
						callData, 0, 5000, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::contractCallLocalMethod);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractCallLocal().getHeader().getNodeTransactionPrecheckCode());

		ContractCallLocalResponse result = response.getContractCallLocal();

		return Pair.of(queryTranList, result);
	}


	public byte[] callContractLocalGetResult(AccountID payerAccount, ContractID contractToCall,
			byte[] data, long gas, long localCallGas) throws Exception {
		ContractCallLocalResponse response = callContractLocal(payerAccount, contractToCall, data, gas, localCallGas);
		if (response != null) {
			return response.getFunctionResult().getContractCallResult().toByteArray();
		} else {
			return null;
		}
	}

	public Response executeContractCall(AccountID payerAccount, ContractID contractToCall, ByteString callData,
			long fee,
			long gas, ResponseType responseType)
			throws Exception {
		return Common.querySubmit(() -> {
			try {
				Transaction paymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder
						.getContractCallLocalQuery(contractToCall, gas, callData, 0L, 5000, paymentTx,
								responseType);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::contractCallLocalMethod);
	}

	TransactionID setValueToContract(AccountID payerAccount, ContractID contractId, int valuetoSet,
			final String FunctionStr)
			throws Exception {
		byte[] dataToSet = Common.encodeSetWithFuncStr(valuetoSet, FunctionStr);
		//set value to simple storage smart contract
		TransactionID tID = callContract(payerAccount, contractId, dataToSet, 0, 250000);
		return tID;
	}

	Map<AccountID, Long> verifyBalance(TransactionID txID, Map<AccountID, Long> preBalance, boolean isRecursive) {
		return verifyBalance(Collections.singletonList(txID), preBalance, isRecursive);
	}

	Map<AccountID, Long> verifyBalance(List<TransactionID> txIDList, Map<AccountID, Long> preBalance,
			boolean isRecursive) {
		List<AccountID> accountIDList = new ArrayList<>(preBalance.keySet());
		accountIDList.add(genesisAccount); //include genesisAccount since it paid for get Tran record query

		Map<AccountID, Long> afterBalance = Common.createBalanceMap(stub,
				accountIDList,
				genesisAccount, genesisKeyPair,
				nodeAccount);

		TransactionRecord record = null;
		TransactionID queryTranID = null;
		try {
			TransferList.Builder builder = TransferList.newBuilder();
			List<TransactionID> queryIDList = new ArrayList<>();

			// merge transfer list from all transactions
			for (TransactionID txID : txIDList) {
				Pair<TransactionRecord, TransactionID> result =
						getTransactionRecordAndQueryTransactionID(genesisAccount,
						txID, false);
				if (result != null) {
					record = result.getLeft();
					queryTranID = result.getRight();
					queryIDList.add(queryTranID);
					TransferList transferList = record.getTransferList();
					builder.mergeFrom(transferList);
				} else {
					log.error("Transaction record is null for txID {}", txID);
					fail("getTransactionRecordAndQueryTransactionID Result is null");
				}
			}
			TransferList mergedTransferList = builder.build();
			Common.verifyAccountBalance(preBalance, mergedTransferList, afterBalance);
			try {
				Common.getReceiptByTransactionId(stub,
						queryTranID); //make the payment transaction inside query finished before we move on
				if (isRecursive) {
					//check if balances after get record are also correct
					Map<AccountID, Long> beforeQueryBalance = afterBalance;
					verifyBalance(queryIDList, beforeQueryBalance, false);
				}
			} catch (Exception e) {
				log.error("Exception ", e);
			}
		} catch (Exception e) {
			log.error("Exception ", e);
		}
		return afterBalance; //return latest balance
	}


	public Pair<List<Transaction>, CryptoGetInfoResponse.AccountInfo> getAccountInfo(CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub,
			AccountID accountID,
			AccountID payerAccount) {
		long fee = FeeClient.getCostForGettingAccountInfo();
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getCryptoGetInfoQuery(accountID, queryPaymentTx,
						ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, cryptoStub::getAccountInfo);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());

		long feeForAccountInfo = response.getCryptoGetInfo().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, feeForAccountInfo);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getCryptoGetInfoQuery(accountID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, cryptoStub::getAccountInfo);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());

		CryptoGetInfoResponse.AccountInfo accountInfo = response.getCryptoGetInfo().getAccountInfo();

		return Pair.of(queryTranList, accountInfo);
	}


	public Pair<List<Transaction>, List<TransactionRecord>> getAccountRecords(
			AccountID accountID,
			AccountID payerAccount) {
		long fee = TestHelper.getCryptoMaxFee();
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getAccountRecordsQuery(accountID, queryPaymentTx,
						ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, stub::getAccountRecords);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode());

		long feeForAccountRecords = response.getCryptoGetAccountRecords().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, feeForAccountRecords);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getAccountRecordsQuery(accountID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, stub::getAccountRecords);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getCryptoGetAccountRecords().getHeader().getNodeTransactionPrecheckCode());

		List<TransactionRecord> recordsList = response.getCryptoGetAccountRecords().getRecordsList();

		return Pair.of(queryTranList, recordsList);
	}

	private static Query getAccountStakes(AccountID accountID, Transaction transaction,
			ResponseType responseType) {
		QueryHeader queryHeader = QueryHeader.newBuilder()
				.setResponseType(responseType)
				.setPayment(transaction)
				.build();
		return Query.newBuilder().setCryptoGetProxyStakers(
				CryptoGetStakersQuery
						.newBuilder()
						.setAccountID(accountID)
						.setHeader(queryHeader))
				.build();
	}

	public Pair<List<Transaction>, AllProxyStakers> getAccountStakes(
			CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub,
			AccountID accountID,
			AccountID payerAccount) {
		long fee = TestHelper.getCryptoMaxFee();
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return getAccountStakes(accountID, queryPaymentTx,
						ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, cryptoStub::getStakersByAccountID);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getCryptoGetProxyStakers().getHeader().getNodeTransactionPrecheckCode());

		long actualFee = response.getCryptoGetProxyStakers().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, actualFee);
				queryTranList.add(queryPaymentTx);
				return getAccountStakes(accountID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, cryptoStub::getStakersByAccountID);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getCryptoGetProxyStakers().getHeader().getNodeTransactionPrecheckCode());

		AllProxyStakers stakes = response.getCryptoGetProxyStakers().getStakers();

		return Pair.of(queryTranList, stakes);
	}


	public Pair<List<Transaction>, ContractGetInfoResponse.ContractInfo> getContractInfo(ContractID contractID,
			AccountID payerAccount) {
		long fee = TestHelper.getCryptoMaxFee();
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getContractGetInfoQuery(contractID, queryPaymentTx, ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::getContractInfo);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());

		long actualFee = response.getContractGetInfo().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, actualFee);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getContractGetInfoQuery(contractID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::getContractInfo);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());

		ContractGetInfoResponse.ContractInfo result = response.getContractGetInfo().getContractInfo();

		return Pair.of(queryTranList, result);
	}

	public Pair<List<Transaction>, List<TransactionRecord>> getContractRecords(
			ContractID contractID,
			AccountID payerAccount) {
		long fee = TestHelper.getCryptoMaxFee();
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getContractRecordsQuery(contractID, queryPaymentTx,
						ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::getTxRecordByContractID);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractGetRecordsResponse().getHeader().getNodeTransactionPrecheckCode());

		long actualFee = response.getContractGetRecordsResponse().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, actualFee);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getContractRecordsQuery(contractID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::getTxRecordByContractID);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractGetRecordsResponse().getHeader().getNodeTransactionPrecheckCode());

		List<TransactionRecord> recordsList = response.getContractGetRecordsResponse().getRecordsList();

		return Pair.of(queryTranList, recordsList);
	}


	public Pair<List<Transaction>, GetBySolidityIDResponse> getBySolidityID(String solidityID,
			AccountID payerAccount) {
		long fee = 0;
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getBySolidityIDQuery(solidityID, queryPaymentTx, ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::getBySolidityID);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode());

		long actualFee = response.getGetBySolidityID().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, actualFee);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getBySolidityIDQuery(solidityID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::getBySolidityID);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getGetBySolidityID().getHeader().getNodeTransactionPrecheckCode());

		GetBySolidityIDResponse result = response.getGetBySolidityID();

		return Pair.of(queryTranList, result);
	}


	public Pair<List<Transaction>, ByteString> getContractByteCode(ContractID contractID,
			AccountID payerAccount) {
		long fee = 0;
		Response response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, fee);
				return RequestBuilder.getContractGetBytecodeQuery(contractID, queryPaymentTx, ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::contractGetBytecode);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractGetBytecodeResponse().getHeader().getNodeTransactionPrecheckCode());

		long actualFee = response.getContractGetBytecodeResponse().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		response = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, actualFee);
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getContractGetBytecodeQuery(contractID, queryPaymentTx, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, sCServiceStub::contractGetBytecode);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getContractGetBytecodeResponse().getHeader().getNodeTransactionPrecheckCode());

		ByteString result = response.getContractGetBytecodeResponse().getBytecode();

		return Pair.of(queryTranList, result);
	}
}
