/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.address;

import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;

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
    /** IP address on the local network (IPv4) */
    private byte[] addressInternalIpv4;
    /** port used on the local network (IPv4) */
    private int portInternalIpv4;
    /** IP address outside the NATing firewall (IPv4) */
    private byte[] addressExternalIpv4;
    /** port used outside the NATing firewall (IPv4) */
    private int portExternalIpv4;
    /** IP address on the local network (IPv6) */
    private byte[] addressInternalIpv6;
    /** port used on the local network (IPv6) */
    private int portInternalIpv6;
    /** port used outside the NATing firewall (IPv6) */
    private int portExternalIpv6;
    /** IP address outside the NATing firewall (IPv6) */
    private byte[] addressExternalIpv6;
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
            @Nullable final byte[] addressInternalIpv4,
            final int portInternalIpv4,
            @Nullable final byte[] addressExternalIpv4,
            final int portExternalIpv4,
            @NonNull final String memo) {
        this(
                id,
                nickname,
                selfName,
                weight, // weight
                addressInternalIpv4,
                portInternalIpv4,
                addressExternalIpv4,
                portExternalIpv4,
                null,
                -1,
                null,
                -1,
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
     * @param addressInternalIpv4 IPv4 address on the inside of the NATing router
     * @param portInternalIpv4    port for the internal IPv4 address
     * @param addressExternalIpv4 IPv4 address on the outside of the NATing router (same as internal if there is no
     *                            NAT)
     * @param portExternalIpv4    port for the external IPv4 address
     * @param addressInternalIpv6 IPv6 address on the inside of the NATing router
     * @param portInternalIpv6    port for the internal IPv6 address
     * @param addressExternalIpv6 address on the outside of the NATing router
     * @param portExternalIpv6    port for the external IPv6 address
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
            @Nullable final byte[] addressInternalIpv4,
            final int portInternalIpv4,
            @Nullable final byte[] addressExternalIpv4,
            final int portExternalIpv4,
            @Nullable final byte[] addressInternalIpv6,
            final int portInternalIpv6,
            @Nullable final byte[] addressExternalIpv6,
            final int portExternalIpv6,
            @Nullable final SerializablePublicKey sigPublicKey,
            @Nullable final SerializablePublicKey encPublicKey,
            @Nullable final SerializablePublicKey agreePublicKey,
            @NonNull final String memo) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
        this.selfName = Objects.requireNonNull(selfName, "selfName must not be null");
        this.weight = weight;
        this.portInternalIpv4 = portInternalIpv4;
        this.portInternalIpv6 = portInternalIpv6;
        this.portExternalIpv4 = portExternalIpv4;
        this.portExternalIpv6 = portExternalIpv6;
        this.addressInternalIpv4 = clone(addressInternalIpv4);
        this.addressInternalIpv6 = clone(addressInternalIpv6);
        this.addressExternalIpv4 = clone(addressExternalIpv4);
        this.addressExternalIpv6 = clone(addressExternalIpv6);
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
        return ClassVersion.SELF_SERIALIZABLE_NODE_ID;
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
    public int getConnectPortIpv4(Address a) {
        return isLocalTo(a) ? getPortInternalIpv4() : getPortExternalIpv4();
    }

    /**
     * Check whether a given Address has the same external IPv4 address as mine.
     *
     * @param a Given Address to check.
     * @return True if they are exactly the same.
     */
    public boolean isLocalTo(Address a) {
        return Arrays.equals(getAddressExternalIpv4(), a.getAddressExternalIpv4());
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
     * Get listening port used on the local IPv4 network.
     *
     * @return The IPv4 port number.
     */
    public int getListenPortIpv4() {
        return getPortInternalIpv4();
    }

    /**
     * Get port used on the local IPv4 network.
     *
     * @return The IPv4 port number.
     */
    public int getPortInternalIpv4() {
        return portInternalIpv4;
    }

    /**
     * Get port used on the local IPv6 network.
     *
     * @return The IPv6 port number.
     */
    public int getPortInternalIpv6() {
        return portInternalIpv6;
    }

    /**
     * Get port used on the external IPv4 network.
     *
     * @return The IPv4 port number.
     */
    public int getPortExternalIpv4() {
        return portExternalIpv4;
    }

    /**
     * Get port used on the external IPv6 network.
     *
     * @return The IPv6 port number.
     */
    public int getPortExternalIpv6() {
        return portExternalIpv6;
    }

    /**
     * Get IPv4 address used on the local IPv4 network.
     *
     * @return The IPv4 address.
     */
    @Nullable
    public byte[] getAddressInternalIpv4() {
        return clone(addressInternalIpv4);
    }

    /**
     * Get IPv6 address used on the local IPv6 network.
     *
     * @return The IPv6 address.
     */
    @Nullable
    public byte[] getAddressInternalIpv6() {
        return clone(addressInternalIpv6);
    }

    /**
     * Get IPv4 address used on the external IPv4 network.
     *
     * @return The IPv4 address.
     */
    @Nullable
    public byte[] getAddressExternalIpv4() {
        return clone(addressExternalIpv4);
    }

    /**
     * Get IPv6 address used on the external IPv6 network.
     *
     * @return The IPv6 address.
     */
    @Nullable
    public byte[] getAddressExternalIpv6() {
        return clone(addressExternalIpv6);
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
     * Create a new Address object based this one with different internal IPv4 port.
     *
     * @param portInternalIpv4 New portInternalIpv4 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetPortInternalIpv4(int portInternalIpv4) {
        Address a = copy();
        a.portInternalIpv4 = portInternalIpv4;
        return a;
    }

    /**
     * Create a new Address object based this one with different internal IPv6 port.
     *
     * @param portInternalIpv6 New portInternalIpv6 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetPortInternalIpv6(int portInternalIpv6) {
        Address a = copy();
        a.portInternalIpv6 = portInternalIpv6;
        return a;
    }

    /**
     * Create a new Address object based this one with different external Ipv4 port.
     *
     * @param portExternalIpv4 New portExternalIpv4 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetPortExternalIpv4(int portExternalIpv4) {
        Address a = copy();
        a.portExternalIpv4 = portExternalIpv4;
        return a;
    }

    /**
     * Create a new Address object based this one with different external Ipv6 port.
     *
     * @param portExternalIpv6 New portExternalIpv6 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetPortExternalIpv6(int portExternalIpv6) {
        Address a = copy();
        a.portExternalIpv6 = portExternalIpv6;
        return a;
    }

    /**
     * Create a new Address object based this one with different internal IPv4 address.
     *
     * @param addressInternalIpv4 New addressInternalIpv4 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetAddressInternalIpv4(@NonNull final byte[] addressInternalIpv4) {
        Objects.requireNonNull(addressInternalIpv4, "addressInternalIpv4 must not be null");
        Address a = copy();
        a.addressInternalIpv4 = clone(addressInternalIpv4);
        return a;
    }

    /**
     * Create a new Address object based this one with different internal IPv6 address.
     *
     * @param addressInternalIpv6 New addressInternalIpv6 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetAddressInternalIpv6(@NonNull final byte[] addressInternalIpv6) {
        Objects.requireNonNull(addressInternalIpv6, "addressInternalIpv6 must not be null");
        Address a = copy();
        a.addressInternalIpv6 = clone(addressInternalIpv6);
        return a;
    }

    /**
     * Create a new Address object based this one with different external IPv4 address.
     *
     * @param addressExternalIpv4 New addressExternalIpv4 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetAddressExternalIpv4(@NonNull final byte[] addressExternalIpv4) {
        Objects.requireNonNull(addressExternalIpv4, "addressExternalIpv4 must not be null");
        Address a = copy();
        a.addressExternalIpv4 = clone(addressExternalIpv4);
        return a;
    }

    /**
     * Create a new Address object based this one with different external IPv6 address.
     *
     * @param addressExternalIpv6 New addressExternalIpv6 for the created Address.
     * @return The new Address.
     */
    @NonNull
    public Address copySetAddressExternalIpv6(@NonNull final byte[] addressExternalIpv6) {
        Objects.requireNonNull(addressExternalIpv6, "addressExternalIpv6 must not be null");
        Address a = copy();
        a.addressExternalIpv6 = clone(addressExternalIpv6);
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
                addressInternalIpv4,
                portInternalIpv4,
                addressExternalIpv4,
                portExternalIpv4,
                addressInternalIpv6,
                portInternalIpv6,
                addressExternalIpv6,
                portExternalIpv6,
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
        outStream.writeByteArray(addressInternalIpv4);
        outStream.writeInt(portInternalIpv4);
        outStream.writeByteArray(addressExternalIpv4);
        outStream.writeInt(portExternalIpv4);
        outStream.writeByteArray(addressInternalIpv6);
        outStream.writeInt(portInternalIpv6);
        outStream.writeByteArray(addressExternalIpv6);
        outStream.writeInt(portExternalIpv6);
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

        addressInternalIpv4 = inStream.readByteArray(MAX_IP_LENGTH);
        portInternalIpv4 = inStream.readInt();
        addressExternalIpv4 = inStream.readByteArray(MAX_IP_LENGTH);
        portExternalIpv4 = inStream.readInt();
        addressInternalIpv6 = inStream.readByteArray(MAX_IP_LENGTH);
        portInternalIpv6 = inStream.readInt();
        addressExternalIpv6 = inStream.readByteArray(MAX_IP_LENGTH);
        portExternalIpv6 = inStream.readInt();

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
                && portInternalIpv4 == address.portInternalIpv4
                && portExternalIpv4 == address.portExternalIpv4
                && portInternalIpv6 == address.portInternalIpv6
                && portExternalIpv6 == address.portExternalIpv6
                && Objects.equals(nickname, address.nickname)
                && Objects.equals(selfName, address.selfName)
                && Arrays.equals(addressInternalIpv4, address.addressInternalIpv4)
                && Arrays.equals(addressExternalIpv4, address.addressExternalIpv4)
                && Arrays.equals(addressInternalIpv6, address.addressInternalIpv6)
                && Arrays.equals(addressExternalIpv6, address.addressExternalIpv6)
                && Arrays.equals(
                        sigPublicKey.getPublicKey().getEncoded(),
                        address.sigPublicKey.getPublicKey().getEncoded())
                && Arrays.equals(
                        encPublicKey.getPublicKey().getEncoded(),
                        address.encPublicKey.getPublicKey().getEncoded())
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
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("id", id)
                .append("nickname", nickname)
                .append("selfName", selfName)
                .append("weight", weight)
                .append("addressInternalIpv4", Arrays.toString(addressInternalIpv4))
                .append("portInternalIpv4", portInternalIpv4)
                .append("addressExternalIpv4", Arrays.toString(addressExternalIpv4))
                .append("portExternalIpv4", portExternalIpv4)
                .append("addressInternalIpv6", Arrays.toString(addressInternalIpv6))
                .append("portInternalIpv6", portInternalIpv6)
                .append("portExternalIpv6", portExternalIpv6)
                .append("addressExternalIpv6", Arrays.toString(addressExternalIpv6))
                .append("sigPublicKey", sigPublicKey)
                .append("encPublicKey", encPublicKey)
                .append("agreePublicKey", agreePublicKey)
                .append("memo", memo)
                .toString();
    }
}
