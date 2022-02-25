package com.hedera.services.legacy.core;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeeClient {
	private static int FEE_DIVISOR_TOTINYBARS = 12000;
	private static ExchangeRate exchangeRate = ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(12).build();
	private static Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = new HashMap<>();

	private static final Logger log = LogManager.getLogger(FeeClient.class);

	public static void main(String args[]) {

		long minVal = Long.MIN_VALUE;
		long val = minVal + (-minVal);
		log.info("The addition " + val);

		getFeeScheduleMap();
	}

	public static void initialize(int hbarEquiv, int centEquiv, byte[] feeSchBytes) {
		FEE_DIVISOR_TOTINYBARS = 1000 * centEquiv / hbarEquiv;
		exchangeRate = ExchangeRate.newBuilder().setHbarEquiv(hbarEquiv).setCentEquiv(centEquiv)
				.build();

		try {
			CurrentAndNextFeeSchedule feeSch = CurrentAndNextFeeSchedule.parseFrom(feeSchBytes);
			List<TransactionFeeSchedule> transFeeSchList =
					feeSch.getCurrentFeeSchedule().getTransactionFeeScheduleList();
			feeSchMap = feeScheduleListToMap(transFeeSchList);
		} catch (InvalidProtocolBufferException ex) {
			System.out.print("ERROR: Exception while decoding Fee file");
		}
	}

	private static Map<HederaFunctionality, Map<SubType, FeeData>> feeScheduleListToMap(
			List<TransactionFeeSchedule> transFeeSchList) {
		for (TransactionFeeSchedule transSch : transFeeSchList) {
			feeSchMap.put(transSch.getHederaFunctionality(), FeesListToMap(transSch.getFeesList()));
		}
		return feeSchMap;
	}

	private static Map<SubType, FeeData> FeesListToMap(List<FeeData> feesList) {
		Map<SubType, FeeData> resultingMap = new HashMap<>();
		for (FeeData feeData : feesList) {
			resultingMap.put(feeData.getSubType(), feeData);
		}
		return resultingMap;
	}

	public static Map<HederaFunctionality, Map<SubType, FeeData>> getFeeScheduleMap() {
		try {
			File feeSchFile = new File("src/main/resource/feeSchedule.txt");
			InputStream fis = new FileInputStream(feeSchFile);
			byte[] fileBytes = new byte[(int) feeSchFile.length()];
			fis.read(fileBytes);
			CurrentAndNextFeeSchedule feeSch = CurrentAndNextFeeSchedule.parseFrom(fileBytes);
			List<TransactionFeeSchedule> transFeeSchList =
					feeSch.getCurrentFeeSchedule().getTransactionFeeScheduleList();
			for (TransactionFeeSchedule transSch : transFeeSchList) {
				feeSchMap.put(transSch.getHederaFunctionality(), FeesListToMap(transSch.getFeesList()));
			}
		} catch (Exception e) {
			log.info("Exception while reading Fee file: " + e.getMessage());
		}
		return feeSchMap;
	}

	public static long getFeeByID(HederaFunctionality hederaFunctionality) {
//		FeeBuilder crBuilder = new FeeBuilder();
//		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
//		Map<SubType, FeeData> feeData = feeSchMap.get(hederaFunctionality);
//		FeeData feeMatrices = crBuilder.getCostForQueryByIDOnly();
//		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
		return 10 * 100_000_000L;
	}


	public static long getCreateAccountFee(Transaction transaction, int payerAcctSigCount)
			throws Exception {
//		CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
//		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
//		Map<SubType, FeeData> feeData = feeSchMap.get(HederaFunctionality.CryptoCreate);
//		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
//		int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
//		int signatureSize = FeeBuilder.getSignatureSize(transaction);
//		SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
//				signatureSize);
//		FeeData feeMatrices = crBuilder.getCryptoCreateTxFeeMatrices(txBody, sigValueObj);
//		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
		return 10 * 100_000_000L;
	}


	public static long getCostForGettingTxRecord() {
//		CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
//		FeeData feeMatrices = crBuilder.getCostTransactionRecordQueryFeeMatrices();
//		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
//		Map<SubType, FeeData> feeData = feeSchMap.get(HederaFunctionality.TransactionGetRecord);
//		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
		return 10 * 100_000_000L;
	}

	public static long getCostForGettingAccountInfo() {
		CryptoFeeBuilder crBuilder = new CryptoFeeBuilder();
		FeeData feeMatrices = crBuilder.getCostCryptoAccountInfoQueryFeeMatrices();
		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
		Map<SubType, FeeData> feeData = feeSchMap.get(HederaFunctionality.CryptoGetInfo);
		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
	}

	public static long getCostContractCallLocalFee(int funcParamSize) {
//		SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
//		FeeData feeMatrices = crBuilder.getCostContractCallLocalFeeMatrices(funcParamSize);
//		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
//		Map<SubType, FeeData> feeData = feeSchMap.get(HederaFunctionality.ContractCallLocal);
//		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
		return 10 * 100_000_000L;
	}

	public static long getCostContractCallFee(Transaction transaction, int payerAcctSigCount)
			throws Exception {
//		SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
//		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
//		int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
//		int signatureSize = FeeBuilder.getSignatureSize(transaction);
//		SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
//				signatureSize);
//		FeeData feeMatrices = crBuilder.getContractCallTxFeeMatrices(txBody, sigValueObj);
//		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
//		Map<SubType, FeeData> feeData = feeSchMap.get(HederaFunctionality.ContractCall);
//		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
		return 100 * 100_000_000L;
	}


	public static long getContractCreateFee(Transaction transaction, int payerAcctSigCount)
			throws Exception {
//		SmartContractFeeBuilder crBuilder = new SmartContractFeeBuilder();
//		TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
//		int totalSignatureCount = FeeBuilder.getSignatureCount(transaction);
//		int signatureSize = FeeBuilder.getSignatureSize(transaction);
//		SigValueObj sigValueObj = new SigValueObj(totalSignatureCount, payerAcctSigCount,
//				signatureSize);
//		FeeData feeMatrices = crBuilder.getContractCreateTxFeeMatrices(txBody, sigValueObj);
//		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
//		Map<SubType, FeeData> feeData = feeSchMap.get(HederaFunctionality.ContractCreate);
//		return crBuilder.getTotalFeeforRequest(feeData.get(SubType.DEFAULT), feeMatrices, exchangeRate);
		return 100 * 100_000_000L;
	}

	public static long getMaxFee() {
		// currently all functionalities have same max fee so just taking CryptoCreate
		Map<HederaFunctionality, Map<SubType, FeeData>> feeSchMap = getFeeScheduleMap();
		Map<SubType, FeeData> feeDataMap = feeSchMap.get(HederaFunctionality.CryptoCreate);
		var defaultFeeData = feeDataMap.get(SubType.DEFAULT);
		return ((defaultFeeData.getNodedata().getMax() + defaultFeeData.getNetworkdata().getMax()
				+ defaultFeeData.getServicedata().getMax())) / FEE_DIVISOR_TOTINYBARS;
	}
}
