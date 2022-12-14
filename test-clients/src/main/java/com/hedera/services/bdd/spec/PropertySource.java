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
package com.hedera.services.bdd.spec;

import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ShardID;
import java.util.stream.Stream;

public interface PropertySource {
    String get(String property);

    default HapiSpec.CostSnapshotMode getCostSnapshotMode(String property) {
        return HapiSpec.CostSnapshotMode.valueOf(get(property));
    }

    default FileID getFile(String property) {
        try {
            return asFile(get(property));
        } catch (Exception ignore) {
        }
        return FileID.getDefaultInstance();
    }

    default AccountID getAccount(String property) {
        try {
            return asAccount(get(property));
        } catch (Exception ignore) {
        }
        return AccountID.getDefaultInstance();
    }

    default ContractID getContract(String property) {
        try {
            return asContract(get(property));
        } catch (Exception ignore) {
        }
        return ContractID.getDefaultInstance();
    }

    default RealmID getRealm(String property) {
        return RealmID.newBuilder().setRealmNum(Long.parseLong(get(property))).build();
    }

    default ShardID getShard(String property) {
        return ShardID.newBuilder().setShardNum(Long.parseLong(get(property))).build();
    }

    default long getLong(String property) {
        return Long.parseLong(get(property));
    }

    default int getInteger(String property) {
        return Integer.parseInt(get(property));
    }

    default Duration getDurationFromSecs(String property) {
        return Duration.newBuilder().setSeconds(getInteger(property)).build();
    }

    default boolean getBoolean(String property) {
        return Boolean.parseBoolean(get(property));
    }

    default byte[] getBytes(String property) {
        return get(property).getBytes();
    }

    default KeyFactory.KeyType getKeyType(String property) {
        return KeyFactory.KeyType.valueOf(get(property));
    }

    static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    static String asAccountString(AccountID account) {
        return String.format(
                "%d.%d.%d", account.getShardNum(), account.getRealmNum(), account.getAccountNum());
    }

    static ContractID asContract(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return ContractID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setContractNum(nativeParts[2])
                .build();
    }

    static FileID asFile(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return FileID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setFileNum(nativeParts[2])
                .build();
    }

    static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }
}
