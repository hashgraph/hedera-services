package com.hedera.services.legacy.autorenew;

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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignatureList.Builder;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.file.FileServiceIT;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hedera.services.legacy.regression.Utilities;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.junit.Assert;

public class CryptoFileAutoRenewDurationCheck {

	org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(
			CryptoFileAutoRenewDurationCheck.class);

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	private static FileServiceGrpc.FileServiceBlockingStub fileStub;

	private static final long MAX_TX_FEE = TestHelper.getCryptoMaxFee();
	private static final int MAX_BUSY_RETRIES = 25;
	private static final int BUSY_RETRY_MS = 200;
	protected static long DEFAULT_INITIAL_ACCOUNT_BALANCE = 100000000000L;
	public static long TX_DURATION_SEC = 2 * 60; // 2 minutes for tx dedup
	public static long DAY_SEC = 24 * 365 * 10 * 60 * 60 * 5;
	public static long DAY_SEC_ACTUAL = 24 * 365; // secs in a day
	protected static String[] files = { "1K.txt", "overview-frame.html" };
	protected static String UPLOAD_PATH = "testfiles/";
	public static Map<String, List<AccountKeyListObj>> hederaStartupAccount = null;
	public static String INITIAL_ACCOUNTS_FILE = TestHelper.getStartUpFile();
	protected String DEFAULT_NODE_ACCOUNT_ID_STR = "0.0.3";
	protected static ByteString fileData = ByteString.copyFrom("test".getBytes());
	private static String host;

	public CryptoFileAutoRenewDurationCheck(int port, String host) {
		// connecting to the grpc server on the port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoFileAutoRenewDurationCheck.stub = CryptoServiceGrpc.newBlockingStub(channel);
		CryptoFileAutoRenewDurationCheck.fileStub = FileServiceGrpc.newBlockingStub(channel);
	}

	public CryptoFileAutoRenewDurationCheck() {
	}

