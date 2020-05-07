package com.hedera.services.fees.calculation;

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

import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.context.primitives.StateView;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.MapKey;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.legacy.logic.ApplicationConstants.ARTIFACTS_PREFIX_FILE_CONTENT;
import static com.hedera.services.legacy.logic.ApplicationConstants.ARTIFACTS_PREFIX_FILE_INFO;
import static com.hedera.services.legacy.logic.ApplicationConstants.LEDGER_PATH;
import static com.hedera.services.legacy.logic.ApplicationConstants.buildPath;

/**
 * Provides various helpers useful for estimating resource usage
 * while calculating fees for transactions or queries.
 *
 * @author Michael Tinker
 */
public class FeeCalcUtils {
	private static final Logger log = LogManager.getLogger(FeeCalcUtils.class);

	public static final Timestamp ZERO_EXPIRY = Timestamp.newBuilder().setSeconds(0).build();

	private FeeCalcUtils(){
		throw new IllegalStateException("Utility class");
	}

	public static Timestamp lookupAccountExpiry(MapKey key, FCMap<MapKey, HederaAccount> accounts) {
		try {
			HederaAccount account = accounts.get(key);
			long expiration = account.getExpirationTime();
			return Timestamp.newBuilder().setSeconds(expiration).build();
		} catch (Exception e) {
			log.debug("Ignoring expiry in fee calculation for {}", key, e);
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

	public static String pathOf(FileID fid) {
		return path(ARTIFACTS_PREFIX_FILE_CONTENT, fid);
	}

	public static String pathOfMeta(FileID fid) {
		return path(ARTIFACTS_PREFIX_FILE_INFO, fid);
	}

	private static String path(String buildMarker, FileID fid) {
		return String.format(
				"%s%s%d",
				buildPath(LEDGER_PATH, "" + fid.getRealmNum()),
				buildMarker,
				fid.getFileNum());
	}
}
