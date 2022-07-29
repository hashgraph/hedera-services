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
package com.hedera.services.files;

import com.google.common.primitives.Longs;
import com.hedera.services.files.store.BytesStoreAdapter;
import com.hedera.services.state.submerkle.EntityId;
import java.util.Map;
import java.util.regex.Pattern;

public final class EntityExpiryMapFactory {
    private static final String LEGACY_PATH_TEMPLATE = "/%d/e%d";
    public static final Pattern LEGACY_PATH_PATTERN = Pattern.compile("/(\\d+)/e(\\d+)");
    private static final int REALM_INDEX = 1;
    private static final int NUM_INDEX = 2;

    EntityExpiryMapFactory() {
        throw new IllegalStateException();
    }

    public static Map<EntityId, Long> entityExpiryMapFrom(Map<String, byte[]> store) {
        return new BytesStoreAdapter<>(
                EntityId.class,
                EntityExpiryMapFactory::toLong,
                Longs::toByteArray,
                EntityExpiryMapFactory::toEid,
                EntityExpiryMapFactory::toKeyString,
                store);
    }

    static EntityId toEid(String key) {
        var matcher = LEGACY_PATH_PATTERN.matcher(key);
        var flag = matcher.matches();
        assert flag;

        return new EntityId(
                0,
                Long.parseLong(matcher.group(REALM_INDEX)),
                Long.parseLong(matcher.group(NUM_INDEX)));
    }

    static Long toLong(byte[] bytes) {
        return (bytes == null) ? null : Longs.fromByteArray(bytes);
    }

    static String toKeyString(EntityId id) {
        return String.format(LEGACY_PATH_TEMPLATE, id.realm(), id.num());
    }
}
