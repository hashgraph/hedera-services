package com.hedera.services.legacy.unit.serialization;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.handler.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.TestHelper;
import com.hedera.services.legacy.proto.utils.CommonUtils;

import java.security.KeyPair;

import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.Before;
import org.junit.Test;

public class DepthCheckValidationTest {

	AccountID payerAccountId;
	AccountID nodeAccountId;

	@Before
	public void setUp() {
		payerAccountId =
				RequestBuilder.getAccountIdBuild(2l, 0l, 0l);
		nodeAccountId =
				RequestBuilder.getAccountIdBuild(3l, 0l, 0l);
	}

	private Transaction createTransaction() {
		KeyPair firstPair = new KeyPairGenerator().generateKeyPair();
		return TestHelper
				.createAccount(payerAccountId, nodeAccountId, firstPair, 1000l);
	}
/*
	private Transaction genSignedTx() throws Exception {
		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKeyBytes = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key key =  Key.newBuilder().setEd25519(ByteString.copyFrom(pubKeyBytes)).build();
		Transaction tx = TestHelper
				.createAccount(payerAccountId, nodeAccountId, pair, 1000l);
		return FileServiceIT.getSignedTransaction(tx, pair.getPrivate());
	}
*/
	@Test
	public void getDepthTest_singleKey() {
		Key key = TestHelper.genKey();
		assert TransactionHandler.getDepth(key) == 0;
	}

	@Test
	public void getDepthTest_thresholdKey() {
		Key key = TestHelper.genThresholdKey(3, 2);
		assert TransactionHandler.getDepth(key) == 3;
	}

	@Test
	public void getDepthTest_multiLayerThresholdKey() {
		Key basicThresholdKey = TestHelper.genThresholdKey(3, 2);
		Key key = TestHelper.genMultiLayerThresholdKey(10, basicThresholdKey);
		assert TransactionHandler.getDepth(key) == 30;
	}

	private Key getKey_Depth47() {
		Key basicThresholdKey = TestHelper.genThresholdKey(3, 2);
		return Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(
				TestHelper.genMultiLayerThresholdKey(15, basicThresholdKey))).build();
	}

	private Key getKey_Depth48() {
		Key basicThresholdKey = TestHelper.genThresholdKey(3, 2);
		return TestHelper.genMultiLayerThresholdKey(16, basicThresholdKey);
	}

	@Test
	public void testValidateTxBodyDepthPositive() throws Exception {
		Transaction origTransaction = createTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		CryptoCreateTransactionBody cryptoCreateTransactionBody = trBody.getCryptoCreateAccount();
		Key key_47 = getKey_Depth47();
		assert TransactionHandler.getDepth(key_47) == 47;
		cryptoCreateTransactionBody = cryptoCreateTransactionBody.toBuilder().setKey(key_47).build();
		assert TransactionHandler.getDepth(cryptoCreateTransactionBody) == 48;
		trBody = trBody.toBuilder().setCryptoCreateAccount(cryptoCreateTransactionBody).build();
		assert TransactionHandler.getDepth(trBody) == 49;
		assert TransactionHandler.validateTxBodyDepth(trBody);
	}

	@Test
	public void testValidateTxBodyDepthNegative() throws Exception {
		Transaction origTransaction = createTransaction();
		TransactionBody trBody = CommonUtils.extractTransactionBody(origTransaction);
		CryptoCreateTransactionBody cryptoCreateTransactionBody = trBody.getCryptoCreateAccount();
		Key key_48 = getKey_Depth48();
		assert TransactionHandler.getDepth(key_48) == 48;
		cryptoCreateTransactionBody = cryptoCreateTransactionBody.toBuilder().setKey(key_48).build();
		assert TransactionHandler.getDepth(cryptoCreateTransactionBody) == 49;
		trBody = trBody.toBuilder().setCryptoCreateAccount(cryptoCreateTransactionBody).build();
		assert TransactionHandler.getDepth(trBody) == 50;
		assert !TransactionHandler.validateTxBodyDepth(trBody);
	}

	private Signature getSig_Depth48(Signature base) {
		return TestHelper.genMultiLayerThresholdSig(16, base);
	}

	private Signature getSig_Depth47(Signature base) {
		return Signature.newBuilder().setSignatureList(SignatureList.newBuilder().addSigs(
				TestHelper.genMultiLayerThresholdSig(15, base))).build();
	}

	private Signature getSig_Depth49(Signature base) {
		return Signature.newBuilder().setSignatureList(SignatureList.newBuilder().addSigs(
				getSig_Depth47(base))).build();
	}
/*   Need to revisit these test cases, most probably they will be removed soon.
	@Test
	public void testValidateTxDepthPositive() throws Exception {
		Transaction signedTx = genSignedTx();
		Signature sig = signedTx.getSigs().getSigs(0);
		assert TransactionValidationUtils.getDepth(sig) == 0;
		Signature thresholdSig = TestHelper.genThresholdSig(2, sig);
		assert TransactionValidationUtils.getDepth(thresholdSig) == 3;
		Signature sig_48 = getSig_Depth48(thresholdSig);
		assert TransactionValidationUtils.getDepth(sig_48) == 48;
		signedTx = signedTx.toBuilder().setSigs(
				SignatureList.newBuilder().addSigs(sig_48).build()).build();
		assert TransactionValidationUtils.getDepth(signedTx) == 50;
		assert TransactionValidationUtils.validateTxDepth(signedTx);
	}

	@Test
	public void testValidateTxDepthNegative() throws Exception {
		Transaction signedTx = genSignedTx();
		Signature sig = signedTx.getSigs().getSigs(0);
		assert TransactionValidationUtils.getDepth(sig) == 0;
		Signature thresholdSig = TestHelper.genThresholdSig(2, sig);
		assert TransactionValidationUtils.getDepth(thresholdSig) == 3;
		Signature sig_49 = getSig_Depth49(thresholdSig);
		assert TransactionValidationUtils.getDepth(sig_49) == 49;
		signedTx = signedTx.toBuilder().setSigs(
				SignatureList.newBuilder().addSigs(sig_49).build()).build();
		assert TransactionValidationUtils.getDepth(signedTx) == 51;
		assert TransactionValidationUtils.validateTxDepth(signedTx) == false;
	}

 */
}
