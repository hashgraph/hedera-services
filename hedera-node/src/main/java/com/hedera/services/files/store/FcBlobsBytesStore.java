package com.hedera.services.files.store;

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

import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toSet;

public class FcBlobsBytesStore extends AbstractMap<String, byte[]> {
        public static Logger log = LogManager.getLogger(FcBlobsBytesStore.class);

        private final Function<byte[], MerkleOptionalBlob> blobFactory;
        private final FCMap<MerkleBlobMeta, MerkleOptionalBlob> pathedBlobs;

        public FcBlobsBytesStore(
                        Function<byte[], MerkleOptionalBlob> blobFactory,
                        FCMap<MerkleBlobMeta, MerkleOptionalBlob> pathedBlobs
        ) {
                this.blobFactory = blobFactory;
                this.pathedBlobs = pathedBlobs;
        }

        private MerkleBlobMeta at(Object key) {
                return new MerkleBlobMeta((String)key);
        }

        @Override
        public void clear() {
                pathedBlobs.clear();
        }

        @Override
        public byte[] remove(Object path) {
                return Optional.ofNullable(pathedBlobs.remove(at(path)))
                                .map(MerkleOptionalBlob::getData)
                                .orElse(null);
        }

        /**
         * Replaces the blob at the given path with the given contents.
         *
         * <B>NOTE:</B> This method breaks the traditional {@code Map} contract,
         * and does not return the contents of the previous blob.
         *
         * @param path the path of the blob
         * @param value the contents to be set
         * @return {@code null}
         */
        @Override
        public byte[] put(String path, byte[] value) {
                /* Note that if the blob at {@code path} was already updated
                in this round, the platform will not hold a copy of the replaced
                leaf, and this leaf will have been deleted before it is returned
                from {@code pathedBlobs#put}. Hence we simply return {@code null}
                here. */
                var blob = blobFactory.apply(value);
                log.debug("Putting {} bytes (hash = {}) @ '{}'",
                                value.length,
                                blob.getHash(),
                                path);
                pathedBlobs.put(at(path), blob);
                return null;
        }

        @Override
        public byte[] get(Object path) {
                return Optional.ofNullable(pathedBlobs.get(at(path)))
                                .map(MerkleOptionalBlob::getData)
                                .orElse(null);
        }

        @Override
        public boolean containsKey(Object path) {
                return pathedBlobs.containsKey(at(path));
        }

        @Override
        public boolean isEmpty() {
                return pathedBlobs.isEmpty();
        }

        @Override
        public int size() {
                return pathedBlobs.size();
        }

        @Override
        public Set<Entry<String, byte[]>> entrySet() {
                return pathedBlobs.entrySet()
                                .stream()
                                .map(entry -> new SimpleEntry<>(entry.getKey().getPath(), entry.getValue().getData()))
                                .collect(toSet());
        }
}
