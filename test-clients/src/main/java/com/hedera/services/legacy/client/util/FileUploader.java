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
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hederahashgraph.service.proto.java.CryptoServiceGrpc;
import com.hederahashgraph.service.proto.java.FileServiceGrpc;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.FeeClient;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.Thread.sleep;
import static org.junit.Assert.fail;

public class FileUploader {
	protected static int FILE_PART_SIZE = 4096; //4K bytes

	private static final Logger log = LogManager.getLogger(FileUploader.class);

	/**
	 * upload a new file or update an existing file
	 */
	public static Pair<List<Transaction>, FileID> uploadFile(
			FileID fileID,
			CryptoServiceGrpc.CryptoServiceBlockingStub stub,
			FileServiceGrpc.FileServiceBlockingStub fileStub,

			AccountID payerAccount,
			PrivateKey payerPrivateKey,
			final List<KeyPair> accessKeys,
			long fileDuration, long transactionFee,
			final Map<String, PrivateKey> pubKey2PrivateKeyMap,
			byte[] bytes,
			long nodeAccountNumber) throws Exception {

		int remainder = 0;

		byte[] firstPartBytes = null;
		if (bytes.length <= FILE_PART_SIZE) {
			firstPartBytes = bytes;
			remainder = 0;
		} else {
			firstPartBytes = CommonUtils.copyBytes(0, FILE_PART_SIZE, bytes);
			remainder = bytes.length - FILE_PART_SIZE;
		}

		List<Transaction> resultTxList = new ArrayList<>();
		Transaction transaction = null;
		if (fileID == null) { //create new one
			//create file with first part
			transaction = createUpdateFile(fileStub,
					payerAccount, payerPrivateKey, accessKeys,
					nodeAccountNumber, firstPartBytes, fileDuration, transactionFee, pubKey2PrivateKeyMap, null);
			log.info("@@@ create file with first part.");


			fileID = Common.getFileIDfromReceipt(stub,
					TransactionBody.parseFrom(transaction.getBodyBytes()).getTransactionID());
		} else {
			transaction = createUpdateFile(fileStub,
					payerAccount, payerPrivateKey, accessKeys,
					nodeAccountNumber, firstPartBytes, fileDuration, transactionFee, pubKey2PrivateKeyMap, fileID);
		}

		resultTxList.add(transaction);

		//append the rest of the parts
		int i = 1;
		while (remainder > 0) {
			byte[] partBytes = remainder <= FILE_PART_SIZE ? CommonUtils.copyBytes(i * FILE_PART_SIZE, remainder, bytes)
					: CommonUtils.copyBytes(i * FILE_PART_SIZE, FILE_PART_SIZE, bytes);
			transaction = appendFile(fileStub, payerAccount, payerPrivateKey, accessKeys,
					fileID, partBytes, nodeAccountNumber, transactionFee, pubKey2PrivateKeyMap);
			log.info("@@@ append file count = " + i);
			resultTxList.add(transaction);

			i++;
			remainder = remainder - FILE_PART_SIZE;
		}

		//wait transaction reach consensus then read back content
		Common.getReceiptByTransactionId(stub,
				TransactionBody.parseFrom(transaction.getBodyBytes()).getTransactionID());

		AccountID nodeAccount = RequestBuilder.getAccountIdBuild(nodeAccountNumber, 0l, 0l);
		// get file content and check again original
		for (i = 0; i < 10; i++) {
			Pair<List<Transaction>, byte[]> result = getFileContent(fileStub, payerAccount, payerPrivateKey, fileID,
					nodeAccount);
			log.info("getFileContent return transaction list size {}", result.getLeft().size());
			resultTxList.addAll(result.getLeft());
			byte[] content = result.getRight();
			if (Arrays.equals(bytes, content)) {
				break;
			} else {
				if (content.length < bytes.length) {
					log.info("Try again to get file content");
					sleep(100); //Wait a while try again
				} else {
					fail(); //report error if file not equal
				}
			}
		}
		return Pair.of(resultTxList, fileID);
	}

	private static Transaction createQueryHeaderTransfer(AccountID payer, PrivateKey payerKey, AccountID nodeAccount,
			long transferAmt, String memo)
			throws Exception {

		Transaction transferTx = Common.createTransfer(payer, payerKey,
				nodeAccount, payer,
				payerKey, nodeAccount, transferAmt, memo);
		return transferTx;

	}


