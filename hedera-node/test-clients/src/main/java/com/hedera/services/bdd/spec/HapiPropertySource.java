/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static java.lang.System.arraycopy;

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public interface HapiPropertySource {
    String get(String property);

    boolean has(String property);

    static HapiPropertySource inPriorityOrder(HapiPropertySource... sources) {
        if (sources.length == 1) {
            return sources[0];
        } else {
            HapiPropertySource overrides = sources[0];
            HapiPropertySource defaults = inPriorityOrder(Arrays.copyOfRange(sources, 1, sources.length));

            return new HapiPropertySource() {
                @Override
                public String get(String property) {
                    return overrides.has(property) ? overrides.get(property) : defaults.get(property);
                }

                @Override
                public boolean has(String property) {
                    return overrides.has(property) || defaults.has(property);
                }
            };
        }
    }

    default HapiSpec.CostSnapshotMode getCostSnapshotMode(String property) {
        return HapiSpec.CostSnapshotMode.valueOf(get(property));
    }

    default HapiSpec.UTF8Mode getUTF8Mode(String property) {
        return HapiSpec.UTF8Mode.valueOf(get(property));
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

    default TimeUnit getTimeUnit(String property) {
        return TimeUnit.valueOf(get(property));
    }

    default double getDouble(String property) {
        return Double.parseDouble(get(property));
    }

    default long getLong(String property) {
        return Long.parseLong(get(property));
    }

    default HapiSpecSetup.TlsConfig getTlsConfig(String property) {
        return HapiSpecSetup.TlsConfig.valueOf(get(property).toUpperCase());
    }

    default HapiSpecSetup.TxnProtoStructure getTxnConfig(String property) {
        return HapiSpecSetup.TxnProtoStructure.valueOf(get(property).toUpperCase());
    }

    default HapiSpecSetup.NodeSelection getNodeSelector(String property) {
        return HapiSpecSetup.NodeSelection.valueOf(get(property).toUpperCase());
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

    default SigControl.KeyAlgo getKeyAlgorithm(String property) {
        return SigControl.KeyAlgo.valueOf(get(property));
    }

    default HapiSpec.SpecStatus getSpecStatus(String property) {
        return HapiSpec.SpecStatus.valueOf(get(property));
    }

    static HapiPropertySource[] asSources(Object... sources) {
        return Stream.of(sources)
                .map(s -> (s instanceof HapiPropertySource)
                        ? s
                        : ((s instanceof Map) ? new MapPropertySource((Map) s) : new JutilPropertySource((String) s)))
                .toArray(n -> new HapiPropertySource[n]);
    }

    static TokenID asToken(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return TokenID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setTokenNum(nativeParts[2])
                .build();
    }

    static String asTokenString(TokenID token) {
        return String.format("%d.%d.%d", token.getShardNum(), token.getRealmNum(), token.getTokenNum());
    }

    static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    static AccountID asAccount(ByteString v) {
        return AccountID.newBuilder().setAlias(v).build();
    }

    static String asAccountString(AccountID account) {
        return String.format("%d.%d.%d", account.getShardNum(), account.getRealmNum(), account.getAccountNum());
    }

    static String asAliasableAccountString(final AccountID account) {
        if (account.getAlias().isEmpty()) {
            return asAccountString(account);
        } else {
            final var literalAlias = account.getAlias().toString();
            return String.format("%d.%d.%s", account.getShardNum(), account.getRealmNum(), literalAlias);
        }
    }

    static TopicID asTopic(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return TopicID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setTopicNum(nativeParts[2])
                .build();
    }

    static String asTopicString(TopicID topic) {
        return String.format("%d.%d.%d", topic.getShardNum(), topic.getRealmNum(), topic.getTopicNum());
    }

    static ContractID asContract(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return ContractID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setContractNum(nativeParts[2])
                .build();
    }

    static String asContractString(ContractID contract) {
        return String.format("%d.%d.%d", contract.getShardNum(), contract.getRealmNum(), contract.getContractNum());
    }

    static ScheduleID asSchedule(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return ScheduleID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setScheduleNum(nativeParts[2])
                .build();
    }

    static String asScheduleString(ScheduleID schedule) {
        return String.format("%d.%d.%d", schedule.getShardNum(), schedule.getRealmNum(), schedule.getScheduleNum());
    }

    static SemanticVersion asSemVer(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return SemanticVersion.newBuilder()
                .setMajor((int) nativeParts[0])
                .setMinor((int) nativeParts[1])
                .setPatch((int) nativeParts[2])
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

    static String asFileString(FileID file) {
        return String.format("%d.%d.%d", file.getShardNum(), file.getRealmNum(), file.getFileNum());
    }

    static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }

    static byte[] asSolidityAddress(final AccountID accountId) {
        return asSolidityAddress((int) accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum());
    }

    static Address idAsHeadlongAddress(final AccountID accountId) {
        return asHeadlongAddress(
                asSolidityAddress((int) accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum()));
    }

    static Address idAsHeadlongAddress(final TokenID tokenId) {
        return asHeadlongAddress(
                asSolidityAddress((int) tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum()));
    }

    static String asHexedSolidityAddress(final AccountID accountId) {
        return CommonUtils.hex(asSolidityAddress(accountId));
    }

    static String asHexedSolidityAddress(final ContractID contractId) {
        return CommonUtils.hex(asSolidityAddress(contractId));
    }

    static String asHexedSolidityAddress(final TokenID tokenId) {
        return CommonUtils.hex(asSolidityAddress(tokenId));
    }

    static byte[] asSolidityAddress(final ContractID contractId) {
        return asSolidityAddress((int) contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum());
    }

    static byte[] asSolidityAddress(final TokenID tokenId) {
        return asSolidityAddress((int) tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum());
    }

    static byte[] asSolidityAddress(final int shard, final long realm, final long num) {
        final byte[] solidityAddress = new byte[20];

        arraycopy(Ints.toByteArray(shard), 0, solidityAddress, 0, 4);
        arraycopy(Longs.toByteArray(realm), 0, solidityAddress, 4, 8);
        arraycopy(Longs.toByteArray(num), 0, solidityAddress, 12, 8);

        return solidityAddress;
    }

    static String asHexedSolidityAddress(final int shard, final long realm, final long num) {
        return CommonUtils.hex(asSolidityAddress(shard, realm, num));
    }

    static ContractID contractIdFromHexedMirrorAddress(final String hexedEvm) {
        return ContractID.newBuilder()
                .setContractNum(Longs.fromByteArray(Arrays.copyOfRange(CommonUtils.unhex(hexedEvm), 12, 20)))
                .build();
    }

    static AccountID accountIdFromHexedMirrorAddress(final String hexedEvm) {
        return AccountID.newBuilder()
                .setAccountNum(Longs.fromByteArray(Arrays.copyOfRange(CommonUtils.unhex(hexedEvm), 12, 20)))
                .build();
    }

    static String literalIdFromHexedMirrorAddress(final String hexedEvm) {
        return HapiPropertySource.asContractString(ContractID.newBuilder()
                .setContractNum(Longs.fromByteArray(Arrays.copyOfRange(CommonUtils.unhex(hexedEvm), 12, 20)))
                .build());
    }
}
