package com.hedera.services.legacy.regression.umbrella;

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
import com.hedera.services.legacy.core.CustomPropertiesSingleton;
import com.hedera.services.legacy.core.TestHelper;
import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Query;
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
import com.hedera.services.legacy.client.util.TransactionSigner;
import com.hederahashgraph.service.proto.java.FileServiceGrpc.FileServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * Implementation of File Service API Regression Tests to be mainly used by UmbrellaTest.
 */
public class FileServiceTest extends CryptoServiceTest {

	private static final Logger log = LogManager.getLogger(FileServiceTest.class);
	private static String UPLOAD_PATH = "regressionTestFiles/";
	private static int WAIT_IN_SEC = 0;
	private static String[] files = { "1K.txt", "1K.jpg", "1K.pdf", "1K.bin" };
	protected static int FILE_PART_SIZE = 4096; //4K bytes
	protected static ConcurrentHashMap<FileID, List<Key>> fid2waclMap = new ConcurrentHashMap<>();

	public FileServiceTest(String testConfigFilePath) {
		super(testConfigFilePath);
	}

	public static void main(String[] args) throws Throwable {
		checkTimestamp();
	}

	public static void checkTimestamp() {
		Timestamp prev = TestHelperComplex.getDefaultCurrentTimestampUTC();
		for (int i = 0; i < 10; i++) {
			Timestamp current = TestHelperComplex.getDefaultCurrentTimestampUTC();
			log.info("i=" + i + "\n prev=" + prev.getNanos() + "\n curr=" + current.getNanos());
			Assertions.assertNotEquals(prev.getNanos(), current.getNanos());
			prev = current;
		}
	}

