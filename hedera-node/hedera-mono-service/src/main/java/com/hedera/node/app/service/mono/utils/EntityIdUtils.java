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

package com.hedera.node.app.service.mono.utils;

import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.numFromCode;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils.unsignedLowOrder32From;
import static java.lang.System.arraycopy;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public final class EntityIdUtils {
    public static final int EVM_ADDRESS_SIZE = 20;
    public static final int ECDSA_SECP256K1_ALIAS_SIZE = 35;
    private static final String ENTITY_ID_FORMAT = "%d.%d.%d";
    private static final String CANNOT_PARSE_PREFIX = "Cannot parse '";
    private static final Pattern ENTITY_NUM_RANGE_PATTERN = Pattern.compile("(\\d+)-(\\d+)");

    private EntityIdUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static String readableId(final Object o) {
        if (o instanceof Id id) {
            return String.format(ENTITY_ID_FORMAT, id.shard(), id.realm(), id.num());
        }
        if (o instanceof AccountID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
        }
        if (o instanceof FileID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getFileNum());
        }
        if (o instanceof TopicID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTopicNum());
        }
        if (o instanceof TokenID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getTokenNum());
        }
        if (o instanceof ScheduleID id) {
            return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getScheduleNum());
        }
        if (o instanceof NftID id) {
            final var tokenID = id.getTokenID();
            return String.format(
                    ENTITY_ID_FORMAT + ".%d",
                    tokenID.getShardNum(),
                    tokenID.getRealmNum(),
                    tokenID.getTokenNum(),
                    id.getSerialNumber());
        }
        return String.valueOf(o);
    }

    /**
     * Returns the {@code AccountID} represented by a literal of the form {@code
     * <shard>.<realm>.<num>}.
     *
     * @param literal the account literal
     * @return the corresponding id
     * @throws IllegalArgumentException if the literal is not formatted correctly
     */
    public static AccountID parseAccount(final String literal) {
        try {
            final var parts = parseLongTriple(literal);
            return AccountID.newBuilder()
                    .setShardNum(parts[0])
                    .setRealmNum(parts[1])
                    .setAccountNum(parts[2])
                    .build();
        } catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(String.format("Argument 'literal=%s' is not an account", literal), e);
        }
    }

    public static Pair<Long, Long> parseEntityNumRange(final String literal) {
        final var matcher = ENTITY_NUM_RANGE_PATTERN.matcher(literal);
        if (matcher.matches()) {
            try {
                final var left = Long.valueOf(matcher.group(1));
                final var right = Long.valueOf(matcher.group(2));
                if (left > right) {
                    throw new IllegalArgumentException(
                            "Range left endpoint " + left + " should be <= right endpoint " + right);
                }
                return Pair.of(left, right);
            } catch (final NumberFormatException nfe) {
                throw new IllegalArgumentException("Argument literal='" + literal + "' has malformatted long value");
            }
        } else {
            throw new IllegalArgumentException("Argument literal='" + literal + "' is not a valid range literal");
        }
    }

    private static long[] parseLongTriple(final String dotDelimited) {
        final long[] triple = new long[3];
        int i = 0;
        long v = 0;
        for (final char c : dotDelimited.toCharArray()) {
            if (c == '.') {
                triple[i++] = v;
                v = 0;
            } else if (c < '0' || c > '9') {
                throw new NumberFormatException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to character '" + c + "'");
            } else {
                v = 10 * v + (c - '0');
                if (v < 0) {
                    throw new IllegalArgumentException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to overflow");
                }
            }
        }
        if (i < 2) {
            throw new IllegalArgumentException(CANNOT_PARSE_PREFIX + dotDelimited + "' due to only " + i + " dots");
        }
        triple[i] = v;
        return triple;
    }

    public static AccountID asAccount(final ContractID cid) {
        return AccountID.newBuilder()
                .setRealmNum(cid.getRealmNum())
                .setShardNum(cid.getShardNum())
                .setAccountNum(cid.getContractNum())
                .build();
    }

    public static ContractID asContract(final AccountID id) {
        return ContractID.newBuilder()
                .setRealmNum(id.getRealmNum())
                .setShardNum(id.getShardNum())
                .setContractNum(id.getAccountNum())
                .build();
    }

    public static FileID asFile(final AccountID id) {
        return FileID.newBuilder()
                .setRealmNum(id.getRealmNum())
                .setShardNum(id.getShardNum())
                .setFileNum(id.getAccountNum())
                .build();
    }

    public static AccountID asAccount(final EntityId jId) {
        if (jId == null || jId.equals(EntityId.MISSING_ENTITY_ID)) {
            return StateView.WILDCARD_OWNER;
        }
        return AccountID.newBuilder()
                .setRealmNum(jId.realm())
                .setShardNum(jId.shard())
                .setAccountNum(jId.num())
                .build();
    }

    public static String asHexedEvmAddress(final AccountID id) {
        return CommonUtils.hex(asEvmAddress(id.getAccountNum()));
    }

    public static byte[] asEvmAddress(final ContractID id) {
        if (isOfEvmAddressSize(id.getEvmAddress())) {
            return id.getEvmAddress().toByteArray();
        } else {
            return asEvmAddress(id.getContractNum());
        }
    }

    public static byte[] asEvmAddress(final AccountID id) {
        return asEvmAddress(id.getAccountNum());
    }

    public static byte[] asEvmAddress(final TokenID id) {
        return asEvmAddress(id.getTokenNum());
    }

    public static byte[] asEvmAddress(final EntityId id) {
        return asEvmAddress(id.num());
    }

    public static Address asTypedEvmAddress(final AccountID id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final EntityId id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final ContractID id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static Address asTypedEvmAddress(final TokenID id) {
        return Address.wrap(Bytes.wrap(asEvmAddress(id)));
    }

    public static String asHexedEvmAddress(final Id id) {
        return CommonUtils.hex(asEvmAddress(id.num()));
    }

    public static byte[] asEvmAddress(final long num) {
        final byte[] evmAddress = new byte[20];
        arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);
        return evmAddress;
    }

    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromBytes(bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18], bytes[19]);
    }

    public static AccountID accountIdFromEvmAddress(final Address address) {
        return accountIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static ContractID contractIdFromEvmAddress(final Address address) {
        return contractIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static TokenID tokenIdFromEvmAddress(final Address address) {
        return tokenIdFromEvmAddress(address.toArrayUnsafe());
    }

    public static AccountID accountIdFromEvmAddress(final byte[] bytes) {
        return AccountID.newBuilder().setAccountNum(numFromEvmAddress(bytes)).build();
    }

    public static ContractID contractIdFromEvmAddress(final byte[] bytes) {
        return ContractID.newBuilder()
                .setContractNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }

    public static TokenID tokenIdFromEvmAddress(final byte[] bytes) {
        return TokenID.newBuilder()
                .setTokenNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
                .build();
    }

    public static String asLiteralString(final AccountID id) {
        return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getAccountNum());
    }

    public static String asLiteralString(final FileID id) {
        return String.format(ENTITY_ID_FORMAT, id.getShardNum(), id.getRealmNum(), id.getFileNum());
    }

    public static String asRelationshipLiteral(final long packedNumbers) {
        final var leftNum = unsignedHighOrder32From(packedNumbers);
        final var rightNum = unsignedLowOrder32From(packedNumbers);
        return "("
                + STATIC_PROPERTIES.scopedIdLiteralWith(leftNum)
                + ", "
                + STATIC_PROPERTIES.scopedIdLiteralWith(rightNum)
                + ")";
    }

    public static String asIdLiteral(final int num) {
        return STATIC_PROPERTIES.scopedIdLiteralWith(numFromCode(num));
    }

    public static String asScopedSerialNoLiteral(final long scopedSerialNo) {
        final var leftNum = unsignedHighOrder32From(scopedSerialNo);
        final var rightNum = unsignedLowOrder32From(scopedSerialNo);
        return STATIC_PROPERTIES.scopedIdLiteralWith(leftNum) + "." + rightNum;
    }

    public static boolean isAlias(final AccountID idOrAlias) {
        return idOrAlias.getAccountNum() == 0 && !idOrAlias.getAlias().isEmpty();
    }

    public static boolean isAlias(final ContractID idOrAlias) {
        return idOrAlias.getContractNum() == 0 && !idOrAlias.getEvmAddress().isEmpty();
    }

    public static EntityNum unaliased(final ContractID idOrAlias, final AliasManager aliasManager) {
        return unaliased(idOrAlias, aliasManager, null);
    }

    public static EntityNum unaliased(
            final ContractID idOrAlias,
            final AliasManager aliasManager,
            @Nullable final Consumer<ByteString> aliasObs) {
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.getEvmAddress();
            final var evmAddress = alias.toByteArray();
            if (HederaEvmContractAliases.isMirror(evmAddress)) {
                return EntityNum.fromLong(numOfMirror(evmAddress));
            }
            if (aliasObs != null) {
                aliasObs.accept(alias);
            }
            return aliasManager.lookupIdBy(alias);
        } else {
            return EntityNum.fromContractId(idOrAlias);
        }
    }

    public static long numOfMirror(final byte[] evmAddress) {
        return Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20));
    }

    public static EntityNum unaliased(final AccountID idOrAlias, final AliasManager aliasManager) {
        return unaliased(idOrAlias, aliasManager, null);
    }

    public static EntityNum unaliased(
            final AccountID idOrAlias, final AliasManager aliasManager, @Nullable final Consumer<ByteString> aliasObs) {
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.getAlias();
            final var evmAddress = alias.toByteArray();
            if (HederaEvmContractAliases.isMirror(evmAddress)) {
                final var accountNum = Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20));
                return EntityNum.fromLong(accountNum);
            }
            if (aliasObs != null) {
                aliasObs.accept(alias);
            }
            return aliasManager.lookupIdBy(alias);
        } else {
            return EntityNum.fromAccountId(idOrAlias);
        }
    }

    public static boolean isOfEvmAddressSize(final ByteString alias) {
        return alias.size() == EVM_ADDRESS_SIZE;
    }

    public static boolean isOfEvmAddressSize(final com.hedera.pbj.runtime.io.buffer.Bytes alias) {
        return alias.length() == EVM_ADDRESS_SIZE;
    }

    public static boolean isOfEvmAddressSize(final byte[] evmAddress) {
        return evmAddress.length == EVM_ADDRESS_SIZE;
    }

    public static boolean isOfEcdsaAddressSize(final ByteString alias) {
        return alias.size() == ECDSA_SECP256K1_ALIAS_SIZE;
    }

    public static boolean isAliasSizeGreaterThanEvmAddress(final com.hedera.pbj.runtime.io.buffer.Bytes alias) {
        return alias.length() > EVM_ADDRESS_SIZE;
    }

    public static boolean isAliasSizeGreaterThanEvmAddress(final ByteString alias) {
        return alias.size() > EVM_ADDRESS_SIZE;
    }
}
