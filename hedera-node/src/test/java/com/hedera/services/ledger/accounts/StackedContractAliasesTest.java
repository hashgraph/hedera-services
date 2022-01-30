package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.utils.EntityNum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.swirlds.common.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StackedContractAliasesTest {
	private static final EntityNum num = EntityNum.fromLong(1234L);
	private static final byte[] rawNonMirrorAddress = unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final byte[] otherRawNonMirrorAddress = unhex("abcdecabcdecabcdecbabcdecabcdecabcdecbbb");
	private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
	private static final Address otherNonMirrorAddress = Address.wrap(Bytes.wrap(otherRawNonMirrorAddress));
	private static final Address mirrorAddress = num.toEvmAddress();

	@Mock
	private ContractAliases wrappedAliases;
	@Mock
	private SigImpactHistorian observer;

	private StackedContractAliases subject;

	@BeforeEach
	void setUp() {
		subject = new StackedContractAliases(wrappedAliases);
	}

	@Test
	void mirrorAddressesAreNotAliases() {
		assertFalse(subject.isActiveAlias(mirrorAddress));
	}

	@Test
	void updatedAliasesAreNecessarilyAliases() {
		subject.changedLinks().put(nonMirrorAddress, mirrorAddress);
		assertTrue(subject.isActiveAlias(nonMirrorAddress));
	}

	@Test
	void resolvableAliasesAreAliases() {
		given(wrappedAliases.isActiveAlias(nonMirrorAddress)).willReturn(true);
		assertTrue(subject.isActiveAlias(nonMirrorAddress));
	}

	@Test
	void unlinkedAliasesAreNotAliases() {
		subject.removedLinks().add(nonMirrorAddress);
		assertFalse(subject.isActiveAlias(nonMirrorAddress));
	}

	@Test
	void refusesToLinkToNonMirrorAddress() {
		assertThrows(IllegalArgumentException.class, () -> subject.link(nonMirrorAddress, nonMirrorAddress));
	}

	@Test
	void refusesToLinkFromMirrorAddress() {
		assertThrows(IllegalArgumentException.class, () -> subject.link(mirrorAddress, mirrorAddress));
	}

	@Test
	void linksUnusedAsExpected() {
		subject.link(nonMirrorAddress, mirrorAddress);
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
	void canCommitNothing() {
		assertDoesNotThrow(() -> subject.commit(null));
	}

	@Test
	void revertingDoesNothingWithNoChanges() {
		assertDoesNotThrow(subject::revert);
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
