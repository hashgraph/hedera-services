package com.hedera.services.state.jasperdb;

import com.hedera.services.state.merkle.virtual.ContractKey;
import com.hedera.services.state.merkle.virtual.ContractUint256;
import com.hedera.services.store.models.Id;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class JasperDbTestUtils {

    public static ContractKey newContractKey(long num) {
//        return new ContractKey(new Id(num,num,num),new ContractUint256(num));
        return new ContractKey(new Id(0,0,num),new ContractUint256(num));
    }

    public static void deleteDirectoryAndContents(Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                Files.deleteIfExists(dir);
            } catch (Exception e) {
                System.err.println("Failed to delete test directory ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
            System.out.println("Deleted data files");
        }
    }

    public static String toLongsString(Hash hash) {
        final var bytes = hash.getValue();
        LongBuffer longBuf =
                ByteBuffer.wrap(bytes)
                        .order(ByteOrder.BIG_ENDIAN)
                        .asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        return Arrays.toString(array);
    }

    public static String toIntsString(ByteBuffer buf) {
        buf.rewind();
        IntBuffer intBuf = buf.asIntBuffer();
        int[] array = new int[intBuf.remaining()];
        intBuf.get(array);
        buf.rewind();
        return Arrays.toString(array);
    }

    public static String toLongsString(ByteBuffer buf) {
        buf.rewind();
        LongBuffer longBuf = buf.asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        buf.rewind();
        return Arrays.toString(array);
    }

    /**
     * Creates a hash containing a int repeated 6 times as longs
     *
     * @return byte array of 6 longs
     */
    public static Hash hash(int value) {
        byte b0 = (byte)(value >>> 24);
        byte b1 = (byte)(value >>> 16);
        byte b2 = (byte)(value >>> 8);
        byte b3 = (byte)value;
        return new HashTools.NoCopyHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
    }

    public static void hexDump(PrintStream out, Path file) throws IOException {
        InputStream is = new FileInputStream(file.toFile());
        int i = 0;

        while (is.available() > 0) {
            StringBuilder sb1 = new StringBuilder();
            StringBuilder sb2 = new StringBuilder("   ");
            out.printf("%04X  ", i * 16);
            for (int j = 0; j < 16; j++) {
                if (is.available() > 0) {
                    int value = (int) is.read();
                    sb1.append(String.format("%02X ", value));
                    if (!Character.isISOControl(value)) {
                        sb2.append((char)value);
                    }
                    else {
                        sb2.append(".");
                    }
                }
                else {
                    for (;j < 16;j++) {
                        sb1.append("   ");
                    }
                }
            }
            out.print(sb1);
            out.println(sb2);
            i++;
        }
        is.close();
    }

    /**
     * Code from method java.util.Collections.shuffle();
     */
    public static int[] shuffle(Random random, int[] array) {
        if (random == null) random = new Random();
        int count = array.length;
        for (int i = count; i > 1; i--) {
            swap(array, i - 1, random.nextInt(i));
        }
        return array;
    }

    private static void swap(int[] array, int i, int j) {
        int temp = array[i];
        array[i] = array[j];
        array[j] = temp;
    }

    public static String randomString(int length, Random random) {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    public static byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }
    // =================================================================================================================
    // useful classes

    public static final class TestHash extends Hash {
        public TestHash(byte[] bytes) {
            super(bytes, DigestType.SHA_384, true, false);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static final class TestLeafData implements VirtualValue {
        public static final int SIZE_BYTES = Integer.BYTES+1024;
        static final byte[] RANDOM_1K = new byte[1024];
        static {
            new Random().nextBytes(RANDOM_1K);
        }
        private int id;
        private byte[] data;

        public TestLeafData() {}

        public TestLeafData(int id) {
            this.id = id;
            this.data = RANDOM_1K;
        }

        @Override
        public void deserialize(SerializableDataInputStream inputStream, int i) throws IOException {
            id = inputStream.readInt();
            data = new byte[1024];
            inputStream.read(data);
        }

        @Override
        public void serialize(SerializableDataOutputStream outputStream) throws IOException {
            outputStream.writeInt(id);
            outputStream.write(data);
        }

        @Override
        public void serialize(ByteBuffer buffer) throws IOException {
            buffer.putInt(id);
            buffer.put(data);
        }

        @Override
        public void deserialize(ByteBuffer buffer, int version) throws IOException {
            id = buffer.getInt();
            data = new byte[1024];
            buffer.get(data);
        }

        @Override
        public VirtualValue copy() {
            return this;
        }

        @Override
        public VirtualValue asReadOnly() {
            return this;
        }

        @Override
        public long getClassId() {
            return 1234;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestLeafData that = (TestLeafData) o;
            return id == that.id && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(id);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }

        @Override
        public String toString() {
            return "TestLeafData{" +
                    "id=" + id +
                    ", data=" + Arrays.toString(data) +
                    '}';
        }

        @Override
        public void release() {

        }
    }

    /**
     * Wrap a VirtualDataSource turning all execptions into runtime exceptions for testing
     */
    public static class VFCDataSourceExceptionWrapper<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K,V> {
        private final VirtualDataSource<K,V> dataSource;

        public VFCDataSourceExceptionWrapper(VirtualDataSource<K,V> dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public void close() {
            try {
                dataSource.close();
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        /**
         * Save a bulk set of changes to internal nodes and leaves.
         *
         * @param firstLeafPath   the new path of first leaf node
         * @param lastLeafPath    the new path of last leaf node
         * @param internalRecords stream of new internal nodes and updated internal nodes
         * @param leafRecords     stream of new leaf nodes and updated leaf nodes
         */
        @Override
        public void saveRecords(long firstLeafPath, long lastLeafPath, Stream<VirtualInternalRecord> internalRecords, Stream<VirtualLeafRecord<K, V>> leafRecords) {
            try {
                dataSource.saveRecords(firstLeafPath, lastLeafPath, internalRecords, leafRecords);
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        @Override
        public VirtualLeafRecord<K, V> loadLeafRecord(K key) {
            try {
                return dataSource.loadLeafRecord(key);
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        @Override
        public VirtualLeafRecord<K, V> loadLeafRecord(long path) {
            try {
                return dataSource.loadLeafRecord(path);
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        @Override
        public VirtualInternalRecord loadInternalRecord(long path) {
            try {
                return dataSource.loadInternalRecord(path);
            } catch (IOException e) { throw new RuntimeException(e); }
        }

        @Override
        public Hash loadLeafHash(long path) {
            try {
                return dataSource.loadLeafHash(path);
            } catch (IOException e) { throw new RuntimeException(e); }
        }
    }

    public static class LongVKeyImpl implements VirtualLongKey, FastCopyable {
        public static final int SIZE_BYTES = Long.BYTES;
        private static final long CLASS_ID = 2544515330134674835L;
        private long value;
        private int hashCode;

        public LongVKeyImpl() {}

        public LongVKeyImpl(long value) {
            setValue(value);
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
            this.hashCode = Long.hashCode(value);
        }

        public int hashCode() {
            return this.hashCode;
        }

        @Override
        public void serialize(ByteBuffer byteBuffer) throws IOException {
            byteBuffer.putLong(value);
        }

        @Override
        public void deserialize(ByteBuffer byteBuffer, int version) throws IOException {
            setValue(byteBuffer.getLong());
        }

        @Override
        public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
            return byteBuffer.getLong() == value;
        }

        public LongVKeyImpl copy() {
            return new LongVKeyImpl(this.value);
        }

        public void release() {}

        public void serialize(SerializableDataOutputStream out) throws IOException {
            out.writeLong(this.value);
        }

        public void deserialize(SerializableDataInputStream in, int version) throws IOException {
            this.value = in.readLong();
        }

        public long getClassId() {
            return 8133160492230511558L;
        }

        public int getVersion() {
            return 1;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            } else if (!(o instanceof LongVKeyImpl)) {
                return false;
            } else {
                LongVKeyImpl that = (LongVKeyImpl)o;
                return this.value == that.value;
            }
        }

        @Override
        public String toString() {
            return "LongVirtualKey{" +
                    "value=" + value +
                    ", hashCode=" + hashCode +
                    '}';
        }

        @Override
        public long getKeyAsLong() {
            return value;
        }
    }
}
