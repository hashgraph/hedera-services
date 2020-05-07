package com.hedera.services.legacy.client.core;

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
import com.hedera.services.legacy.client.util.Common;
import com.hedera.services.legacy.client.util.FileUploader;
import com.hedera.services.legacy.core.TestHelper;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hederahashgraph.service.proto.java.SmartContractServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.lang3.tuple.Pair;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GrpcStub {
	String host;
	int port;

	SmartContractServiceGrpc.SmartContractServiceBlockingStub scStub;
	CryptoServiceGrpc.CryptoServiceBlockingStub cryptoStub;
	FileServiceGrpc.FileServiceBlockingStub fileStub;
	ManagedChannel channel;

	public GrpcStub(String host, int port) {
		this.host = host;
		this.port = port;

		channel = ManagedChannelBuilder.forAddress(host, port)
				.usePlaintext(true)
				.build();
		cryptoStub = CryptoServiceGrpc.newBlockingStub(channel);
		scStub = SmartContractServiceGrpc.newBlockingStub(channel);
		fileStub = FileServiceGrpc.newBlockingStub(channel);
	}

	public Pair<List<Transaction>, FileGetInfoResponse.FileInfo> getFileInfo(AccountID payerAccount,
			PrivateKey payerKey,
			long nodeAccountNumber,
			FileID fid)
	{
		return FileUploader.getFileInfo(fileStub, payerAccount, payerKey, nodeAccountNumber, fid);
	}

	public Pair<List<Transaction>, FileID> uploadFile(AccountID payerAccount,
			PrivateKey payerPrivateKey,
			final List<KeyPair> accessKeys,
			long fileDuration, long transactionFee,
			final Map<String, PrivateKey> pubKey2PrivateKeyMap,
			byte[] bytes,
			long nodeAccountNumber) throws Exception {

		return FileUploader.uploadFile(null, cryptoStub, fileStub,
				payerAccount, payerPrivateKey, accessKeys, fileDuration, transactionFee,
		pubKey2PrivateKeyMap,
		bytes,
		nodeAccountNumber);
	}

	public Pair<List<Transaction>, FileID> updateFile(FileID fileID,
			AccountID payerAccount,
			PrivateKey payerPrivateKey,
			final List<KeyPair> accessKeys,
			long fileDuration, long transactionFee,
			final Map<String, PrivateKey> pubKey2PrivateKeyMap,
			byte[] bytes,
			long nodeAccountNumber) throws Exception {

		return FileUploader.uploadFile(fileID, cryptoStub, fileStub,
				payerAccount, payerPrivateKey, accessKeys, fileDuration, transactionFee,
				pubKey2PrivateKeyMap,
				bytes,
				nodeAccountNumber);
	}

	public TransactionID deleteFile(AccountID payerAccount,
			Key payerKey,
			final List<KeyPair> accessKeys,
			AccountID nodeID,
			FileID fileID,
			Map<String, PrivateKey> pubKey2privKeyMap){

		SignatureList signatures = SignatureList.newBuilder()
				.getDefaultInstanceForType();
		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Duration transactionDuration = Duration.newBuilder().setSeconds(2 * 60).build();
		List<Key> waclPubKeyList = new ArrayList<Key>();
		for (KeyPair pair :accessKeys) {
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			waclPubKeyList.add(waclKey);
		}
		Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclPubKeyList)).build();
		List<Key> keys = new ArrayList<>();
		keys.add(payerKey);
		keys.add(waclKey);

		try {

			Transaction transaction = Common.tranSubmit(() -> {
				try {
					Transaction FileDeleteRequest = RequestBuilder.getFileDeleteBuilder(
							payerAccount.getAccountNum(), 0L, 0L,
							nodeID.getAccountNum(), 0L, 0L, TestHelper.getFileMaxFee(),
							timestamp, transactionDuration, true, "FileDelete", signatures, fileID);

					Transaction txSigned = TransactionSigner.signTransactionComplex(FileDeleteRequest, keys, pubKey2privKeyMap);

					return txSigned;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}, fileStub::createFile);
			return TransactionBody.parseFrom(transaction.getBodyBytes()).getTransactionID();
		} catch (Exception e) {
			return null;
		}

	}

	public TransactionID deleteAccount(AccountID payer, Key payerKey,
			AccountID deleteAccount, Key accKey,
			AccountID transferAccount,
			AccountID nodeAccount, Map<String, PrivateKey> pubKey2privKeyMap) throws InvalidProtocolBufferException  {
		Transaction transaction = Common.tranSubmit(() -> {
			Transaction deleteRequest;
			try {
				deleteRequest = Common.buildCryptoDelete(payer, payerKey,
						deleteAccount, accKey,
						transferAccount,
						nodeAccount, pubKey2privKeyMap);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			return deleteRequest;
		}, cryptoStub::cryptoDelete);
		if (transaction != null) {
			return TransactionBody.parseFrom(transaction.getBodyBytes())
					.getTransactionID();
		} else {
			return null;
		}
	}
}
