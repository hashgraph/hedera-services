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
package com.hedera.services.utils;

import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SerializationUtils {
    private SerializationUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static void serializeHbarAllowances(
            SerializableDataOutputStream out, final Map<EntityNum, Long> cryptoAllowances)
            throws IOException {
        out.writeInt(cryptoAllowances.size());
        for (Map.Entry<EntityNum, Long> entry : cryptoAllowances.entrySet()) {
            out.writeLong(entry.getKey().longValue());
            out.writeLong(entry.getValue());
        }
    }

    public static void serializeFungibleAllowances(
            final SerializableDataOutputStream out,
            final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances)
            throws IOException {
        out.writeInt(fungibleTokenAllowances.size());
        for (Map.Entry<FcTokenAllowanceId, Long> entry : fungibleTokenAllowances.entrySet()) {
            out.writeSerializable(entry.getKey(), true);
            out.writeLong(entry.getValue());
        }
    }

    public static Map<FcTokenAllowanceId, Long> deserializeFungibleAllowances(
            final SerializableDataInputStream in) throws IOException {
        var numFungibleTokenAllowances = in.readInt();
        if (numFungibleTokenAllowances == 0) {
            return Collections.emptyMap();
        }
        final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances = new TreeMap<>();
        while (numFungibleTokenAllowances-- > 0) {
            final FcTokenAllowanceId fungibleAllowanceId = in.readSerializable();
            final Long value = in.readLong();
            fungibleTokenAllowances.put(fungibleAllowanceId, value);
        }
        return fungibleTokenAllowances;
    }

    public static void serializeNftOperatorApprovals(
            SerializableDataOutputStream out, final Set<FcTokenAllowanceId> approveForAllNfts)
            throws IOException {
        out.writeInt(approveForAllNfts.size());
        for (final var allowanceId : approveForAllNfts) {
            out.writeSerializable(allowanceId, true);
        }
    }

    public static Map<EntityNum, Long> deserializeHbarAllowances(
            final SerializableDataInputStream in) throws IOException {
        var numCryptoAllowances = in.readInt();
        if (numCryptoAllowances == 0) {
            return Collections.emptyMap();
        }
        final Map<EntityNum, Long> cryptoAllowances = new TreeMap<>();
        while (numCryptoAllowances-- > 0) {
            final var entityNum = EntityNum.fromLong(in.readLong());
            final var allowance = in.readLong();
            cryptoAllowances.put(entityNum, allowance);
        }
        return cryptoAllowances;
    }

    public static Set<FcTokenAllowanceId> deserializeNftOperatorApprovals(
            final SerializableDataInputStream in) throws IOException {
        var numApproveForAllNftsAllowances = in.readInt();
        if (numApproveForAllNftsAllowances == 0) {
            return Collections.emptySet();
        }
        final Set<FcTokenAllowanceId> approveForAllNftsAllowances = new TreeSet<>();
        while (numApproveForAllNftsAllowances-- > 0) {
            final FcTokenAllowanceId allowanceId = in.readSerializable();
            approveForAllNftsAllowances.add(allowanceId);
        }
        return approveForAllNftsAllowances;
    }
}
