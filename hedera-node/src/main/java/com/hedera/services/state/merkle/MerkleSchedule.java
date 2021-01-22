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
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.Arrays;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.google.protobuf.ByteString.copyFrom;
import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.swirlds.common.CommonUtils.hex;
import static java.util.stream.Collectors.toList;


public class MerkleSchedule extends AbstractMerkleLeaf implements FCMValue {
    static final int MERKLE_VERSION = 1;

    static final int NUM_ED25519_PUBKEY_BYTES = 32;
    static final int UPPER_BOUND_MEMO_UTF8_BYTES = 1024;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d2b7d9e673285fcL;
    static DomainSerdes serdes = new DomainSerdes();

    public static final JKey UNUSED_KEY = null;
    public static final EntityId UNUSED_PAYER = null;

    private byte[] transactionBody;
    private String memo;
    private JKey adminKey = UNUSED_KEY;
    private EntityId payer = UNUSED_PAYER;
    private EntityId schedulingAccount;
    private RichInstant schedulingTXValidStart;
    private Set<JKey> signers = new LinkedHashSet<>();
    private boolean deleted;

    private Set<ByteString> notary = ConcurrentHashMap.newKeySet();
    private List<byte[]> signatories = new ArrayList<>();

    @Deprecated
    public static final MerkleSchedule.Provider LEGACY_PROVIDER = new MerkleSchedule.Provider();

    public MerkleSchedule() { }

    public MerkleSchedule(
            byte[] transactionBody,
            EntityId schedulingAccount,
            RichInstant schedulingTXValidStart
    ) {
        this.transactionBody = transactionBody;
        this.schedulingAccount = schedulingAccount;
        this.schedulingTXValidStart = schedulingTXValidStart;
    }

    @Deprecated
    public static class Provider implements SerializedObjectProvider {
        @Override
        public FastCopyable deserialize(DataInputStream in) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    /* Notary functions */
    public boolean witnessValidEd25519Signature(byte[] key) {
    	var usableKey = copyFrom(key);
    	if (notary.contains(usableKey)) {
    		return false;
        } else {
            signatories.add(key);
            notary.add(usableKey);
            return true;
        }
    }

    public boolean hasValidEd25519Signature(byte[] key) {
    	return notary.contains(copyFrom(key));
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
                Objects.equals(this.memo, that.memo) &&
                Objects.equals(this.payer, that.payer) &&
                Objects.equals(this.schedulingAccount, that.schedulingAccount) &&
                Objects.equals(this.schedulingTXValidStart, that.schedulingTXValidStart) &&
                signersMatch(this.signers, that.signers) &&
                equalUpToDecodability(this.adminKey, that.adminKey) &&
                signatoriesAreSame(this.signatories, that.signatories);
    }

    private boolean signatoriesAreSame(List<byte[]> a, List<byte[]> b) {
        if (a.size() != b.size()) {
            return false;
        } else {
        	for (int i = 0, n = a.size(); i < n; i++) {
        	    if (!Arrays.areEqual(a.get(i), b.get(i))) {
        	        return false;
                }
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                deleted,
                transactionBody,
                memo,
                payer,
                schedulingAccount,
                schedulingTXValidStart,
                signers,
                adminKey);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleSchedule.class)
                .add("deleted", deleted)
                .add("transactionBody", hex(transactionBody))
                .add("memo", memo)
                .add("payer", readablePayer())
                .add("schedulingAccount", schedulingAccount)
                .add("schedulingTXValidStart", schedulingTXValidStart)
                .add("signers", readableSigners())
                .add("signatories", signatories.stream().map(Hex::encodeHexString).collect(toList()))
                .add("adminKey", describe(adminKey))
                .toString();
    }

    private String readablePayer() {
        return Optional.ofNullable(payer).map(EntityId::toAbbrevString).orElse("<N/A>");
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        deleted = in.readBoolean();
        int txBodyLength = in.readInt();
        transactionBody = in.readByteArray(txBodyLength);
        payer = serdes.readNullableSerializable(in);
        schedulingAccount = in.readSerializable();
        schedulingTXValidStart = RichInstant.from(in);
        deserializeSigners(in);
        adminKey = serdes.readNullable(in, serdes::deserializeKey);
        int numSignatories = in.readInt();
        while (numSignatories-- > 0) {
            witnessValidEd25519Signature(in.readByteArray(NUM_ED25519_PUBKEY_BYTES));
        }
        memo = serdes.readNullableString(in, UPPER_BOUND_MEMO_UTF8_BYTES);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeBoolean(deleted);
        out.writeInt(transactionBody.length);
        out.writeByteArray(transactionBody);
        serdes.writeNullableSerializable(payer, out);
        out.writeSerializable(schedulingAccount, true);
        schedulingTXValidStart.serialize(out);
        serializeSigners(out);
        serdes.writeNullable(adminKey, out, serdes::serializeKey);
        out.writeInt(signatories.size());
        for (byte[] key : signatories) {
            out.writeByteArray(key);
        }
        serdes.writeNullableString(memo, out);
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
        var signersCopy = new LinkedHashSet<>(signers);

        var fc = new MerkleSchedule(
                transactionBody,
                schedulingAccount,
                schedulingTXValidStart);

        fc.setMemo(memo);
        fc.setDeleted(deleted);
        fc.setSigners(signersCopy);
        if (payer != UNUSED_PAYER) {
            fc.setPayer(payer);
        }
        if (adminKey != UNUSED_KEY) {
            fc.setAdminKey(adminKey);
        }
        for (byte[] signatory : signatories) {
            fc.witnessValidEd25519Signature(signatory);
        }

        return fc;
    }

    public byte[] transactionBody() { return this.transactionBody; }

    public String memo() { return this.memo; }

    public void setMemo(String memo) { this.memo = memo; }

    public boolean hasAdminKey() {
        return adminKey != UNUSED_KEY;
    }

    public Optional<JKey> adminKey() {
        return Optional.ofNullable(adminKey);
    }

    public void setAdminKey(JKey adminKey) {
        this.adminKey = adminKey;
    }

    public void setPayer(EntityId payer) { this.payer = payer; }

    public EntityId payer() { return this.payer; }

    public boolean hasPayer() { return payer != UNUSED_PAYER; }

    public EntityId schedulingAccount() { return this.schedulingAccount; }

    public RichInstant schedulingTXValidStart() { return this.schedulingTXValidStart; }

    public Set<JKey> signers() { return signers; }

    public void setSigners(Set<JKey> signers) { this.signers = signers; }

    public void addSigners(Set<JKey> signer) {
        this.signers.addAll(signer);
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
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

    private void deserializeSigners(SerializableDataInputStream in) throws IOException {
        int signersSize = in.readInt();
        signers = new LinkedHashSet<>();
        for (int i = 0; i < signersSize; i++) {
            signers.add(serdes.deserializeKey(in));
        }
    }

    private void serializeSigners(SerializableDataOutputStream out) throws IOException {
        out.writeInt(signers.size());
        for (var entry : signers) {
            serdes.serializeKey(entry, out);
        }
    }


    private boolean signersMatch(Set<JKey> a, Set<JKey> b) {
        if (a.size() != b.size()) {
            return false;
        }
        return a.containsAll(b);
    }
}
