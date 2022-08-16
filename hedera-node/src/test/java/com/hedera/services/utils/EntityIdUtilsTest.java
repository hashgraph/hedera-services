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
package com.hedera.services.utils;

import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.unaliased;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.BDDMockito.given;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdUtilsTest {
    @Mock private AliasManager aliasManager;

    @Test
    void echoesUnaliasedContractId() {
        final var literalId = ContractID.newBuilder().setContractNum(1234).build();

        assertEquals(EntityNum.fromLong(1234), EntityIdUtils.unaliased(literalId, aliasManager));
        assertEquals(
                EntityNum.MISSING_NUM,
                EntityIdUtils.unaliased(ContractID.getDefaultInstance(), aliasManager));
    }

    @Test
    void useEvmAddressDirectlyIfMirror() {
        final byte[] mockAddr = unhex("0000000000000000000000009abcdefabcdefbbb");
        final var num = Longs.fromByteArray(Arrays.copyOfRange(mockAddr, 12, 20));
        final var expectedId = EntityNum.fromLong(num);
        final var input =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(mockAddr)).build();

        given(aliasManager.isMirror(mockAddr)).willReturn(true);
        assertEquals(expectedId, EntityIdUtils.unaliased(input, aliasManager));
    }

    @Test
    void extractsMirrorNum() {
        final byte[] mockAddr = unhex("0000000000000000000000009abcdefabcdefbbb");

        assertEquals(0x9abcdefabcdefbbbL, EntityIdUtils.numOfMirror(mockAddr));
    }

    @Test
    void returnsResolvedContractIdIfNonMirro() {
        final byte[] mockAddr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
        final var extantNum = EntityNum.fromLong(1_234_567L);
        final var input =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(mockAddr)).build();
        given(aliasManager.lookupIdBy(ByteString.copyFrom(mockAddr))).willReturn(extantNum);

        assertEquals(extantNum, EntityIdUtils.unaliased(input, aliasManager));
    }

    @Test
    void echoesUnaliasedAccountId() {
        final var literalId = AccountID.newBuilder().setAccountNum(1234).build();

        assertEquals(EntityNum.fromLong(1234), unaliased(literalId, aliasManager));
        assertEquals(
                EntityNum.MISSING_NUM, unaliased(AccountID.getDefaultInstance(), aliasManager));
    }

    @Test
    void useAliasDirectlyIfMirror() {
        final byte[] mockAddr = unhex("0000000000000000000000009abcdefabcdefbbb");
        final var num = Longs.fromByteArray(Arrays.copyOfRange(mockAddr, 12, 20));
        final var expectedId = EntityNum.fromLong(num);
        final var input = AccountID.newBuilder().setAlias(ByteString.copyFrom(mockAddr)).build();

        given(aliasManager.isMirror(mockAddr)).willReturn(true);
        assertEquals(expectedId, unaliased(input, aliasManager));
    }

    @Test
    void returnsResolvedAccountIdIfNonMirro() {
        final byte[] mockAddr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
        final var extantNum = EntityNum.fromLong(1_234_567L);
        final var input = AccountID.newBuilder().setAlias(ByteString.copyFrom(mockAddr)).build();
        given(aliasManager.lookupIdBy(ByteString.copyFrom(mockAddr))).willReturn(extantNum);

        assertEquals(extantNum, unaliased(input, aliasManager));
    }

    @Test
    void observesUnalising() {
        final byte[] mockAddr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
        final var extantNum = EntityNum.fromLong(1_234_567L);
        final var input = AccountID.newBuilder().setAlias(ByteString.copyFrom(mockAddr)).build();
        given(aliasManager.lookupIdBy(ByteString.copyFrom(mockAddr))).willReturn(extantNum);

        AtomicReference<ByteString> observer = new AtomicReference<>();
        unaliased(input, aliasManager, observer::set);
        assertEquals(ByteString.copyFrom(mockAddr), observer.get());
    }

    @Test
    void correctLiteral() {
        assertEquals("1.2.3", asLiteralString(asAccount("1.2.3")));
        assertEquals("11.22.33", asLiteralString(IdUtils.asFile("11.22.33")));
    }

    @Test
    void serializesExpectedSolidityAddress() {
        final byte[] shardBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
        };
        final var shard = Ints.fromByteArray(shardBytes);
        final byte[] realmBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
            (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
        };
        final var realm = Longs.fromByteArray(realmBytes);
        final byte[] numBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
            (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
        };
        final var num = Longs.fromByteArray(numBytes);
        final byte[] expected = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
            (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
            (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
        };
        final var create2AddressBytes = Hex.decode("0102030405060708090a0b0c0d0e0f1011121314");
        final var equivAccount = asAccount(String.format("%d.%d.%d", shard, realm, num));
        final var equivContract = asContract(String.format("%d.%d.%d", shard, realm, num));
        final var equivToken = asToken(String.format("%d.%d.%d", shard, realm, num));
        final var create2Contract =
                ContractID.newBuilder()
                        .setEvmAddress(ByteString.copyFrom(create2AddressBytes))
                        .build();

        final var actual = asEvmAddress(shard, realm, num);
        final var typedActual = EntityIdUtils.asTypedEvmAddress(equivAccount);
        final var typedToken = EntityIdUtils.asTypedEvmAddress(equivToken);
        final var anotherActual = EntityIdUtils.asEvmAddress(equivContract);
        final var create2Actual = EntityIdUtils.asEvmAddress(create2Contract);
        final var actualHex = EntityIdUtils.asHexedEvmAddress(equivAccount);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, anotherActual);
        assertArrayEquals(expected, typedActual.toArray());
        assertArrayEquals(expected, typedToken.toArray());
        assertArrayEquals(create2AddressBytes, create2Actual);
        assertEquals(CommonUtils.hex(expected), actualHex);
        assertEquals(equivAccount, EntityIdUtils.accountIdFromEvmAddress(actual));
        assertEquals(equivContract, contractIdFromEvmAddress(actual));
        assertEquals(equivToken, tokenIdFromEvmAddress(actual));
    }

    @ParameterizedTest
    @CsvSource({
        "0,Cannot parse '0' due to only 0 dots",
        "0.a.0,Argument 'literal=0.a.0' is not an account",
        "...,Argument 'literal=...' is not an account",
        "1.2.3.4,Argument 'literal=1.2.3.4' is not an account",
        "1.2.three,Argument 'literal=1.2.three' is not an account",
        "1.2.333333333333333333333,Cannot parse '1.2.333333333333333333333' due to overflow"
    })
    void rejectsInvalidAccountLiterals(final String badLiteral, final String desiredMsg) {
        final var e = assertThrows(IllegalArgumentException.class, () -> parseAccount(badLiteral));
        assertEquals(desiredMsg, e.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"1.0.0", "0.1.0", "0.0.1", "1.2.3"})
    void parsesValidLiteral(final String goodLiteral) {
        assertEquals(asAccount(goodLiteral), parseAccount(goodLiteral));
    }

    @Test
    void prettyPrintsIds() {
        final var id = new Id(1, 2, 3);

        assertEquals("1.2.3", EntityIdUtils.readableId(id));
    }

    @Test
    void prettyPrintsScheduleIds() {
        final var id =
                ScheduleID.newBuilder().setShardNum(1).setRealmNum(2).setScheduleNum(3).build();

        assertEquals("1.2.3", EntityIdUtils.readableId(id));
    }

    @Test
    void asSolidityAddressHexWorksProperly() {
        final var id = new Id(1, 2, 3);

        assertEquals(
                "0000000100000000000000020000000000000003", EntityIdUtils.asHexedEvmAddress(id));
    }

    @Test
    void asSolidityAddressBytesWorksProperly() {
        final var id =
                AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

        final var result = EntityIdUtils.asEvmAddress(id);

        final var expectedBytes =
                new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asSolidityAddressBytesFromToken() {
        final var id = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();

        final var result = EntityIdUtils.asEvmAddress(id);

        final var expectedBytes =
                new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void prettyPrintsTokenIds() {
        final var id = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3).build();

        assertEquals("1.2.3", EntityIdUtils.readableId(id));
    }

    @Test
    void prettyPrintsTopicIds() {
        final var id = TopicID.newBuilder().setShardNum(1).setRealmNum(2).setTopicNum(3).build();

        assertEquals("1.2.3", EntityIdUtils.readableId(id));
    }

    @Test
    void prettyPrintsAccountIds() {
        final var id =
                AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

        assertEquals("1.2.3", EntityIdUtils.readableId(id));
    }

    @Test
    void prettyPrintsFileIds() {
        final var id = FileID.newBuilder().setShardNum(1).setRealmNum(2).setFileNum(3).build();

        assertEquals("1.2.3", EntityIdUtils.readableId(id));
    }

    @Test
    void prettyPrintsNftIds() {
        final var tokenID = TokenID.newBuilder().setShardNum(1).setRealmNum(2).setTokenNum(3);
        final var id = NftID.newBuilder().setTokenID(tokenID).setSerialNumber(1).build();

        assertEquals("1.2.3.1", EntityIdUtils.readableId(id));
    }

    @Test
    void givesUpOnNonAccountIds() {
        final String id = "my-account";

        assertEquals(id, EntityIdUtils.readableId(id));
    }

    @Test
    void asContractWorks() {
        final var expected =
                ContractID.newBuilder().setShardNum(1).setRealmNum(2).setContractNum(3).build();
        final var id =
                AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

        final var cid = EntityIdUtils.asContract(id);

        assertEquals(expected, cid);
    }

    @Test
    void asAccountFromContractWorks() {
        final var expected =
                AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();
        final var id =
                ContractID.newBuilder().setShardNum(1).setRealmNum(2).setContractNum(3).build();

        final var aid = EntityIdUtils.asAccount(id);

        assertEquals(expected, aid);
    }

    @Test
    void asAccountFromEntityWorks() {
        final var expected =
                AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();
        final var id =
                EntityId.fromGrpcAccountId(
                        AccountID.newBuilder()
                                .setShardNum(1)
                                .setRealmNum(2)
                                .setAccountNum(3)
                                .build());

        final var aid = EntityIdUtils.asAccount(id);

        assertEquals(expected, aid);
    }

    @Test
    void asFileWorks() {
        final var expected =
                FileID.newBuilder().setShardNum(1).setRealmNum(2).setFileNum(3).build();
        final var id =
                AccountID.newBuilder().setShardNum(1).setRealmNum(2).setAccountNum(3).build();

        final var fid = EntityIdUtils.asFile(id);

        assertEquals(expected, fid);
    }

    @Test
    void asRelStringWorks() {
        // given:
        final var numbers = BitPackUtils.packedNums(123, 456);

        // when:
        final var actual = EntityIdUtils.asRelationshipLiteral(numbers);

        // then:
        assertEquals("(0.0.123, 0.0.456)", actual);
    }

    @Test
    void codeToLiteralWorks() {
        // setup:
        final var bigNum = (long) Integer.MAX_VALUE + 123;

        // given:
        final var lit = EntityIdUtils.asIdLiteral(BitPackUtils.codeFromNum(bigNum));

        // expect:
        assertEquals("0.0." + bigNum, lit);
    }

    @Test
    void scopedSerialNoWorks() {
        // setup:
        final var bigNum = (long) Integer.MAX_VALUE + 123;
        final var serialNo = bigNum + 1;

        // given:
        final var lit =
                EntityIdUtils.asScopedSerialNoLiteral(BitPackUtils.packedNums(bigNum, serialNo));

        // expect:
        assertEquals("0.0." + bigNum + "." + serialNo, lit);
    }

    @CsvSource({"1-2,1,2", "123-456,123,456"})
    @ParameterizedTest
    void canParseValidRanges(String literal, long left, long right) {
        final var range = EntityIdUtils.parseEntityNumRange(literal);

        assertEquals(left, range.getLeft(), "Left endpoint should match");
        assertEquals(right, range.getRight(), "Right endpoint should match");
    }

    @CsvSource({
        "nonsense,Argument literal='nonsense' is not a valid range literal",
        "-1-,Argument literal='-1-' is not a valid range literal",
        "-2,Argument literal='-2' is not a valid range literal",
        "123-,Argument literal='123-' is not a valid range literal",
        "456-123,Range left endpoint 456 should be <= right endpoint 123",
        "12345678901234567890-123,Argument literal='12345678901234567890-123' has malformatted long"
                + " value"
    })
    @ParameterizedTest
    void rejectsInvalidRanges(String literal, String iaeMsg) {
        try {
            EntityIdUtils.parseEntityNumRange(literal);
        } catch (IllegalArgumentException iae) {
            assertEquals(iaeMsg, iae.getMessage(), "Exception message should be well-formatted");
            return;
        }
        fail("'" + literal + "' should be rejected with " + iaeMsg);
    }
}
