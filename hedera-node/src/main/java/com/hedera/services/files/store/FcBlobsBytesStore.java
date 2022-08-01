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
package com.hedera.services.files.store;

import static java.lang.Long.parseLong;

import com.hedera.services.state.merkle.internals.BlobKey;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.swirlds.virtualmap.VirtualMap;
import java.util.AbstractMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public class FcBlobsBytesStore extends AbstractMap<String, byte[]> {
    public static final VirtualBlobValue EMPTY_BLOB = new VirtualBlobValue(new byte[0]);

    private final Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> blobSupplier;

    public static final int LEGACY_BLOB_CODE_INDEX = 3;

    public FcBlobsBytesStore(Supplier<VirtualMap<VirtualBlobKey, VirtualBlobValue>> blobSupplier) {
        this.blobSupplier = blobSupplier;
    }

    /**
     * The string we are parsing has one of five special forms:
     *
     * <ul>
     *   <li>{@literal /0/f{num}} for file data; or,
     *   <li>{@literal /0/k{num}} for file metadata; or,
     *   <li>{@literal /0/s{num}} for contract bytecode; or,
     *   <li>{@literal /0/d{num}} for contract storage; or,
     *   <li>{@literal /0/e{num}} for prior expiration time of a system-deleted entity.
     * </ul>
     *
     * So we get the type from the character code at index 3, and parse the entity number starting
     * at index 4, to get the appropriate {@link BlobKey}.
     *
     * @param path a string with one of the five forms above
     * @return a fixed-size map key with equivalent meaning
     */
    VirtualBlobKey at(Object path) {
        return VirtualBlobKey.fromPath((String) path);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * Removes the blob at the given path.
     *
     * <p><B>NOTE:</B> This method breaks the standard {@code Map} contract, and does not return the
     * contents of the removed blob.
     *
     * @param path the path of the blob
     * @return {@code null}
     */
    @Override
    public byte[] remove(Object path) {
        blobSupplier.get().put(at(path), EMPTY_BLOB);
        return null;
    }

    /**
     * Replaces the blob at the given path with the given contents.
     *
     * <p><B>NOTE:</B> This method breaks the standard {@code Map} contract, and does not return the
     * contents of the previous blob.
     *
     * @param path the path of the blob
     * @param value the contents to be set
     * @return null, no matter if the path already had an associated value
     */
    @Override
    public byte[] put(String path, byte[] value) {
        final VirtualBlobValue blob = new VirtualBlobValue(value);
        blobSupplier.get().put(at(path), blob);
        return null;
    }

    @Override
    public byte[] get(Object path) {
        return Optional.ofNullable(blobSupplier.get().get(at(path)))
                .map(VirtualBlobValue::getData)
                .orElse(null);
    }

    @Override
    public boolean containsKey(Object path) {
        return blobSupplier.get().containsKey(at(path));
    }

    @Override
    public boolean isEmpty() {
        return blobSupplier.get().isEmpty();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<String, byte[]>> entrySet() {
        throw new UnsupportedOperationException();
    }

    /**
     * As the string we are parsing matches /0/f{num} for file data, /0/k{num} for file metadata,
     * /0/s{num} for contract bytecode, and /0/e{num} for system deleted files, character at third
     * position is used to recognize the type of blob
     *
     * @param key given blob key
     * @return the entity number from the path
     */
    public static long getEntityNumFromPath(final String key) {
        return parseLong(key.substring(LEGACY_BLOB_CODE_INDEX + 1));
    }
}
