package com.hedera.services.sysfiles.serdes;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Provides data binding from the fee schedules JSON created by the Hedera product team,
 * to the {@link CurrentAndNextFeeSchedule} protobuf type.
 */
@SuppressWarnings("unchecked")
public class FeesJsonToProtoSerde {
	private static final String[] FEE_SCHEDULE_KEYS = { "currentFeeSchedule", "nextFeeSchedule" };
	private static final String EXPIRY_TIME_KEY = "expiryTime";
	private static final String TXN_FEE_SCHEDULE_KEY = "transactionFeeSchedule";
	private static final String FEE_DATA_KEY = "fees";
	private static final String HEDERA_FUNCTION_KEY = "hederaFunctionality";
	private static final String[] FEE_COMPONENT_KEYS = { "nodedata", "networkdata", "servicedata" };
	private static final String[] RESOURCE_KEYS =
			{ "constant", "bpt", "vpt", "rbh", "sbh", "gas", "bpr", "sbpr", "min", "max" };

	private FeesJsonToProtoSerde() {
		throw new UnsupportedOperationException("Static utilities class, should not be instantiated");
	}

	public static CurrentAndNextFeeSchedule loadFeeScheduleFromJson(String jsonResource) throws Exception {
		return buildFrom(om -> om.readValue(
				FeesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(jsonResource),
				List.class));
	}

	public static CurrentAndNextFeeSchedule loadFeeScheduleFromStream(InputStream in) throws Exception {
		return buildFrom(om -> om.readValue(in, List.class));
	}

	public static CurrentAndNextFeeSchedule parseFeeScheduleFromJson(String literal) throws Exception {
		return buildFrom(om -> om.readValue(literal, List.class));
	}

	private static CurrentAndNextFeeSchedule buildFrom(ThrowingReader reader) throws Exception {
		final var feeSchedules = CurrentAndNextFeeSchedule.newBuilder();

		final var om = new ObjectMapper();
		final var rawFeeSchedules = reader.readValueWith(om);
		constructWithBuilder(feeSchedules, rawFeeSchedules);

		return feeSchedules.build();
	}

	interface ThrowingReader {
		List<Map<String, Object>> readValueWith(ObjectMapper om) throws Exception;
	}

	private static void constructWithBuilder(
			CurrentAndNextFeeSchedule.Builder builder,
			List<Map<String, Object>> rawFeeSchedules
	) throws Exception {
		int i = 0;
		for (String rawFeeSchedule : FEE_SCHEDULE_KEYS) {
			set(
					CurrentAndNextFeeSchedule.Builder.class,
					builder,
					rawFeeSchedule,
					FeeSchedule.class,
					bindFeeScheduleFrom((List<Map<String, Object>>) rawFeeSchedules.get(i++).get(rawFeeSchedule)));
		}
	}

	private static FeeSchedule bindFeeScheduleFrom(List<Map<String, Object>> rawFeeSchedule) throws Exception {
		FeeSchedule.Builder feeSchedule = FeeSchedule.newBuilder();

		for (Map<String, Object> part : rawFeeSchedule) {
			if (part.containsKey(EXPIRY_TIME_KEY)) {
				long expiry = Long.parseLong(part.get(EXPIRY_TIME_KEY) + "");
				feeSchedule.setExpiryTime(TimestampSeconds.newBuilder().setSeconds(expiry));
			} else {
				feeSchedule.addTransactionFeeSchedule(bindTxnFeeScheduleFrom(
						(Map<String, Object>) part.get(TXN_FEE_SCHEDULE_KEY)));
			}
		}

		return feeSchedule.build();
	}

	private static TransactionFeeSchedule bindTxnFeeScheduleFrom(
			Map<String, Object> rawTxnFeeSchedule
	) throws Exception {
		final var txnFeeSchedule = TransactionFeeSchedule.newBuilder();
		var key = translateClaimFunction((String) rawTxnFeeSchedule.get(HEDERA_FUNCTION_KEY));
		txnFeeSchedule.setHederaFunctionality(HederaFunctionality.valueOf(key));
		var feesList = (List<Object>) rawTxnFeeSchedule.get(FEE_DATA_KEY);

		for (Object o : feesList) {
			txnFeeSchedule.addFees(bindFeeDataFrom((Map<String, Object>) o));
		}

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

	private static FeeData bindFeeDataFrom(Map<String, Object> rawFeeData) throws Exception {
		FeeData.Builder feeData = FeeData.newBuilder();

		if (rawFeeData.get("subType") == null) {
			feeData.setSubType(SubType.DEFAULT);
		} else {
			feeData.setSubType(stringToSubType((String) rawFeeData.get("subType")));
		}

		for (String feeComponent : FEE_COMPONENT_KEYS) {
			set(
					FeeData.Builder.class,
					feeData,
					feeComponent,
					FeeComponents.class,
					bindFeeComponentsFrom((Map<String, Object>) rawFeeData.get(feeComponent)));
		}

		return feeData.build();
	}

	static SubType stringToSubType(String subType) {
		switch (subType) {
			case "TOKEN_FUNGIBLE_COMMON":
				return SubType.TOKEN_FUNGIBLE_COMMON;
			case "TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES":
				return SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
			case "TOKEN_NON_FUNGIBLE_UNIQUE":
				return SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
			case "TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES":
				return SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
			case "SCHEDULE_CREATE_CONTRACT_CALL":
				return SubType.SCHEDULE_CREATE_CONTRACT_CALL;
			default:
				return SubType.DEFAULT;
		}
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

	static <R, T> void set(Class<R> builderType, R builder, String property, Class<T> valueType,
			T value) throws Exception {
		Method setter = builderType.getDeclaredMethod(setterName(property), valueType);
		setter.invoke(builder, value);
	}

	static String setterName(String property) {
		return "set" + property.substring(0, 1).toUpperCase() + property.substring(1);
	}
}
