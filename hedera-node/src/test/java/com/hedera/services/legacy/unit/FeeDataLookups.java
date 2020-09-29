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

import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.fees.calculation.FeeCalcUtils;
import com.hedera.services.legacy.unit.handler.FileServiceHandler;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.ConsensusServiceFeeBuilder;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SigValueObj;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import com.swirlds.fcmap.FCMap;

public class FeeDataLookups {

	static FeeData getCryptoCreateTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new CryptoFeeBuilder().getCryptoCreateTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getCryptoUpdateTransactionFeeMatrices(TransactionBody txBody,
			Timestamp expirationTimeStamp,
			SigValueObj sigValObj, Key existingKey) throws InvalidTxBodyException {
	  return new CryptoFeeBuilder().getCryptoUpdateTxFeeMatrices(txBody, sigValObj,
			  expirationTimeStamp, existingKey);
	}

	static FeeData getCryptoTransferTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new CryptoFeeBuilder().getCryptoTransferTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getCryptoDeleteTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new CryptoFeeBuilder().getCryptoDeleteTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getSmartContractCreateTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new SmartContractFeeBuilder().getContractCreateTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getSmartContractCallTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new SmartContractFeeBuilder().getContractCallTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getSmartContractUpdateTransactionFeeMatrices(TransactionBody txBody,
			Timestamp contractExpiryTime, SigValueObj sigValObj) throws InvalidTxBodyException {
	  return new SmartContractFeeBuilder().getContractUpdateTxFeeMatrices(txBody, contractExpiryTime, sigValObj);

	}

	static FeeData getFileCreateTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new FileFeeBuilder().getFileCreateTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getFileDeleteTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new FileFeeBuilder().getFileDeleteTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getFileAppendTransactionFeeMatrices(TransactionBody txBody,
			Timestamp expirationTimeStamp,
			SigValueObj sigValObj) throws InvalidTxBodyException {
	  return new FileFeeBuilder().getFileAppendTxFeeMatrices(txBody, expirationTimeStamp,
			  sigValObj);
	}

	static FeeData getFileUpdateTransactionFeeMatrices(TransactionBody txBody,
			Timestamp expirationTimeStamp,
			SigValueObj sigValObj) throws InvalidTxBodyException {
	  return new FileFeeBuilder().getFileUpdateTxFeeMatrices(txBody, expirationTimeStamp,
			  sigValObj);
	}

