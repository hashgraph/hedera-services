package com.hedera.services.context;

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
import com.hedera.services.legacy.services.context.primitives.ExchangeRateSetWrapper;
import com.hedera.services.legacy.services.context.primitives.SequenceNumber;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.swirlds.common.AddressBook;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.fcmap.FCMap;

import java.io.IOException;
import java.time.Instant;

import static com.swirlds.platform.Utilities.readInstant;
import static com.swirlds.platform.Utilities.writeInstant;

/**
 * Container that implements a deterministic serde for the collection
 * of objects that make up the saved state for Hedera Services.
 *
 * @author Michael Tinker
 */
public class PrimitiveContext {
	static final long LEGACY_VERSION = 4;
	static final long CURRENT_VERSION = 5;
	static final long VERSION_WITH_EXCHANGE_RATE = 4;
	static final long FIRST_VERSION_WITH_TOPICS = 5;

	long versionAtStateInit;
	Instant consensusTimeOfLastHandledTxn;
	AddressBook addressBook;
	SequenceNumber seqNo;
	ExchangeRateSetWrapper midnightRateSet;
	FCMap<MapKey, HederaAccount> accounts;
	FCMap<StorageKey, StorageValue> storage;
	FCMap<MapKey, Topic> topics;

	public FCMap<StorageKey, StorageValue> getStorage() {
		return storage;
	}

	public FCMap<MapKey, HederaAccount> getAccounts() {
		return accounts;
	}

	public FCMap<MapKey, Topic> getTopics() {
		return topics;
	}

	public AddressBook getAddressBook() {
		return addressBook;
	}

	public PrimitiveContext(AddressBook addressBook) {
		this(
				0L,
				null,
				addressBook,
				new SequenceNumber(ApplicationConstants.HEDERA_START_SEQUENCE),
				new ExchangeRateSetWrapper(),
				new FCMap<>(MapKey::deserialize, HederaAccount::deserialize),
				new FCMap<>(StorageKey::deserialize, StorageValue::deserialize),
				new FCMap<>(MapKey::deserialize, Topic::deserialize));
	}

	public PrimitiveContext(
			long versionAtStateInit,
			AddressBook addressBook,
			SequenceNumber seqNo,
			FCMap<MapKey, HederaAccount> accounts,
			FCMap<StorageKey, StorageValue> storage,
			FCMap<MapKey, Topic> topics
	) {
		this(
				versionAtStateInit,
				null,
				addressBook,
				seqNo,
				new ExchangeRateSetWrapper(),
				accounts,
				storage,
				topics);
	}

	public PrimitiveContext(PrimitiveContext ctx) {
		this(
				ctx.versionAtStateInit,
				ctx.consensusTimeOfLastHandledTxn,
				ctx.addressBook.copy(),
				ctx.seqNo.copy(),
				ctx.midnightRateSet.copy(),
				ctx.accounts.copy(),
				ctx.storage.copy(),
				ctx.topics.copy());
	}

	public PrimitiveContext(
			long versionAtStateInit,
			Instant consensusTimeOfLastHandledTxn,
			AddressBook addressBook,
			SequenceNumber seqNo,
			ExchangeRateSetWrapper midnightRateSet,
			FCMap<MapKey, HederaAccount> accounts,
			FCMap<StorageKey, StorageValue> storage,
			FCMap<MapKey, Topic> topics) {
		this.versionAtStateInit = versionAtStateInit;
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
		this.addressBook = addressBook;
		this.seqNo = seqNo;
		this.midnightRateSet = midnightRateSet;
		this.accounts = accounts;
		this.storage = storage;
		this.topics = topics;
	}

	public void copyTo(FCDataOutputStream outputStream) throws IOException {
		outputStream.writeLong(CURRENT_VERSION);
		seqNo.copyTo(outputStream);
		addressBook.copyTo(outputStream);
		accounts.copyTo(outputStream);
		storage.copyTo(outputStream);
		outputStream.writeBoolean(true);
		midnightRateSet.copyTo(outputStream);
		if (consensusTimeOfLastHandledTxn == null) {
			outputStream.writeBoolean(false);
		} else {
			outputStream.writeBoolean(true);
			writeInstant(outputStream, consensusTimeOfLastHandledTxn);
		}
		topics.copyTo(outputStream);
	}

	public void copyToExtra(FCDataOutputStream outputStream) throws IOException {
		outputStream.writeLong(CURRENT_VERSION);
		seqNo.copyToExtra(outputStream);
		addressBook.copyToExtra(outputStream);
		accounts.copyToExtra(outputStream);
		storage.copyToExtra(outputStream);
		topics.copyToExtra(outputStream);
	}

	public void copyFrom(FCDataInputStream inputStream) throws IOException {
		versionAtStateInit = inputStream.readLong();
		seqNo.copyFrom(inputStream);
		AddressBook tmp = new AddressBook();
		tmp.copyFrom(inputStream);
		accounts.copyFrom(inputStream);
		storage.copyFrom(inputStream);
		if (versionAtStateInit >= VERSION_WITH_EXCHANGE_RATE) {
			if (inputStream.readBoolean()) {
				midnightRateSet.copyFrom(inputStream);
			}
			if (inputStream.readBoolean()) {
				consensusTimeOfLastHandledTxn = readInstant(inputStream);
			}
		} else {
			midnightRateSet = new ExchangeRateSetWrapper();
			consensusTimeOfLastHandledTxn = null;
		}
		if (versionAtStateInit >= FIRST_VERSION_WITH_TOPICS) {
			topics.copyFrom(inputStream);
		} else {
			topics.clear();
		}
	}

	public void copyFromExtra(FCDataInputStream inputStream) throws IOException {
		long version = inputStream.readLong();
		seqNo.copyFromExtra(inputStream);
		AddressBook tmp = new AddressBook();
		tmp.copyFromExtra(inputStream);
		if (version == LEGACY_VERSION) {
			accounts.disableCopyCheck();
			storage.disableCopyCheck();
		}
		accounts.copyFromExtra(inputStream);
		storage.copyFromExtra(inputStream);
		if (version == LEGACY_VERSION) {
			accounts.enableCopyCheck();
			storage.enableCopyCheck();
		}
		if (version >= FIRST_VERSION_WITH_TOPICS) {
			topics.copyFromExtra(inputStream);
		} else {
			topics.clear();
		}
	}
}
