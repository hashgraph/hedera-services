package com.hedera.services.legacy.file;

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
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.regression.Utilities;
import com.hedera.services.legacy.regression.umbrella.FileServiceTest;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.DecoderException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * System delete and undelete for file and smart contract?
 *
 * @author akshay Date 2019-03-25 14:14
 */
public class SystemDeleteIT {
	private static long systemDeleteAccounts = 59;
	private static long systemUndeleteAccounts = 60;

	private static final Logger log = LogManager.getLogger(SystemDeleteIT.class);
	private Map<AccountID, List<PrivateKey>> accountKeys = new HashMap<AccountID, List<PrivateKey>>();

	public static String fileName = TestHelper.getStartUpFile();
	private static FileServiceGrpc.FileServiceBlockingStub fileServiceBlockingStub;
	private static SmartContractServiceGrpc.SmartContractServiceBlockingStub smartContractServiceBlockingStub;
	private static CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;

	public SystemDeleteIT(int port, String host) {
		// connecting to the grpc server on the port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		SystemDeleteIT.fileServiceBlockingStub = FileServiceGrpc.newBlockingStub(channel);
		SystemDeleteIT.cryptoStub = CryptoServiceGrpc.newBlockingStub(channel);
		SystemDeleteIT.smartContractServiceBlockingStub = SmartContractServiceGrpc
				.newBlockingStub(channel);
	}

	public SystemDeleteIT() {
	}

