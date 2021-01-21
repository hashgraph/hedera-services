package com.hedera.services.legacy.unit;

/*-
 * ‌
 * Hedera Services Node
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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.unit.handler.CryptoHandlerTestHelper;
import com.hedera.services.legacy.util.ComplexKeyManager;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.fcmap.FCMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;

public class CryptoTxRecordTransferListTest {
	
	long payerAccount;
	long nodeAccount;
	
	private AccountID nodeAccountId;
	private AccountID payerAccountId;
	private AccountID deleteAccountID;
	private AccountID transferAccountID;
	FCStorageWrapper storageWrapper;
	TransactionHandler transactionHandler = null;
	FCMap<MerkleEntityId, MerkleAccount> fcMap = null;
	private long LARGE_BALANCE = 1000000000000000l;
	AccountID feeAccount;
	List<AccountAmount> accountAmountsList;
	HederaFunctionality hederaFunc;
	Key keys;
	List<Key> keyListp ;

	@Before
	public void setUp() throws Exception {
		payerAccount = 10011;
		nodeAccount = 3l;
		payerAccountId = RequestBuilder.getAccountIdBuild(payerAccount, 0l, 0l);
		nodeAccountId = RequestBuilder.getAccountIdBuild(nodeAccount, 0l, 0l);
		fcMap = new FCMap<>();
		feeAccount = RequestBuilder.getAccountIdBuild(98l, 0l, 0l);
		accountAmountsList = new LinkedList<>();
		hederaFunc = HederaFunctionality.CryptoTransfer;

		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		List<Key> keyList = new ArrayList<>();
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		keyList.add(PrivateKeyToKey(pair.getPrivate()));
		addKeyMap(pair, pubKey2privKeyMap);
		SignatureList sigList = SignatureList.getDefaultInstance();
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		 keyListp = Collections.singletonList(key);
		keys = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(keyListp).build()).build();

	}
	
	    @Test
		public void testTxRecordForCryptoDelete() throws Exception {
			long nodeBalance = 7000000;
			long payerBalance = LARGE_BALANCE;
			long deleteAcctBalance = 100000l;
			long transferAccountBalance = 200000l;
			deleteAccountID = RequestBuilder.getAccountIdBuild(1020l, 0l, 0l);
			transferAccountID = RequestBuilder.getAccountIdBuild(1021l, 0l, 0l);
			createAccount(payerAccountId, payerBalance, keys);
			createAccount(nodeAccountId, nodeBalance, keys);
			createAccount(feeAccount, 1000l, keys);
			createAccount(deleteAccountID, deleteAcctBalance, keys);
			createAccount(transferAccountID, transferAccountBalance, keys);
			Instant consensusTimestamp = Instant.now(Clock.systemUTC()).plusSeconds(20);
			TransactionBody txBody = getDeleteTransactionBody();
			CryptoHandlerTestHelper crHandler = new CryptoHandlerTestHelper(fcMap);
			TransactionRecord trRecord = crHandler.cryptoDelete(txBody, consensusTimestamp);
			for(AccountAmount accountAmount : trRecord.getTransferList().getAccountAmountsList()) {
				if(accountAmount.getAccountID().equals(deleteAccountID)) {
					Assert.assertEquals(-deleteAcctBalance, accountAmount.getAmount());
				}else if(accountAmount.getAccountID().equals(transferAccountID)) {
					Assert.assertEquals(deleteAcctBalance, accountAmount.getAmount());
				}

			}
		}
	    
		@Test
		public void testTransferTxTransferList() throws Exception {
			long nodeBalance = 7000000;
			long payerBalance = 100000000;
			long transferAccountBalance = 200000l;
			createAccount(payerAccountId, payerBalance, keys);
			createAccount(nodeAccountId, nodeBalance, keys);
			createAccount(feeAccount, 1000l, keys);
			transferAccountID = RequestBuilder.getAccountIdBuild(1021l, 0l, 0l);
			createAccount(transferAccountID, transferAccountBalance, keys);
			long transactionFee = 93651;
			Duration transactionDuration = RequestBuilder.getDuration(100);
			Instant startTimeInstant = Instant.now(Clock.systemUTC()).minusSeconds(-10);
			Timestamp startTime = RequestBuilder.getTimestamp(startTimeInstant);
			Instant consensusTimestamp = Instant.now(Clock.systemUTC()).plusSeconds(20);
			TransactionBody txBody = getDummyTransaction(startTime, transactionDuration, transactionFee);
			hederaFunc = HederaFunctionality.CryptoTransfer;
			CryptoHandlerTestHelper crHandler = new CryptoHandlerTestHelper(fcMap);
			TransactionRecord txRecord = crHandler.cryptoTransfer(txBody, consensusTimestamp);
			
			for(AccountAmount accountAmount : txRecord.getTransferList().getAccountAmountsList()) {
				if(accountAmount.getAccountID().equals(payerAccountId)) {
					Assert.assertEquals(-100, accountAmount.getAmount());
				}else if(accountAmount.getAccountID().equals(transferAccountID)) {
					Assert.assertEquals(100, accountAmount.getAmount());
				}
			
			}	
		
		}
	    
	    private TransactionBody getTransferTransactionBody(AccountID senderActID, AccountID receiverAcctID, long amount,
				long txFee, String memo, Duration transactionDuration, Timestamp timestamp) {
			AccountAmount a1 = AccountAmount.newBuilder().setAccountID(senderActID).setAmount(-amount).build();
			AccountAmount a2 = AccountAmount.newBuilder().setAccountID(receiverAcctID).setAmount(amount).build();
			TransferList transferList = TransferList.newBuilder().addAccountAmounts(a1).addAccountAmounts(a2).build();
			CryptoTransferTransactionBody cryptoTransferTransaction = CryptoTransferTransactionBody.newBuilder()
					.setTransfers(transferList).build();
			TransactionBody.Builder txBodyBuilder = RequestBuilder.getTxBodyBuilder(txFee, timestamp, transactionDuration,
					false, memo, senderActID, receiverAcctID);
			txBodyBuilder.setCryptoTransfer(cryptoTransferTransaction);

			return txBodyBuilder.build();
		}

		private TransactionBody getDummyTransaction(Timestamp startTime, Duration transactionDuration, long txFee)
				throws Exception {

			long amount = 100;
			String memo = "Validate Tx Fee";
			TransactionBody transferTxBody = getTransferTransactionBody(payerAccountId, transferAccountID, amount, txFee, memo,
					transactionDuration, startTime);

			return transferTxBody;
		}
	    
	   
	    
	    

	private static Key PrivateKeyToKey(PrivateKey privateKey) {
		byte[] pubKey = ((EdDSAPrivateKey) privateKey).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		return key;
	}

	private static void addKeyMap(KeyPair pair, Map<String, PrivateKey> pubKey2privKeyMap) {
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		String pubKeyHex = MiscUtils.commonsBytesToHex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
	}

	private TransactionBody getDeleteTransactionBody() {


		Instant startTimeInstant = Instant.now(Clock.systemUTC()).minusSeconds(-10);
		Timestamp startTime = RequestBuilder.getTimestamp(startTimeInstant);
		Duration transactionDuration = RequestBuilder.getDuration(100);



		Transaction deleteTx = RequestBuilder.getAccountDeleteRequest(deleteAccountID, transferAccountID,
			       payerAccount,  0l, 0l,  nodeAccount, 0l, 0l, 1000000l,
			       startTime,  transactionDuration, false, "Delete Account Test");
		TransactionBody txBody = null;
		 try {
			txBody =
			            com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody(deleteTx);
		} catch (InvalidProtocolBufferException e) {
		}
		 return txBody;

	}
		

	private void createAccount(AccountID payerAccount, long balance, Key key) throws Exception {
		MerkleEntityId mk = new MerkleEntityId();
		mk.setNum(payerAccount.getAccountNum());
		mk.setRealm(0);
		MerkleAccount mv = new MerkleAccount();
		mv.setBalance(balance);
		JKey jkey = JKey.mapKey(key);
		mv.setKey(jkey);
		fcMap.put(mk, mv);
		ComplexKeyManager.setAccountKey(payerAccount, key);
	}
}
