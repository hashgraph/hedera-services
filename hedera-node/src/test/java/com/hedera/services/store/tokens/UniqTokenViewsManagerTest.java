package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UniqTokenViewsManagerTest {
	@Mock
	private Iterator<MerkleUniqueTokenId> expectedIterator;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByType;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByOwner;

	private UniqTokenViewsManager subject;

	@BeforeEach
	void setUp() {
		subject = new UniqTokenViewsManager(() -> nftsByType, () -> nftsByOwner);
	}

	@Test
	void createsAssociationAsExpected() {
		// when:
		subject.createAssociation(oneNft, aOwner);

		// then:
		verify(nftsByType).associate(cToken, oneNft);
		verify(nftsByOwner).associate(aOwner, oneNft);
	}

	@Test
	void forgetsAssociationAsExpected() {
		// when:
		subject.forgetAssociation(oneNft, aOwner);

		// then:
		verify(nftsByType).disassociate(cToken, oneNft);
		verify(nftsByOwner).disassociate(aOwner, oneNft);
	}

	@Test
	void changesAssociationAsExpected() {
		// when:
		subject.changeAssociation(oneNft, aOwner, bOwner);

		// then:
		verify(nftsByOwner).disassociate(aOwner, oneNft);
		verify(nftsByOwner).associate(bOwner, oneNft);
	}

	@Test
	void ownedAssociationsAsExpected() {
		given(nftsByOwner.get(aOwner, someStart, someEnd)).willReturn(expectedIterator);

		// when:
		final var actualIterator = subject.ownedAssociations(aOwner, someStart, someEnd);

		// then:
		assertSame(expectedIterator, actualIterator);
	}

	private final int someStart = 1;
	private final int someEnd = 2;
	private final EntityId aOwner = new EntityId(1, 2, 3);
	private final EntityId bOwner = new EntityId(2, 3, 4);
	private final EntityId cToken = new EntityId(3,4 , 5);
	private final MerkleUniqueTokenId oneNft = new MerkleUniqueTokenId(cToken, 1L);
	private final MerkleUniqueTokenId twoNft = new MerkleUniqueTokenId(cToken, 2L);
	private final MerkleUniqueTokenId threeNft = new MerkleUniqueTokenId(cToken, 3L);
}