	public static void main(String args[])
			throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		SystemDeleteIT systemDeleteIT = new SystemDeleteIT(port, host);
		systemDeleteIT.demo();
	}

	private AccountKeyListObj getGenKeyPairObj() throws URISyntaxException, IOException {

		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

		return keyFromFile.get("START_ACCOUNT").get(0);
	}

	private void demo() throws Exception {
		AccountKeyListObj accountKeyListObj = getGenKeyPairObj();
		KeyPairObj genKeyPairObj = accountKeyListObj.getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		AccountID genesisAccount = accountKeyListObj.getAccountId();
		AccountID payerAccount4Delete = RequestBuilder.getAccountIdBuild(systemDeleteAccounts, 0L, 0L);
		AccountID payerAccount4Undelete = RequestBuilder
				.getAccountIdBuild(systemUndeleteAccounts, 0L, 0L);
		accountKeys.put(genesisAccount, Collections.singletonList(genesisPrivateKey));
		long defaultNodeAccountSeq = Utilities.getDefaultNodeAccount();
		AccountID defaultNodeAccount = RequestBuilder
				.getAccountIdBuild(defaultNodeAccountSeq, 0L, 0L);

		// step 1 : transfer money into account 59 and 60
		for (int i = 0; i < 2; i++) {
			AccountID payer = payerAccount4Delete;
			if (i == 1) {
				payer = payerAccount4Undelete;
			}


			Transaction transfer1 = TestHelper.createTransferSigMap(genesisAccount, genKeyPair,
					payer, genesisAccount, genKeyPair, defaultNodeAccount, 1000000000000000000l);

			log.info(
					"Transferring 1000 coin from genesis account to system account num " + payer.getAccountNum() + "..." +
							".");
			TransactionResponse transferRes = cryptoStub.cryptoTransfer(transfer1);
			Assert.assertNotNull(transferRes);
			Assert.assertEquals(ResponseCodeEnum.OK, transferRes.getNodeTransactionPrecheckCode());
			log.info(
					"Pre Check Response transfer :: " + transferRes.getNodeTransactionPrecheckCode().name());
			TransactionBody transferBody = TransactionBody.parseFrom(transfer1.getBodyBytes());
			TransactionReceipt txReceipt =
					TestHelper.getTxReceipt(transferBody.getTransactionID(), cryptoStub);
			Assert.assertNotNull(txReceipt);
			log.info("-----------------------------------------");
		}

		// create a random file
		String fileName = "PayTest.bin";
		FileID simpleStorageFileId = LargeFileUploadIT
				.uploadFile(genesisAccount, fileName, Collections.singletonList(genesisPrivateKey));
		System.out.println(simpleStorageFileId + "is the file ID");

		// build system Delete Transaction body

		SystemDeleteTransactionBody systemDeleteTransactionBody = SystemDeleteTransactionBody
				.newBuilder()
				.setFileID(simpleStorageFileId)
				.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(10000000000l)).build();
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionValidDuration = RequestBuilder.getDuration(100);
		AccountID nodeAccountID = AccountID.newBuilder().setAccountNum(3l).build();
		long transactionFee = TestHelper.getContractMaxFee();
		boolean generateRecord = false;
		String memo = "system delete";

		TransactionID transactionID = TransactionID.newBuilder()
				.setAccountID(AccountID.newBuilder().setAccountNum(systemDeleteAccounts).build())
				.setTransactionValidStart(timestamp).build();
		TransactionBody transactionBody = TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setNodeAccountID(nodeAccountID)
				.setTransactionFee(transactionFee)
				.setTransactionValidDuration(transactionValidDuration)
				.setGenerateRecord(generateRecord)
				.setMemo(memo)
				.setSystemDelete(systemDeleteTransactionBody)
				.build();

		byte[] bodyBytesArr = transactionBody.toByteArray();
		ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);

		Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();

		Transaction signedTransaction = TransactionSigner
				.signTransaction(tx, Collections.singletonList(genesisPrivateKey));
		// append the special account signature
		Transaction signedTransactiontKeys = null;
		try {
			signedTransactiontKeys = FileServiceTest
					.appendSignature(signedTransaction, Collections.singletonList(genesisPrivateKey));
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		log.info(signedTransactiontKeys
				+ ":: is the system delete transaction signed with the payer plus wacl");

		TransactionResponse response1 = fileServiceBlockingStub.systemDelete(signedTransactiontKeys);
		log.info(response1.getNodeTransactionPrecheckCode());
		Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
		TransactionReceipt txReceipt = null;
		txReceipt = TestHelper.getTxReceipt(transactionID, cryptoStub);
		log.info(txReceipt);

		// system unDelete File

		SystemUndeleteTransactionBody systemUndeleteTransactionBody = SystemUndeleteTransactionBody
				.newBuilder()
				.setFileID(simpleStorageFileId).build();

		Timestamp timestamp1 = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionValidDuration1 = RequestBuilder.getDuration(100);
		AccountID nodeAccountID1 = AccountID.newBuilder().setAccountNum(3l).build();
		long transactionFee1 = TestHelper.getCryptoMaxFee();
		boolean generateRecord1 = false;
		String memo1 = "system unDelete";

		TransactionID transactionID1 = TransactionID.newBuilder()
				.setAccountID(AccountID.newBuilder().setAccountNum(systemUndeleteAccounts).build())
				.setTransactionValidStart(timestamp1).build();
		TransactionBody transactionBody1 = TransactionBody.newBuilder()
				.setTransactionID(transactionID1)
				.setNodeAccountID(nodeAccountID1)
				.setTransactionFee(transactionFee1)
				.setTransactionValidDuration(transactionValidDuration1)
				.setGenerateRecord(generateRecord1)
				.setMemo(memo1)
				.setSystemUndelete(systemUndeleteTransactionBody)
				.build();

		byte[] bodyBytesArr1 = transactionBody1.toByteArray();
		ByteString bodyBytes1 = ByteString.copyFrom(bodyBytesArr1);

		Transaction tx1 = Transaction.newBuilder().setBodyBytes(bodyBytes1).build();

		Transaction signedTransaction1 = TransactionSigner
				.signTransaction(tx1, Collections.singletonList(genesisPrivateKey));
		// append the special account signature
		Transaction signedTransactiontKeys1 = null;
		try {
			signedTransactiontKeys1 = FileServiceTest
					.appendSignature(signedTransaction1, Collections.singletonList(genesisPrivateKey));
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		log.info(signedTransactiontKeys1
				+ ":: is the system delete transaction signed with the payer plus wacl");

		TransactionResponse response2 = fileServiceBlockingStub.systemUndelete(signedTransactiontKeys1);
		log.info(response2.getNodeTransactionPrecheckCode());
		Assert.assertEquals(response2.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
		TransactionReceipt txReceipt1 = null;
		txReceipt1 = TestHelper.getTxReceipt(transactionID1, cryptoStub);
		log.info(txReceipt1);

		// system delete smart contract
		ContractID payTestContractId = null;

		if (simpleStorageFileId != null) {
			log.info("Smart Contract file uploaded successfully");
			payTestContractId = createContract(genesisAccount, simpleStorageFileId, 24 * 60 * 60 * 30);
			Assert.assertNotNull(payTestContractId);
			System.out.println(payTestContractId + " :: is the contract ID");
		}

		SystemDeleteTransactionBody systemDeleteTransactionBody10 = SystemDeleteTransactionBody
				.newBuilder()
				.setContractID(payTestContractId)
				.setExpirationTime(TimestampSeconds.newBuilder().setSeconds(10000000000l)).build();

		Timestamp timestamp10 = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionValidDuration10 = RequestBuilder.getDuration(100);
		AccountID nodeAccountID10 = AccountID.newBuilder().setAccountNum(3l).build();
		long transactionFee10 = TestHelper.getCryptoMaxFee();
		boolean generateRecord10 = false;
		String memo10 = "system delete smart Contract";

		TransactionID transactionID10 = TransactionID.newBuilder()
				.setAccountID(AccountID.newBuilder().setAccountNum(systemDeleteAccounts).build())
				.setTransactionValidStart(timestamp10).build();
		TransactionBody transactionBody10 = TransactionBody.newBuilder()
				.setTransactionID(transactionID10)
				.setNodeAccountID(nodeAccountID10)
				.setTransactionFee(transactionFee10)
				.setTransactionValidDuration(transactionValidDuration10)
				.setGenerateRecord(generateRecord10)
				.setMemo(memo10)
				.setSystemDelete(systemDeleteTransactionBody10)
				.build();

		byte[] bodyBytesArr10 = transactionBody10.toByteArray();
		ByteString bodyBytes10 = ByteString.copyFrom(bodyBytesArr10);

		Transaction tx10 = Transaction.newBuilder().setBodyBytes(bodyBytes10).build();

		Transaction signedTransaction10 = TransactionSigner
				.signTransaction(tx10, Collections.singletonList(genesisPrivateKey));
		// append the special account signature
		Transaction signedTransactiontKeys10 = null;
		try {
			signedTransactiontKeys10 = FileServiceTest
					.appendSignature(signedTransaction10, Collections.singletonList(genesisPrivateKey));
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		log.info(signedTransactiontKeys10
				+ ":: is the system delete transaction signed with the payer plus wacl smart contract");

		TransactionResponse response10 = smartContractServiceBlockingStub
				.systemDelete(signedTransactiontKeys10);
		log.info(response10.getNodeTransactionPrecheckCode());
		Assert.assertEquals(response10.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
		TransactionReceipt txReceipt10 = null;
		txReceipt10 = TestHelper.getTxReceipt(transactionID10, cryptoStub);
		log.info(txReceipt10);

		// system unDelete File

		SystemUndeleteTransactionBody systemUndeleteTransactionBody20 = SystemUndeleteTransactionBody
				.newBuilder()
				.setContractID(payTestContractId).build();

		Timestamp timestamp20 = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionValidDuration20 = RequestBuilder.getDuration(100);
		AccountID nodeAccountID20 = AccountID.newBuilder().setAccountNum(3l).build();
		long transactionFee20 = TestHelper.getCryptoMaxFee();
		boolean generateRecord20 = false;
		String memo20 = "system unDelete SMart Contract";

		TransactionID transactionID20 = TransactionID.newBuilder()
				.setAccountID(AccountID.newBuilder().setAccountNum(systemUndeleteAccounts).build())
				.setTransactionValidStart(timestamp20).build();
		TransactionBody transactionBody20 = TransactionBody.newBuilder()
				.setTransactionID(transactionID20)
				.setNodeAccountID(nodeAccountID20)
				.setTransactionFee(transactionFee20)
				.setTransactionValidDuration(transactionValidDuration20)
				.setGenerateRecord(generateRecord20)
				.setMemo(memo20)
				.setSystemUndelete(systemUndeleteTransactionBody20)
				.build();

		byte[] bodyBytesArr20 = transactionBody20.toByteArray();
		ByteString bodyBytes20 = ByteString.copyFrom(bodyBytesArr20);

		Transaction tx20 = Transaction.newBuilder().setBodyBytes(bodyBytes20).build();

		Transaction signedTransaction20 = TransactionSigner
				.signTransaction(tx20, Collections.singletonList(genesisPrivateKey));
		// append the special account signature
		Transaction signedTransactiontKeys20 = null;
		try {
			signedTransactiontKeys20 = FileServiceTest
					.appendSignature(signedTransaction20, Collections.singletonList(genesisPrivateKey));
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		log.info(signedTransactiontKeys20
				+ ":: is the system un delete transaction signed with the payer plus wacl");

		TransactionResponse response20 = smartContractServiceBlockingStub
				.systemUndelete(signedTransactiontKeys20);
		log.info(response2.getNodeTransactionPrecheckCode());
		Assert.assertEquals(response2.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
		TransactionReceipt txReceipt20 = null;
		txReceipt20 = TestHelper.getTxReceipt(transactionID20, cryptoStub);
		log.info(txReceipt20);


	}

	private ContractID createContract(AccountID payerAccount, FileID contractFile,
			long durationInSeconds) throws Exception {
		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		AccountID nodeAccount = AccountID.newBuilder().setAccountNum(3l).build();
		ContractID createdContract = null;
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();

		Duration contractAutoRenew = Duration.newBuilder().setSeconds(durationInSeconds).build();
		SmartContractServiceGrpc.SmartContractServiceBlockingStub stub = SmartContractServiceGrpc
				.newBlockingStub(channel);

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).minusSeconds(13));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		Transaction createContractRequest = TestHelper
				.getCreateContractRequest(payerAccount.getAccountNum(), payerAccount.getRealmNum(),
						payerAccount.getShardNum(), nodeAccount.getAccountNum(), nodeAccount.getRealmNum(),
						nodeAccount.getShardNum(), 100000l, timestamp,
						transactionDuration, true, "", 250000, contractFile, ByteString.EMPTY, 0,
						contractAutoRenew, accountKeys.get(payerAccount), "");

		TransactionResponse response = stub.createContract(createContractRequest);
		System.out.println(
				" createContract Pre Check Response :: " + response.getNodeTransactionPrecheckCode()
						.name());

		TransactionBody createContractBody = TransactionBody
				.parseFrom(createContractRequest.getBodyBytes());
		TransactionGetReceiptResponse contractCreateReceipt = getReceipt(
				createContractBody.getTransactionID());
		if (contractCreateReceipt != null) {
			createdContract = contractCreateReceipt.getReceipt().getContractID();
		}
		channel.shutdown();

		return createdContract;
	}

	private TransactionGetReceiptResponse getReceipt(TransactionID transactionId) throws Exception {
		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		TransactionGetReceiptResponse receiptToReturn = null;
		Query query = Query.newBuilder()
				.setTransactionGetReceipt(RequestBuilder.getTransactionGetReceiptQuery(
						transactionId, ResponseType.ANSWER_ONLY)).build();
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoServiceGrpc.CryptoServiceBlockingStub stub = CryptoServiceGrpc.newBlockingStub(channel);
		Response transactionReceipts = stub.getTransactionReceipts(query);
		int attempts = 1;
		while (attempts <= 180 && !transactionReceipts.getTransactionGetReceipt()
				.getReceipt()
				.getStatus().name().equalsIgnoreCase(ResponseCodeEnum.SUCCESS.name())) {
			Thread.sleep(1000);
			transactionReceipts = stub.getTransactionReceipts(query);
			System.out.println("waiting to getTransactionReceipts as Success..." +
					transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus().name());
			attempts++;
		}
		if (transactionReceipts.getTransactionGetReceipt().getReceipt().getStatus()
				.equals(ResponseCodeEnum.SUCCESS)) {
			receiptToReturn = transactionReceipts.getTransactionGetReceipt();
		}
		channel.shutdown();
		return transactionReceipts.getTransactionGetReceipt();

	}


}
