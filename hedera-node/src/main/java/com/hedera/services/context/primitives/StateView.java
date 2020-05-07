package com.hedera.services.context.primitives;

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

import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Optional;

import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.legacy.core.jproto.JKey.mapJKey;
import static java.util.Collections.unmodifiableMap;

public class StateView {
	private static final Logger log = LogManager.getLogger(StateView.class);

	private static final byte[] EMPTY_CONTENTS = new byte[0];
	public static final JKey EMPTY_WACL = new JKeyList();

	public static final FCMap<MapKey, Topic> EMPTY_TOPICS =
			new FCMap<>(MapKey::deserialize, Topic::deserialize);
	public static final FCMap<MapKey, HederaAccount> EMPTY_ACCOUNTS =
			new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);
	public static final FCMap<StorageKey, StorageValue> EMPTY_STORAGE =
			new FCMap<>(StorageKey::deserialize, StorageValue::deserialize);
	public static final StateView EMPTY_VIEW = new StateView(EMPTY_TOPICS, EMPTY_ACCOUNTS);

	Map<FileID, byte[]> fileContents;
	Map<FileID, JFileInfo> fileAttrs;
	private final FCMap<MapKey, Topic> topics;
	private final FCMap<MapKey, HederaAccount> accounts;

	public StateView(
			FCMap<MapKey, Topic> topics,
			FCMap<MapKey, HederaAccount> accounts
	) {
		this(topics, accounts, EMPTY_STORAGE);
	}

	public StateView(
			FCMap<MapKey, Topic> topics,
			FCMap<MapKey, HederaAccount> accounts,
			FCMap<StorageKey, StorageValue> storage
	) {
		this.topics = topics;
		this.accounts = accounts;

		Map<String, byte[]> blobStore = unmodifiableMap(new FcBlobsBytesStore(StorageValue::new, storage));
		fileContents = DataMapFactory.dataMapFrom(blobStore);
		fileAttrs = MetadataMapFactory.metaMapFrom(blobStore);
	}

	public Optional<JFileInfo> attrOf(FileID id) {
		return Optional.ofNullable(fileAttrs.get(id));
	}

	public Optional<byte[]> contentsOf(FileID id) {
		return Optional.ofNullable(fileContents.get(id));
	}

	public Optional<FileGetInfoResponse.FileInfo> infoFor(FileID id) {
		try {
			var attr = fileAttrs.get(id);
			if (attr == null) {
				return Optional.empty();
			}

			var info = FileGetInfoResponse.FileInfo.newBuilder()
					.setFileID(id)
					.setDeleted(attr.isDeleted())
					.setExpirationTime(Timestamp.newBuilder().setSeconds(attr.getExpirationTimeSeconds()))
					.setSize(Optional.ofNullable(fileContents.get(id)).orElse(EMPTY_CONTENTS).length);
			if (!attr.getWacl().isEmpty()) {
				info.setKeys(mapJKey(attr.getWacl()).getKeyList());
			}
			return Optional.of(info.build());
		} catch (Exception unknown) {
			log.warn("Unexpected problem getting info for {}", readableId(id), unknown);
			return Optional.empty();
		}
	}

	public FCMap<MapKey, Topic> topics() {
		return topics;
	}

	public FCMap<MapKey, HederaAccount> accounts() {
		return accounts;
	}
}
