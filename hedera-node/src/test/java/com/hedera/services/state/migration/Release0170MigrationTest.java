package com.hedera.services.state.migration;

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

import static com.hedera.services.state.migration.Release0170Migration.moveLargeFcmsToBinaryRoutePositions;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.org.LegacyStateChildIndices;
import com.hedera.services.state.org.StateChildIndices;
import com.hedera.services.state.org.StateVersions;
import com.swirlds.common.AddressBook;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Release0170MigrationTest {
  @Mock private ServicesState state;
  @Mock private AddressBook addressBook;
  @Mock private MerkleNetworkContext networkContext;
  @Mock private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
  @Mock private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels;
  @Mock private Release0170Migration.TreeCopier treeCopier;

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
    given(state.getChild(LegacyStateChildIndices.ADDRESS_BOOK)).willReturn(addressBook);
    // and:
    given(networkContext.copy()).willReturn(networkContext);
    given(state.getChild(LegacyStateChildIndices.NETWORK_CTX)).willReturn(networkContext);
    // and:
    given(state.getChild(LegacyStateChildIndices.UNIQUE_TOKENS)).willReturn(nfts);
    given(state.getChild(LegacyStateChildIndices.TOKEN_ASSOCIATIONS)).willReturn(tokenRels);

    // when:
    moveLargeFcmsToBinaryRoutePositions(state, StateVersions.RELEASE_0160_VERSION);

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
