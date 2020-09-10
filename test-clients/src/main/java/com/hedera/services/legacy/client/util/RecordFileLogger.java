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
import com.google.protobuf.TextFormat;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.LiveHash;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

public class RecordFileLogger implements LoggerInterface {
	static FileWriter fw = null;

	public static void start() {
		// called at the start of record file parsing
	}
	public static void finish() {
		// called at the end of record file parsing
	}
	public static void initFile(String fileName) {
		// called when a file starts processing
		// open a resource such as a file or database here
		try
		{
		    String filename= "output/recordStream.txt";
		    fw = new FileWriter(filename,true);
		}
		catch(IOException ioe)
		{
		    System.err.println("IOException: " + ioe.getMessage());
		}
	}
	public static void completeFile() {
		// called when a file completes processing
		// close the file or database here
		try
		{
		    if (fw != null) {
		    	fw.close();
		    }
		}
		catch(IOException ioe)
		{
		    System.err.println("IOException: " + ioe.getMessage());
		}
	}
	public static void storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord) throws InvalidProtocolBufferException {
		// called for each record so it can be processed, transformed and stored
		// these are sample transaction and record parsing
		TransactionBody body = CommonUtils.extractTransactionBody(transaction);

		// TransactionID - unique for each transaction, the combination of the three
		// values below uniquely identifies a transaction within Hedera
		Long transactionValidStartSeconds = body.getTransactionID().getTransactionValidStart().getSeconds();
		int transactionValidStartNanos = body.getTransactionID().getTransactionValidStart().getNanos();
		Long initiatingAccountNumber = body.getTransactionID().getAccountID().getAccountNum();

		// transaction information
		boolean generateRecord = body.getGenerateRecord();
		String txMemo = body.getMemo();
		Long nodeAccountNumber = body.getNodeAccountID().getAccountNum();
		Long transactionFee = body.getTransactionFee();
		Long transactionValidDurationSeconds = body.getTransactionValidDuration().getSeconds();

		// generic record data
		Long consensusTimeStampSeconds = txRecord.getConsensusTimestamp().getSeconds();
		int consensusTimeStampNanos = txRecord.getConsensusTimestamp().getNanos();
		TransactionReceipt receipt = txRecord.getReceipt();

		if (receipt.getStatus() == ResponseCodeEnum.SUCCESS) {
			// transaction was successful
		} else {
			// transaction failed for some reason.
		}

		if (body.hasContractCall()) {
			ContractCallTransactionBody contractCall = body.getContractCall();
			long amount = contractCall.getAmount();
			ContractID contractId = contractCall.getContractID();
			ByteString functionParameters = contractCall.getFunctionParameters();
			long gas = contractCall.getGas();

			// now get the call results from the record
			ByteString bloom = txRecord.getContractCallResult().getBloom();
			ByteString callResult = txRecord.getContractCallResult().getContractCallResult();
			String errorMessage = txRecord.getContractCallResult().getErrorMessage();
			long gasUsed = txRecord.getContractCallResult().getGasUsed();
			int logInfoCount = txRecord.getContractCallResult().getLogInfoCount();
//			txRecord.getContractCallResult().getLogInfo(index);
		} else if (body.hasContractCreateInstance()) {
			ContractCreateTransactionBody contractCreate = body.getContractCreateInstance();
			Key adminKey = contractCreate.getAdminKey();
			Duration autoRenewPeriod = contractCreate.getAutoRenewPeriod();
			ByteString constructorParameters = contractCreate.getConstructorParameters();
			FileID fileId = contractCreate.getFileID();
			long gas = contractCreate.getGas();
			long initialBalance = contractCreate.getInitialBalance();
			String memo = contractCreate.getMemo();

			// now get the contract number from the record
			Long contractNum = txRecord.getContractCreateResult().getContractID().getContractNum();
		} else if (body.hasContractDeleteInstance()) {
			ContractDeleteTransactionBody contractDelete = body.getContractDeleteInstance();

			ContractID contractId = contractDelete.getContractID();
			AccountID transferAccountId = contractDelete.getTransferAccountID();
			ContractID transferContractId = contractDelete.getTransferContractID();
		} else if (body.hasContractUpdateInstance()) {
			ContractUpdateTransactionBody contractUpdate = body.getContractUpdateInstance();

			contractUpdate.getAdminKey();
			contractUpdate.getAutoRenewPeriod();
			contractUpdate.getContractID();
			contractUpdate.getExpirationTime();
			contractUpdate.getFileID();
			contractUpdate.getMemo();
			contractUpdate.getProxyAccountID();

		} else if (body.hasCryptoAddLiveHash()) {
			CryptoAddLiveHashTransactionBody addLiveHash = body.getCryptoAddLiveHash();

			LiveHash claim = addLiveHash.getLiveHash();

			AccountID accountId = claim.getAccountId();
			Duration duration = claim.getDuration();
			ByteString hash = claim.getHash();

			KeyList keys = claim.getKeys();
			for (int i=0; i < keys.getKeysCount(); i++) {
				Key key = keys.getKeys(i);
				if (key.hasKeyList()) {
					// key is a key list
					KeyList list = key.getKeyList();
				} else if (key.hasThresholdKey()) {
					//  key is a threshold key
					ThresholdKey thresholdKey = key.getThresholdKey();
					KeyList list = thresholdKey.getKeys();
					int threshold = thresholdKey.getThreshold();
				} else if (key.hasContractID()) {
					// key is a contract
					ContractID contractId = key.getContractID();
				} else {
					ByteString ed25519 = key.getEd25519();
				}
			}

		} else if (body.hasCryptoCreateAccount()) {
			CryptoCreateTransactionBody create = body.getCryptoCreateAccount();

			Duration autoRenewPeriod = create.getAutoRenewPeriod();
			long initialBalance = create.getInitialBalance();
			Key key = create.getKey();
			AccountID proxyAccountId = create.getProxyAccountID();
			long receiveRecordThreshold = create.getReceiveRecordThreshold();
			boolean receiverSigRequired = create.getReceiverSigRequired();
			long sendRecordThreshold = create.getSendRecordThreshold();

			long newAccountNum = txRecord.getReceipt().getAccountID().getAccountNum();

		} else if (body.hasCryptoDelete()) {
			CryptoDeleteTransactionBody delete = body.getCryptoDelete();

			AccountID accountToDelete = delete.getDeleteAccountID();
			AccountID transferToAccount = delete.getTransferAccountID();

		} else if (body.hasCryptoDeleteLiveHash()) {
			CryptoDeleteLiveHashTransactionBody delete = body.getCryptoDeleteLiveHash();

			AccountID accountToDeleteFrom = delete.getAccountOfLiveHash();
			ByteString hashToDelete = delete.getLiveHashToDelete();

		} else if (body.hasCryptoTransfer()) {
			CryptoTransferTransactionBody transfer = body.getCryptoTransfer();

			TransferList transfers = transfer.getTransfers();
			for (int i=0; i < transfers.getAccountAmountsCount(); i++) {
				AccountAmount accountAmount = transfers.getAccountAmounts(i);
				AccountID account = accountAmount.getAccountID();
				long amount = accountAmount.getAmount();
			}
		} else if (body.hasCryptoUpdateAccount()) {
			CryptoUpdateTransactionBody update = body.getCryptoUpdateAccount();

			AccountID accountToUpdate = update.getAccountIDToUpdate();
			Duration autoRenewPeriod = update.getAutoRenewPeriod();
			Timestamp expirationTime = update.getExpirationTime();
			Key key = update.getKey();
			AccountID proxyAccountId = update.getProxyAccountID();
			boolean receiverSignatureRequired = false;
			if (update.hasReceiverSigRequiredWrapper()) {
				receiverSignatureRequired = update.getReceiverSigRequiredWrapper().getValue();
			} else {
				receiverSignatureRequired = update.getReceiverSigRequired();

			}
			long receiveRecordThreshold = Long.MAX_VALUE;
			if (update.hasReceiveRecordThresholdWrapper()) {
				receiveRecordThreshold = update.getReceiveRecordThresholdWrapper().getValue();
			} else {
				receiveRecordThreshold = update.getReceiveRecordThreshold();
			}
			long sendRecordThreshold = Long.MAX_VALUE;
			if (update.hasSendRecordThresholdWrapper()) {
				sendRecordThreshold = update.getSendRecordThresholdWrapper().getValue();
			} else {
				sendRecordThreshold = update.getSendRecordThreshold();
			}

		} else if (body.hasFileAppend()) {
			FileAppendTransactionBody append = body.getFileAppend();

			ByteString contents = append.getContents();
			FileID fileId = append.getFileID();

		} else if (body.hasFileCreate()) {
			FileCreateTransactionBody create = body.getFileCreate();

			ByteString contents = create.getContents();
			Timestamp expirationTime = create.getExpirationTime();
			KeyList keys = create.getKeys();

			long newFileId = txRecord.getReceipt().getFileID().getFileNum();
		} else if (body.hasFileDelete()) {
			FileDeleteTransactionBody delete = body.getFileDelete();

			long fileToDelete = delete.getFileID().getFileNum();
		} else if (body.hasFileUpdate()) {
			FileUpdateTransactionBody update = body.getFileUpdate();

			ByteString newContents = update.getContents();
			Timestamp newExpirationTime = update.getExpirationTime();
			FileID fileId = update.getFileID();
			KeyList keys = update.getKeys();

		} else if (body.hasSystemDelete()) {
			SystemDeleteTransactionBody delete = body.getSystemDelete();

			ContractID contractToDelete = delete.getContractID();
			Long expirationTimeSeconds = delete.getExpirationTime().getSeconds();
			FileID fileId = delete.getFileID();

		} else if (body.hasSystemUndelete()) {
			SystemUndeleteTransactionBody undelete = body.getSystemUndelete();

			ContractID contractId = undelete.getContractID();
			FileID fileId = undelete.getFileID();

		} else if (body.hasFreeze()) {
			FreezeTransactionBody freeze = body.getFreeze();

			int endHour = freeze.getEndHour();
			int endMin = freeze.getEndMin();
			int startHour = freeze.getStartHour();
			int startMin = freeze.getStartMin();
		} else {
			// unknown transaction type
		}

		// write the transaction and record to a text file
		try {
			fw.write("TRANSACTION:" + counter + "\n");
//			fw.write(Utility.printTransactionNice(transaction));
			fw.write("RECORD:" + counter + "\n");
			fw.write(TextFormat.printToString(txRecord));
			fw.write("\n");
			fw.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void storeSignature(String signature) {
		// called for each signature so it can be processed, transformed and stored
		try {
			fw.write("SIGNATURE\n");
			fw.write(signature);
			fw.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
