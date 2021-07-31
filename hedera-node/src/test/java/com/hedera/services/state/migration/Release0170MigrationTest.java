package com.hedera.services.state.migration;

import com.hedera.services.ServicesState;
import com.hedera.services.state.LegacyStateChildIndices;
import com.hedera.services.state.StateChildIndices;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.swirlds.common.AddressBook;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.migration.Release0170Migration.moveLargeFcmsToBinaryRoutePositions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class Release0170MigrationTest {
	@Mock
	private ServicesState state;
	@Mock
	private AddressBook addressBook;
	@Mock
	private MerkleNetworkContext networkContext;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels;
	@Mock
	private Release0170Migration.TreeCopier treeCopier;


	@BeforeEach
	void setUp() {
		Release0170Migration.setTreeCopier(treeCopier);
	}

	@AfterEach
	void cleanup() {
		Release0170Migration.setTreeCopier(MerkleCopy::copyTreeToLocation);
	}

	@Test
	void swapsChildrenAsExpected() {
		given(addressBook.copy()).willReturn(addressBook);
		given(state.getChild(LegacyStateChildIndices.ADDRESS_BOOK))	.willReturn(addressBook);
		// and:
		given(networkContext.copy()).willReturn(networkContext);
		given(state.getChild(LegacyStateChildIndices.NETWORK_CTX)).willReturn(networkContext);
		// and:
		given(state.getChild(LegacyStateChildIndices.UNIQUE_TOKENS)).willReturn(nfts);
		given(state.getChild(LegacyStateChildIndices.TOKEN_ASSOCIATIONS)).willReturn(tokenRels);

		// when:
		moveLargeFcmsToBinaryRoutePositions(state);

		// then:
		verify(addressBook).copy();
		verify(state).setChild(StateChildIndices.ADDRESS_BOOK, addressBook);
		verify(treeCopier).copyToLocation(state, StateChildIndices.UNIQUE_TOKENS, nfts);
		// and:
		verify(networkContext).copy();
		verify(state).setChild(StateChildIndices.NETWORK_CTX, networkContext);
		verify(treeCopier).copyToLocation(state, StateChildIndices.TOKEN_ASSOCIATIONS, tokenRels);
	}

	@Test
	void isUninstantiable() {
		assertThrows(IllegalStateException.class, Release0170Migration::new);
	}
}