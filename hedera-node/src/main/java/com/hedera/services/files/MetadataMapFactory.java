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

import com.hedera.services.files.store.BytesStoreAdapter;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.regex.Pattern;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

public class MetadataMapFactory {
	private static final Logger log = LogManager.getLogger(MetadataMapFactory.class);

	private static final String LEGACY_PATH_TEMPLATE = "/%d/k%d";
	private static final Pattern LEGACY_PATH_PATTERN = Pattern.compile("/(\\d+)/k(\\d+)");
	private static final int REALM_INDEX = 1;
	private static final int ACCOUNT_INDEX = 2;

	MetadataMapFactory(){
		throw new IllegalStateException();
	}

	public static Map<FileID, JFileInfo> metaMapFrom(Map<String, byte[]> store) {
		return new BytesStoreAdapter<>(
				FileID.class,
				MetadataMapFactory::toAttr,
				MetadataMapFactory::toValueBytes,
				MetadataMapFactory::toFid,
				MetadataMapFactory::toKeyString,
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

	static String toKeyString(FileID fid) {
		return String.format(LEGACY_PATH_TEMPLATE, fid.getRealmNum(), fid.getFileNum());
	}

	static JFileInfo toAttr(byte[] bytes) {
		try {
			return (bytes == null) ? null : JFileInfo.deserialize(bytes);
		} catch (Exception internal) {
			log.warn("Argument 'bytes={}' was not a serialized JFileInfo!", encodeHexString(bytes));
			throw new IllegalArgumentException(internal);
		}
	}

	static byte[] toValueBytes(JFileInfo attr) {
		try {
			return attr.serialize();
		} catch (Exception internal) {
			try {
				log.warn("Argument 'attr={}' could not be serialized!", attr);
			} catch (Exception terminal) {
				log.warn("Argument 'attr' could not be serialized, nor represented as a string!", terminal);
			}
			throw new IllegalArgumentException(internal);
		}
	}
}
