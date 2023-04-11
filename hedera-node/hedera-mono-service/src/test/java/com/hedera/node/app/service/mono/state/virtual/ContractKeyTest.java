/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.ContractKey.MERKLE_VERSION;
import static com.hedera.node.app.service.mono.state.virtual.ContractKey.RUNTIME_CONSTRUCTABLE_ID;
import static com.hedera.node.app.service.mono.state.virtual.ContractKey.readKeySize;
import static com.hedera.node.app.service.mono.state.virtual.KeyPackingUtils.asPackedInts;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.google.common.primitives.Ints;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContractKeyTest {
    private final long contractNum = 1234L;
    private final long key = 123L;
    private final long otherContractNum = 1235L;
    private final long otherKey = 124L;
    private final UInt256 largeKey =
            UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
    private final UInt256 uIntKey = UInt256.valueOf(key);
    private final byte[] key_array = uIntKey.toArray();

    private ContractKey subject;

    @Test
    void orderingPrioritizesIdThenKey() {
        final var base = new ContractKey(contractNum, key);
        final var sameButDiff = base;
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum = new ContractKey(contractNum + 1, key);
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerKey = new ContractKey(contractNum, 1);
        assertEquals(+1, base.compareTo(smallerKey));
    }

    // The first two columns form a ContractKey(id, 32-byte word) and the third column is the
    // result of running ContractKey.hashCode() on that key using the v0.35.3 tag.
    @CsvSource({
        "5347959350198992124,cd53935a7e183dac3557134dfc73766479c520c7ffeace8723488bffa2da3fa2,-672529924",
        "4793581390134811504,4a164095f80400a99cc906b97cda21a0ccbdcc68c30f25acfb3be365188ffa92,-117896145",
        "495049458345236169,ccc59e0e848fc33929be60250bea580a0b627462a40caa923cef92b52521d91d,-2046179395",
        "1431041771021977088,b9d0e90be872a7dbec4374994456d827511b5119e91753e4c470bce5f70ae8c9,1791841599",
        "7691249564562953528,3c9c72d879d1f8d3c58bfd3646104bc4b3e15a1ab9620046110281148be061a5,-791511019",
        "5054707002752848259,a8d3ac6828f9b9c0c40d1d852928e32d2c3ac8594b833dfde0690d860c146679,-864395628",
        "3197462047532630427,302b67d47cae0cbb28a088c54e06a7f9d0f7294c79c9a24a7a7dd338c2120d04,-416588588",
        "7450692693395689069,83155a9613b37702263c403a1155634391fe2cb52a602c471111afc5556a10bc,-1845261180",
        "6927692228180407389,0eee723a1379e18de57b833948aaaa27a3ab50782ec3e3357c033324c8e6b6ea,1695791916",
        "4625164975176510159,f7526434d62eb147a11c0d186c795d98ad5053089f9b677fb99e637978bf8d6d,-173217838",
    })
    @ParameterizedTest
    void computesV035HashCodes(final long contractNum, final String keyHex, final int expectedCode) {
        final var word = CommonUtils.unhex(keyHex);
        final var subject = new ContractKey(contractNum, word);
        assertEquals(expectedCode, subject.hashCode());
    }

    @Test
    void equalsWork() {
        var testSubject1 = new ContractKey(contractNum, key);
        var testSubject2 = new ContractKey(contractNum, key_array);
        var testSubject3 =
                new ContractKey(contractNum, new int[] {0, 0, 0, 0, 0, 0, (int) (key >> Integer.SIZE), (int) key});
        var testSubject4 = new ContractKey(contractNum, otherKey);
        var testSubject5 = new ContractKey(otherContractNum, key);

        subject = new ContractKey();
        subject.setContractId(contractNum);
        subject.setKey(key);

        assertEquals(testSubject1, testSubject2);
        assertEquals(testSubject3, testSubject2);
        assertEquals(subject, testSubject3);
        assertEquals(subject, subject);
        assertNotEquals(subject, testSubject4);
        assertNotEquals(null, subject);
        assertNotEquals(subject, key);
        assertNotEquals(subject, testSubject5);
        assertArrayEquals(testSubject1.getKey(), testSubject2.getKey());
        assertEquals(testSubject2.getContractId(), testSubject3.getContractId());
        assertEquals(subject.toString(), testSubject1.toString());
        assertEquals(subject.getUint256Byte(0), testSubject2.getUint256Byte(0));
        var forcedEqualsCheck = subject.equals(key);
        assertFalse(forcedEqualsCheck, "forcing equals on two different class types.");
    }

    @ParameterizedTest
    @CsvSource({"0,1,4096,1048576,1099511627776,562949953421312,9223372036854775807"})
    void contractIdSerdesWork(final long contractId) throws IOException {
        final var key = new ContractKey(contractId, 0);
        final var buffer = ByteBuffer.allocate(256);
        key.serialize(buffer);
        buffer.rewind();
        buffer.get();
        final var deserializedId =
                ContractKey.deserializeContractID(key.getContractIdNonZeroBytes(), buffer, ByteBuffer::get);
        assertEquals(contractId, deserializedId);
    }

    @Test
    void gettersWork() {
        subject = new ContractKey(contractNum, key_array);

        assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertEquals(MERKLE_VERSION, subject.getVersion());
        assertEquals(BigInteger.valueOf(key), subject.getKeyAsBigInteger());
    }

    @Test
    void serializeWorks() throws IOException {
        subject = new ContractKey(contractNum, key_array);

        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();

        subject.serialize(out);

        inOrder.verify(out).write(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
            inOrder.verify(out).write((byte) (subject.getContractId() >> (b * 8)));
        }
        for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
            inOrder.verify(out).write(subject.getUint256Byte(b));
        }
    }

    @Test
    void serializeUsingByteBufferWorks() throws IOException {
        subject = new ContractKey(contractNum, key_array);

        final var out = mock(ByteBuffer.class);
        final var inOrder = inOrder(out);

        final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();

        subject.serialize(out);
        inOrder.verify(out).put(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
            inOrder.verify(out).put((byte) (subject.getContractId() >> (b * 8)));
        }
        for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
            inOrder.verify(out).put(subject.getUint256Byte(b));
        }
    }

    @Test
    void deserializeWorks() throws IOException {
        subject = new ContractKey(Long.MAX_VALUE, key);
        final var testSubject = new ContractKey();

        final var fin = mock(SerializableDataInputStream.class);
        given(fin.readByte())
                .willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (subject.getContractId() >> 56))
                .willReturn((byte) (subject.getContractId() >> 48))
                .willReturn((byte) (subject.getContractId() >> 40))
                .willReturn((byte) (subject.getContractId() >> 32))
                .willReturn((byte) (subject.getContractId() >> 24))
                .willReturn((byte) (subject.getContractId() >> 16))
                .willReturn((byte) (subject.getContractId() >> 8))
                .willReturn((byte) (subject.getContractId()))
                .willReturn(subject.getUint256Byte(0));

        testSubject.deserialize(fin, 1);

        assertEquals(subject, testSubject);
    }

    @Test
    void deserializeWithByteBufferWorks() throws IOException {
        subject = new ContractKey(contractNum, key);
        final var testSubject = new ContractKey();

        final var bin = mock(ByteBuffer.class);
        given(bin.get())
                .willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (subject.getContractId() >> 8))
                .willReturn((byte) (subject.getContractId()))
                .willReturn(subject.getUint256Byte(0));

        testSubject.deserialize(bin, 1);

        assertEquals(subject, testSubject);
    }

    @Test
    void deserializeLargeKeyWorks() throws IOException {
        subject = new ContractKey(contractNum, largeKey.toArray());

        final var fin = mock(SerializableDataInputStream.class);
        given(fin.readByte())
                .willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
                .willReturn((byte) (subject.getContractId() >> 8))
                .willReturn((byte) (subject.getContractId()))
                .willReturn(
                        subject.getUint256Byte(31),
                        subject.getUint256Byte(30),
                        subject.getUint256Byte(29),
                        subject.getUint256Byte(28),
                        subject.getUint256Byte(27),
                        subject.getUint256Byte(26),
                        subject.getUint256Byte(25),
                        subject.getUint256Byte(24),
                        subject.getUint256Byte(23),
                        subject.getUint256Byte(22),
                        subject.getUint256Byte(21),
                        subject.getUint256Byte(20),
                        subject.getUint256Byte(19),
                        subject.getUint256Byte(18),
                        subject.getUint256Byte(17),
                        subject.getUint256Byte(16),
                        subject.getUint256Byte(15),
                        subject.getUint256Byte(14),
                        subject.getUint256Byte(13),
                        subject.getUint256Byte(12),
                        subject.getUint256Byte(11),
                        subject.getUint256Byte(10),
                        subject.getUint256Byte(9),
                        subject.getUint256Byte(8),
                        subject.getUint256Byte(7),
                        subject.getUint256Byte(6),
                        subject.getUint256Byte(5),
                        subject.getUint256Byte(4),
                        subject.getUint256Byte(3),
                        subject.getUint256Byte(2),
                        subject.getUint256Byte(1),
                        subject.getUint256Byte(0));

        final var testSubject = new ContractKey();
        testSubject.deserialize(fin, 1);

        assertEquals(subject, testSubject);
    }

    @Test
    void readKeySizeWorks() {
        subject = new ContractKey(contractNum, key);
        final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
        final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();
        final var bin = mock(ByteBuffer.class);

        given(bin.get()).willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());

        assertEquals(1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes, readKeySize(bin));
    }

    @Test
    void calculatesNonZeroBytesCorrectly() {
        subject = new ContractKey(0, 0);

        assertEquals(1, subject.getContractIdNonZeroBytes());
        assertEquals(1, subject.getUint256KeyNonZeroBytes());
    }

    @Test
    void cannotUseInvalidKeys() {
        final var notEight = 7;
        final var not32 = 41;
        final int[] intArr = new int[notEight];
        final byte[] byteArr = new byte[not32];

        subject = new ContractKey();

        assertThrows(IllegalArgumentException.class, () -> new ContractKey(contractNum, (byte[]) null));
        assertThrows(IllegalArgumentException.class, () -> new ContractKey(contractNum, byteArr));
        assertThrows(IllegalArgumentException.class, () -> subject.setKey(null));
        assertThrows(IllegalArgumentException.class, () -> new ContractKey(contractNum, intArr));
    }

    @Test
    void toStringWorks() {
        subject = new ContractKey(contractNum, key);
        final var subjectDescription = "ContractKey{id=1234(4D2), key=123(0,0,0,0,0,0,0,7B)}";

        assertEquals(subjectDescription, subject.toString());
    }

    @Test
    void packsOneIntAsExpected() {
        final var ints = asPackedInts(UInt256.valueOf(100L).toArray());
        assertArrayEquals(new int[] {0, 0, 0, 0, 0, 0, 0, 100}, ints);
    }

    @Test
    void refusesToPackNonsense() {
        final byte[] nullBytes = null;
        assertThrows(IllegalArgumentException.class, () -> asPackedInts(nullBytes));
        final int not32 = 17;
        final var bytes = new byte[not32];
        assertThrows(IllegalArgumentException.class, () -> asPackedInts(bytes));
    }

    @CsvSource({
        "-1806530950,-2093446894,-1291596980,1836267745,-868711703,1994865710,1486235392,89268451",
        "1861848309,517745644,-243623496,2075815091,-629179791,757859505,327257688,-278391897",
        "576331089,516580313,-1405053523,-1435418758,840608222,-1665385228,-940878767,1825664739",
        "475306718,1500503753,677704189,-1451652196,-503828291,494501022,932345414,-1886498088",
        "1668223719,-533534549,1267228217,-771726856,1049051417,-211458117,-930866890,-768673597",
        "744849374,433724948,-472539246,1187920106,2054923808,1347467396,-11386934,-1632362211",
        "-1594118990,-844570361,1630591304,820749687,-1709165974,213582391,1311172337,918271256",
        "171804798,-1744236801,2094722701,685482263,-565603262,1968563920,-762655156,1481812521",
        "-844903528,-831683594,-16423900,-278321853,1873896845,-266656458,-1872718468,-510960702",
        "-22130352,1167136334,1555593921,-1288558840,1701145985,-2027232091,-1866141402,-2646571"
    })
    @ParameterizedTest
    void packsVariousAsExpected(
            final int a, final int b, final int c, final int d, final int e, final int f, final int g, final int h) {
        final byte[] aBytes = Ints.toByteArray(a);
        final byte[] bBytes = Ints.toByteArray(b);
        final byte[] cBytes = Ints.toByteArray(c);
        final byte[] dBytes = Ints.toByteArray(d);
        final byte[] eBytes = Ints.toByteArray(e);
        final byte[] fBytes = Ints.toByteArray(f);
        final byte[] gBytes = Ints.toByteArray(g);
        final byte[] hBytes = Ints.toByteArray(h);
        final byte[] bytes = {
            aBytes[0], aBytes[1], aBytes[2], aBytes[3],
            bBytes[0], bBytes[1], bBytes[2], bBytes[3],
            cBytes[0], cBytes[1], cBytes[2], cBytes[3],
            dBytes[0], dBytes[1], dBytes[2], dBytes[3],
            eBytes[0], eBytes[1], eBytes[2], eBytes[3],
            fBytes[0], fBytes[1], fBytes[2], fBytes[3],
            gBytes[0], gBytes[1], gBytes[2], gBytes[3],
            hBytes[0], hBytes[1], hBytes[2], hBytes[3]
        };

        final int[] actual = asPackedInts(bytes);
        assertEquals(a, actual[0]);
        assertEquals(b, actual[1]);
        assertEquals(c, actual[2]);
        assertEquals(d, actual[3]);
        assertEquals(e, actual[4]);
        assertEquals(f, actual[5]);
        assertEquals(g, actual[6]);
        assertEquals(h, actual[7]);
    }

    @Test
    void getContractIdNonZeroBytesAndUint256KeyNonZeroBytesWorksWithZeroValues() {
        subject = new ContractKey(0, 0);
        assertEquals(0, subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
    }

    @Test
    void computeNonZeroBytesWorkWithZeroInt() {
        assertEquals(1, KeyPackingUtils.computeNonZeroBytes(0));
    }
}
