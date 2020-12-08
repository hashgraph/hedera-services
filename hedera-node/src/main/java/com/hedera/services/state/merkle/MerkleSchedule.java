package com.hedera.services.state.merkle;

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.FCMElement;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.bouncycastle.util.Arrays;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.swirlds.common.CommonUtils.hex;


public class MerkleSchedule extends AbstractMerkleLeaf implements FCMValue {
    static final int SIGNATURE_BYTES = 64;
    static final int MERKLE_VERSION = 1;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d2b7d9e673285fcL;
    static DomainSerdes serdes = new DomainSerdes();

    public static final JKey UNUSED_KEY = null;

    private byte[] transactionBody;
    private JKey adminKey = UNUSED_KEY;
    private HashSet<JKey> signers = new HashSet<>();
    private Map<JKey, byte[]> signatures = new HashMap<>();
    private boolean executeImmediately;
    private boolean deleted;

    @Deprecated
    public static final MerkleSchedule.Provider LEGACY_PROVIDER = new MerkleSchedule.Provider();

    public MerkleSchedule() { }

    public MerkleSchedule(
            byte[] transactionBody,
            JKey adminKey,
            HashSet<JKey> signers,
            Map<JKey, byte[]> signatures
    ) {
        this.transactionBody = transactionBody;
        this.adminKey = adminKey;
        this.signers = signers;
        this.signatures = signatures;
    }

    @Deprecated
    public static class Provider implements SerializedObjectProvider {
        @Override
        public FastCopyable deserialize(DataInputStream in) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    /* Object */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || MerkleSchedule.class != o.getClass()) {
            return false;
        }

        var that = (MerkleSchedule) o;
        return this.deleted == that.deleted &&
                this.executeImmediately == that.executeImmediately &&
                Arrays.areEqual(this.transactionBody, that.transactionBody) &&
                equalUpToDecodability(this.adminKey, that.adminKey) &&
                signersMatch(this.signers, that.signers) &&
                signaturesMatch(this.signatures, that.signatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                deleted,
                transactionBody,
                executeImmediately,
                adminKey,
                signers,
                signatures);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleSchedule.class)
                .add("deleted", deleted)
                .add("transactionBody", hex(transactionBody))
                .add("executedImmediately", executeImmediately)
                .add("adminKey", describe(adminKey))
                .add("signers", readableSigners())
                .add("signatures", readableSignatures())
                .toString();
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        deleted = in.readBoolean();
        int txBodyLength = in.readInt();
        transactionBody = in.readByteArray(txBodyLength);
        executeImmediately = in.readBoolean();
        adminKey = serdes.readNullable(in, serdes::deserializeKey);
        deserializeSigners(in);
        deserializeSignatures(in);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(deleted);
        out.writeInt(transactionBody.length);
        out.writeByteArray(transactionBody);
        out.writeBoolean(executeImmediately);
        serdes.writeNullable(adminKey, out, serdes::serializeKey);
        serializeSigners(out);
        serializeSignatures(out);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public FCMElement copy() {
        var signaturesCopy = signatures.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, HashMap::new));
        var signersCopy = new HashSet<>(signers);

        var fc = new MerkleSchedule(
                transactionBody,
                adminKey,
                signersCopy,
                signaturesCopy
        );

        fc.setDeleted(deleted);
        fc.setExecuteImmediately(executeImmediately);
        if (adminKey != UNUSED_KEY) {
            fc.setAdminKey(adminKey);
        }

        return fc;
    }

    public byte[] transactionBody() { return this.transactionBody; }

    public void setTransactionBody(byte[] transactionBody) { this.transactionBody = transactionBody; }

    public boolean hasAdminKey() {
        return adminKey != UNUSED_KEY;
    }

    public Optional<JKey> adminKey() {
        return Optional.ofNullable(adminKey);
    }

    public void setAdminKey(JKey adminKey) {
        this.adminKey = adminKey;
    }

    public HashSet<JKey> signers() { return signers; }

    public void setSigners(HashSet<JKey> signers) { this.signers = signers; }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isExecuteImmediately() { return executeImmediately; }

    public void setExecuteImmediately(boolean executeImmediately) { this.executeImmediately = executeImmediately; }

    public Map<JKey, byte[]> signatures() { return signatures; }

    public void setSignatures(Map<JKey, byte[]> signatures) { this.signatures = signatures; }

    public void addSignature(JKey key, byte[] signature) {
        signatures.put(key, signature);
    }

    private String readableSigners() {
        var sb = new StringBuilder("[");
        sb.append(
                signers
                        .stream()
                        .map(MiscUtils::describe)
                        .collect(Collectors.joining(", "))
        );
        return sb.append("]").toString();
    }

    private String readableSignatures() {
        var sb = new StringBuilder("[");
        sb.append(
                signatures
                        .entrySet()
                        .stream()
                        .map(s -> describe(s.getKey()) + " : " + hex(s.getValue()))
                        .collect(Collectors.joining(", "))
        );
        return sb.append("]").toString();
    }

    private void deserializeSigners(SerializableDataInputStream in) throws IOException {
        int signersSize = in.readInt();
        signers = new HashSet<>();
        for (int i = 0; i < signersSize; i++) {
            signers.add(serdes.deserializeKey(in));
        }
    }

    private void deserializeSignatures(SerializableDataInputStream in) throws IOException {
        int signaturesSize = in.readInt();
        for (int i = 0; i < signaturesSize; i++) {
            JKey pubKey = serdes.deserializeKey(in);
            byte[] signature = in.readByteArray(SIGNATURE_BYTES);
            signatures.put(pubKey, signature);
        }
    }

    private void serializeSigners(SerializableDataOutputStream out) throws IOException {
        out.writeInt(signers.size());
        for (JKey key : signers) {
            serdes.serializeKey(key, out);
        }
    }

    private void serializeSignatures(SerializableDataOutputStream out) throws IOException {
        out.writeInt(signatures.size());
        for (var entry : signatures.entrySet()) {
            serdes.serializeKey(entry.getKey(), out);
            out.writeByteArray(entry.getValue());
        }
    }

    private boolean signersMatch(HashSet<JKey> a, HashSet<JKey> b) {
        if (a.size() != b.size()) {
            return false;
        }

        var iterator = b.iterator();
        for (JKey aKey : a) {
            var bKey = iterator.next();
            if (!equalUpToDecodability(aKey, bKey)) {
                return false;
            }
        }

        return true;
    }

    private boolean signaturesMatch(Map<JKey,byte[]> a, Map<JKey,byte[]> b) {
        if (a.size() != b.size()) {
            return false;
        }

        var aKeys = a.keySet();
        var bKeys = b.keySet();
        var iterator = bKeys.iterator();

        for (JKey aKey : aKeys) {
            var bKey = iterator.next();
            if (!equalUpToDecodability(aKey, bKey)) {
                return false;
            }
            if (!Arrays.areEqual(a.get(aKey), b.get(bKey))) {
                return false;
            }
        }

        return true;
    }
}