	static FeeData getAddLiveHashTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new CryptoFeeBuilder().getCryptoAddLiveHashTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getDeleteLiveHashTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new CryptoFeeBuilder().getCryptoDeleteLiveHashTxFeeMatrices(txBody, sigValObj);
	}

	static FeeData getSystemDeleteTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj numSignatures)
		throws InvalidTxBodyException {
	  return new FileFeeBuilder().getSystemDeleteFileTxFeeMatrices(txBody, numSignatures);
	}

	static FeeData getSystemUndeleteTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj numSignatures)
		throws InvalidTxBodyException {
	  return new FileFeeBuilder().getSystemUnDeleteFileTxFeeMatrices(txBody, numSignatures);
	}

	static FeeData getContractDeleteTransactionFeeMatrices(TransactionBody txBody,
			SigValueObj sigValObj)
		throws InvalidTxBodyException {
	  return new SmartContractFeeBuilder().getContractDeleteTxFeeMatrices(txBody, sigValObj);
	}

	static public FeeData getConsensusUpdateTopicTransactionFeeMatrices(
			TransactionBody txBody, SigValueObj sigValObj, FCMap<MerkleEntityId, MerkleTopic> topicFCMap) throws Exception {
		MerkleTopic merkleTopic = topicFCMap.get(MerkleEntityId.fromTopicId(txBody.getConsensusUpdateTopic().getTopicID()));
		long rbsIncrease = ConsensusServiceFeeBuilder.getUpdateTopicRbsIncrease(
				txBody.getTransactionID().getTransactionValidStart(),
				JKey.mapJKey(merkleTopic.getAdminKey()), JKey.mapJKey(merkleTopic.getSubmitKey()),
				merkleTopic.getMemo(), merkleTopic.hasAutoRenewAccountId(), getTopicExpirationTimeStamp(merkleTopic),
				txBody.getConsensusUpdateTopic());
		return ConsensusServiceFeeBuilder.getConsensusUpdateTopicFee(txBody, rbsIncrease, sigValObj);
	}

	public static FeeData computeUsageMetrics(
			TransactionBody txBody,
			FileServiceHandler fileHandler,
			FCMap<MerkleEntityId, MerkleAccount> accountFCMap,
			FCMap<MerkleEntityId, MerkleTopic> topicFCMap, SigValueObj sigValObj
	) {
		FeeData feeMatrices = FeeData.getDefaultInstance();
		try {
			if (txBody.hasCryptoTransfer()) {
				feeMatrices = getCryptoTransferTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasCryptoCreateAccount()) {
				feeMatrices = getCryptoCreateTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasCryptoDelete()) {
				feeMatrices = getCryptoDeleteTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasCryptoUpdateAccount()) {
				MerkleEntityId accountIDMerkleEntityId = MerkleEntityId.fromAccountId(txBody.getTransactionID().getAccountID());
				Timestamp expirationTimeStamp = FeeCalcUtils.lookupAccountExpiry(accountIDMerkleEntityId, accountFCMap);
				MerkleAccount account = accountFCMap.get(accountIDMerkleEntityId);
				Key existingKey = JKey.mapJKey(account.getKey());
				feeMatrices = getCryptoUpdateTransactionFeeMatrices(txBody, expirationTimeStamp, sigValObj, existingKey);
			} else if (txBody.hasContractCreateInstance()) {
				feeMatrices = getSmartContractCreateTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasContractCall()) {
				feeMatrices = getSmartContractCallTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasContractUpdateInstance()) {
				ContractID contractID = txBody.getContractUpdateInstance().getContractID();
				Timestamp expirationTimeStamp = FeeCalcUtils.lookupAccountExpiry(MerkleEntityId.fromContractId(contractID), accountFCMap);
				feeMatrices = getSmartContractUpdateTransactionFeeMatrices(txBody, expirationTimeStamp, sigValObj);
			} else if (txBody.hasFileCreate()) {
				feeMatrices = getFileCreateTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasFileAppend()) {
			    Timestamp expirationTimeStamp = getFileExpirationTimeStamp(fileHandler, txBody.getFileAppend().getFileID());
				feeMatrices = getFileAppendTransactionFeeMatrices(txBody, expirationTimeStamp, sigValObj);
			} else if (txBody.hasFileUpdate()) {
				Timestamp expirationTimeStamp = getFileExpirationTimeStamp(fileHandler, txBody.getFileUpdate().getFileID());
				feeMatrices = getFileUpdateTransactionFeeMatrices(txBody, expirationTimeStamp, sigValObj);
			} else if (txBody.hasFileDelete()) {
				feeMatrices = getFileDeleteTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasCryptoAddLiveHash()) {
				feeMatrices = getAddLiveHashTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasCryptoDeleteLiveHash()) {
				feeMatrices = getDeleteLiveHashTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasSystemDelete()) {
				feeMatrices = getSystemDeleteTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasSystemUndelete()) {
				feeMatrices = getSystemUndeleteTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasContractDeleteInstance()) {
				feeMatrices = getContractDeleteTransactionFeeMatrices(txBody, sigValObj);
			} else if (txBody.hasConsensusCreateTopic()) {
				feeMatrices = ConsensusServiceFeeBuilder.getConsensusCreateTopicFee(txBody, sigValObj);
			} else if (txBody.hasConsensusUpdateTopic()) {
				feeMatrices = getConsensusUpdateTopicTransactionFeeMatrices(txBody, sigValObj, topicFCMap);
			} else if (txBody.hasConsensusDeleteTopic()) {
				feeMatrices = ConsensusServiceFeeBuilder.getConsensusDeleteTopicFee(txBody, sigValObj);
			} else if (txBody.hasConsensusSubmitMessage()) {
				feeMatrices = ConsensusServiceFeeBuilder.getConsensusSubmitMessageFee(txBody, sigValObj);
			}
		} catch (Exception e) {
		}

		return feeMatrices;
	}

	public static Timestamp getTopicExpirationTimeStamp(MerkleTopic merkleTopic) {
		try {
			long expiration = merkleTopic.getExpirationTimestamp().getSeconds();
			return Timestamp.newBuilder().setSeconds(expiration).build();
		}catch(NullPointerException e) {
			// for invalid topic id, there wont be any expiration time, but network and node fee will be charged
			// In this case, the rbh charges will be zero, passing the expiration timestamp as zero, so rbh wont be charged
			return Timestamp.newBuilder().setSeconds(0).build();
		}
	}

	public static Timestamp getFileExpirationTimeStamp(
			FileServiceHandler fileHandler, FileID fileId) throws Exception{
		try {
			return fileHandler.getFileInfo(fileId).getExpirationTime();
		}catch(InvalidFileIDException e) {
			// for invalid FileID, there wont be any expiration time, but network and node fee will be charged
			// In this case, the sbh charges will be zero, passing the expiration timestamp as zero, so sbh wont be charged
			return Timestamp.newBuilder().setSeconds(0).build();
		}
	}
}
