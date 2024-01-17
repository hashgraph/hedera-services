/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.platform.system.address;

import static com.swirlds.common.utility.NonCryptographicHashing.hash32;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;

/**
 * One address in an address book, including all the info about a member. It is immutable. Each getter for a byte array
 * returns a clone of that array. The constructor clones all the arrays passed to it. Each "copySet" simply returns a
 * new deep copy with one variable different. So it isn't a setter, but is a variant of the builder design pattern
 * without a separate build() method.
 */
public class Address implements SelfSerializable {
    private static final long CLASS_ID = 0x5acfd3a4a32376eL;

    /** The Class Versions for this class */
    private static class ClassVersion {
        /**
         * The original version of the class.
         */
        public static final int ORIGINAL = 3;
        /**
         * The NodeId is SelfSerializable.
         *
         * @since 0.39.0
         */
        public static final int SELF_SERIALIZABLE_NODE_ID = 4;
        /**
         * added support for dns, removed unused IPv6
         */
        public static final int ADD_DNS_SUPPORT = 5;
    }

    private static final byte[] ALL_INTERFACES = new byte[] {0, 0, 0, 0};
    private static final int MAX_IP_LENGTH = 16;
    private static final int STRING_MAX_BYTES = 512;

    /** ID of this member. All agree on numbering for old members, and if config.txt used */
    private NodeId id;
    /** name this member uses to refer to that member */
    private String nickname;
    /** name that member uses to refer to their self */
    private String selfName;
    /** the member's nonnegative weight, used for weighted voting */
    private long weight;
    /** the IP or DNS name on the local network */
    private String hostnameInternal;
    /** port used on the local network */
    private int portInternal;
    /** the IP or DNS name outside the NATing firewall */
    private String hostnameExternal;
    /** port used outside the NATing firewall */
    private int portExternal;
    /** public key of the member used for signing */
    private SerializablePublicKey sigPublicKey;
    /** public key of the member used for encrypting */
    private SerializablePublicKey encPublicKey;
    /** public key of the member used for TLS key agreement */
    private SerializablePublicKey agreePublicKey;
    /**
     * a String that can be part of any address to supply additional information about that node
     */
    private String memo;

    /**
     * Default constructor for Address.
     */
    public Address() {
        this(
                NodeId.FIRST_NODE_ID,
                "",
                "",
                1,
                null,
                -1,
                null,
                -1,
                (SerializablePublicKey) null,
                (SerializablePublicKey) null,
                (SerializablePublicKey) null,
                "");
    }

    public Address(
            @NonNull final NodeId id,
            @NonNull final String nickname,
            @NonNull final String selfName,
            final long weight,
            @Nullable final String hostnameInternal,
            final int portInternal,
            @Nullable final String hostnameExternal,
            final int portExternal,
            @NonNull final String memo) {
        this(
                id,
                nickname,
                selfName,
                weight, // weight
                hostnameInternal,
                portInternal,
                hostnameExternal,
                portExternal,
                null,
                null,
                (SerializablePublicKey) null,
                memo);
    }

    private byte[] clone(byte[] x) {
        return x == null ? x : x.clone();
    }

