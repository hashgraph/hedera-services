/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.migration.HederaAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class AliasManagerTest {
    private static final ByteString alias = ByteString.copyFromUtf8("aaaa");
    private static final EntityNum num = EntityNum.fromLong(1234L);
    private static final byte[] rawNonMirrorAddress =
            unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
    private static final Address mirrorAddress = num.toEvmAddress();
    private static final byte[] ECDSA_PUBLIC_KEY =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private static final byte[] ECDSA_PUBLIC_KEY_ADDRESS =
            Hex.decode("a94f5374fce5edbc8e2a8697c15331677e6ebf0b");
    private static final byte[] notQuiteEcdsaPublicKey =
            Key.newBuilder()
                    .setECDSASecp256K1(
                            ByteStringUtils.wrapUnsafely(
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                            .getBytes()))
                    .build()
                    .toByteArray();

    private FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();

    private AliasManager subject = new AliasManager(() -> aliases);

    @Test
    void resolvesLinkedNonMirrorAsExpected() {
        subject.link(ByteString.copyFrom(rawNonMirrorAddress), num);
        assertEquals(num.toEvmAddress(), subject.resolveForEvm(nonMirrorAddress));
    }

    @Test
    void non20ByteStringCannotBeMirror() {
        assertFalse(subject.isMirror(new byte[] {(byte) 0xab, (byte) 0xcd}));
        assertFalse(subject.isMirror(unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbbde")));
    }

    @Test
    void resolvesUnlinkedNonMirrorAsExpected() {
        assertSame(nonMirrorAddress, subject.resolveForEvm(nonMirrorAddress));
    }

    @Test
    void resolvesMirrorAsExpected() {
        assertSame(mirrorAddress, subject.resolveForEvm(mirrorAddress));
    }

    @Test
    void doesntSupportTransactionalSemantics() {
        assertThrows(UnsupportedOperationException.class, () -> subject.commit(null));
        assertThrows(UnsupportedOperationException.class, () -> subject.filterPendingChanges(null));
        assertThrows(UnsupportedOperationException.class, subject::revert);
    }

    @Test
    void canLinkAndUnlinkAddresses() {
        subject.link(nonMirrorAddress, mirrorAddress);
        assertEquals(
                Map.of(ByteString.copyFrom(nonMirrorAddress.toArrayUnsafe()), num),
                subject.getAliases());

        subject.unlink(nonMirrorAddress);
        assertEquals(Collections.emptyMap(), subject.getAliases());
    }

    @Test
    void canLinkAndUnlinkEthereumAddresses()
            throws InvalidProtocolBufferException, DecoderException {
        Key key = Key.parseFrom(ECDSA_PUBLIC_KEY);
        JKey jKey = JKey.mapKey(key);
        boolean added = subject.maybeLinkEvmAddress(jKey, num);
        assertTrue(added);
        assertEquals(
                Map.of(ByteString.copyFrom(ECDSA_PUBLIC_KEY_ADDRESS), num), subject.getAliases());

        subject.forgetEvmAddress(ByteString.copyFrom(ECDSA_PUBLIC_KEY));
        assertEquals(Collections.emptyMap(), subject.getAliases());
    }

    @Test
    void noopOnTryingToForgetMalformattedSecp256k1Key() {
        assertDoesNotThrow(
                () ->
                        subject.forgetEvmAddress(
                                ByteStringUtils.wrapUnsafely(notQuiteEcdsaPublicKey)));
    }

    @Test
    void skipsUnrecoverableEthereumAddresses()
            throws InvalidProtocolBufferException, DecoderException {
        Key key = Key.parseFrom(ECDSA_PUBLIC_KEY);
        JKey jKey = JKey.mapKey(key);
        boolean added = subject.maybeLinkEvmAddress(jKey, num, any -> null);
        assertFalse(added);
    }

    @Test
    void ignoresNullKeys() {
        assertFalse(subject.maybeLinkEvmAddress(null, num, EthTxSigs::recoverAddressFromPubKey));
    }

    @Test
    void wontLinkOrUnlinked25519Key() throws DecoderException {
        var keyData = ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
        Key key = Key.newBuilder().setEd25519(keyData).build();
        JKey jKey = JKey.mapKey(key);
        boolean added = subject.maybeLinkEvmAddress(jKey, num, EthTxSigs::recoverAddressFromPubKey);
        assertFalse(added);
        assertEquals(Map.of(), subject.getAliases());

        subject.link(keyData, num);
        subject.forgetEvmAddress(keyData);
        assertEquals(Map.of(keyData, num), subject.getAliases());
    }

    @Test
    void createAliasAddsToMap() {
        subject.link(alias, num);

        assertEquals(Map.of(alias, num), subject.getAliases());
    }

    @Test
    void forgetReturnsExpectedValues() {
        final var unusedAlias = ByteString.copyFromUtf8("bbb");
        aliases.put(alias, num);
        assertFalse(subject.forgetAlias(ByteString.EMPTY));
        assertFalse(subject.forgetAlias(unusedAlias));
        assertTrue(subject.forgetAlias(alias));
    }

    @Test
    void forgetAliasRemovesFromMap() {
        subject.getAliases().put(alias, num);

        subject.unlink(alias);

        assertFalse(subject.getAliases().containsKey(alias));
    }

    @Test
    void isAliasChecksForMapMembershipOnly() {
        assertFalse(subject.isInUse(nonMirrorAddress));
        subject.link(nonMirrorAddress, mirrorAddress);
        assertTrue(subject.isInUse(nonMirrorAddress));
    }

    @Test
    void lookupIdByECDSAKeyAliasShouldReturnNumFromEVMAddressAliasMap()
            throws InvalidProtocolBufferException, DecoderException {
        subject.link(ByteString.copyFrom(ECDSA_PUBLIC_KEY_ADDRESS), num);
        assertEquals(num, subject.lookupIdBy(ByteString.copyFrom(ECDSA_PUBLIC_KEY)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rebuildsFromMap() throws ConstructableRegistryException {
        ConstructableRegistry.registerConstructable(
                new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));

        final var withNum = EntityNum.fromLong(1L);
        final var withoutNum = EntityNum.fromLong(2L);
        final var contractNum = EntityNum.fromLong(3L);
        final var ecdsaNum = EntityNum.fromLong(4L);
        final var susNum = EntityNum.fromLong(5L);
        final var notQuiteEcdsaNum = EntityNum.fromLong(6L);
        final var expiredAlias = ByteString.copyFromUtf8("zyxwvut");
        final var upToDateAlias = ByteString.copyFromUtf8("abcdefg");
        final var ecdsaAlias = ByteString.copyFrom(ECDSA_PUBLIC_KEY);
        final var ecdsaAddress = ByteString.copyFrom(ECDSA_PUBLIC_KEY_ADDRESS);
        final var contractAlias = ByteString.copyFrom(rawNonMirrorAddress);
        final var susAlias = ByteString.copyFromUtf8("012345678901234567891");
        final var notQuiteEcdsaAlias = ByteString.copyFrom(notQuiteEcdsaPublicKey);
        final var mockObserver = mock(BiConsumer.class);

        final var accountWithAlias = new MerkleAccount();
        accountWithAlias.setAlias(upToDateAlias);
        final var accountWithECDSAAlias = new MerkleAccount();
        accountWithECDSAAlias.setAlias(ecdsaAlias);
        final var accountWithNoAlias = new MerkleAccount();
        final var contractAccount = new MerkleAccount();
        contractAccount.setSmartContract(true);
        contractAccount.setAlias(contractAlias);
        final var susAccount = new MerkleAccount();
        susAccount.setAlias(susAlias);
        final var notQuiteEcdsaAccount = new MerkleAccount();
        notQuiteEcdsaAccount.setAlias(notQuiteEcdsaAlias);

        final MerkleMap<EntityNum, MerkleAccount> liveAccounts = new MerkleMap<>();
        liveAccounts.put(withNum, accountWithAlias);
        liveAccounts.put(ecdsaNum, accountWithECDSAAlias); // This will add _2_ aliases on rebuild
        liveAccounts.put(withoutNum, accountWithNoAlias);
        liveAccounts.put(contractNum, contractAccount);
        liveAccounts.put(susNum, susAccount);
        liveAccounts.put(notQuiteEcdsaNum, notQuiteEcdsaAccount);

        subject.getAliases().put(expiredAlias, withoutNum);
        subject.rebuildAliasesMap(
                AccountStorageAdapter.fromInMemory(liveAccounts),
                (BiConsumer<EntityNum, HederaAccount>) mockObserver);

        final var finalMap = subject.getAliases();
        assertEquals(6, finalMap.size());
        assertEquals(withNum, subject.getAliases().get(upToDateAlias));
        assertEquals(ecdsaNum, subject.getAliases().get(ecdsaAlias));
        assertEquals(ecdsaNum, subject.getAliases().get(ecdsaAddress));
        assertEquals(susNum, subject.getAliases().get(susAlias));
        assertEquals(notQuiteEcdsaNum, subject.getAliases().get(notQuiteEcdsaAlias));

        // finally when
        subject.forgetAlias(accountWithAlias.getAlias());
        assertEquals(5, subject.getAliases().size());
        subject.forgetEvmAddress(accountWithAlias.getAlias());
        assertEquals(5, subject.getAliases().size());
        subject.forgetAlias(accountWithECDSAAlias.getAlias());
        assertEquals(4, subject.getAliases().size());
        subject.forgetEvmAddress(accountWithECDSAAlias.getAlias());
        assertEquals(3, subject.getAliases().size());
        subject.forgetEvmAddress(ByteString.copyFromUtf8("This is not a valid alias"));
        assertEquals(3, subject.getAliases().size());
        verify(mockObserver, times(6)).accept(any(), any());
    }
}
