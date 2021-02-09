package com.hedera.services.bdd.suites.utils.sysfiles;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Provides data binding from the fee schedules JSON created by
 * the Hedera product team, to the {@link CurrentAndNextFeeSchedule}
 * protobuf type.
 *
 * @author Michael Tinker
 */
@SuppressWarnings("unchecked")
public class FeeScheduleDeJson {
	private static String[] FEE_SCHEDULE_KEYS = { "currentFeeSchedule", "nextFeeSchedule" };
	private static final String EXPIRY_TIME_KEY = "expiryTime";
	private static final String TXN_FEE_SCHEDULE_KEY = "transactionFeeSchedule";
	private static final String FEE_DATA_KEY = "feeData";
	private static final String HEDERA_FUNCTION_KEY = "hederaFunctionality";
	private static final String[] FEE_COMPONENT_KEYS = { "nodedata", "networkdata", "servicedata" };
	private static final String[] RESOURCE_KEYS =
			{ "constant", "bpt", "vpt", "rbh", "sbh", "gas", "bpr", "sbpr", "min", "max" };

	public static CurrentAndNextFeeSchedule fromJsonLiteral(String stylized) throws Exception {
		var om = new ObjectMapper();
		List<Map<String, Object>> rawFeeSchedules = (List<Map<String, Object>>)om.readValue(stylized, List.class);
		return fromMapList(rawFeeSchedules);
	}

	public static CurrentAndNextFeeSchedule fromJson(String jsonLoc) throws Exception {
		List<Map<String, Object>> rawFeeSchedules = asMapList(jsonLoc);
		return fromMapList(rawFeeSchedules);
	}

	private static CurrentAndNextFeeSchedule fromMapList(List<Map<String, Object>> mapList) throws Exception {
		var feeSchedules = CurrentAndNextFeeSchedule.newBuilder();
		int i = 0;
		for (String rawFeeSchedule : FEE_SCHEDULE_KEYS) {
			set(
					CurrentAndNextFeeSchedule.Builder.class,
					feeSchedules,
					rawFeeSchedule,
					FeeSchedule.class,
					bindFeeScheduleFrom((List<Map<String, Object>>)mapList.get(i++).get(rawFeeSchedule)));
		}
		return feeSchedules.build();
	}

	static List<Map<String, Object>> asMapList(String jsonLoc) {
		ObjectMapper om = new ObjectMapper();
		try (InputStream in = Files.newInputStream(Paths.get(jsonLoc))) {
			return (List<Map<String, Object>>)om.readValue(in, List.class);
		} catch (Exception e) {
			throw new IllegalStateException(String.format("Cannot load fee schedules '%s'!", jsonLoc), e);
		}
	}

	static FeeSchedule bindFeeScheduleFrom(List<Map<String, Object>> rawFeeSchedule) throws Exception {
		FeeSchedule.Builder feeSchedule = FeeSchedule.newBuilder();

		for (Map<String, Object> part : rawFeeSchedule) {
			if (part.containsKey(EXPIRY_TIME_KEY)) {
				long expiry = Long.parseLong(part.get(EXPIRY_TIME_KEY) + "");
				feeSchedule.setExpiryTime(TimestampSeconds.newBuilder().setSeconds(expiry));
			} else {
				feeSchedule.addTransactionFeeSchedule(bindTxnFeeScheduleFrom(
						(Map<String, Object>)part.get(TXN_FEE_SCHEDULE_KEY)));
			}
		}

		return feeSchedule.build();
	}

	static TransactionFeeSchedule bindTxnFeeScheduleFrom(Map<String, Object> rawTxnFeeSchedule) throws Exception {
		TransactionFeeSchedule.Builder txnFeeSchedule = TransactionFeeSchedule.newBuilder();
		var key = translateClaimFunction((String)rawTxnFeeSchedule.get(HEDERA_FUNCTION_KEY));
		txnFeeSchedule.setHederaFunctionality(HederaFunctionality.valueOf(key));
		txnFeeSchedule.setFeeData(bindFeeDataFrom((Map<String, Object>)rawTxnFeeSchedule.get(FEE_DATA_KEY)));
		return txnFeeSchedule.build();
	}

	private static String translateClaimFunction(String key) {
		if (key.equals("CryptoAddClaim")) {
			return "CryptoAddLiveHash";
		} else if (key.equals("CryptoDeleteClaim")) {
			return "CryptoDeleteLiveHash";
		} else if (key.equals("CryptoGetClaim")) {
			return "CryptoGetLiveHash";
		} else {
			return key;
		}
	}

	static FeeData bindFeeDataFrom(Map<String, Object> rawFeeData) throws Exception {
		FeeData.Builder feeData = FeeData.newBuilder();

		for (String feeComponent : FEE_COMPONENT_KEYS) {
			set(
					FeeData.Builder.class,
					feeData,
					feeComponent,
					FeeComponents.class,
					bindFeeComponentsFrom((Map<String, Object>)rawFeeData.get(feeComponent)));
		}

		return feeData.build();
	}

	static FeeComponents bindFeeComponentsFrom(Map<String, Object> rawFeeComponents) throws Exception {
		FeeComponents.Builder feeComponents = FeeComponents.newBuilder();
		for (String resource : RESOURCE_KEYS) {
			set(
					FeeComponents.Builder.class,
					feeComponents,
					resource,
					long.class,
					Long.parseLong(rawFeeComponents.get(resource) + ""));
		}
		return feeComponents.build();
	}

	static <R, T> void set(Class<R> builderType, R builder, String property, Class<T> valueType, T value) throws Exception {
		Method setter = builderType.getDeclaredMethod(setterName(property), valueType);
		setter.invoke(builder, value);
	}

	static String setterName(String property) {
		return "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
	}
}
