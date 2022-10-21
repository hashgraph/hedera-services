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

import com.hedera.services.files.store.BytesStoreAdapter;
import com.hederahashgraph.api.proto.java.FileID;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

public class DataMapFactory {
    private static final String LEGACY_PATH_TEMPLATE = "/%d/f%d";
    public static final Pattern LEGACY_PATH_PATTERN = Pattern.compile("/(\\d+)/f(\\d+)");
    private static final int REALM_INDEX = 1;
    private static final int ACCOUNT_INDEX = 2;

    DataMapFactory() {
        throw new IllegalStateException();
    }

    public static Map<FileID, byte[]> dataMapFrom(Map<String, byte[]> store) {
        return new BytesStoreAdapter<>(
                FileID.class,
                Function.identity(),
                Function.identity(),
                DataMapFactory::toFid,
                DataMapFactory::toKeyString,
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
}
