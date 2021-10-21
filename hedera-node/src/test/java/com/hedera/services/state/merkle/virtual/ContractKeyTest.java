package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ContractKeyTest {

    @Test
    public void testComputeNonZeroBytes() {
        assertEquals(1,ContractKey.computeNonZeroBytes(0x0000000000000000L));
        assertEquals(1,ContractKey.computeNonZeroBytes(0x000000000000000FL));
        assertEquals(1,ContractKey.computeNonZeroBytes(0x00000000000000FFL));
        assertEquals(2,ContractKey.computeNonZeroBytes(0x0000000000000FFFL));
        assertEquals(2,ContractKey.computeNonZeroBytes(0x000000000000FFFFL));
        assertEquals(3,ContractKey.computeNonZeroBytes(0x0000000000FFFFFFL));
        assertEquals(4,ContractKey.computeNonZeroBytes(0x00000000FFFFFFFFL));
        assertEquals(5,ContractKey.computeNonZeroBytes(0x000000FFFFFFFFFFL));
        assertEquals(6,ContractKey.computeNonZeroBytes(0x0000FFFFFFFFFFFFL));
        assertEquals(7,ContractKey.computeNonZeroBytes(0x00FFFFFFFFFFFFFFL));
        assertEquals(8,ContractKey.computeNonZeroBytes(0xFFFFFFFFFFFFFFFFL));
        assertEquals(1,ContractKey.computeNonZeroBytes(0x00000000));
        assertEquals(1,ContractKey.computeNonZeroBytes(0x0000000F));
        assertEquals(1,ContractKey.computeNonZeroBytes(0x000000FF));
        assertEquals(2,ContractKey.computeNonZeroBytes(0x00000FFF));
        assertEquals(2,ContractKey.computeNonZeroBytes(0x0000FFFF));
        assertEquals(3,ContractKey.computeNonZeroBytes(0x00FFFFFF));
        assertEquals(4,ContractKey.computeNonZeroBytes(0xFFFFFFFF));
        assertEquals(1,ContractKey.computeNonZeroBytes(new int[]{0,0,0,0,0,0,0,0}));
        assertEquals(1,ContractKey.computeNonZeroBytes(new int[]{0,0,0,0,0,0,0,0x000000FF}));
        assertEquals(2,ContractKey.computeNonZeroBytes(new int[]{0,0,0,0,0,0,0,0x0000FFFF}));
        assertEquals(4,ContractKey.computeNonZeroBytes(new int[]{0,0,0,0,0,0,0,0xFFFFFFFF}));
        assertEquals(6,ContractKey.computeNonZeroBytes(new int[]{0,0,0,0,0,0,0x0000FFFF,0xFFFFFFFF}));
        assertEquals(8,ContractKey.computeNonZeroBytes(new int[]{0,0,0,0,0,0,0xFFFFFFFF,0xFFFFFFFF}));
        assertEquals(32,ContractKey.computeNonZeroBytes(new int[]{0xFFFFFFFF,0xFFFFFFFF,0xFFFFFFFF,0xFFFFFFFF,0xFFFFFFFF,0xFFFFFFFF,0xFFFFFFFF,0xFFFFFFFF}));
    }

    @Test
    public void testGetUint256Byte() {
        ContractKey key;

        key = new ContractKey(0,0);
        for (int i = 0; i < 32; i++)  assertEquals(0,key.getUint256Byte(i));

        key = new ContractKey(0,0x00FFFFFFFFFFFFFFL);
        for (int i = 0; i < 7; i++) assertEquals((byte)0xFF,key.getUint256Byte(i));
        for (int i = 7; i < 32; i++) assertEquals(0,key.getUint256Byte(i));

        key = new ContractKey(0,0xFFFFFFFFFFFFFFFFL);
        for (int i = 0; i < 8; i++) assertEquals((byte)0xFF,key.getUint256Byte(i));
        for (int i = 8; i < 32; i++) assertEquals(0,key.getUint256Byte(i));

        key = new ContractKey(0,new int[]{0,0,0,0,0,0x00FFFFFF,0xFFFFFFFF,0xFFFFFFFF});
        for (int i = 0; i < 11; i++) assertEquals((byte)0xFF,key.getUint256Byte(i));
        for (int i = 11; i < 32; i++) assertEquals(0,key.getUint256Byte(i));
    }


    @Test
    public void testLongConstructor() {
        ContractKey contractKey;

        contractKey = new ContractKey(0,0);
        assertEquals(0,contractKey.getContractId());
        assertEquals(Arrays.toString(new int[]{0,0,0,0,0,0,0,0}),Arrays.toString(contractKey.getKey()));
        assertEquals(1,contractKey.getContractIdNonZeroBytes());
        assertEquals(1,contractKey.getUint256KeyNonZeroBytes());

        contractKey = new ContractKey(1,0);
        assertEquals(1,contractKey.getContractId());
        assertEquals(Arrays.toString(new int[]{0,0,0,0,0,0,0,0}),Arrays.toString(contractKey.getKey()));
        assertEquals(1,contractKey.getContractIdNonZeroBytes());
        assertEquals(1,contractKey.getUint256KeyNonZeroBytes());

        contractKey = new ContractKey(0,1);
        assertEquals(0,contractKey.getContractId());
        assertEquals(Arrays.toString(new int[]{0,0,0,0,0,0,0,1}),Arrays.toString(contractKey.getKey()));
        assertEquals(1,contractKey.getContractIdNonZeroBytes());
        assertEquals(1,contractKey.getUint256KeyNonZeroBytes());

        contractKey = new ContractKey(1,1);
        assertEquals(1,contractKey.getContractId());
        assertEquals(Arrays.toString(new int[]{0,0,0,0,0,0,0,1}),Arrays.toString(contractKey.getKey()));
        assertEquals(1,contractKey.getContractIdNonZeroBytes());
        assertEquals(1,contractKey.getUint256KeyNonZeroBytes());

        contractKey = new ContractKey(0x0000FFFF00FFFFFFL,0x0000FFFF00FFFFFFL);
        assertEquals(0x0000FFFF00FFFFFFL,contractKey.getContractId());
        assertEquals(Arrays.toString(new int[]{0,0,0,0,0,0,0x0000FFFF,0x00FFFFFF}),Arrays.toString(contractKey.getKey()));
        assertEquals(6,contractKey.getContractIdNonZeroBytes());
        assertEquals(6,contractKey.getUint256KeyNonZeroBytes());
    }

    @Test
    public void testSerialize() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(1+8+32);

        buf.clear();
        new ContractKey(0,0).serialize(buf);
        printSerializedData(buf.array(),buf.position());
        assertEquals(3,buf.position());
        assertEquals(0,buf.array()[0]);
        assertEquals(0,buf.array()[1]);
        assertEquals(0,buf.array()[2]);

        buf.clear();
        new ContractKey(1,0).serialize(buf);
        printSerializedData(buf.array(),buf.position());
        assertEquals(3,buf.position());
        assertEquals(0,buf.array()[0]);
        assertEquals(1,buf.array()[1]);
        assertEquals(0,buf.array()[2]);

        buf.clear();
        new ContractKey(0,1).serialize(buf);
        printSerializedData(buf.array(),buf.position());
        assertEquals(3,buf.position());
        assertEquals(0,buf.array()[0]);
        assertEquals(0,buf.array()[1]);
        assertEquals(1,buf.array()[2]);

        buf.clear();
        new ContractKey(0x0000000000FFFFFFL,0x0000000000FFFFFFL).serialize(buf);
        printSerializedData(buf.array(),buf.position());
        assertEquals(7,buf.position());
        assertEquals((byte)0b01000010,buf.array()[0]);
        assertEquals((byte)0xFF,buf.array()[1]);
        assertEquals((byte)0xFF,buf.array()[2]);
        assertEquals((byte)0xFF,buf.array()[3]);
        assertEquals((byte)0xFF,buf.array()[4]);
        assertEquals((byte)0xFF,buf.array()[5]);
        assertEquals((byte)0xFF,buf.array()[6]);

        buf.clear();
        new ContractKey(0x0000000000A5A5A5L,0x0000000000A5A5A5L).serialize(buf);
        printSerializedData(buf.array(),buf.position());
        assertEquals(7,buf.position());
        assertEquals((byte)0b01000010,buf.array()[0]);
        assertEquals((byte)0xA5,buf.array()[1]);
        assertEquals((byte)0xA5,buf.array()[2]);
        assertEquals((byte)0xA5,buf.array()[3]);
        assertEquals((byte)0xA5,buf.array()[4]);
        assertEquals((byte)0xA5,buf.array()[5]);
        assertEquals((byte)0xA5,buf.array()[6]);
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        testSerializeDeserialize(new ContractKey(0,0));
        testSerializeDeserialize(new ContractKey(1,0));
        testSerializeDeserialize(new ContractKey(0xA5A5L,0));
        testSerializeDeserialize(new ContractKey(0xA5A5A5L,0));
        testSerializeDeserialize(new ContractKey(0xA5A5A5A5L,0));
        testSerializeDeserialize(new ContractKey(0xA5A5A5A5A5A5A5A5L,0));
        testSerializeDeserialize(new ContractKey(0,1));
        testSerializeDeserialize(new ContractKey(0,0xA5A5L));
        testSerializeDeserialize(new ContractKey(0,0xA5A5A5L));
        testSerializeDeserialize(new ContractKey(0,0xA5A5A5A5A5A5A5A5L));
        testSerializeDeserialize(new ContractKey(0,new int[]{0,0,0,0,0,3,2,1}));
        testSerializeDeserialize(new ContractKey(0,new int[]{0,0,0,5,4,3,2,1}));
        testSerializeDeserialize(new ContractKey(0,new int[]{8,7,6,5,4,3,2,1}));
    }

    private void testSerializeDeserialize(ContractKey key) throws IOException {
        // using byte buffers
        ByteBuffer buf = ByteBuffer.allocate(1+8+32);
        key.serialize(buf);
        buf.flip();
        printSerializedData(buf.array(),buf.remaining());
        ContractKey key2 = new ContractKey();
        key2.deserialize(buf,key.getVersion());
        assertEquals(key, key2);
        // using streams
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1+8+32);
        SerializableDataOutputStream sout = new SerializableDataOutputStream(bout);
        key.serialize(sout);
        bout.flush();
        bout.close();
        byte[] bytes = bout.toByteArray();
        printSerializedData(bytes,bytes.length);
        ContractKey key3 = new ContractKey();
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        SerializableDataInputStream sin = new SerializableDataInputStream(bin);
        key3.deserialize(sin,key.getVersion());
        assertEquals(key, key3);
    }

    @Test
    public void testContractIdNonZeroBytesAndUint256KeyNonZeroBytes() {
        assertEquals(0,new ContractKey(0,0).getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        assertEquals(0,new ContractKey(1,0).getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        assertEquals(0,new ContractKey(0,1).getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        assertEquals(0,new ContractKey(1,1).getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        assertEquals((byte)0b00100000,new ContractKey(0xFFFF,1).getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        assertEquals((byte)0b00100001,new ContractKey(0xFFFF,0xFFFF).getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
        assertEquals(1,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0));
        assertEquals(1,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0));
        assertEquals(2,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0b00100000));
        assertEquals(1,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0b00100000));
        assertEquals(2,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0b00100001));
        assertEquals(2,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0b00100001));
        assertEquals(3,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0b01000010));
        assertEquals(3,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0b01000010));
        assertEquals(8,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0b11111111));
        assertEquals(32,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0b11111111));
        assertEquals(1,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0));
        assertEquals(1,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0));
        assertEquals(1,ContractKey.getContractIdNonZeroBytesFromPacked((byte)0));
        assertEquals(1,ContractKey.getUint256KeyNonZeroBytesFromPacked((byte)0));
    }

    @Test
    public void testHash() {
        List<Integer> hashes = new ArrayList<>();
        hashes.add(new ContractKey(1,0).hashCode());
        hashes.add(new ContractKey(0xA5A5L,0).hashCode());
        hashes.add(new ContractKey(0xA5A5A5L,0).hashCode());
        hashes.add(new ContractKey(0xA5A5A5A5L,0).hashCode());
        hashes.add(new ContractKey(0xA5A5A5A5A5A5A5A5L,0).hashCode());
        hashes.add(new ContractKey(0,1).hashCode());
        hashes.add(new ContractKey(0,0xA5A5L).hashCode());
        hashes.add(new ContractKey(0,0xA5A5A5L).hashCode());
        hashes.add(new ContractKey(0,0xA5A5A5A5A5A5A5A5L).hashCode());
        hashes.add(new ContractKey(0,new int[]{0,0,0,0,0,3,2,1}).hashCode());
        hashes.add(new ContractKey(0,new int[]{0,0,0,5,4,3,2,1}).hashCode());
        hashes.add(new ContractKey(0,new int[]{8,7,6,5,4,3,2,1}).hashCode());
        for (int i = 0; i< hashes.size();i++) {
            final var hash = hashes.get(i);
            assertEquals(1,hashes.stream().filter(h -> h.equals(hash)).count(),"i= "+i+" hash="+hash);
        }
    }

    @Test
    public void testGetClassId() {
        assertEquals(0xb2c0a1f733950abdL,new ContractKey().getClassId());
    }

    @Test
    public void testGetVersion() {
        assertEquals(1,new ContractKey().getVersion());
    }

    public void printSerializedData(byte[] data, int length) {
        var contractIdNonZeroBytes = (byte)(data[0] >> 4);
        var uint256KeyNonZeroBytes = (byte)(data[0] & 0b00001111);
        final var hexBytes = new ArrayList<String>();
        for (int i = 0; i < length; i++) hexBytes.add(String.format("%02X ", data[i]).toUpperCase());
        final var hexData = String.join(",", hexBytes);
        System.out.println("SerializedData -> IdLength=" + contractIdNonZeroBytes+" uintLength="+uint256KeyNonZeroBytes
                +" data="+hexData);
    }
}
