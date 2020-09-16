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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
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
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.exception.InvalidNodeTransactionPrecheckCode;
import com.hedera.services.legacy.file.FileServiceIT;
import com.hedera.services.legacy.regression.Utilities;
import com.hedera.services.legacy.regression.umbrella.FileServiceTest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.codec.DecoderException;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;

/**
 * Tests CryptoAddClam, CryptoGetCLaim and CryptoDeleteLiveHash functionalities
 *
 * @author Achal
 */
public class CryptoLiveHashTest {

	private static final Logger log = LogManager.getLogger(CryptoLiveHashTest.class);

	public static String fileName = TestHelper.getStartUpFile();
	private static CryptoServiceGrpc.CryptoServiceBlockingStub stub;
	List<PrivateKey> waclPrivKeyList;


	public CryptoLiveHashTest(int port, String host) {
		// connecting to the grpc server on the port
		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		CryptoLiveHashTest.stub = CryptoServiceGrpc.newBlockingStub(channel);
	}

	public static void main(String args[])
			throws Exception {

		Properties properties = TestHelper.getApplicationProperties();
		String host = properties.getProperty("host");
		int port = Integer.parseInt(properties.getProperty("port"));
		CryptoLiveHashTest cryptoLiveHashs = new CryptoLiveHashTest(port, host);
		for (int i = 0; i < 10; i++) {
			log.info("run number :: " + i);
			cryptoLiveHashs.demo();
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

//    // create 1st account by payer as genesis
		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		Transaction transaction = TestHelper
				.createAccountWithSigMap(payerAccount, defaultNodeAccount, firstPair, 1000000l,
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

		// creating the request for crypto claims
		byte[] messageDigest = null;

		MessageDigest md = MessageDigest.getInstance("SHA-384");
		messageDigest = md.digest("achal".getBytes());
		log.info(messageDigest + " :: are the claim bytes");

		List<Key> waclPubKeyList = new ArrayList<>();

		waclPrivKeyList = new ArrayList<>();
		genWacl(5, waclPubKeyList, waclPrivKeyList);

		List<PrivateKey> privKeys = Collections.singletonList(firstPair.getPrivate());

		byte[] pubKey = ((EdDSAPublicKey) firstPair.getPublic()).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		log.info("key is" + key);
		KeyList keyList = KeyList.newBuilder().addKeys(key).build();
		log.info(keyList);
		Key key1 = Key.newBuilder().setKeyList(keyList).build();
		log.info(key1);
		KeyList keyList1 = KeyList.newBuilder().addKeys(key1).build();
		log.info(keyList1);
		Timestamp timestamp = TestHelper.getDefaultCurrentTimestampUTC();
		Duration transactionValidDuration = RequestBuilder.getDuration(100);
		AccountID nodeAccountID = AccountID.newBuilder().setAccountNum(3l).build();
		long transactionFee = 100000;
		boolean generateRecord = false;
		String memo = "add claims";
		ByteString claimbyteString = ByteString.copyFrom(messageDigest);
		LiveHash claim = LiveHash.newBuilder().setHash(claimbyteString).setAccountId(newlyCreateAccountId1)
				.setKeys(KeyList.newBuilder().addAllKeys(waclPubKeyList).build()).setDuration(
						Duration.newBuilder().setSeconds(timestamp.getSeconds() + 10000000000l).build())
				.build();
		CryptoAddLiveHashTransactionBody cryptoAddLiveHashTransactionBody = CryptoAddLiveHashTransactionBody.
				newBuilder().setLiveHash(claim).build();

		TransactionID transactionID = TransactionID.newBuilder().setAccountID(newlyCreateAccountId1)
				.setTransactionValidStart(timestamp).build();
		TransactionBody transactionBody = TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setNodeAccountID(nodeAccountID)
				.setTransactionFee(transactionFee)
				.setTransactionValidDuration(transactionValidDuration)
				.setGenerateRecord(generateRecord)
				.setMemo(memo)
				.setCryptoAddLiveHash(cryptoAddLiveHashTransactionBody)
				.build();

		byte[] bodyBytesArr = transactionBody.toByteArray();
		ByteString bodyBytes = ByteString.copyFrom(bodyBytesArr);

		Transaction tx = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
		List<PrivateKey> fromPrivKeyList = new ArrayList<>();
		fromPrivKeyList.add(0, firstPair.getPrivate());
		Transaction signedTransaction = TransactionSigner
				.signTransaction(tx, Collections.singletonList(firstPair.getPrivate()));
		log.info(signedTransaction + ":: is the signed transaction");
		Transaction signedTransactiontKeys = null;
		try {
			signedTransactiontKeys = FileServiceTest.appendSignature(signedTransaction, waclPrivKeyList);
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		log.info(signedTransactiontKeys + ":: is the transaction signed with the payer plus wacl");

		TransactionResponse response1 = stub.addLiveHash(signedTransactiontKeys);
		log.info(response1.getNodeTransactionPrecheckCode());
		Assert.assertEquals(response1.getNodeTransactionPrecheckCode(), ResponseCodeEnum.OK);
		TransactionReceipt txReceipt = null;
		txReceipt = TestHelper.getTxReceipt(transactionID, stub);
		log.info(txReceipt);

		// get claim
		Transaction transferTransaction = TestHelper
				.createTransferSigMap(newlyCreateAccountId1, firstPair, nodeAccountID,
						newlyCreateAccountId1, firstPair, nodeAccountID, 100000);

		QueryHeader queryHeader = QueryHeader.newBuilder().setPayment(transferTransaction)
				.setResponseType(
						ResponseType.ANSWER_ONLY).build();

		CryptoGetLiveHashQuery cryptoGetLiveHashQuery = CryptoGetLiveHashQuery.newBuilder()
				.setAccountID(newlyCreateAccountId1)
				.setHeader(queryHeader).setHash(claimbyteString).build();
		Query query = Query.newBuilder().setCryptoGetLiveHash(cryptoGetLiveHashQuery).build();

		Response cryptoGetLiveHashResponse = stub.getLiveHash(query);
		log.info(
				"Crypto Get LiveHash response is :: " + cryptoGetLiveHashResponse.getCryptoGetLiveHash().getLiveHash()
						.getKeys());
		log.info(
				"Crypto Get LiveHash response is :: " + cryptoGetLiveHashResponse.getCryptoGetLiveHash().getLiveHash()
						.getAccountId());
		log.info(
				"Crypto Get LiveHash response is :: " + cryptoGetLiveHashResponse.getCryptoGetLiveHash().getLiveHash()
						.getHash());
		log.info(
				"Crypto Get LiveHash response is :: " + cryptoGetLiveHashResponse.getCryptoGetLiveHash().getLiveHash()
						.hasKeys());
		log.info(
				"Crypto Get LiveHash response is :: " + cryptoGetLiveHashResponse.getCryptoGetLiveHash().getLiveHash()
						.getDuration());

		// delete claim
		timestamp = TestHelper.getDefaultCurrentTimestampUTC();

		TransactionID transactionID1 = TransactionID.newBuilder().setAccountID(newlyCreateAccountId1)
				.setTransactionValidStart(timestamp).build();

		CryptoDeleteLiveHashTransactionBody cryptoDeleteLiveHashTransactionBody = CryptoDeleteLiveHashTransactionBody
				.newBuilder()
				.setAccountOfLiveHash(newlyCreateAccountId1).setLiveHashToDelete(claimbyteString).build();
		TransactionBody transactionBodyDeleteLiveHash = TransactionBody.newBuilder()
				.setTransactionID(transactionID1)
				.setNodeAccountID(nodeAccountID)
				.setTransactionFee(100000l)
				.setTransactionValidDuration(transactionValidDuration)
				.setGenerateRecord(false)
				.setMemo("delete claim")
				.setCryptoDeleteLiveHash(cryptoDeleteLiveHashTransactionBody)
				.build();

		bodyBytesArr = transactionBodyDeleteLiveHash.toByteArray();
		bodyBytes = ByteString.copyFrom(bodyBytesArr);

		Transaction transaction1 = Transaction.newBuilder().setBodyBytes(bodyBytes).build();
		Transaction signedTransaction1 = TransactionSigner
				.signTransaction(transaction1, Collections.singletonList(firstPair.getPrivate()));

		Transaction signedTransactiontKeys1 = null;
		try {
			signedTransactiontKeys1 = FileServiceIT.appendSignature(signedTransaction1, waclPrivKeyList);
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (SignatureException e) {
			e.printStackTrace();
		} catch (DecoderException e) {
			e.printStackTrace();
		}

		TransactionResponse response2 = stub.deleteLiveHash(signedTransactiontKeys1);

		log.info(response2.getNodeTransactionPrecheckCode());

		txReceipt = TestHelper.getTxReceipt(transactionID1, stub);
		log.info(txReceipt);

////     make sure claim is deleted

		Transaction transferTransaction2 = TestHelper
				.createTransferSigMap(newlyCreateAccountId1, firstPair, nodeAccountID,
						newlyCreateAccountId1, firstPair, nodeAccountID, 100000);

		QueryHeader queryHeader2 = QueryHeader.newBuilder().setPayment(transferTransaction2)
				.setResponseType(
						ResponseType.ANSWER_ONLY).build();

		CryptoGetLiveHashQuery cryptoGetLiveHashQuery2 = CryptoGetLiveHashQuery.newBuilder()
				.setAccountID(newlyCreateAccountId1)
				.setHeader(queryHeader2).setHash(claimbyteString).build();
		Query query2 = Query.newBuilder().setCryptoGetLiveHash(cryptoGetLiveHashQuery2).build();

		Response cryptoGetLiveHashResponse2 = stub.getLiveHash(query2);
		log.info("Crypto Get LiveHash response is :: " + cryptoGetLiveHashResponse2.getCryptoGetLiveHash());

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

	protected static List<PrivateKey> getPayerPrivateKey(long payerSeqNum)
			throws InvalidKeySpecException, DecoderException {
		AccountID accountID = RequestBuilder.getAccountIdBuild(payerSeqNum, 0l, 0l);
		List<PrivateKey> privKey = FileServiceIT.getAccountPrivateKeys(accountID);
		return privKey;
	}

}
