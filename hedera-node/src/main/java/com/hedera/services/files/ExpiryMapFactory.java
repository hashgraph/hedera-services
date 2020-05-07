package com.hedera.services.files;

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

import com.google.common.primitives.Longs;
import com.hedera.services.files.store.BytesStoreAdapter;
import com.hederahashgraph.api.proto.java.FileID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.regex.Pattern;

public class ExpiryMapFactory {
	private static final Logger log = LogManager.getLogger(ExpiryMapFactory.class);

	private static final String LEGACY_PATH_TEMPLATE = "/%d/e%d";
	private static final Pattern LEGACY_PATH_PATTERN = Pattern.compile("/(\\d+)/e(\\d+)");
	private static final int REALM_INDEX = 1;
	private static final int ACCOUNT_INDEX = 2;

	private  ExpiryMapFactory(){
		throw new IllegalStateException("Factory class");
	}

	public static Map<FileID, Long> expiryMapFrom(Map<String, byte[]> store) {
		return new BytesStoreAdapter<>(
				FileID.class,
				ExpiryMapFactory::toLong,
				Longs::toByteArray,
				ExpiryMapFactory::toFid,
				ExpiryMapFactory::toKeyString,
				store);
	}

	static FileID toFid(String key) {
		var matcher = LEGACY_PATH_PATTERN.matcher(key);
		var flag = matcher.matches();
		assert flag;

		return FileID.newBuilder()
				.setShardNum(0)
				.setRealmNum(Long.parseLong(matcher.group(REALM_INDEX)))
				.setFileNum(Long.parseLong(matcher.group(ACCOUNT_INDEX)))
				.build();
	}

	static Long toLong(byte[] bytes) {
		try {
			return (bytes == null) ? null : Longs.fromByteArray(bytes);
		} catch (Exception impossible) {
			log.warn("Expiry data not a serialized long!", impossible);
			throw new IllegalArgumentException(impossible);
		}
	}

	static String toKeyString(FileID fid) {
		return String.format(LEGACY_PATH_TEMPLATE, fid.getRealmNum(), fid.getFileNum());
	}
}
