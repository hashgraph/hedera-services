package virtual;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

@SuppressWarnings("unused")
public class Account implements VirtualValue {
    private static final int MAX_STRING_BYTES = 120;
    public static final int SERIALIZED_SIZE = (4 * Long.BYTES) + // key
            Long.BYTES +        // expiry
            Long.BYTES +        // hbarBalance
            Long.BYTES +        // autoRenewSecs
            1 +                 // memo length
            MAX_STRING_BYTES +  // memo (extra space for multi-byte chars)
            1 +                 // deleted | smartContract | receiverSigRequired | hasProxy
            Id.SERIALIZED_SIZE; // proxy

    private byte[] key = new byte[32];
    private long expiry = System.currentTimeMillis() + (60 * 60 * 24 * 90);
    private long hbarBalance;
    private long autoRenewSecs = 60 * 60 * 24 * 90;
    private String memo = null;
    private boolean deleted = false;
    private boolean smartContract = false;
    private boolean receiverSigRequired = false;
    private Id proxy;

    private transient boolean readOnly = false;

    @Override
    public String toString() {
        return "Account(hbar=" + hbarBalance + ", memo=" + memo + ")";
    }

    public Account() {

    }

    public Account(Account source, boolean readOnly) {
        this.key = Arrays.copyOf(source.key, 32);
        this.expiry = source.expiry;
        this.hbarBalance = source.hbarBalance;
        this.autoRenewSecs = source.autoRenewSecs;
        this.memo = source.memo;
        this.deleted = source.deleted;
        this.smartContract = source.smartContract;
        this.receiverSigRequired = source.receiverSigRequired;
        this.proxy = source.proxy;
        this.readOnly = readOnly;
    }

    public long getHbarBalance() {
        return hbarBalance;
    }

    public void setHbarBalance(long newBalance) {
        if (!readOnly) {
            this.hbarBalance = newBalance;
        }
    }

    public Id getProxy() {
        return proxy;
    }

    public void setProxy(Id proxy) {
        if (!readOnly) {
            this.proxy = proxy;
        }
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        if (!readOnly) {
            if (memo != null && memo.length() > 100) {
                this.memo = memo.substring(0, 100);
            } else {
                this.memo = memo;
            }
        }
    }

    // TODO A long array of tokens. This is an open-ended list. We haven't solved for that yet.

    @Override
    public void serialize(ByteBuffer byteBuffer) {
        final int initialPosition = byteBuffer.position();
        byteBuffer.put(key);
        byteBuffer.putLong(expiry);
        byteBuffer.putLong(hbarBalance);
        byteBuffer.putLong(autoRenewSecs);

        // get the string length, write the string (up to 100 chars), and skip anything left.
        final var bytes = memo == null ? null : memo.getBytes(StandardCharsets.UTF_8);
        if (bytes == null) {
            byteBuffer.put((byte) 0);
            byteBuffer.position(byteBuffer.position() + MAX_STRING_BYTES);
        } else {
            final var extra = Math.max(0, MAX_STRING_BYTES - bytes.length);
            byteBuffer.put((byte) bytes.length);
            byteBuffer.put(bytes, 0, Math.min(bytes.length, MAX_STRING_BYTES));
            byteBuffer.position(byteBuffer.position() + extra);
        }
        assert (byteBuffer.position() - initialPosition) == (7 * Long.BYTES) + 1 + MAX_STRING_BYTES :
                "byteBuffer.position() [" + (byteBuffer.position() - initialPosition) + "] != (7*Long.BYTES) + 1 + MAX_STRING_BYTES [" + ((7 * Long.BYTES) + 1 + MAX_STRING_BYTES) + "]";

        // byte pack the three booleans
        byte packed = 0;
        packed |= deleted ? 0b0001 : 0;
        packed |= smartContract ? 0b0010 : 0;
        packed |= receiverSigRequired ? 0b0100 : 0;
        packed |= proxy != null ? 0b1000 : 0;
        byteBuffer.put(packed);

        // Write the proxy
        if (proxy != null) {
            proxy.serialize(byteBuffer);
        } else {
            byteBuffer.putLong(0);
        }
        assert (byteBuffer.position() - initialPosition) == SERIALIZED_SIZE :
                "byteBuffer.position() [" + (byteBuffer.position() - initialPosition) + "] != SERIALIZED_SIZE [" + SERIALIZED_SIZE + "]";
    }

