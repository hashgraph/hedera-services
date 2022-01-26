package virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureClassLoader;
import java.util.Objects;

@SuppressWarnings("unused")
final class Id implements VirtualLongKey {
    public static final int SERIALIZED_SIZE = Long.BYTES;

    private long num;

    public Id() {
    }

    public Id(long num) {
        this.num = num;
    }

    public long getRealm() {
        return 0;
    }

    public long getShard() {
        return 0;
    }

    public long getNum() {
        return num;
    }

    @Override
    public void serialize(ByteBuffer byteBuffer) {
        byteBuffer.putLong(num);
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int v) {
        num = byteBuffer.getLong();
    }


    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        num = in.readLong();
    }

    @Override
    public long getClassId() {
        return 0xef6e56805f996b61L;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(num);
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Id id = (Id) o;
        return num == id.num;
    }

    @Override
    public int hashCode() {
        return Objects.hash(num);
    }

    @Override
    public String toString() {
        return "Id(" + num + ")";
    }

    @Override
    public long getKeyAsLong() {
        return num;
    }

    public static class IdKeySerializer implements KeySerializer<Id> {
        private static final long CLASS_ID = 0x3f69b6c2cd580162L;
        private static final int CURRENT_VERSION = 1;

        @Override
        public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
            /* No-op */
        }

        @Override
        public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
            /* No-op */
        }

        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        @Override
        public int getVersion() {
            return CURRENT_VERSION;
        }

        @Override
        public int deserializeKeySize(ByteBuffer byteBuffer) {
            return Long.BYTES;
        }

        @Override
        public boolean equals(final ByteBuffer byteBuffer, final int i, final Id id) throws IOException {
            return byteBuffer.getLong() == id.num;
        }

        @Override
        public boolean isVariableSize() {
            return false;
        }

        @Override
        public int getSerializedSize() {
            return Long.BYTES;
        }

        @Override
        public long getCurrentDataVersion() {
            return 1;
        }

        @Override
        public Id deserialize(ByteBuffer byteBuffer, long l) throws IOException {
            return new Id(byteBuffer.getLong());
        }

        @Override
        public int serialize(Id id, SerializableDataOutputStream serializableDataOutputStream) throws IOException {
            serializableDataOutputStream.writeLong(id.getKeyAsLong());
            return Long.BYTES;
        }
    }
}
