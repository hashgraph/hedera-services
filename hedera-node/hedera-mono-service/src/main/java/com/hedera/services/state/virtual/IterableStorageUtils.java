/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import static com.hedera.services.utils.MapValueListUtils.removeFromMapValueList;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import java.util.Objects;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

public class IterableStorageUtils {
    private static final String NO_ITERABLE_NFTS = "[]";
    private static final String NO_ITERABLE_STORAGE = "[]";

    private IterableStorageUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static String joinedOwnedNfts(
            final EntityNumPair firstKey, final MerkleMap<EntityNumPair, MerkleUniqueToken> nfts) {
        if (firstKey == null) {
            return NO_ITERABLE_NFTS;
        }
        final var sb = new StringBuilder("[");
        var isFirstValue = true;
        EntityNumPair nextKey = firstKey;
        while (!EntityNumPair.MISSING_NUM_PAIR.equals(nextKey)) {
            final var value =
                    Objects.requireNonNull(
                            nfts.get(nextKey), "Linked key " + nextKey + " had no mapped value");
            sb.append(isFirstValue ? "" : ", ")
                    .append("0.0.")
                    .append(value.getKey().getHiOrderAsLong())
                    .append(".")
                    .append(value.getKey().getLowOrderAsLong());
            isFirstValue = false;
            nextKey = value.getNext().asEntityNumPair();
        }
        return sb.append("]").toString();
    }

    public static String joinedStorageMappings(
            final ContractKey firstKey,
            final VirtualMap<ContractKey, IterableContractValue> storage) {
        if (firstKey == null) {
            return NO_ITERABLE_STORAGE;
        }

        final var sb = new StringBuilder("[");
        var isFirstValue = true;
        final long contractId = firstKey.getContractId();
        ContractKey nextKey = firstKey;
        while (nextKey != null) {
            final var value = storage.get(nextKey);
            Objects.requireNonNull(value, "Linked key " + nextKey + " had no mapped value");
            sb.append(isFirstValue ? "" : ", ")
                    .append(nextKey)
                    .append(" -> ")
                    .append(CommonUtils.hex(value.getValue()));
            isFirstValue = false;
            nextKey = value.getNextKeyScopedTo(contractId);
        }
        return sb.append("]").toString();
    }

    /**
     * "Upserts" a key/value pair in the given {@code VirtualMap}, as follows:
     *
     * <ol>
     *   <li>If the key is already present, simply updates its value with no other actions.
     *   <li>Otherwise, inserts a new key/value pair in the given {@code VirtualMap} at the front of
     *       the doubly-linked list of the relevant contract's storage, updating the prev/next keys
     *       of the "adjacent" values as needed.
     * </ol>
     *
     * Uses {@link VirtualMap#getForModify(VirtualKey)}.
     *
     * @param key the key of the new mapping
     * @param value the value of the new mapping
     * @param rootKey the key of the root mapping in the storage list
     * @param rootValue if pre-fetched, the value of the root mapping in the storage list
     * @param storage the working copy of the storage map
     * @return the new root key, for convenience
     */
    public static ContractKey inPlaceUpsertMapping(
            @NotNull final ContractKey key,
            @NotNull final IterableContractValue value,
            @Nullable final ContractKey rootKey,
            @Nullable final IterableContractValue rootValue,
            @NotNull final VirtualMap<ContractKey, IterableContractValue> storage) {
        return internalUpsertMapping(key, value, rootKey, rootValue, storage, true);
    }

    /**
     * "Upserts" a key/value pair in the given {@code VirtualMap}, as follows:
     *
     * <ol>
     *   <li>If the key is already present, simply updates its value with no other actions.
     *   <li>Otherwise, inserts a new key/value pair in the given {@code VirtualMap} at the front of
     *       the doubly-linked list of the relevant contract's storage, updating the prev/next keys
     *       of the "adjacent" values as needed.
     * </ol>
     *
     * Does <b>not</b> use {@link VirtualMap#getForModify(VirtualKey)}.
     *
     * @param key the key of the new mapping
     * @param value the value of the new mapping
     * @param rootKey the key of the root mapping in the storage list
     * @param rootValue if pre-fetched, the value of the root mapping in the storage list
     * @param storage the working copy of the storage map
     * @return the new root key, for convenience
     */
    public static ContractKey overwritingUpsertMapping(
            @NotNull final ContractKey key,
            @NotNull final IterableContractValue value,
            @Nullable final ContractKey rootKey,
            @Nullable final IterableContractValue rootValue,
            @NotNull final VirtualMap<ContractKey, IterableContractValue> storage) {
        return internalUpsertMapping(key, value, rootKey, rootValue, storage, false);
    }

    /**
     * Removes the key/value pair with the given key in the given {@code VirtualMap}, and updates
     * the doubly-linked list of the relevant contract's storage to maintain the prev/next keys of
     * the "adjacent" value(s) as needed.
     *
     * @param key the key of the mapping to remove
     * @param root the key of the root mapping in the storage list
     * @param storage the working copy of the storage map
     * @return the new root key, for convenience
     */
    public static @Nullable ContractKey removeMapping(
            @NotNull final ContractKey key,
            @NotNull final ContractKey root,
            @NotNull final VirtualMap<ContractKey, IterableContractValue> storage) {
        return removeFromMapValueList(
                key, root, new ContractStorageListMutation(key.getContractId(), storage));
    }

    private static ContractKey internalUpsertMapping(
            @NotNull final ContractKey key,
            @NotNull final IterableContractValue value,
            @Nullable final ContractKey rootKey,
            @Nullable final IterableContractValue rootValue,
            @NotNull final VirtualMap<ContractKey, IterableContractValue> storage,
            boolean useGetForModify) {
        final IterableContractValue oldValue;
        if (useGetForModify) {
            oldValue = storage.getForModify(key);
        } else {
            final var candidateValue = storage.get(key);
            // Note it is ONLY safe to call copy() here---making the map's value
            // immutable!---because we immediately put() the mutable value below
            oldValue = (candidateValue != null) ? candidateValue.copy() : null;
        }
        if (oldValue != null) {
            oldValue.setValue(value.getValue());
            if (!useGetForModify) {
                storage.put(key, oldValue);
            }
            return rootKey;
        } else {
            storage.put(key, value);
            if (rootKey != null) {
                value.setNextKey(rootKey.getKey());
                final IterableContractValue nextValue;
                if (rootValue != null) {
                    nextValue = rootValue;
                } else {
                    nextValue =
                            useGetForModify
                                    ? Objects.requireNonNull(
                                            storage.getForModify(rootKey),
                                            () -> "Missing root " + rootKey)
                                    // Note it is ONLY safe to call copy() here---making the map's
                                    // value
                                    // immutable!---because we immediately put() the mutable value
                                    // below
                                    : Objects.requireNonNull(
                                                    storage.get(rootKey),
                                                    () -> "Missing root " + rootKey)
                                            .copy();
                }
                nextValue.setPrevKey(key.getKey());
                if (!useGetForModify && rootValue == null) {
                    storage.put(rootKey, nextValue);
                }
            }
            return key;
        }
    }
}