	public static void main(String args[])
			throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		CryptoFileAutoRenewDurationCheck autoRenewDurationCheck = new CryptoFileAutoRenewDurationCheck(
				port, host);
		autoRenewDurationCheck.demo();
	}

	public void demo()
			throws Exception {

//
		Map<String, List<AccountKeyListObj>> keyFromFile = TestHelper.getKeyFromFile(fileName);

		List<AccountKeyListObj> genesisAccount = keyFromFile.get("START_ACCOUNT");
		// get Private Key
		KeyPairObj genKeyPairObj = genesisAccount.get(0).getKeyPairList().get(0);
		PrivateKey genesisPrivateKey = genKeyPairObj.getPrivateKey();
		KeyPair genKeyPair = new KeyPair(genKeyPairObj.getPublicKey(), genesisPrivateKey);
		AccountID payerAccount = genesisAccount.get(0).getAccountId();

		AccountID defaultNodeAccount = RequestBuilder
				.getAccountIdBuild(Utilities.getDefaultNodeAccount(), 0l, 0l);

		// create 1st account by payer as genesis
		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper
				.createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 1000000l,
						genKeyPair);
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

		Duration autoRenew = RequestBuilder.getDuration(1000000001);
		Transaction updateaccount1 = TestHelper.updateAccount(newlyCreateAccountId1, payerAccount,
				genesisPrivateKey, defaultNodeAccount, autoRenew);
		List<PrivateKey> privateKeyList = new ArrayList<>();
		privateKeyList.add(genesisPrivateKey);
		privateKeyList.add(firstPair.getPrivate());
		Transaction signUpdate = TransactionSigner.signTransaction(updateaccount1, privateKeyList);
		log.info("updateAccount request=" + signUpdate);
		response = stub.updateAccount(signUpdate);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
				response.getNodeTransactionPrecheckCode());
		log.info("updating unsuccessful" + "\n");
		log.info("--------------------------------------");

		autoRenew = RequestBuilder.getDuration(5);
		updateaccount1 = TestHelper.updateAccount(newlyCreateAccountId1, payerAccount,
				genesisPrivateKey, defaultNodeAccount, autoRenew);
		signUpdate = TransactionSigner.signTransaction(updateaccount1, privateKeyList);
		log.info("updateAccount request=" + signUpdate);
		response = stub.updateAccount(signUpdate);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
				response.getNodeTransactionPrecheckCode());
		log.info("updating unsuccessful" + "\n");
		log.info("--------------------------------------");

		// create 2nd account by payer as genesis
		KeyPair secondPair = new KeyPairGenerator().generateKeyPair();
		transaction = TestHelper
				.createAccountWithFeeAutoRenewCheck(payerAccount, defaultNodeAccount, secondPair, 1000000l,
						Collections.singletonList(genesisPrivateKey));
		response = stub.createAccount(transaction);
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
				response.getNodeTransactionPrecheckCode());
		log.info("Pre Check Response of Create second account :: " + response
				.getNodeTransactionPrecheckCode().name());

		// check for create and update file
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC);
		SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();
		List<Key> waclPubKeyList = new ArrayList<>();
		List<PrivateKey> waclPrivKeyList = new ArrayList<>();
		genWacl(3, waclPubKeyList, waclPrivKeyList);
		Duration transactionDuration = RequestBuilder.getDuration(5);

		// fetching private key of payer account

		long nodeAccountNumber = Utilities.getDefaultNodeAccount();
		Transaction FileCreateRequest = RequestBuilder
				.getFileCreateBuilder(payerAccount.getAccountNum(), 0l, 0l, nodeAccountNumber, 0l, 0l,
						MAX_TX_FEE,
						timestamp, transactionDuration, true, "FileCreate", signatures, fileData, fileExp,
						waclPubKeyList);
		body = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
		Transaction filesignedByPayer = TransactionSigner
				.signTransaction(FileCreateRequest, Collections.singletonList(genesisPrivateKey));

		// append wacl sigs
		Transaction filesigned = FileServiceIT.appendSignature(filesignedByPayer, waclPrivKeyList);
		log.info("\n-----------------------------------");
		log.info("FileCreate: request = " + filesigned);

		response = fileStub.createFile(filesigned);

		Assert.assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
				response.getNodeTransactionPrecheckCode());
		log.info("creating file unsuccessful" + "\n");
		log.info("--------------------------------------");

		// actually create a file now
		timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC_ACTUAL);
		signatures = SignatureList.newBuilder().getDefaultInstanceForType();
		transactionDuration = RequestBuilder.getDuration(5);

		// fetching private key of payer account

		nodeAccountNumber = Utilities.getDefaultNodeAccount();
		FileCreateRequest = RequestBuilder
				.getFileCreateBuilder(payerAccount.getAccountNum(), 0l, 0l, nodeAccountNumber, 0l, 0l,
						MAX_TX_FEE,
						timestamp, transactionDuration, true, "FileCreate", signatures, fileData, fileExp,
						waclPubKeyList);
		body = TransactionBody.parseFrom(FileCreateRequest.getBodyBytes());
		filesignedByPayer = TransactionSigner
				.signTransaction(FileCreateRequest, Collections.singletonList(genesisPrivateKey));

		// append wacl sigs
		filesigned = FileServiceIT.appendSignature(filesignedByPayer, waclPrivKeyList);
		log.info("\n-----------------------------------");
		log.info("FileCreate: request = " + filesigned);

		response = fileStub.createFile(filesigned);

		Assert.assertEquals(ResponseCodeEnum.OK, response.getNodeTransactionPrecheckCode());
		log.info("creating file success" + "\n");
		log.info("--------------------------------------");
		TransactionID txId = body.getTransactionID();

		Query query = Query.newBuilder()
				.setTransactionGetReceipt(
						RequestBuilder.getTransactionGetReceiptQuery(txId, ResponseType.ANSWER_ONLY))
				.build();
		log.info("\n-----------------------------------");
		Response transactionReceipts = TestHelper.fetchReceipts(query, stub, log, host);
		FileID fid = transactionReceipts.getTransactionGetReceipt().getReceipt().getFileID();
		log.info("GetTxReceipt: file ID = " + fid);
		Assert.assertNotNull(fid);
		Assert.assertNotEquals(0, fid.getFileNum());

		// update file
		fileExp = ProtoCommonUtils.getCurrentTimestampUTC(DAY_SEC * 10);
		List<Key> newWaclPubKeyList = new ArrayList<>();
		List<PrivateKey> newWaclPrivKeyList = new ArrayList<>();
		genWacl(3, newWaclPubKeyList, newWaclPrivKeyList);
		KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclPubKeyList).build();

		timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		nodeAccountNumber = Utilities.getDefaultNodeAccount();
		Transaction FileUpdateRequest = RequestBuilder
				.getFileUpdateBuilder(payerAccount.getAccountNum(), 0l, 0l, nodeAccountNumber, 0l, 0l,
						MAX_TX_FEE,
						timestamp, fileExp, transactionDuration, true, "FileUpdate", signatures, fileData, fid,
						wacl);

		Transaction txSignedByPayer = TransactionSigner
				.signTransaction(FileUpdateRequest,
						Collections.singletonList(genesisPrivateKey)); // sign with payer keys
		Transaction txSignedByCreationWacl = appendSignature(txSignedByPayer,
				waclPrivKeyList); // sign with creation wacl keys
		Transaction txSigned = appendSignature(txSignedByCreationWacl,
				newWaclPrivKeyList); // sign with new wacl keys

		log.info("\n-----------------------------------");
		log.info(
				"FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp + "\nWACL keys = "
						+ newWaclPubKeyList);
		log.info("FileUpdate: request = " + txSigned);

		response = fileStub.updateFile(txSigned);
		log.info("FileUpdate with data, exp, and wacl respectively, Response :: "
				+ response.getNodeTransactionPrecheckCode().name());
		Assert.assertNotNull(response);
		Assert.assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
				response.getNodeTransactionPrecheckCode());

	}

	public void genWacl(int numKeys, List<Key> waclPubKeyList, List<PrivateKey> waclPrivKeyList) {
		for (int i = 0; i < numKeys; i++) {
			KeyPair pair = new KeyPairGenerator().generateKeyPair();
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			waclPubKeyList.add(waclKey);
			waclPrivKeyList.add(pair.getPrivate());
		}


	}

	public static Transaction appendSignature(Transaction transaction, List<PrivateKey> privKeys)
			throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException, SignatureException,
			DecoderException {

		byte[] txByteArray = transaction.getBodyBytes().toByteArray();

		List<Signature> currSigs = transaction.getSigs().getSigsList();
		Builder allSigListBuilder = SignatureList.newBuilder();
		Builder waclSigListBuilder = SignatureList.newBuilder();
		allSigListBuilder.addAllSigs(currSigs);
		for (PrivateKey privKey : privKeys) {
			String payerAcctSig = null;
			payerAcctSig = HexUtils
					.bytes2Hex(TransactionSigner.signBytes(txByteArray, privKey).toByteArray());
			Signature signaturePayeeAcct = null;
			signaturePayeeAcct = Signature.newBuilder()
					.setEd25519(ByteString.copyFrom(HexUtils.hexToBytes(payerAcctSig))).build();
			waclSigListBuilder.addSigs(signaturePayeeAcct);
		}

		Signature waclSigs = Signature.newBuilder().setSignatureList(waclSigListBuilder.build())
				.build();
		allSigListBuilder.addSigs(waclSigs);
		Transaction txSigned = Transaction.newBuilder().setBodyBytes(transaction.getBodyBytes())
				.setSigs(allSigListBuilder.build()).build();
		return txSigned;
	}

}
