package com.hedera.services;

import com.hedera.services.state.LegacyStateChildIndices;
import com.hedera.services.state.StateChildIndices;
import com.hedera.services.state.StateMetadata;
import com.hedera.services.state.StateVersions;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.swirlds.common.AddressBook;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMInternalNode;
import com.swirlds.fcmap.internal.FCMLeaf;
import com.swirlds.fcmap.internal.FCMTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.swirlds.common.constructable.ConstructableRegistry.registerConstructable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ServicesStateTest {
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	private FCMap<MerkleEntityId, MerkleTopic> topics;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens;
	@Mock
	private FCMap<MerkleEntityId, MerkleSchedule> schedules;
	@Mock
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	@Mock
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;
	@Mock
	private FCOneToManyRelation<Integer, Long> uniqueTokenAssociations;
	@Mock
	private FCOneToManyRelation<Integer, Long> uniqueOwnershipAssociations;
	@Mock
	private FCOneToManyRelation<Integer, Long> uniqueOwnershipTreasuryAssociations;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private AddressBook addressBook;
	@Mock
	private MerkleDiskFs diskFs;
	@Mock
	private StateMetadata metadata;

	private ServicesState subject = new ServicesState();

	@Test
	void minimumVersionIsRelease0160() {
		// expect:
		assertEquals(StateVersions.RELEASE_0160_VERSION, subject.getMinimumSupportedVersion());
	}

	@Test
	void minimumChildCountsAsExpected() {
		// expect:
		assertEquals(
				LegacyStateChildIndices.NUM_0160_CHILDREN,
				subject.getMinimumChildCount(StateVersions.RELEASE_0160_VERSION));
		assertEquals(
				StateChildIndices.NUM_0170_CHILDREN,
				subject.getMinimumChildCount(StateVersions.RELEASE_0170_VERSION));
		assertThrows(IllegalArgumentException.class,
				() -> subject.getMinimumChildCount(StateVersions.RELEASE_0160_VERSION - 1));
		assertThrows(IllegalArgumentException.class,
				() -> subject.getMinimumChildCount(StateVersions.RELEASE_0170_VERSION + 1));
	}

	@Test
	void merkleMetaAsExpected() {
		// expect:
		assertEquals(0x8e300b0dfdafbb1aL, subject.getClassId());
		assertEquals(StateVersions.CURRENT_VERSION, subject.getVersion());
	}

	@Test
	void copyConstructorWorks() {
		// setup:
	}

	@Test
	void doesntMigrateFromRelease0170() {
		// given:
		subject.addDeserializedChildren(Collections.emptyList(), StateVersions.RELEASE_0170_VERSION);

		// expect:
		assertDoesNotThrow(subject::initialize);
	}

	@Test
	void migratesFromRelease0160AsExpected() throws ConstructableRegistryException {
		// setup:
		final var addressBook = new AddressBook();
		final var networkContext = new MerkleNetworkContext();
		networkContext.setSeqNo(new SequenceNumber(1234L));
		networkContext.setMidnightRates(new ExchangeRates(1, 2, 3, 4, 5, 6));
		final FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts = new FCMap<>();
		final FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels = new FCMap<>();
		final var nftKey = new MerkleUniqueTokenId(MISSING_ENTITY_ID, 1L);
		final var tokenRelsKey = new MerkleEntityAssociation(0, 0, 2, 0, 0, 3);
		// and:
		registerConstructable(new ClassConstructorPair(FCMap.class, FCMap::new));
		registerConstructable(new ClassConstructorPair(FCMTree.class, FCMTree::new));
		registerConstructable(new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		registerConstructable(new ClassConstructorPair(FCMInternalNode.class, FCMInternalNode::new));
		// and:
		nfts.put(nftKey, new MerkleUniqueToken(MISSING_ENTITY_ID, "TBD".getBytes(), MISSING_INSTANT));
		tokenRels.put(tokenRelsKey, new MerkleTokenRelStatus(1_234L, true, false));
		// and:
		final List<MerkleNode> legacyChildren = legacyChildrenWith(addressBook, networkContext, nfts, tokenRels);

		// given:
		subject.addDeserializedChildren(legacyChildren, StateVersions.RELEASE_0160_VERSION);

		// when:
		subject.initialize();

		// then:
		assertEquals(addressBook, subject.getChild(StateChildIndices.ADDRESS_BOOK));
		assertEquals(addressBook, subject.addressBook());
		assertEquals(
				networkContext.midnightRates(),
				((MerkleNetworkContext)subject.getChild(StateChildIndices.NETWORK_CTX)).midnightRates());
		assertEquals(networkContext.midnightRates(), subject.networkCtx().midnightRates());
		assertEquals(
				nfts.get(nftKey),
				((FCMap<MerkleUniqueTokenId, MerkleUniqueToken>)subject.getChild(StateChildIndices.UNIQUE_TOKENS))
						.get(nftKey));
		assertEquals(nfts.get(nftKey), subject.uniqueTokens().get(nftKey));
		assertEquals(
				tokenRels.get(tokenRelsKey),
				((FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>)subject.getChild(StateChildIndices.TOKEN_ASSOCIATIONS))
						.get(tokenRelsKey));
		assertEquals(tokenRels.get(tokenRelsKey), subject.tokenAssociations().get(tokenRelsKey));
	}

	private List<MerkleNode> legacyChildrenWith(
			AddressBook addressBook,
			MerkleNetworkContext networkContext,
			FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts,
			FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels
	) {
		final List<MerkleNode> legacyChildren = new ArrayList<>();
		legacyChildren.add(addressBook);
		legacyChildren.add(networkContext);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(tokenRels);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(null);
		legacyChildren.add(nfts);
		return legacyChildren;
	}
}