package com.hedera.services.fees.calculation;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides various helpers useful for estimating resource usage
 * while calculating fees for transactions or queries.
 *
 * @author Michael Tinker
 */
public class FeeCalcUtils {
	private static final Logger log = LogManager.getLogger(FeeCalcUtils.class);

	public static final Timestamp ZERO_EXPIRY = Timestamp.newBuilder().setSeconds(0).build();

	private FeeCalcUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static Timestamp lookupAccountExpiry(MerkleEntityId key, FCMap<MerkleEntityId, MerkleAccount> accounts) {
		try {
			MerkleAccount account = accounts.get(key);
			long expiration = account.getExpiry();
			return Timestamp.newBuilder().setSeconds(expiration).build();
		} catch (Exception ignore) {
			log.debug("Ignoring expiry in fee calculation for {}", key);
			return ZERO_EXPIRY;
		}
	}

	private static Timestamp asTimestamp(long expiry) {
		return Timestamp.newBuilder().setSeconds(expiry).build();
	}

	public static Timestamp lookupFileExpiry(FileID fid, StateView view) {
		return view.attrOf(fid)
				.map(info -> asTimestamp(info.getExpirationTimeSeconds()))
				.orElse(ZERO_EXPIRY);
	}

	public static FeeData sumOfUsages(FeeData a, FeeData b) {
		return FeeData.newBuilder()
				.setNodedata(sumOfScopedUsages(a.getNodedata(), b.getNodedata()))
				.setNetworkdata(sumOfScopedUsages(a.getNetworkdata(), b.getNetworkdata()))
				.setServicedata(sumOfScopedUsages(a.getServicedata(), b.getServicedata()))
				.build();
	}

	private static FeeComponents sumOfScopedUsages(FeeComponents a, FeeComponents b) {
		return FeeComponents.newBuilder()
				.setMin(Math.min(a.getMin(), b.getMin()))
				.setMax(Math.max(a.getMax(), b.getMax()))
				.setConstant(a.getConstant() + b.getConstant())
				.setBpt(a.getBpt() + b.getBpt())
				.setVpt(a.getVpt() + b.getVpt())
				.setRbh(a.getRbh() + b.getRbh())
				.setSbh(a.getSbh() + b.getSbh())
				.setGas(a.getGas() + b.getGas())
				.setTv(a.getTv() + b.getTv())
				.setBpr(a.getBpr() + b.getBpr())
				.setSbpr(a.getSbpr() + b.getSbpr())
				.build();
	}
}
