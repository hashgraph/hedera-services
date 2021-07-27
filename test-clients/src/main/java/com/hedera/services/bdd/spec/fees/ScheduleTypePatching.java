package com.hedera.services.bdd.spec.fees;

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

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

public class ScheduleTypePatching {
	private static final EnumSet<SubType> ONLY_DEFAULT = EnumSet.of(DEFAULT);
	static final Map<HederaFunctionality, EnumSet<SubType>> FUNCTIONS_WITH_REQUIRED_SUBTYPES;
	static {
		FUNCTIONS_WITH_REQUIRED_SUBTYPES = new EnumMap<>(HederaFunctionality.class);
		/* The functions with non-DEFAULT prices in hapi-fees/src/main/resources/canonical-prices.json */
		List.of(TokenMint, TokenBurn, TokenAccountWipe).forEach(function ->
				FUNCTIONS_WITH_REQUIRED_SUBTYPES.put(function, EnumSet.of(
						TOKEN_FUNGIBLE_COMMON, TOKEN_NON_FUNGIBLE_UNIQUE)));
		FUNCTIONS_WITH_REQUIRED_SUBTYPES.put(TokenCreate, EnumSet.of(
				TOKEN_FUNGIBLE_COMMON, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
				TOKEN_NON_FUNGIBLE_UNIQUE, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES));
		FUNCTIONS_WITH_REQUIRED_SUBTYPES.put(CryptoTransfer, EnumSet.of(
				DEFAULT,
				TOKEN_FUNGIBLE_COMMON, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES,
				TOKEN_NON_FUNGIBLE_UNIQUE, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES));
	}

	public FeeSchedule withPatchedTypesIfNecessary(FeeSchedule possiblyUntypedSchedule) {
		final var usableSchedule = FeeSchedule.newBuilder();
		for (var tfs : possiblyUntypedSchedule.getTransactionFeeScheduleList()) {
			if (tfs.hasFeeData()) {
				final var usableTfs = TransactionFeeSchedule.newBuilder();
				final var fn = tfs.getHederaFunctionality();
				usableTfs.setHederaFunctionality(fn);
				final EnumSet<SubType> requiredTypes = FUNCTIONS_WITH_REQUIRED_SUBTYPES.getOrDefault(fn, ONLY_DEFAULT);
				ensurePatchedFeeScheduleHasRequiredTypes(tfs, usableTfs, requiredTypes);
				usableSchedule.addTransactionFeeSchedule(usableTfs);
			} else {
				/* Must have been >= release 0.16.0 fee schedule */
				return possiblyUntypedSchedule;
			}
		}
		return usableSchedule.build();
	}

	private void ensurePatchedFeeScheduleHasRequiredTypes(
			TransactionFeeSchedule origTfs,
			TransactionFeeSchedule.Builder patchedTfs,
			EnumSet<SubType> requiredTypes
	) {
		/* The deprecated prices are the final fallback; if even they are not set, the function will be free */
		final var oldDefaultPrices = origTfs.getFeeData();
		FeeData explicitDefaultPrices = null;

		/* First determine what types are already present; and what default prices to use, if any */
		final List<SubType> listedTypes = new ArrayList<>();
		for (var typedPrices : origTfs.getFeesList()) {
			final var type = typedPrices.getSubType();
			listedTypes.add(type);
			if (type == DEFAULT) {
				explicitDefaultPrices = typedPrices;
			}
		}

		final Set<SubType> presentTypes = EnumSet.copyOf(listedTypes);
		for (var type : requiredTypes) {
			if (!presentTypes.contains(type)) {
				if (explicitDefaultPrices != null) {
					patchedTfs.addFees(explicitDefaultPrices.toBuilder().setSubType(type).build());
				} else {
					patchedTfs.addFees(oldDefaultPrices.toBuilder().setSubType(type).build());
				}
			}
		}
	}
}