	private static Pair<List<Transaction>, byte[]> getFileContent(FileServiceGrpc.FileServiceBlockingStub fileStub,
			AccountID payerAccount,
			PrivateKey payerKey,
			FileID fid, AccountID nodeAccount)
			throws Exception {

		long feeForFileContentCost = FeeClient.getFeeByID(HederaFunctionality.FileGetContents);
		Response getFileCostInfoResponse = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, payerKey, nodeAccount,
						feeForFileContentCost,
						"queryFileContentFee");
				Query getFileInfoQuery = RequestBuilder.getFileGetContentBuilder(queryPaymentTx, fid,
						ResponseType.COST_ANSWER);
				return getFileInfoQuery;
			} catch (Exception e) {
				return null;
			}
		}, fileStub::getFileContent);

		long queryGetFileContentCostFee = getFileCostInfoResponse.getFileGetContents().getHeader()
				.getCost();

		final List<Transaction> queryTranList = new ArrayList<>();

		Response fileContentResp = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, payerKey, nodeAccount,
						queryGetFileContentCostFee,
						"queryFileContentAnswer");
				Query getFileInfoQuery = RequestBuilder.getFileGetContentBuilder(queryPaymentTx, fid,
						ResponseType.ANSWER_ONLY);
				queryTranList.add(queryPaymentTx);
				return getFileInfoQuery;
			} catch (Exception e) {
				return null;
			}
		}, fileStub::getFileContent);

		FileGetContentsResponse.FileContents fileContent = fileContentResp.getFileGetContents().getFileContents();
		ByteString actualFileData = fileContent.getContents();
		return Pair.of(queryTranList, actualFileData.toByteArray());
	}


	public static Transaction appendFile(FileServiceGrpc.FileServiceBlockingStub fileStub,
			AccountID payerAccount,
			PrivateKey payerPrivateKey,
			final List<KeyPair> accessKeys,
			FileID fid, byte[] bytes, long nodeAccountNumber, long transactionFee,
			final Map<String, PrivateKey> pubKey2PrivateKeyMap) throws InvalidProtocolBufferException {

		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Duration transactionDuration = RequestBuilder.getDuration(30);
		ByteString fileData = ByteString.copyFrom(bytes);
		SignatureList signatures = SignatureList.newBuilder()
				.getDefaultInstanceForType();

		//Extract private key and public key
		List<PrivateKey> waclPrivKeyList = new ArrayList<>();

		List<Key> sigMapKeyList = new ArrayList<>();
		sigMapKeyList.add(Common.PrivateKeyToKey(payerPrivateKey));
		for (KeyPair pair : accessKeys) {
			waclPrivKeyList.add(pair.getPrivate());
			sigMapKeyList.add(Common.PrivateKeyToKey(pair.getPrivate()));
		}

		List<Key> waclPubKeyList = new ArrayList<>();
		for (KeyPair pair : accessKeys) {
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			waclPubKeyList.add(waclKey);
		}

		Transaction transaction = Common.tranSubmit(() -> {
			try {
				Transaction fileAppendRequest = RequestBuilder
						.getFileAppendBuilder(payerAccount.getAccountNum(), 0l, 0l,
								nodeAccountNumber, 0l, 0l, transactionFee,
								timestamp, transactionDuration, true, "FileAppend", signatures, fileData, fid);
				Transaction fileSigned = TransactionSigner.signTransactionComplex(fileAppendRequest, sigMapKeyList,
						pubKey2PrivateKeyMap);
				return fileSigned;
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		}, fileStub::appendContent);
		return transaction;
	}

	public static Pair<List<Transaction>, FileGetInfoResponse.FileInfo> getFileInfo(
			FileServiceGrpc.FileServiceBlockingStub fileStub,
			AccountID payerAccount,
			PrivateKey payerKey,
			long nodeAccountNumber,
			FileID fid) {
		long feeForFileInfoCost = FeeClient.getFeeByID(HederaFunctionality.FileGetInfo);
		final AccountID nodeAccount = RequestBuilder.getAccountIdBuild(nodeAccountNumber, 0l, 0l);
		Response fileInfoResp = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, payerKey, nodeAccount,
						feeForFileInfoCost,
						"queryFileInfoFee");
				return RequestBuilder.getFileGetInfoBuilder(queryPaymentTx, fid, ResponseType.COST_ANSWER);
			} catch (Exception e) {
				return null;
			}
		}, fileStub::getFileInfo);

		Assert.assertEquals(ResponseCodeEnum.OK,
				fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());

		long feeForFileInfo = fileInfoResp.getFileGetInfo().getHeader().getCost();
		final List<Transaction> queryTranList = new ArrayList<>();

		fileInfoResp = Common.querySubmit(() -> {
			try {
				Transaction queryPaymentTx = createQueryHeaderTransfer(payerAccount, payerKey, nodeAccount,
						feeForFileInfo,
						"QueryFileInfoAnswer");
				queryTranList.add(queryPaymentTx);
				return RequestBuilder.getFileGetInfoBuilder(queryPaymentTx, fid, ResponseType.ANSWER_ONLY);
			} catch (Exception e) {
				return null;
			}
		}, fileStub::getFileInfo);

		Assert.assertEquals(ResponseCodeEnum.OK,
				fileInfoResp.getFileGetInfo().getHeader().getNodeTransactionPrecheckCode());

		FileGetInfoResponse.FileInfo fileInfo = fileInfoResp.getFileGetInfo().getFileInfo();
		log.info("fileGetInfoQuery: info = " + fileInfo);
		FileID actualFid = fileInfo.getFileID();
		Assert.assertEquals(fid, actualFid);

		return Pair.of(queryTranList, fileInfo);
	}

	/**
	 *
	 */
	private static Transaction createUpdateFile(FileServiceGrpc.FileServiceBlockingStub fileStub,
			AccountID payerAccount,
			PrivateKey payerPrivateKey,
			final List<KeyPair> accessKeys,
			long nodeAccountNumber, byte[] bytes,
			long fileDuration, long transactionFee,
			final Map<String, PrivateKey> pubKey2PrivateKeyMap,
			FileID fileID) {
		Timestamp timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()));
		Timestamp fileExp = ProtoCommonUtils.addSecondsToTimestamp(timestamp, fileDuration);
		SignatureList signatures = SignatureList.newBuilder().getDefaultInstanceForType();

		Duration transactionDuration = RequestBuilder.getDuration(30);
		ByteString fileData = ByteString.copyFrom(bytes);


		//Extract private key and public key
		List<PrivateKey> waclPrivKeyList = new ArrayList<>();

		List<Key> sigMapKeyList = new ArrayList<>();
		sigMapKeyList.add(Common.PrivateKeyToKey(payerPrivateKey));
		for (KeyPair pair : accessKeys) {
			waclPrivKeyList.add(pair.getPrivate());
			sigMapKeyList.add(Common.PrivateKeyToKey(pair.getPrivate()));
		}

		List<Key> waclPubKeyList = new ArrayList<>();
		for (KeyPair pair : accessKeys) {
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key waclKey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			waclPubKeyList.add(waclKey);
		}

		Transaction transaction;
		if (fileID == null) {
			transaction = Common.tranSubmit(() -> {
				try {
					Transaction FileCreateRequest = RequestBuilder
							.getFileCreateBuilder(payerAccount.getAccountNum(), 0L, 0L,
									nodeAccountNumber, 0L, 0L, transactionFee,
									timestamp, transactionDuration, true, "FileCreate",
									signatures, fileData, fileExp,
									waclPubKeyList);
					Transaction fileSigned = TransactionSigner.signTransactionComplex(FileCreateRequest, sigMapKeyList,
							pubKey2PrivateKeyMap);
					return fileSigned;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}, fileStub::createFile);
		} else {
			transaction = Common.tranSubmit(() -> {
				try {
					Transaction FileUpdateRequest = RequestBuilder
							.getFileUpdateBuilder(payerAccount.getAccountNum(), 0L, 0L,
									nodeAccountNumber, 0L, 0L, transactionFee,
									timestamp, fileExp, transactionDuration, true, "FileUpdate",
									signatures, fileData, fileID);
					Transaction fileSigned = TransactionSigner.signTransactionComplex(FileUpdateRequest, sigMapKeyList,
							pubKey2PrivateKeyMap);
					return fileSigned;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			}, fileStub::updateFile);
		}
		return transaction;
	}

}
