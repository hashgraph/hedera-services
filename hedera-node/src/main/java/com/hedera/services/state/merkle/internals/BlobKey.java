/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle.internals;

import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_STORAGE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY;

import com.hedera.services.utils.MiscUtils;

public record BlobKey(BlobType type, long entityNum) {
    public enum BlobType {
        FILE_DATA,
        FILE_METADATA,
        CONTRACT_STORAGE,
        CONTRACT_BYTECODE,
        SYSTEM_DELETED_ENTITY_EXPIRY
    }

    @Override
    public int hashCode() {
        final var result = type.hashCode();
        return result * 31 + (int) MiscUtils.perm64(entityNum);
    }

    /**
     * Returns the type corresponding to a legacy character code.
     *
     * @param code the legacy blob code
     * @return the blob type
     */
    public static BlobType typeFromCharCode(final char code) {
        return switch (code) {
            case 'f' -> FILE_DATA;
            case 'k' -> FILE_METADATA;
            case 's' -> CONTRACT_BYTECODE;
            case 'd' -> CONTRACT_STORAGE;
            case 'e' -> SYSTEM_DELETED_ENTITY_EXPIRY;
            default -> throw new IllegalArgumentException("Invalid legacy code '" + code + "'");
        };
    }
}
