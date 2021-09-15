package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractValueTest {
    private static class TestData {
        public final long longValue;
        public final BigInteger bigIntegerValue;
        public final byte[] bytesValue;

        public TestData(long longValue, byte[] bytesValue) {
            this.longValue = longValue;
            this.bigIntegerValue = BigInteger.valueOf(longValue);
            this.bytesValue = bytesValue;
        }
    }

    private static final TestData[] TEST_DATA = new TestData[] {
            new TestData(0,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,0}),
            new TestData(1,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,1}),
            new TestData(16,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,16}),
            new TestData(0xFF,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,0,(byte)0xFF}),
            new TestData(0xFFFF,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,0,0,0,0,(byte)0xFF,(byte)0xFF}),
            new TestData(0xFFFFFFFFFFFFL,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    0,0,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF}),
            new TestData(0x0FFFFFFFFFFFFFFFL,new byte[]{0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0,
                    (byte)0x0F,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF,(byte)0xFF}),
    };


    @Test
    public void testLongConstructors() {
        for (var testData:TEST_DATA) {
            assertEquals(Arrays.toString(testData.bytesValue),
                    Arrays.toString(new ContractValue(testData.longValue).getValue()));
            assertEquals(new ContractValue(testData.longValue),new ContractValue(testData.bigIntegerValue));
            assertEquals(new ContractValue(testData.longValue),new ContractValue(testData.bytesValue));
        }
    }

    @Test
    public void testAsAndGetters() {
        for (var testData:TEST_DATA) {
            final ContractValue expected = new ContractValue(testData.longValue);
            assertEquals(Arrays.toString(testData.bytesValue), Arrays.toString(expected.getValue()));
            assertEquals(testData.longValue,expected.asLong());
            assertEquals(testData.bigIntegerValue,expected.asBigInteger());
        }
    }
    @Test
    public void testSetters() {
        for (var testData:TEST_DATA) {
            final ContractValue expected = new ContractValue(testData.longValue);

            final ContractValue longTest = new ContractValue();
            longTest.setValue(testData.longValue);
            assertEquals(expected, longTest);

            final ContractValue bigIntTest = new ContractValue();
            bigIntTest.setValue(testData.bigIntegerValue);
            assertEquals(expected,bigIntTest);

            final ContractValue bytesTest = new ContractValue();
            bytesTest.setValue(testData.bytesValue);
            assertEquals(expected,bytesTest);
        }
    }

    @Test
    public void testSerializeDeserialize() throws IOException {
        for (var testData:TEST_DATA) {
            testSerializeDeserialize(new ContractValue(testData.longValue));
        }
    }

    private void testSerializeDeserialize(ContractValue value) throws IOException {
        // using byte buffers
        ByteBuffer buf = ByteBuffer.allocate(32);
        value.serialize(buf);
        buf.flip();
        ContractValue value2 = new ContractValue();
        value2.deserialize(buf,value.getVersion());
        assertEquals(value, value2);
        // using streams
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1+8+32);
        SerializableDataOutputStream sout = new SerializableDataOutputStream(bout);
        value.serialize(sout);
        bout.flush();
        bout.close();
        byte[] bytes = bout.toByteArray();
        ContractValue value3 = new ContractValue();
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        SerializableDataInputStream sin = new SerializableDataInputStream(bin);
        value3.deserialize(sin,value.getVersion());
        assertEquals(value, value3);
    }

    @Test
    public void testHash() {
        List<Integer> hashes = new ArrayList<>();
        hashes.add(new ContractValue(0).hashCode());
        hashes.add(new ContractValue(1).hashCode());
        hashes.add(new ContractValue(0xA5A5L).hashCode());
        hashes.add(new ContractValue(0xA5A5A5L).hashCode());
        hashes.add(new ContractValue(0xA5A5A5A5A5A5A5A5L).hashCode());
        for (int i = 0; i< hashes.size();i++) {
            final var hash = hashes.get(i);
            assertEquals(1,hashes.stream().filter(h -> h.equals(hash)).count(),"i= "+i+" hash="+hash);
        }
    }

    @Test
    public void testGetClassId() {
        assertEquals(0xd7c4802f00979857L,new ContractValue().getClassId());
    }

    @Test
    public void testGetVersion() {
        assertEquals(1,new ContractValue().getVersion());
    }
}
