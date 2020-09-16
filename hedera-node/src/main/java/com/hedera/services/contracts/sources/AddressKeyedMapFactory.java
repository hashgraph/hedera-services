package com.hedera.services.contracts.sources;

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

import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;
import static java.lang.Long.parseLong;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AddressKeyedMapFactory {
	static final String LEGACY_BYTECODE_PATH_TEMPLATE = "/%d/s%d";
	static final Pattern LEGACY_BYTECODE_PATH_PATTERN = Pattern.compile("/(\\d+)/s(\\d+)");
	static final String LEGACY_STORAGE_PATH_TEMPLATE = "/%d/d%d";
	static final Pattern LEGACY_STORAGE_PATH_PATTERN = Pattern.compile("/(\\d+)/d(\\d+)");

	public static Map<byte[], byte[]> bytecodeMapFrom(Map<String, byte[]> store) {
		var storageMap = new BytesStoreAdapter<>(
				byte[].class,
				Function.identity(),
				Function.identity(),
				toAddressMapping(LEGACY_BYTECODE_PATH_PATTERN),
				toKeyMapping(LEGACY_BYTECODE_PATH_TEMPLATE),
				store);
		storageMap.setDelegateEntryFilter(toRelevancyPredicate(LEGACY_BYTECODE_PATH_PATTERN));
		return storageMap;
	}

	public static Map<byte[], byte[]> storageMapFrom(Map<String, byte[]> store) {
		var storageMap = new BytesStoreAdapter<>(
				byte[].class,
				Function.identity(),
				Function.identity(),
				toAddressMapping(LEGACY_STORAGE_PATH_PATTERN),
				toKeyMapping(LEGACY_STORAGE_PATH_TEMPLATE),
				store);
		storageMap.setDelegateEntryFilter(toRelevancyPredicate(LEGACY_STORAGE_PATH_PATTERN));
		return storageMap;
	}

	static Predicate<String> toRelevancyPredicate(final Pattern legacyPathPattern) {
		return key -> legacyPathPattern.matcher(key).matches();
	}

	static Function<byte[], String> toKeyMapping(final String legacyPathTemplate) {
		return address -> {
			var id = accountParsedFromSolidityAddress(address);
			return String.format(legacyPathTemplate, id.getRealmNum(), id.getAccountNum());
		};
	}

	static Function<String, byte[]> toAddressMapping(final Pattern legacyPathPattern) {
		return key -> {
			var matcher = legacyPathPattern.matcher(key);
			var flag = matcher.matches();
			assert flag;

			return asSolidityAddress(0, parseLong(matcher.group(1)), parseLong(matcher.group(2)));
		};
	}
}