    /**
     * constructor for a mutable address for one member.
     *
     * @param id                  the ID for that member
     * @param nickname            the name given to that member by the member creating this address
     * @param selfName            the name given to that member by themself
     * @param weight              the amount of weight (0 if they should have no influence on the consensus)
     * @param hostnameInternal    the IP or DNS name on the local network
     * @param portInternal        port for the internal address
     * @param hostnameExternal    the IP or DNS name outside the NATing firewall
     * @param portExternal        port for the external address
     * @param sigPublicKey        public key used for signing
     * @param encPublicKey        public key used for encryption
     * @param agreePublicKey      public key used for key agreement in TLS
     * @param memo                additional information about the node, can be null
     */
    public Address(
            @NonNull final NodeId id,
            @NonNull final String nickname,
            @NonNull final String selfName,
            final long weight,
            @Nullable final String hostnameInternal,
            final int portInternal,
            @Nullable final String hostnameExternal,
            final int portExternal,
            @Nullable final SerializablePublicKey sigPublicKey,
            @Nullable final SerializablePublicKey encPublicKey,
            @Nullable final SerializablePublicKey agreePublicKey,
            @NonNull final String memo) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
        this.selfName = Objects.requireNonNull(selfName, "selfName must not be null");
        this.weight = weight;
        this.portInternal = portInternal;
        this.portExternal = portExternal;
        this.hostnameInternal = hostnameInternal;
        this.hostnameExternal = hostnameExternal;
        this.sigPublicKey = sigPublicKey;
        this.encPublicKey = encPublicKey;
        this.agreePublicKey = agreePublicKey;
        this.memo = Objects.requireNonNull(memo, "memo must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ADD_DNS_SUPPORT;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * The nonnegative weight for this member, which is the voting weight for consensus.
     *
     * @return the weight
     */
    public long getWeight() {
        return weight;
    }

    /**
     * Convenience method to check if this node has zero weight.
     *
     * @return true if this node has zero weight
     */
    public boolean isZeroWeight() {
        return weight == 0;
    }

    /**
     * Get the NodeId of this address.
     *
     * @return the NodeId of this address.
     */
    @NonNull
    public NodeId getNodeId() {
        return id;
    }

    /**
     * Get name this member uses to refer to that member.
     *
     * @return The nickname of this member.
     */
    @NonNull
    public String getNickname() {
        return nickname;
    }

    /**
     * Get the name this member uses to refer to itself
     *
     * @return Name to refer itself.
     */
    @NonNull
    public String getSelfName() {
        return selfName;
    }

    /**
     * Get local IP port
     *
     * @param a the Address object to be operated on
     * @return port number
     */
    public int getConnectPort(Address a) {
        return isLocalTo(a) ? getPortInternal() : getPortExternal();
    }

    /**
     * Check whether a given Address has the same external address as mine.
     *
     * @param a Given Address to check.
     * @return True if they are exactly the same.
     */
    public boolean isLocalTo(Address a) {
        return Objects.equals(getHostnameExternal(), a.getHostnameExternal());
    }

    /**
     * Get the IPv4 address for listening all interfaces, [0.0.0.0].
     *
     * @return The IPv4 address to listen all interface: [0.0.0.0].
     */
    @NonNull
    public byte[] getListenAddressIpv4() {
        return ALL_INTERFACES;
    }

    /**
     * Get listening port used on the local network.
     *
     * @return The port number.
     */
    public int getListenPort() {
        return getPortInternal();
    }

    /**
     * Get port used on the local network.
     *
     * @return The port number.
     */
    public int getPortInternal() {
        return portInternal;
    }

    /**
     * Get port used on the external network.
     *
     * @return The port number.
     */
    public int getPortExternal() {
        return portExternal;
    }

    /**
     * @return the IP or DNS name on the local network
     */
    @Nullable
    public String getHostnameInternal() {
        return hostnameInternal;
    }

    /**
     * @return the IP or DNS name outside the NATing firewall
     */
    @Nullable
    public String getHostnameExternal() {
        return hostnameExternal;
    }

    /**
     * Get public key of the member used for signing.
     *
     * @return This member's PublicKey for signing.
     */
    @Nullable
    public PublicKey getSigPublicKey() {
        return sigPublicKey.getPublicKey();
    }

    /**
     * Get public key of the member used for encrypting.
     *
     * @return This member's PublicKey for encrypting.
     */
    @Nullable
    public PublicKey getEncPublicKey() {
        return encPublicKey.getPublicKey();
    }

    /**
     * Get the public key of the member used for TLS key agreement.
     *
     * @return The member's PublicKey used for TLS key agreement.
     */
    @Nullable
    public PublicKey getAgreePublicKey() {
        return agreePublicKey.getPublicKey();
    }

    /**
     * Get the String that is part of any address to supply additional information about this node.
     *
     * @return The String to supply additional information about this node.
     */
    @NonNull
    public String getMemo() {
        return memo;
    }

    /**
     * Create a new Address object based this one with different NodeId.
     *
     * @param id new NodeId for the created Address.
     * @return the new Address.
     */
    @NonNull
    public Address copySetNodeId(@NonNull final NodeId id) {
        Objects.requireNonNull(id, "id must not be null");
        final Address a = copy();
        a.id = id;
        return a;
    }

    /**
     * Create a new Address object based this one with different weight.
     *
     * @param weight New weight for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetWeight(long weight) {
        Address a = copy();
        a.weight = weight;
        return a;
    }

    /**
     * Create a new Address object based this one with different nickname.
     *
     * @param nickname New nickname for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetNickname(@NonNull final String nickname) {
        Objects.requireNonNull(nickname, "nickname must not be null");
        Address a = copy();
        a.nickname = nickname;
        return a;
    }

    /**
     * Create a new Address object based this one with different selfName.
     *
     * @param selfName New selfName for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetSelfName(@NonNull final String selfName) {
        Objects.requireNonNull(selfName, "selfName must not be null");
        Address a = copy();
        a.selfName = selfName;
        return a;
    }

    /**
     * Create a new Address object based this one with different internal port.
     *
     * @param portInternal New portInternal for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetPortInternal(int portInternal) {
        Address a = copy();
        a.portInternal = portInternal;
        return a;
    }

    /**
     * Create a new Address object based this one with different external port.
     *
     * @param portExternal New portExternal for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetPortExternal(int portExternal) {
        Address a = copy();
        a.portExternal = portExternal;
        return a;
    }

    /**
     * Create a new Address object based this one with different internal hostname.
     *
     * @param hostnameInternal New hostnameInternal for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetHostnameInternal(@NonNull final String hostnameInternal) {
        Objects.requireNonNull(hostnameInternal, "hostnameInternal must not be null");
        Address a = copy();
        a.hostnameInternal = hostnameInternal;
        return a;
    }

    /**
     * Create a new Address object based this one with different externam hostname.
     *
     * @param hostnameExternal New hostnameExternal for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetHostnameExternal(@NonNull final String hostnameExternal) {
        Objects.requireNonNull(hostnameExternal, "hostnameExternal must not be null");
        Address a = copy();
        a.hostnameExternal = hostnameExternal;
        return a;
    }

    /**
     * Create a new Address object based this one with different PublicKey for signature.
     *
     * @param sigPublicKey New sigPublicKey for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetSigPublicKey(@NonNull final PublicKey sigPublicKey) {
        Objects.requireNonNull(sigPublicKey, "sigPublicKey must not be null");
        Address a = copy();
        a.sigPublicKey = new SerializablePublicKey(sigPublicKey);
        return a;
    }

    /**
     * Create a new Address object based this one with different PublicKey for encrypting.
     *
     * @param encPublicKey New encPublicKey for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetEncPublicKey(@NonNull final PublicKey encPublicKey) {
        Objects.requireNonNull(encPublicKey, "encPublicKey must not be null");
        Address a = copy();
        a.encPublicKey = new SerializablePublicKey(encPublicKey);
        return a;
    }

    /**
     * Create a new Address object based this one with different PublicKey for TLS key agreement.
     *
     * @param agreePublicKey New agreePublicKey for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetAgreePublicKey(@NonNull final PublicKey agreePublicKey) {
        Objects.requireNonNull(agreePublicKey, "agreePublicKey must not be null");
        Address a = copy();
        a.agreePublicKey = new SerializablePublicKey(agreePublicKey);
        return a;
    }

    /**
     * Create a new Address object based this one with different memo.
     *
     * @param memo New memo for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetMemo(@NonNull final String memo) {
        Objects.requireNonNull(memo, "memo must not be null");
        final Address a = copy();
        a.memo = memo;
        return a;
    }

    /**
     * Create a new Address object based on this one.
     *
     * @return A duplication of current Address object.
     */
    public Address copy() {
        return new Address(
                id,
                nickname,
                selfName,
                weight,
                hostnameInternal,
                portInternal,
                hostnameExternal,
                portExternal,
                sigPublicKey,
                encPublicKey,
                agreePublicKey,
                memo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream outStream) throws IOException {
        outStream.writeSerializable(id, false);
        outStream.writeNormalisedString(nickname);
        outStream.writeNormalisedString(selfName);
        outStream.writeLong(weight);
        outStream.writeNormalisedString(hostnameInternal);
        outStream.writeInt(portInternal);
        outStream.writeNormalisedString(hostnameExternal);
        outStream.writeInt(portExternal);
        outStream.writeSerializable(sigPublicKey, false);
        outStream.writeSerializable(encPublicKey, false);
        outStream.writeSerializable(agreePublicKey, false);
        outStream.writeNormalisedString(memo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream inStream, int version) throws IOException {
        if (version < ClassVersion.SELF_SERIALIZABLE_NODE_ID) {
            id = new NodeId(inStream.readLong());
        } else {
            id = inStream.readSerializable(false, NodeId::new);
        }
        nickname = inStream.readNormalisedString(STRING_MAX_BYTES);
        selfName = inStream.readNormalisedString(STRING_MAX_BYTES);
        weight = inStream.readLong();

        if (version < ClassVersion.ADD_DNS_SUPPORT) {
            hostnameInternal = ipString(inStream.readByteArray(MAX_IP_LENGTH));
        } else {
            hostnameInternal = inStream.readNormalisedString(MAX_IP_LENGTH);
        }
        portInternal = inStream.readInt();
        if (version < ClassVersion.ADD_DNS_SUPPORT) {
            hostnameExternal = ipString(inStream.readByteArray(MAX_IP_LENGTH));
        } else {
            hostnameExternal = inStream.readNormalisedString(MAX_IP_LENGTH);
        }
        portExternal = inStream.readInt();
        if (version < ClassVersion.ADD_DNS_SUPPORT) {
            inStream.readByteArray(MAX_IP_LENGTH); // addressInternalIpv6
            inStream.readInt(); // portInternalIpv6
            inStream.readByteArray(MAX_IP_LENGTH); // addressExternalIpv6
            inStream.readInt(); // portExternalIpv6
        }

        switch (version) {
            case 1:
                sigPublicKey = new SerializablePublicKey();
                encPublicKey = new SerializablePublicKey();
                agreePublicKey = new SerializablePublicKey();
                // before version 2, the key type was not written
                sigPublicKey.deserializeVersion0(inStream, "RSA");
                encPublicKey.deserializeVersion0(inStream, "EC");
                agreePublicKey.deserializeVersion0(inStream, "EC");
                break;
            case 2:
                // in version 2 the key type was written as a string and the key version was not written
                sigPublicKey = new SerializablePublicKey();
                encPublicKey = new SerializablePublicKey();
                agreePublicKey = new SerializablePublicKey();
                sigPublicKey.deserialize(inStream, 1);
                encPublicKey.deserialize(inStream, 1);
                agreePublicKey.deserialize(inStream, 1);
                break;
            default:
                sigPublicKey = inStream.readSerializable(false, SerializablePublicKey::new);
                encPublicKey = inStream.readSerializable(false, SerializablePublicKey::new);
                agreePublicKey = inStream.readSerializable(false, SerializablePublicKey::new);
        }
        memo = inStream.readNormalisedString(STRING_MAX_BYTES);
    }

    /**
     * Return the String of dot format of the IPv4 address.
     *
     * @param ip IP address.
     * @return IP address String of dot format.
     */
    public static String ipString(byte[] ip) {
        return "" + (0xff & ip[0]) + "." + (0xff & ip[1]) + "." + (0xff & ip[2]) + "." + (0xff & ip[3]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Address address = (Address) o;
        return equalsWithoutWeight(address) && weight == address.weight;
    }

    /**
     * Checks for equality with another addresses without checking the equality of weight.
     *
     * @param address The other address to check for equality with this address.
     * @return true if all values in the other address match this address without consideration of weight, false
     * otherwise.
     */
    public boolean equalsWithoutWeight(@NonNull final Address address) {
        return Objects.equals(id, address.id)
                && portInternal == address.portInternal
                && portExternal == address.portExternal
                && Objects.equals(nickname, address.nickname)
                && Objects.equals(selfName, address.selfName)
                && Objects.equals(hostnameInternal, address.hostnameInternal)
                && Objects.equals(hostnameExternal, address.hostnameExternal)
                && Arrays.equals(
                        sigPublicKey.getPublicKey().getEncoded(),
                        address.sigPublicKey.getPublicKey().getEncoded())
                && Arrays.equals(
                        agreePublicKey.getPublicKey().getEncoded(),
                        address.agreePublicKey.getPublicKey().getEncoded())
                && Objects.equals(memo, address.memo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return hash32(id.id());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("nickname", nickname)
                .append("selfName", selfName)
                .append("weight", weight)
                .append("hostnameInternal", hostnameInternal)
                .append("portInternalIpv4", portInternal)
                .append("hostnameExternal", hostnameExternal)
                .append("portExternalIpv4", portExternal)
                .append("sigPublicKey", sigPublicKey)
                .append("agreePublicKey", agreePublicKey)
                .append("memo", memo)
                .toString();
    }
}
