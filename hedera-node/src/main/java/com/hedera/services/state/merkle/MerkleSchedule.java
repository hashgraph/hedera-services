package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
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
    private HashSet<EntityId> signers = new HashSet<>();
    private Map<EntityId, byte[]> signatures = new HashMap<>();
    private boolean deleted;

    @Deprecated
    public static final MerkleSchedule.Provider LEGACY_PROVIDER = new MerkleSchedule.Provider();

    public MerkleSchedule() { }

    public MerkleSchedule(
            byte[] transactionBody,
            HashSet<EntityId> signers,
            Map<EntityId, byte[]> signatures
    ) {
        this.transactionBody = transactionBody;
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
                Arrays.areEqual(this.transactionBody, that.transactionBody) &&
                equalUpToDecodability(this.adminKey, that.adminKey) &&
                this.signers.equals(that.signers) &&
                signaturesMatch(this.signatures, that.signatures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                deleted,
                transactionBody,
                adminKey,
                signers,
                signatures);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleSchedule.class)
                .add("deleted", deleted)
                .add("transactionBody", hex(transactionBody))
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
        deserializeSigners(in);
        deserializeSignatures(in);
        adminKey = serdes.readNullable(in, serdes::deserializeKey);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(deleted);
        out.writeInt(transactionBody.length);
        out.writeByteArray(transactionBody);
        serializeSigners(out);
        serializeSignatures(out);
        serdes.writeNullable(adminKey, out, serdes::serializeKey);
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
    public MerkleSchedule copy() {
        var signaturesCopy = signatures.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, HashMap::new));
        var signersCopy = new HashSet<>(signers);

        var fc = new MerkleSchedule(
                transactionBody,
                signersCopy,
                signaturesCopy
        );

        fc.setDeleted(deleted);
        if (adminKey != UNUSED_KEY) {
            fc.setAdminKey(adminKey);
        }

        return fc;
    }

    public byte[] transactionBody() { return this.transactionBody; }

    public boolean hasAdminKey() {
        return adminKey != UNUSED_KEY;
    }

    public Optional<JKey> adminKey() {
        return Optional.ofNullable(adminKey);
    }

    public void setAdminKey(JKey adminKey) {
        this.adminKey = adminKey;
    }

    public HashSet<EntityId> signers() { return signers; }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public Map<EntityId, byte[]> signatures() { return signatures; }

    public void putSignature(EntityId key, byte[] signature) {
        if (signature.length != SIGNATURE_BYTES) {
            throw new IllegalArgumentException(String.format("Invalid signature length: %d!", signature.length));
        }
        signatures.put(key, signature);
    }

    private String readableSigners() {
        var sb = new StringBuilder("[");
        sb.append(
                signers
                        .stream()
                        .map(EntityId::toString)
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
                        .map(s -> s.getKey() + " : " + hex(s.getValue()))
                        .collect(Collectors.joining(", "))
        );
        return sb.append("]").toString();
    }

    private void deserializeSigners(SerializableDataInputStream in) throws IOException {
        int signersSize = in.readInt();
        signers = new HashSet<>();
        for (int i = 0; i < signersSize; i++) {
            signers.add(in.readSerializable());
        }
    }

    private void deserializeSignatures(SerializableDataInputStream in) throws IOException {
        int signaturesSize = in.readInt();
        for (int i = 0; i < signaturesSize; i++) {
            EntityId id = in.readSerializable();
            byte[] signature = in.readByteArray(SIGNATURE_BYTES);
            signatures.put(id, signature);
        }
    }

    private void serializeSigners(SerializableDataOutputStream out) throws IOException {
        out.writeInt(signers.size());
        for (EntityId id : signers) {
           out.writeSerializable(id, true);
        }
    }

    private void serializeSignatures(SerializableDataOutputStream out) throws IOException {
        out.writeInt(signatures.size());
        for (var entry : signatures.entrySet()) {
            out.writeSerializable(entry.getKey(), true);
            out.writeByteArray(entry.getValue());
        }
    }

    private boolean signaturesMatch(Map<EntityId, byte[]> a, Map<EntityId, byte[]> b) {
        var aKeys = a.keySet();
        var bKeys = b.keySet();
        if (!aKeys.equals(bKeys)) {
            return false;
        }

        for (EntityId aKey : aKeys) {
            if (!Arrays.areEqual(a.get(aKey), b.get(aKey))) {
                return false;
            }
        }

        return true;
    }
}
