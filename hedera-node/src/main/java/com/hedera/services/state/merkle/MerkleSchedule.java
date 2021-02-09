package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.swirlds.common.FCMValue;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.util.Arrays;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private long expiry;

    private Set<ByteString> notary = ConcurrentHashMap.newKeySet();
    private List<byte[]> signatories = new ArrayList<>();


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
        return this.expiry == that.expiry &&
                Arrays.areEqual(this.transactionBody, that.transactionBody) &&
                Objects.equals(this.memo, that.memo) &&
                Objects.equals(this.payer, that.payer) &&
                Objects.equals(this.schedulingAccount, that.schedulingAccount) &&
                Objects.equals(this.schedulingTXValidStart, that.schedulingTXValidStart) &&
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
                expiry,
                transactionBody,
                memo,
                payer,
                schedulingAccount,
                schedulingTXValidStart,
                adminKey);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleSchedule.class)
                .add("expiry", expiry)
                .add("transactionBody", hex(transactionBody))
                .add("memo", memo)
                .add("payer", readablePayer())
                .add("schedulingAccount", schedulingAccount)
                .add("schedulingTXValidStart", schedulingTXValidStart)
                .add("signatories", signatories.stream().map(Hex::encodeHexString).collect(toList()))
                .add("adminKey", describe(adminKey))
                .toString();
    }

    private String readablePayer() {
        return Optional.ofNullable(payer).map(EntityId::toAbbrevString).orElse("<N/A>");
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        expiry = in.readLong();
        int txBodyLength = in.readInt();
        transactionBody = in.readByteArray(txBodyLength);
        payer = serdes.readNullableSerializable(in);
        schedulingAccount = in.readSerializable();
        schedulingTXValidStart = RichInstant.from(in);
        adminKey = serdes.readNullable(in, serdes::deserializeKey);
        int numSignatories = in.readInt();
        while (numSignatories-- > 0) {
            witnessValidEd25519Signature(in.readByteArray(NUM_ED25519_PUBKEY_BYTES));
        }
        memo = serdes.readNullableString(in, UPPER_BOUND_MEMO_UTF8_BYTES);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(expiry);
        out.writeInt(transactionBody.length);
        out.writeByteArray(transactionBody);
        serdes.writeNullableSerializable(payer, out);
        out.writeSerializable(schedulingAccount, true);
        schedulingTXValidStart.serialize(out);
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

        var fc = new MerkleSchedule(
                transactionBody,
                schedulingAccount,
                schedulingTXValidStart);

        fc.setMemo(memo);
        fc.setExpiry(expiry);
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

    public Optional<String> memo() { return Optional.ofNullable(this.memo); }

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

    public List<byte[]> signatories() { return signatories; }

    public void setExpiry(long expiry) { this.expiry = expiry; }

    public long expiry() { return expiry; }
}
