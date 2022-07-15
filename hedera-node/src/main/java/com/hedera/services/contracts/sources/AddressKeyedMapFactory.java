/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.contracts.sources;

import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static java.lang.Long.parseLong;

import com.hedera.services.files.store.BytesStoreAdapter;
import com.hedera.services.utils.EntityIdUtils;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class AddressKeyedMapFactory {
    static final String LEGACY_BYTECODE_PATH_TEMPLATE = "/%d/s%d";
    public static final Pattern LEGACY_BYTECODE_PATH_PATTERN = Pattern.compile("/(\\d+)/s(\\d+)");
    private static final String LEGACY_STORAGE_PATH_TEMPLATE = "/%d/d%d";
    private static final Pattern LEGACY_STORAGE_PATH_PATTERN = Pattern.compile("/(\\d+)/d(\\d+)");

    private AddressKeyedMapFactory() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static Map<byte[], byte[]> bytecodeMapFrom(final Map<String, byte[]> store) {
        return mapFrom(store, LEGACY_BYTECODE_PATH_PATTERN, LEGACY_BYTECODE_PATH_TEMPLATE);
    }

    public static Map<byte[], byte[]> storageMapFrom(final Map<String, byte[]> store) {
        return mapFrom(store, LEGACY_STORAGE_PATH_PATTERN, LEGACY_STORAGE_PATH_TEMPLATE);
    }

    private static Map<byte[], byte[]> mapFrom(
            final Map<String, byte[]> store,
            final Pattern legacyPathPattern,
            final String legacyPathTemplate) {
        final var storageMap =
                new BytesStoreAdapter<>(
                        byte[].class,
                        Function.identity(),
                        Function.identity(),
                        toAddressMapping(legacyPathPattern),
                        toKeyMapping(legacyPathTemplate),
                        store);
        storageMap.setDelegateEntryFilter(toRelevancyPredicate(legacyPathPattern));
        return storageMap;
    }

    static Predicate<String> toRelevancyPredicate(final Pattern legacyPathPattern) {
        return key -> legacyPathPattern.matcher(key).matches();
    }

    static Function<byte[], String> toKeyMapping(final String legacyPathTemplate) {
        return address -> {
            final var id = EntityIdUtils.accountIdFromEvmAddress(address);
            return String.format(legacyPathTemplate, id.getRealmNum(), id.getAccountNum());
        };
    }

    static Function<String, byte[]> toAddressMapping(final Pattern legacyPathPattern) {
        return key -> {
            final var matcher = legacyPathPattern.matcher(key);
            assert matcher.matches();

            return asEvmAddress(0, parseLong(matcher.group(1)), parseLong(matcher.group(2)));
        };
    }
}