    @Override
    public void deserialize(ByteBuffer byteBuffer, int v) {
        byteBuffer.get(key);
        expiry = byteBuffer.getLong();
        hbarBalance = byteBuffer.getLong();
        autoRenewSecs = byteBuffer.getLong();
        final var b = new byte[byteBuffer.get()];
        final var pos = byteBuffer.position();
        byteBuffer.get(b);
        memo = new String(b, StandardCharsets.UTF_8);
        byteBuffer.position(pos + MAX_STRING_BYTES);
        byte packed = byteBuffer.get();
        deleted = (packed & 0b001) == 0b001;
        smartContract = (packed & 0b010) == 0b010;
        receiverSigRequired = (packed & 0b100) == 0b100;
        final var hasProxy = (packed & 0b0100) == 0b0100;
        if (hasProxy) {
            proxy = new Id();
            proxy.deserialize(byteBuffer, v);
        }
    }

    @Override
    public Account copy() {
        return new Account(this, false);
    }

    @Override
    public Account asReadOnly() {
        return new Account(this, true);
    }

    @Override
    public void release() {
        // no-op
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
        byte[] b = new byte[SERIALIZED_SIZE];
        //noinspection ResultOfMethodCallIgnored
        serializableDataInputStream.read(b);
        final var buf = ByteBuffer.wrap(b);
        deserialize(buf, i);
    }

    @Override
    public long getClassId() {
        return 0x8d15a77db06f905eL;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        final int initialPosition = out.size();
        out.write(key);
        out.writeLong(expiry);
        out.writeLong(hbarBalance);
        out.writeLong(autoRenewSecs);

        // get the string length, write the string (up to 100 chars), and skip anything left.
        final var bytes = memo == null ? null : memo.getBytes(StandardCharsets.UTF_8);
        if (bytes == null) {
            out.write((byte) 0);
            for (int i = 0; i < MAX_STRING_BYTES; i++) { // padding
                out.write((byte) 0);
            }
        } else {
            final var extra = Math.max(0, MAX_STRING_BYTES - bytes.length);
            out.write((byte) bytes.length);
            out.write(bytes, 0, Math.min(bytes.length, MAX_STRING_BYTES));
            for (int i = 0; i < extra; i++) { // padding
                out.write((byte) 0);
            }
        }
        assert (out.size() - initialPosition) == (7 * Long.BYTES) + 1 + MAX_STRING_BYTES :
                "byteBuffer.position() [" + (out.size() - initialPosition) + "] != (7*Long.BYTES) + 1 + MAX_STRING_BYTES [" + ((7 * Long.BYTES) + 1 + MAX_STRING_BYTES) + "]";

        // byte pack the three booleans
        byte packed = 0;
        packed |= deleted ? 0b0001 : 0;
        packed |= smartContract ? 0b0010 : 0;
        packed |= receiverSigRequired ? 0b0100 : 0;
        packed |= proxy != null ? 0b1000 : 0;
        out.write(packed);

        // Write the proxy
        if (proxy != null) {
            proxy.serialize(out);
        } else {
            out.writeLong(0);
        }
        assert (out.size() - initialPosition) == SERIALIZED_SIZE :
                "byteBuffer.position() [" + (out.size() - initialPosition) + "] != SERIALIZED_SIZE [" + SERIALIZED_SIZE + "]";
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return expiry == account.expiry && hbarBalance == account.hbarBalance && autoRenewSecs == account.autoRenewSecs && deleted == account.deleted && smartContract == account.smartContract && receiverSigRequired == account.receiverSigRequired && Arrays.equals(key, account.key) && Objects.equals(memo, account.memo) && Objects.equals(proxy, account.proxy);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(expiry, hbarBalance, autoRenewSecs, memo, deleted, smartContract, receiverSigRequired, proxy);
        result = 31 * result + Arrays.hashCode(key);
        return result;
    }
}
