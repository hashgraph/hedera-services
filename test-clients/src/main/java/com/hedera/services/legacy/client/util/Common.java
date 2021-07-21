package com.hedera.services.legacy.client.util;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.client.core.BuildTransaction;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.swirlds.common.CommonUtils;
import io.grpc.StatusRuntimeException;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class Common {
	private final static Logger log = LogManager.getLogger(Common.class);

	/**
	 * A utility function used to submit transaction to different stub whether response handling and retry.
	 * If response is BUSY or PLATFORM_TRANSACTION_NOT_CREATED then try build transaction again and resubmit,
	 * otherwise assert as unexpected error (insufficient fee, invalid signature, etc)
	 *
	 * @param builder
	 * 		the function call to create transaction to be submitted
	 * @param stubFunc
	 * 		the stub function entry to submit the request
	 * @return return the successfully submitted transactions.
	 */
	public static Transaction tranSubmit(BuildTransaction builder, Function<Transaction, TransactionResponse> stubFunc)
			throws StatusRuntimeException {
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
			} catch (InterruptedException e) {
				log.error("Exception ", e);
				return null;
			} catch (io.grpc.StatusRuntimeException e) {
				throw e;
			}
		}
		return transaction;
	}

	public static void addKeyMap(KeyPair pair, Map<String, PrivateKey> pubKey2privKeyMap) {
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		String pubKeyHex = CommonUtils.hex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
	}

	public static Key PrivateKeyToKey(PrivateKey privateKey) {
		byte[] pubKey = ((EdDSAPrivateKey) privateKey).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		return key;
	}

	public static Transaction buildCryptoDelete(AccountID payer, Key payerKey,
			AccountID deleteAccount, Key accKey,
			AccountID transferAccount,
			AccountID nodeAccount, Map<String, PrivateKey> pubKey2privKeyMap) {
		Duration transactionValidDuration = RequestBuilder.getDuration(100);
		CryptoDeleteTransactionBody cryptoDeleteTransactionBody = CryptoDeleteTransactionBody
				.newBuilder().setDeleteAccountID(deleteAccount).setTransferAccountID(transferAccount)
				.build();
		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		TransactionID transactionID = TransactionID.newBuilder().setAccountID(payer)
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

	public static long getAccountBalance(CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			AccountID accountID,
			AccountID payerAccount, KeyPair payerKeyPair, AccountID nodeAccount) throws Exception {
		Response accountInfoResponse = TestHelper.getCryptoGetBalance(stub, accountID, payerAccount,
				payerKeyPair, nodeAccount);

		Assert.assertNotNull(accountInfoResponse.getCryptogetAccountBalance());
		return accountInfoResponse.getCryptogetAccountBalance().getBalance();
	}
}
