package utils;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/**
 * Collection of common utility methods and classes used in tests
 */
@SuppressWarnings("unused")
public class CommonTestUtils {
    public static final int HASH_DATA_SIZE = DigestType.SHA_384.digestLength() + Integer.BYTES + Integer.BYTES; // int for digest type and int for byte array length

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
//            System.out.println("Deleted data files");
        }
    }

    public static void printDirectorySize(Path dir) {
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try {
                long size = Files.walk(dir)
                        .filter(p -> p.toFile().isFile())
                        .mapToLong(p -> p.toFile().length())
                        .sum();
                System.out.printf("Test data storage size: %,.1f Mb\n",(double)size/(1024d*1024d));
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

    public static String toLongsString(ByteBuffer buf) {
        buf.rewind();
        LongBuffer longBuf = buf.asLongBuffer();
        long[] array = new long[longBuf.remaining()];
        longBuf.get(array);
        return Arrays.toString(array);
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
                new EntityId(iteration,iteration,iteration), 1001, 0);
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

    public static void printMemoryUsage() {
        for (MemoryPoolMXBean mpBean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mpBean.getType() == MemoryType.HEAP) {
                System.out.printf("     Name: %s: %s\n",mpBean.getName(), mpBean.getUsage());
            }
        }
        System.out.println("    Runtime.getRuntime().totalMemory() = " + Runtime.getRuntime().totalMemory());
    }

    // =================================================================================================================
    // useful classes

    public static final class TestHash extends Hash {
        public TestHash(byte[] bytes) {
            super(bytes, DigestType.SHA_384, true, false);
        }
    }

    public static final class TestLeafData implements SelfSerializable {
        public static final int SIZE_BYTES = HASH_DATA_SIZE+1024;
        static final byte[] RANDOM_1K = new byte[1024];
        static {
            new Random().nextBytes(RANDOM_1K);
        }
        private Hash hash;
        private byte[] data;

        public TestLeafData() {}

        public TestLeafData(int id) {
            this.hash = hash(id);
            this.data = RANDOM_1K;
        }

        @Override
        public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
            hash = new Hash();
            hash.deserialize(serializableDataInputStream,hash.getVersion());
            data = new byte[1024];
            //noinspection ResultOfMethodCallIgnored
            serializableDataInputStream.read(data);
        }

        @Override
        public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
            hash.serialize(serializableDataOutputStream);
            serializableDataOutputStream.write(data);
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
            return Objects.equals(hash, that.hash) && Arrays.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(hash);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }

        @Override
        public String toString() {
            return "TestLeafData{" +
                    "hash=" + hash +
                    ", data=" + Arrays.toString(data) +
                    '}';
        }
    }
}
