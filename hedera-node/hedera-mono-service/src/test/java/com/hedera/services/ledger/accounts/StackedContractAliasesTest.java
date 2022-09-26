/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StackedContractAliasesTest {
    private static final EntityNum num = EntityNum.fromLong(1234L);
    private static final byte[] rawNonMirrorAddress =
            unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final byte[] otherRawNonMirrorAddress =
            unhex("abcdecabcdecabcdecbabcdecabcdecabcdecbbb");
    private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
    private static final Address otherNonMirrorAddress =
            Address.wrap(Bytes.wrap(otherRawNonMirrorAddress));
    private static final Address mirrorAddress = num.toEvmAddress();
    private static final Address otherMirrorAddress = EntityNum.fromLong(1235L).toEvmAddress();
    private static final ContractID normalId = num.toGrpcContractID();
    private static final ContractID aliasedId =
            ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(rawNonMirrorAddress)).build();

    @Mock private ContractAliases wrappedAliases;
    @Mock private SigImpactHistorian observer;

    private StackedContractAliases subject;

    @Test
    void getsCurrentAddressForAliased() {
        final var mockSubject = mock(ContractAliases.class);

        given(mockSubject.resolveForEvm(nonMirrorAddress)).willReturn(mirrorAddress);
        doCallRealMethod().when(mockSubject).currentAddress(aliasedId);

        final var actual = mockSubject.currentAddress(aliasedId);
        assertEquals(mirrorAddress, actual);
    }

    @Test
    void getsCurrentAddressForNonAliased() {
        final var mockSubject = mock(ContractAliases.class);

        doCallRealMethod().when(mockSubject).currentAddress(normalId);

        final var actual = mockSubject.currentAddress(normalId);
        assertEquals(num.toEvmAddress(), actual);
    }

    @BeforeEach
    void setUp() {
        subject = new StackedContractAliases(wrappedAliases);
    }

    @Test
    void mirrorAddressesAreNotAliases() {
        assertFalse(subject.isInUse(mirrorAddress));
    }

    @Test
    void updatedAliasesAreNecessarilyAliases() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        assertTrue(subject.isInUse(nonMirrorAddress));
    }

    @Test
    void resolvableAliasesAreAliases() {
        given(wrappedAliases.isInUse(nonMirrorAddress)).willReturn(true);
        assertTrue(subject.isInUse(nonMirrorAddress));
    }

    @Test
    void unlinkedAliasesAreNotAliases() {
        subject.removedLinks().add(nonMirrorAddress);
        assertFalse(subject.isInUse(nonMirrorAddress));
    }

    @Test
    void refusesToLinkToNonMirrorAddress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.link(nonMirrorAddress, nonMirrorAddress));
    }

    @Test
    void refusesToLinkFromMirrorAddress() {
        assertThrows(
                IllegalArgumentException.class, () -> subject.link(mirrorAddress, mirrorAddress));
    }

    @Test
    void resolvesMirrorIdToSelf() {
        assertSame(mirrorAddress, subject.resolveForEvm(mirrorAddress));
    }

    @Test
    void resolvesNewlyLinkedAliasToAddress() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        assertSame(mirrorAddress, subject.resolveForEvm(nonMirrorAddress));
    }

    @Test
    void resolvesUnlinkedAliasToNull() {
        subject.removedLinks().add(nonMirrorAddress);
        assertNull(subject.resolveForEvm(nonMirrorAddress));
    }

    @Test
    void resolvesUntouchedAliasViaWrapped() {
        given(wrappedAliases.resolveForEvm(nonMirrorAddress)).willReturn(mirrorAddress);
        assertSame(mirrorAddress, subject.resolveForEvm(nonMirrorAddress));
    }

    @Test
    void linkingAddsToMap() {
        subject.link(nonMirrorAddress, mirrorAddress);
        assertSame(mirrorAddress, subject.changedLinks().get(nonMirrorAddress));
    }

    @Test
    void linkingUndoesRemoval() {
        subject.removedLinks().add(nonMirrorAddress);
        subject.link(nonMirrorAddress, mirrorAddress);
        assertSame(mirrorAddress, subject.changedLinks().get(nonMirrorAddress));
    }

    @Test
    void refusesToUnlinkMirrorAddress() {
        assertThrows(IllegalArgumentException.class, () -> subject.unlink(mirrorAddress));
    }

    @Test
    void unlinkingUpdatesRemoved() {
        subject.unlink(nonMirrorAddress);
        assertTrue(subject.removedLinks().contains(nonMirrorAddress));
    }

    @Test
    void unlinkingUndoesChange() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        subject.unlink(nonMirrorAddress);
        assertFalse(subject.changedLinks().containsKey(nonMirrorAddress));
        assertTrue(subject.removedLinks().contains(nonMirrorAddress));
    }

    @Test
    void canCommitAndFilterNothing() {
        assertDoesNotThrow(() -> subject.commit(null));
        assertDoesNotThrow(() -> subject.filterPendingChanges(null));
    }

    @Test
    void revertingDoesNothingWithNoChanges() {
        assertDoesNotThrow(subject::revert);
    }

    @Test
    void filteringChangesWorks() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        subject.changedLinks().put(otherNonMirrorAddress, otherMirrorAddress);

        subject.filterPendingChanges(address -> address.equals(mirrorAddress));

        assertEquals(Map.of(nonMirrorAddress, mirrorAddress), subject.changedLinks());
    }

    @Test
    void revertingNullsOutChanges() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        subject.removedLinks().add(otherNonMirrorAddress);

        subject.revert();

        assertTrue(subject.changedLinks().isEmpty());
        assertTrue(subject.removedLinks().isEmpty());
    }

    @Test
    void removesUnlinkedAndLinksChanged() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        subject.removedLinks().add(otherNonMirrorAddress);

        subject.commit(null);

        verify(wrappedAliases).unlink(otherNonMirrorAddress);
        verify(wrappedAliases).link(nonMirrorAddress, mirrorAddress);
    }

    @Test
    void removesUnlinkedAndLinksChangedWithObserverPropagationIfSet() {
        subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
        subject.removedLinks().add(otherNonMirrorAddress);

        subject.commit(observer);

        verify(observer).markAliasChanged(ByteString.copyFrom(rawNonMirrorAddress));
        verify(observer).markAliasChanged(ByteString.copyFrom(otherRawNonMirrorAddress));
    }
}