	/**
	 * Write bytes to a file.
	 *
	 * @param path
	 * 		file write path
	 * @param data
	 * 		byte array representation of data to write
	 * @param append
	 * 		append to existing file if true
	 * @throws IOException
	 * 		when error with IO
	 */
	public static void writeToFile(String path, byte[] data, boolean append) throws IOException {
		File f = new File(path);
		File parent = f.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}
		try (FileOutputStream fos = new FileOutputStream(f, append)) {
			fos.write(data);
			fos.flush();
		} catch (IOException e) {
			throw e;
		}
	}

	/**
	 * Updates a file given file Id.
	 *
	 * @param fid
	 * 		the ID of the file to be updated
	 * @param fileType
	 * 		the path of the file whose content will be used to generate data as the update
	 * @param payerID
	 * 		the fee payer ID, acting as payer for transaction
	 * @param nodeID
	 * 		the node ID, default listening account
	 * @param oldWaclKeyList
	 * 		existing wacl keys
	 * @param newWaclKeyList
	 * 		the new wacl keys to replace existing ones
	 * @throws Throwable
	 * 		indicates failure to update the file
	 */
	protected void updateFile(FileID fid, String fileType, AccountID payerID, AccountID nodeID,
			List<Key> oldWaclKeyList, List<Key> newWaclKeyList) throws Throwable {
		Timestamp fileExp = ProtoCommonUtils
				.getCurrentTimestampUTC(CustomPropertiesSingleton.getInstance().getUpdateDurationValue());

		KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclKeyList).build();
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		byte[] fileData = genFileContent(3, fileType);
		Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, fileExp, transactionDuration, true,
				"FileUpdate",
				ByteString.copyFrom(fileData), fid, wacl);

		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key existingWaclKey = Key.newBuilder()
				.setKeyList(KeyList.newBuilder().addAllKeys(oldWaclKeyList)).build();
		Key newWaclKey = Key.newBuilder().setKeyList(wacl).build();

		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(existingWaclKey);
		keys.add(newWaclKey);
		Transaction txSigned = TransactionSigner
				.signTransactionComplexWithSigMap(FileUpdateRequest, keys, pubKey2privKeyMap);

		log.info("\n-----------------------------------");
		log.info(
				"FileUpdate: input data = " + fileData + "\nexpirationTime = " + fileExp + "\nWACL keys = "
						+ newWaclKeyList);
		log.info("FileUpdate: request = " + txSigned);

		checkTxSize(txSigned);

		TransactionResponse response = retryLoopTransaction(nodeID, txSigned, "updateFile");

		log.info("FileUpdate with data, exp, and wacl respectively, Response :: "
				+ response.getNodeTransactionPrecheckCodeValue());
		Assertions.assertNotNull(response);
		Assertions.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(FileUpdateRequest);
		TransactionID txId = body.getTransactionID();
		cache.addTransactionID(txId);

		if (getReceipt) {
			TransactionReceipt receipt = getTxReceipt(txId);
			Assertions.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
			getFileInfo(fid, payerID, nodeID);
		}
	}


	public Transaction updateFile(FileID fid, AccountID payerID, AccountID nodeID,
			List<Key> oldWaclKeyList, List<Key> newWaclKeyList, ByteString fileData, String memo,
			Timestamp fileExp) throws Throwable {
		KeyList wacl = KeyList.newBuilder().addAllKeys(newWaclKeyList).build();
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction FileUpdateRequest = RequestBuilder.getFileUpdateBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, fileExp, transactionDuration, true,
				memo, fileData, fid, wacl);

		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key existingWaclKey = Key.newBuilder()
				.setKeyList(KeyList.newBuilder().addAllKeys(oldWaclKeyList)).build();
		Key newWaclKey = Key.newBuilder().setKeyList(wacl).build();

		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(existingWaclKey);
		keys.add(newWaclKey);
		Transaction txSigned = TransactionSigner
				.signTransactionComplexWithSigMap(FileUpdateRequest, keys, pubKey2privKeyMap);

		checkTxSize(txSigned);
		TransactionBody updateBody = TransactionBody.parseFrom(txSigned.getBodyBytes());
		if (updateBody.getTransactionID() == null || !updateBody.getTransactionID()
				.hasTransactionValidStart()) {
			return updateFile(fid, payerID, nodeID,
					oldWaclKeyList, newWaclKeyList, fileData, memo, fileExp);
		}
		return txSigned;
	}

	/**
	 * Deletes a file given the file ID.
	 *
	 * @param fid
	 * 		the ID of the file to be deleted
	 * @param payerID
	 * 		the fee payer ID, acting as payer for the transaction
	 * @param nodeID
	 * 		the node ID, default listening account
	 * @param waclKeyList
	 * 		the file creation WACL public key list
	 * @throws Throwable
	 * 		indicates failure to delete the file
	 */
	protected void deleteFile(FileID fid, AccountID payerID, AccountID nodeID, List<Key> waclKeyList)
			throws Throwable {
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		Transaction FileDeleteRequest = RequestBuilder.getFileDeleteBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, transactionDuration, true,
				"FileDelete",
				fid);

		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(waclKey);
		Transaction txSigned = TransactionSigner
				.signTransactionComplexWithSigMap(FileDeleteRequest, keys, pubKey2privKeyMap);

		log.info("\n-----------------------------------");
		log.info("FileDelete: request = " + txSigned);
		checkTxSize(txSigned);

		TransactionResponse response = retryLoopTransaction(nodeID, txSigned, "deleteFile");

		log.info("FileDelete Response :: " + response.getNodeTransactionPrecheckCodeValue());
		Assertions.assertNotNull(response);
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(FileDeleteRequest);
		TransactionID txId = body.getTransactionID();
		cache.addTransactionID(txId);

		if (getReceipt) {
			TransactionReceipt receipt = getTxReceipt(txId);
			Assertions.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());
		}
	}

	/**
	 * Creates a file on the ledger from given file contents
	 *
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account
	 * @param fileData
	 * 		contents of the file to be used while creating file
	 * @param waclKeyList
	 * 		the file creation WACL public key list
	 * @param cacheTxID
	 * 		whether to cache transaction ID
	 * @param getReceiptAndFileID
	 * 		whether or not get receipt and return ID of created file
	 * @return ID of the file created if getReceiptAndFileID is true, null otherwise
	 * @throws Throwable
	 * 		indicates failure while creating file
	 */
	public FileID createFile(AccountID payerID, AccountID nodeID, ByteString fileData,
			List<Key> waclKeyList, boolean cacheTxID, boolean getReceiptAndFileID) throws Throwable {
		log.info("@@@ upload file: file size in byte = " + fileData.size());
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();
		CustomPropertiesSingleton properties = CustomPropertiesSingleton.getInstance();
		Timestamp fileExp = ProtoCommonUtils.getCurrentTimestampUTC(properties.getFileDurtion());

		Transaction FileCreateRequest = RequestBuilder.getFileCreateBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, transactionDuration, true,
				"FileCreate", fileData,
				fileExp, waclKeyList);
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(FileCreateRequest);
		TransactionID txId = body.getTransactionID();

		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(waclKey);
		Transaction filesigned = TransactionSigner
				.signTransactionComplexWithSigMap(FileCreateRequest, keys, pubKey2privKeyMap);

		log.info("\n-----------------------------------");
		log.info("FileCreate: request = " + filesigned);
		checkTxSize(filesigned);

		TransactionResponse response = retryLoopTransaction(nodeID, filesigned, "createFile");

		log.info("FileCreate Response :: " + response);
		Assertions.assertNotNull(response);
		Assertions.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
		if (cacheTxID) {
			cache.addTransactionID(txId);
		}

		if (!getReceiptAndFileID) {
			return null;
		}

		// get the file ID
		TransactionReceipt receipt = getTxReceipt(txId);
		if (!ResponseCodeEnum.SUCCESS.name().equals(receipt.getStatus().name())) {
			throw new Exception(
					"Create file failed! The receipt retrieved receipt=" + receipt);
		}
		FileID fid = receipt.getFileID();
		log.info("GetTxReceipt: file ID = " + fid);
		Assertions.assertNotNull(fid);
		Assertions.assertNotEquals(0, fid.getFileNum());

		return fid;
	}

	private TransactionResponse retryLoopTransaction(AccountID nodeID, Transaction transaction,
			String Action)
			throws Exception {
		FileServiceBlockingStub stub = null;
		ManagedChannel[] createdChannels = new ManagedChannel[1];
		if (isOneUseChannel) {
			stub = createFileServiceStub(createdChannels);
		} else {
			stub = getStub(nodeID);
		}

		TransactionResponse response = null;
		for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

			try {
				switch (Action) {
					case "createFile":
						response = stub.createFile(transaction);
						break;
					case "deleteFile":
						response = stub.deleteFile(transaction);
						break;
					case "updateFile":
						response = stub.updateFile(transaction);
						break;
					case "appendContent":
						response = stub.appendContent(transaction);
						break;
					default:
						throw new IllegalArgumentException();
				}
			} catch (StatusRuntimeException ex) {
				log.error("Platform exception ...", ex);
				Status status = ex.getStatus();
				String errorMsg = status.getDescription();
				if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
					if (isOneUseChannel) {
						stub = createFileServiceStub(createdChannels);
					} else {
						stub = newStub(nodeID);
					}
				}
				Thread.sleep(BUSY_RETRY_MS);
				continue;
			}

			if (!ResponseCodeEnum.BUSY.equals(response.getNodeTransactionPrecheckCode())) {
				break;
			}
			Thread.sleep(BUSY_RETRY_MS);
			log.info("Transaction: Busy retry");
		}

		if (isOneUseChannel) {
			createdChannels[0].shutdown();
		}
		return response;
	}

	private Response retryLoopQuery(AccountID nodeID, Query query, String Action)
			throws Exception {
		FileServiceBlockingStub stub = null;
		ManagedChannel[] createdChannels = new ManagedChannel[1];
		if (isOneUseChannel) {
			stub = createFileServiceStub(createdChannels);
		} else {
			stub = getStub(nodeID);
		}

		Response response = null;
		for (int i = 0; i <= MAX_BUSY_RETRIES; i++) {

			ResponseCodeEnum precheckCode;
			try {
				switch (Action) {
					case "getFileInfo":
						response = stub.getFileInfo(query);
						precheckCode = response.getFileGetInfo()
								.getHeader().getNodeTransactionPrecheckCode();
						break;
					default:
						throw new IllegalArgumentException();
				}
			} catch (StatusRuntimeException ex) {
				log.error("Platform exception ...", ex);
				Status status = ex.getStatus();
				String errorMsg = status.getDescription();
				if (status.equals(Status.UNAVAILABLE) && errorMsg != null && errorMsg.contains("max_age")) {
					if (isOneUseChannel) {
						stub = createFileServiceStub(createdChannels);
					} else {
						stub = newStub(nodeID);
					}
				}
				Thread.sleep(BUSY_RETRY_MS);
				continue;
			}

			if (!ResponseCodeEnum.BUSY.equals(precheckCode)) {
				break;
			}
			Thread.sleep(BUSY_RETRY_MS);
			log.info("Query: Busy retry");
		}

		if (isOneUseChannel) {
			createdChannels[0].shutdown();
		}
		return response;
	}

	/**
	 * create file from the file contents
	 *
	 * @param payerID
	 * 		payer account id, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account
	 * @param fileData
	 * 		contents of the file to be used while creating file
	 * @param waclKeyList
	 * 		the file creation WACL public key list
	 * @param fileExp
	 * 		expiration time for the file
	 * @param memo
	 * 		memo of the file create transaction
	 * @return file create transaction
	 * @throws Throwable
	 * 		indicates failure while creating file
	 */
	public Transaction createFile(AccountID payerID, AccountID nodeID, ByteString fileData,
			List<Key> waclKeyList, Timestamp fileExp, String memo) throws Throwable {
		log.info("@@@ upload file: file size in byte = " + fileData.size());
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();

		Transaction FileCreateRequest = RequestBuilder.getFileCreateBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, transactionDuration, true,
				memo, fileData,
				fileExp, waclKeyList);
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(FileCreateRequest);
		TransactionID txId = body.getTransactionID();

		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeyList)).build();
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(waclKey);
		Transaction filesigned = TransactionSigner
				.signTransactionComplexWithSigMap(FileCreateRequest, keys, pubKey2privKeyMap);
		TransactionBody txBody = TransactionBody.parseFrom(filesigned.getBodyBytes());
		if (txBody.getTransactionID() == null || !txBody.getTransactionID()
				.hasTransactionValidStart()) {
			return createFile(payerID, nodeID, fileData, waclKeyList, fileExp, memo);
		}
		log.info("\n-----------------------------------");
		log.info("FileCreate: request = " + filesigned);
		checkTxSize(filesigned);

		return filesigned;
	}

	/**
	 * Uploads a large file from a given path.
	 *
	 * @param savePath
	 * 		the file path locally saving the content retrieved from the uploaded file on
	 * 		the ledger
	 * @param bytes
	 * 		the content of the file to upload
	 * @param payerID
	 * 		the fee payer ID, acting as payer for the transaction
	 * @param nodeID
	 * 		the node ID, default listening account
	 * @param waclPubKeyList
	 * 		WACL public key list
	 * @return the fileID of uploaded file
	 * @throws Throwable
	 * 		indicates failure while uploading file
	 */
	public FileID uploadFile(String savePath, byte[] bytes, AccountID payerID, AccountID nodeID,
			List<Key> waclPubKeyList) throws Throwable {
		int numParts = bytes.length / FILE_PART_SIZE;
		int remainder = bytes.length % FILE_PART_SIZE;
		log.info("@@@ file size=" + bytes.length + "; FILE_PART_SIZE=" + FILE_PART_SIZE + "; numParts="
				+ numParts + "; remainder=" + remainder);

		byte[] firstPartBytes = null;
		if (bytes.length <= FILE_PART_SIZE) {
			firstPartBytes = bytes;
			remainder = 0;
		} else {
			firstPartBytes = Arrays.copyOfRange(bytes, 0, FILE_PART_SIZE);
		}

		// create file with first part
		ByteString fileData = ByteString.copyFrom(firstPartBytes);
		FileID fid = createFile(payerID, nodeID, fileData, waclPubKeyList, false, true);
		log.info("@@@ created file with first part: fileID = " + fid);

		getFileInfo(fid, payerID, nodeID);
		TimeUnit.SECONDS.sleep(WAIT_IN_SEC);

		// append the rest of the parts
		int i = 1;
		for (; i < numParts; i++) {
			byte[] partBytes = Arrays.copyOfRange(bytes,i * FILE_PART_SIZE, (i + 1) * FILE_PART_SIZE);
			fileData = ByteString.copyFrom(partBytes);
			appendFile(payerID, nodeID, fid, fileData, waclPubKeyList);
			log.info("@@@ append file count = " + i);
			TimeUnit.SECONDS.sleep(WAIT_IN_SEC);
			getFileInfo(fid, payerID, nodeID);
		}

		if (remainder > 0) {
			final var start = numParts * FILE_PART_SIZE;
			byte[] partBytes = Arrays.copyOfRange(bytes, start, start + remainder);
			fileData = ByteString.copyFrom(partBytes);
			appendFile(payerID, nodeID, fid, fileData, waclPubKeyList);
			log.info("@@@ append file count = " + i);
			TimeUnit.SECONDS.sleep(WAIT_IN_SEC);
			getFileInfo(fid, payerID, nodeID);
		}

		// get file content and save to disk
		byte[] content = getFileContent(fid, payerID, nodeID).toByteArray();
		Assertions.assertArrayEquals(bytes, content);
		saveFile(content, savePath);
		return fid;
	}

	/**
	 * Appends a file with some data.
	 *
	 * @param payerID
	 * 		the fee payer ID, acting as payer for the transaction
	 * @param nodeID
	 * 		the node ID, default listening account
	 * @param fid
	 * 		file id of the file whose content needs to be appended
	 * @param fileData
	 * 		contents of the file to be appended
	 * @param waclKeys
	 * 		WACL public key list
	 * @throws Throwable
	 * 		indicates failure while appending file
	 */
	public void appendFile(AccountID payerID, AccountID nodeID, FileID fid, ByteString fileData,
			List<Key> waclKeys) throws Throwable {
		Timestamp timestamp = TestHelperComplex.getDefaultCurrentTimestampUTC();

		Transaction fileAppendRequest = RequestBuilder.getFileAppendBuilder(payerID.getAccountNum(),
				payerID.getRealmNum(), payerID.getShardNum(), nodeID.getAccountNum(), nodeID.getRealmNum(),
				nodeID.getShardNum(), TestHelper.getFileMaxFee(), timestamp, transactionDuration, true,
				"FileAppend", fileData,
				fid);

		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(fileAppendRequest);
		TransactionID txId = body.getTransactionID();

		Key payerKey = acc2ComplexKeyMap.get(payerID);
		Key waclKey = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(waclKeys)).build();
		List<Key> keys = new ArrayList<Key>();
		keys.add(payerKey);
		keys.add(waclKey);
		Transaction txSigned = TransactionSigner
				.signTransactionComplexWithSigMap(fileAppendRequest, keys, pubKey2privKeyMap);

		log.info("\n-----------------------------------");
		log.info("FileAppend: request = " + txSigned);
		checkTxSize(txSigned);

		TransactionResponse response = retryLoopTransaction(nodeID, txSigned, "appendContent");

		log.info("FileAppend Response :: " + response);
		Assertions.assertNotNull(response);
		Assertions.assertEquals(ResponseCodeEnum.OK_VALUE, response.getNodeTransactionPrecheckCodeValue());
		cache.addTransactionID(txId);

		if (getReceipt) {
			TransactionReceipt receipt = getTxReceipt(txId);
			Assertions.assertEquals(ResponseCodeEnum.SUCCESS.name(), receipt.getStatus().name());

			getFileInfo(fid, payerID, nodeID);
		}
	}

	/**
	 * Generates the content of a file.
	 *
	 * @param fileSizeKB
	 * 		size of the file to be generated
	 * @param fileType
	 * 		type of the file to be generated
	 * @return file bytes content generated for the file
	 * @throws IOException
	 * 		exception caused if file doesn't exist
	 * @throws URISyntaxException
	 * 		exception caused if the URI for the path can't be parsed
	 */
	protected byte[] genFileContent(int fileSizeKB, String fileType)
			throws IOException, URISyntaxException {
		String filePath = null;
		for (int j = 0; j < fileTypes.length; j++) {
			if (fileType.equals(fileTypes[j])) {
				filePath = files[j];
				break;
			}
		}

		byte[] destBytes = null;
		byte[] bytes = readFileContent(filePath);
		if (fileSizeKB == 1) {
			destBytes = bytes;
		} else {
			destBytes = new byte[bytes.length * fileSizeKB];
			for (int i = 0; i < fileSizeKB; i++) {
				for (int j = 0; j < bytes.length; j++) {
					destBytes[i * bytes.length + j] = bytes[j];
				}
			}
		}
		return destBytes;
	}

	/**
	 * Reads the content of a file from a given file path.
	 *
	 * @param filePath
	 * 		path of the file
	 * @return file bytes read from the file
	 * @throws IOException
	 * 		indicates failure reading file from the given path
	 */
	private byte[] readFileContent(String filePath) throws IOException {
		String localPath = UPLOAD_PATH + filePath;
		File dir = new File(localPath);
		if (!dir.exists()) {
			localPath = "src/main/resource/" + localPath;
		}
		log.info("Getting File contents: " + localPath);
		Path path = Paths.get(localPath);
		return Files.readAllBytes(path);
	}

	/**
	 * Gets the file info given a file ID
	 *
	 * @param fid
	 * 		target file ID to get file info for
	 * @param payerID
	 * 		payer account ID, acting as payer for the transaction
	 * @param nodeID
	 * 		node account id, default listening account
	 * @return queried file info
	 * @throws Exception
	 * 		indicates failure while getting file info for a file
	 */
	public FileInfo getFileInfo(FileID fid, AccountID payerID, AccountID nodeID)
			throws Exception {
		long fileGetInfoCost = getFileGetInfoCost(fid, payerID, nodeID);

		Transaction paymentTxSigned = getQueryPaymentSignedWithFee(payerID, nodeID, "fileGetInfoQuery",
				fileGetInfoCost);
		Query fileGetInfoQuery = RequestBuilder
				.getFileGetInfoBuilder(paymentTxSigned, fid, ResponseType.ANSWER_ONLY);
		log.info("\n-----------------------------------");
		log.info("fileGetInfoQuery: query = " + fileGetInfoQuery);

		Response fileInfoResp = retryLoopQuery(nodeID, fileGetInfoQuery, "getFileInfo");

		Assertions.assertNotNull(fileInfoResp);
		ResponseCodeEnum code = fileInfoResp.getFileGetInfo().getHeader()
				.getNodeTransactionPrecheckCode();
		if (code != ResponseCodeEnum.OK) {
			throw new Exception(
					"Precheck error geting file info! Precheck code = " + code.name() + "\nfileGetInfoQuery="
							+ fileGetInfoQuery);
		}
		FileInfo fileInfo = fileInfoResp.getFileGetInfo().getFileInfo();
		log.info("fileGetInfoQuery: info = " + fileInfo);
		FileID actualFid = fileInfo.getFileID();
		Assertions.assertEquals(fid, actualFid);
		return fileInfo;
	}

	private long getFileGetInfoCost(FileID fid, AccountID payerID, AccountID nodeID) throws Exception {
		Transaction paymentTxSignedCost = getQueryPaymentSigned(payerID, nodeID, "fileGetInfoQueryCost");
		Query fileGetInfoQueryCost = RequestBuilder
				.getFileGetInfoBuilder(paymentTxSignedCost, fid, ResponseType.COST_ANSWER);
		Response fileInfoRespCost = retryLoopQuery(nodeID, fileGetInfoQueryCost, "getFileInfo");
		return fileInfoRespCost.getFileGetInfo().getHeader().getCost();
	}

	protected void saveFile(byte[] content, String filePath) throws IOException {
		String path = "saved" + FileSystems.getDefault().getSeparator() + filePath;
		writeToFile(path, content, false);
		String workDir = System.getProperty("user.dir");
		log.info(
				"File downloaded and saved at project root as: " + workDir + FileSystems.getDefault()
						.getSeparator() + path);
	}

	/**
	 * Generates given number of wacls keys.
	 *
	 * @param numKeys
	 * 		number of keys to generate, each key may have different key type
	 * @return generated list of keys
	 */
	public List<Key> genWaclComplex(int numKeys) {
		List<Key> keys = new ArrayList<>();
		for (int i = 0; i < numKeys; i++) {
			String accountKeyType = getRandomAccountKeyType();
			Key key = genComplexKey(accountKeyType);
			keys.add(key);
		}

		return keys;
	}

	/**
	 * Gets a random file ID from fid to wacl Map. Null is returned if no files available.
	 *
	 * @return file id
	 */
	FileID getRandomFileID() {
		FileID rv = null;
		synchronized (fid2waclMap) {
			if (fid2waclMap.size() != 0) {
				Set<Entry<FileID, List<Key>>> entrySet = fid2waclMap.entrySet();
				int index = FileServiceTest.rand.nextInt(entrySet.size());
				Iterator<Entry<FileID, List<Key>>> iter = entrySet.iterator();
				for (int i = 0; i < entrySet.size(); i++) {
					Entry<FileID, List<Key>> entry = iter.next();
					if (i == index) {
						rv = entry.getKey();
						break;
					}
				}
			}
		}

		log.info("@@getRandomFileID: fid=" + rv);
		return rv;
	}

	/**
	 * Remove a random entry of file ID and list of wacl keys from the fid to wacl map.
	 *
	 * @return the removed entry, null if no files available.
	 */
	Entry<FileID, List<Key>> removeRandomFileID2waclEntry() {
		Entry<FileID, List<Key>> entry = null;
		synchronized (fid2waclMap) {
			if (fid2waclMap.size() != 0) {
				Set<Entry<FileID, List<Key>>> entrySet = fid2waclMap.entrySet();
				int index = FileServiceTest.rand.nextInt(entrySet.size());
				Iterator<Entry<FileID, List<Key>>> iter = entrySet.iterator();
				for (int i = 0; i < entrySet.size(); i++) {
					entry = iter.next();
					if (i == index) {
						fid2waclMap.remove(entry.getKey());
						break;
					}
				}
			}
		}
		return entry;
	}

}
