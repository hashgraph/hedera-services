package com.hedera.services.state.merkle.v2;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.virtualh.Account;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class VFCDataSourceTestUtils {
    public static final int HASH_DATA_SIZE = DigestType.SHA_384.digestLength() + Integer.BYTES + Integer.BYTES; // int for digest type and int for byte array length

    public static void deleteDirectoryAndContents(Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                //noinspection ResultOfMethodCallIgnored
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception e) {
                System.err.println("Failed to delete test directory ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
            System.out.println("Deleted data files");
        }
    }

    public static void printDirectorySize(Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                long size = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum();
                long count = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .count();
                System.out.printf("Test data storage %d files totalling size: %,.1f Mb\n",count,(double)size/(1024d*1024d));
            } catch (Exception e) {
                System.err.println("Failed to measure size of directory. ["+dir.toFile().getAbsolutePath()+"]");
                e.printStackTrace();
            }
        }
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
        return new TestHash(new byte[] {
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3,
                0,0,0,0,b0,b1,b2,b3
        });
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

    /** Helper function to create a suppler for a classes default constructor */
    public static <S> Supplier<S> supplerFromClass(String className) throws Exception {
        @SuppressWarnings("unchecked") Class<S> slotIndexClass = (Class<S>) Class.forName(className);
        Constructor<S> slotIndexConstructor = slotIndexClass.getConstructor();
        return () -> {
            try {
                return slotIndexConstructor.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        };
    }

    /** Helper function to create a suppler for a classes instance */
    public static <I> I instanceFromClass(Class<I> slotIndexClass) throws Exception {
        Constructor<I> slotIndexConstructor = slotIndexClass.getConstructor();
        try {
            return slotIndexConstructor.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int measureLengthOfSerializable(SelfSerializable serializable) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5000);
        try {
            serializable.serialize(new SerializableDataOutputStream(out));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out.size();
    }

    public static MerkleAccountState createRandomMerkleAccountState(int iteration, Random random) {
        byte[] key = new byte[JEd25519Key.ED25519_BYTE_LENGTH];
        random.nextBytes(key);
        JEd25519Key ed25519Key = new JEd25519Key(key);
        return new MerkleAccountState(
                ed25519Key,
                iteration,iteration,iteration,
                randomString(256, random),
                false,false,false,
                new EntityId(iteration,iteration,iteration));
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

    public static ByteString randomUtf8ByteString(int n) {
        return ByteString.copyFrom(randomUtf8Bytes(n));
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
        public void update(ByteBuffer buffer) throws IOException {
            buffer.putInt(id);
            buffer.put(data);
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

    @SuppressWarnings("unused")
    public static class SerializableAccount implements VirtualKey {
        public static final int SIZE_BYTES = Long.BYTES*3;
        private Account account;

        public SerializableAccount() {}

        public SerializableAccount(Account account) {
            this.account = account;
        }
        public SerializableAccount(long shardNum, long realmNum, long accountNum) {
            this.account = new Account(shardNum,realmNum,accountNum);
        }

        @Override
        public void serialize(ByteBuffer byteBuffer) throws IOException {
            if (account != null) {
                byteBuffer.putLong(account.shardNum());
                byteBuffer.putLong(account.realmNum());
                byteBuffer.putLong(account.accountNum());
            } else {
                byteBuffer.putLong(Long.MIN_VALUE);
                byteBuffer.putLong(Long.MIN_VALUE);
                byteBuffer.putLong(Long.MIN_VALUE);
            }
        }

        @Override
        public void deserialize(ByteBuffer byteBuffer, int version) throws IOException {
            long shard = byteBuffer.getLong();
            if (shard != Long.MIN_VALUE) {
                this.account = new Account(
                        shard,
                        byteBuffer.getLong(),
                        byteBuffer.getLong()
                );
            } else {
                account = null;
            }
        }

        @Override
        public boolean equals(ByteBuffer byteBuffer, int version) throws IOException {
            if (this.account == null) {
                return byteBuffer.getLong() == Long.MIN_VALUE;
            } else {
                return byteBuffer.getLong() == account.shardNum() &&
                        byteBuffer.getLong() == account.realmNum() &&
                        byteBuffer.getLong() == account.accountNum();
            }
        }

        @Override
        public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
            long shard = serializableDataInputStream.readLong();
            if (shard != Long.MIN_VALUE) {
                this.account = new Account(
                        shard,
                        serializableDataInputStream.readLong(),
                        serializableDataInputStream.readLong()
                );
            } else {
                account = null;
            }
        }

        @Override
        public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
            if (account != null) {
                serializableDataOutputStream.writeLong(account.shardNum());
                serializableDataOutputStream.writeLong(account.realmNum());
                serializableDataOutputStream.writeLong(account.accountNum());
            } else {
                serializableDataOutputStream.writeLong(Long.MIN_VALUE);
                serializableDataOutputStream.writeLong(Long.MIN_VALUE);
                serializableDataOutputStream.writeLong(Long.MIN_VALUE);
            }
        }

        @Override
        public long getClassId() {
            return 3456;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SerializableAccount that = (SerializableAccount) o;
            return Objects.equals(account, that.account);
        }

        @Override
        public int hashCode() {
            return Objects.hash(account);
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

    /**
     * Wrap a VirtualDataSource turning all execptions into runtime exceptions for testing
     */
    public static class VFCDataSourceExceptionWrapper<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K,V> {
        private final VirtualDataSource<K,V> dataSource;

        public VFCDataSourceExceptionWrapper(VirtualDataSource<K,V> dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Object startTransaction() {
            return dataSource.startTransaction();
        }

        @Override
        public void commitTransaction(Object handle) {
            dataSource.commitTransaction(handle);
        }

        @Override
        public void close() {
            try {
                dataSource.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Hash loadLeafHash(long path) {
            try {
                return dataSource.loadLeafHash(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Hash loadInternalHash(long path) {
            try {
                return dataSource.loadInternalHash(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public V loadLeafValue(long path) {
            try {
                return dataSource.loadLeafValue(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public V loadLeafValue(K key) {
            try {
                return dataSource.loadLeafValue(key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public K loadLeafKey(long path) {
            try {
                return dataSource.loadLeafKey(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public long loadLeafPath(K key) {
            try {
                return dataSource.loadLeafPath(key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void saveInternal(long path, Hash hash) {
            try {
                dataSource.saveInternal(path,hash);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void updateLeaf(long oldPath, long newPath, K key, Hash hash) {
            try {
                dataSource.updateLeaf(oldPath,newPath, key, hash);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void updateLeaf(long path, K key, V value, Hash hash) {
            try {
                dataSource.updateLeaf(path, key, value, hash);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void addLeaf(long path, K key, V value, Hash hash) {
            try {
                dataSource.addLeaf(path,key, value,hash);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
