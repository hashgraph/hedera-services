/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromByteString;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.suites.utils.sysfiles.BookEntryPojo.asOctets;
import static java.lang.System.arraycopy;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.config.converter.LongPairConverter;
import com.hedera.node.config.types.LongPair;
import com.hedera.node.config.types.StreamMode;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.EntityNumber;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public interface HapiPropertySource {

    String ENTITY_STRING = "%d.%d.%d";

    static byte[] explicitBytesOf(@NonNull final Address address) {
        var asBytes = address.value().toByteArray();
        // Might have a leading zero byte to make it positive
        if (asBytes.length == 21) {
            asBytes = Arrays.copyOfRange(asBytes, 1, 21);
        }
        return asBytes;
    }

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

    /**
     * Returns an {@link StreamMode} parsed from the given property.
     * @param property the property to get the value from
     * @return the {@link StreamMode} value
     */
    default StreamMode getStreamMode(@NonNull final String property) {
        requireNonNull(property);
        return StreamMode.valueOf(get(property));
    }

    default ServiceEndpoint getServiceEndpoint(String property) {
        try {
            return asServiceEndpoint(get(property));
        } catch (Exception ignore) {
            System.out.println("Unable to parse service endpoint from property: " + property);
        }
        return ServiceEndpoint.DEFAULT;
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

    default ScaleFactor getScaleFactor(@NonNull final String property) {
        requireNonNull(property);
        return ScaleFactor.from(get(property));
    }

    default double getDouble(String property) {
        return Double.parseDouble(get(property));
    }

    default long getLong(String property) {
        return Long.parseLong(get(property));
    }

    /**
     * Returns a {@link LongPair} from the given property.
     * @param property the property to get the value from
     * @return the {@link LongPair} value
     */
    default LongPair getLongPair(@NonNull final String property) {
        return new LongPairConverter().convert(get(property));
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
                .toArray(HapiPropertySource[]::new);
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
        return String.format(ENTITY_STRING, token.getShardNum(), token.getRealmNum(), token.getTokenNum());
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
        return String.format(ENTITY_STRING, account.getShardNum(), account.getRealmNum(), account.getAccountNum());
    }

    static String asAliasableAccountString(final AccountID account) {
        if (account.getAlias().isEmpty()) {
            return asAccountString(account);
        } else {
            final var literalAlias = account.getAlias().toString();
            return String.format(ENTITY_STRING, account.getShardNum(), account.getRealmNum(), literalAlias);
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
        return String.format(ENTITY_STRING, topic.getShardNum(), topic.getRealmNum(), topic.getTopicNum());
    }

    /**
     * Interprets the given string as a comma-separated list of {@code {<IP>|<DNS>}:{<PORT>}} pairs, returning a list
     * of {@link ServiceEndpoint} instances with the appropriate host references set.
     * @param v the string to interpret
     * @return the parsed list of {@link ServiceEndpoint} instances
     */
    static List<ServiceEndpoint> asCsServiceEndpoints(@NonNull final String v) {
        requireNonNull(v);
        return Stream.of(v.split(","))
                .map(HapiPropertySource::asTypedServiceEndpoint)
                .toList();
    }

    /**
     * Interprets the given string as a {@code {<IP>|<DNS>}:{<PORT>}} pair, returning an {@link ServiceEndpoint}
     * with the appropriate host reference set.
     * @param v the string to interpret
     * @return the parsed {@link ServiceEndpoint}
     */
    static ServiceEndpoint asTypedServiceEndpoint(@NonNull final String v) {
        requireNonNull(v);
        try {
            return asServiceEndpoint(v);
        } catch (Exception ignore) {
            return asDnsServiceEndpoint(v);
        }
    }

    static ServiceEndpoint asServiceEndpoint(String v) {
        String[] parts = v.split(":");
        return ServiceEndpoint.newBuilder()
                .ipAddressV4(fromByteString(asOctets(parts[0])))
                .port(Integer.parseInt(parts[1]))
                .build();
    }

    static ServiceEndpoint invalidServiceEndpoint() {
        return ServiceEndpoint.newBuilder()
                .ipAddressV4(Bytes.wrap(new byte[3]))
                .port(33)
                .build();
    }

    static ServiceEndpoint asDnsServiceEndpoint(String v) {
        String[] parts = v.split(":");
        return ServiceEndpoint.newBuilder()
                .domainName(parts[0])
                .port(Integer.parseInt(parts[1]))
                .build();
    }

    static ContractID asContract(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return ContractID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setContractNum(nativeParts[2])
                .build();
    }

    static ContractID asContractIdWithEvmAddress(ByteString address) {
        return ContractID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setEvmAddress(address)
                .build();
    }

    static String asContractString(ContractID contract) {
        return String.format(ENTITY_STRING, contract.getShardNum(), contract.getRealmNum(), contract.getContractNum());
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
        return String.format(ENTITY_STRING, schedule.getShardNum(), schedule.getRealmNum(), schedule.getScheduleNum());
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

    static EntityNumber asEntityNumber(String v) {
        return EntityNumber.newBuilder().setNumber(Long.parseLong(v)).build();
    }

    static String asFileString(FileID file) {
        return String.format(ENTITY_STRING, file.getShardNum(), file.getRealmNum(), file.getFileNum());
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
