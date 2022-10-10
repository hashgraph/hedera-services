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

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

import com.hedera.services.files.store.BytesStoreAdapter;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MetadataMapFactory {
    private static final Logger log = LogManager.getLogger(MetadataMapFactory.class);

    private static final String LEGACY_PATH_TEMPLATE = "/%d/k%d";
    public static final Pattern LEGACY_PATH_PATTERN = Pattern.compile("/(\\d+)/k(\\d+)");
    private static final int REALM_INDEX = 1;
    private static final int ACCOUNT_INDEX = 2;

    private MetadataMapFactory() {
        throw new UnsupportedOperationException("Factory Class");
    }

    public static Map<FileID, HFileMeta> metaMapFrom(final Map<String, byte[]> store) {
        return new BytesStoreAdapter<>(
                FileID.class,
                MetadataMapFactory::toAttr,
                MetadataMapFactory::toValueBytes,
                MetadataMapFactory::toFid,
                MetadataMapFactory::toKeyString,
                store);
    }

    static FileID toFid(final String key) {
        final var matcher = LEGACY_PATH_PATTERN.matcher(key);
        final var flag = matcher.matches();
        assert flag;

        return FileID.newBuilder()
                .setShardNum(STATIC_PROPERTIES.getShard())
                .setRealmNum(Long.parseLong(matcher.group(REALM_INDEX)))
                .setFileNum(Long.parseLong(matcher.group(ACCOUNT_INDEX)))
                .build();
    }

    static String toKeyString(final FileID fid) {
        return String.format(LEGACY_PATH_TEMPLATE, fid.getRealmNum(), fid.getFileNum());
    }

    public static HFileMeta toAttr(final byte[] bytes) {
        try {
            return (bytes == null || bytes.length == 0) ? null : HFileMeta.deserialize(bytes);
        } catch (final IOException internal) {
            log.warn("Argument 'bytes={}' was not a serialized HFileMeta!", CommonUtils.hex(bytes));
            throw new IllegalArgumentException(internal);
        }
    }

    public static byte[] toValueBytes(final HFileMeta attr) {
        try {
            return attr.serialize();
        } catch (final IOException internal) {
            try {
                log.warn("Argument 'attr={}' could not be serialized!", attr);
            } catch (Exception terminal) {
                log.warn(
                        "Argument 'attr' could not be serialized, nor represented as a string!",
                        terminal);
            }
            throw new IllegalArgumentException(internal);
        }
    }
}
