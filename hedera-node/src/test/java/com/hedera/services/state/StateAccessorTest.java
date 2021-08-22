package com.hedera.services.state;

import com.hedera.services.ServicesState;
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
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StateAccessorTest {
	@Mock
	private ServicesState state;
	@Mock
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	@Mock
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	@Mock
	private FCMap<MerkleEntityId, MerkleTopic> topics;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;
	@Mock
	private FCMap<MerkleEntityId, MerkleSchedule> scheduleTxs;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private AddressBook addressBook;
	@Mock
	private MerkleDiskFs diskFs;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens;
	@Mock
	private FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations;
	@Mock
	private FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations;
	@Mock
	private FCOneToManyRelation<PermHashInteger, Long> uniqueTreasuryOwnershipAssociations;

	private StateAccessor subject;

	@BeforeEach
	void setUp() {
		subject = new StateAccessor(state);
	}

	@Test
	void childrenGetUpdatedAsExpected() {
		given(state.accounts()).willReturn(accounts);
		given(state.storage()).willReturn(storage);
		given(state.topics()).willReturn(topics);
		given(state.tokens()).willReturn(tokens);
		given(state.tokenAssociations()).willReturn(tokenAssociations);
		given(state.scheduleTxs()).willReturn(scheduleTxs);
		given(state.networkCtx()).willReturn(networkCtx);
		given(state.addressBook()).willReturn(addressBook);
		given(state.diskFs()).willReturn(diskFs);
		given(state.uniqueTokens()).willReturn(uniqueTokens);
		given(state.uniqueTokenAssociations()).willReturn(uniqueTokenAssociations);
		given(state.uniqueOwnershipAssociations()).willReturn(uniqueOwnershipAssociations);
		given(state.uniqueTreasuryOwnershipAssociations()).willReturn(uniqueTreasuryOwnershipAssociations);

		// when:
		subject.updateFrom(state);

		// then:
		assertSame(accounts, subject.accounts());
		assertSame(storage, subject.storage());
		assertSame(topics, subject.topics());
		assertSame(tokens, subject.tokens());
		assertSame(tokenAssociations, subject.tokenAssociations());
		assertSame(scheduleTxs, subject.schedules());
		assertSame(networkCtx, subject.networkCtx());
		assertSame(addressBook, subject.addressBook());
		assertSame(diskFs, subject.diskFs());
		assertSame(uniqueTokens, subject.uniqueTokens());
		assertSame(uniqueTokenAssociations, subject.uniqueTokenAssociations());
		assertSame(uniqueOwnershipAssociations, subject.uniqueOwnershipAssociations());
		assertSame(uniqueTreasuryOwnershipAssociations, subject.uniqueOwnershipTreasuryAssociations());
	}

	@Test
	void childrenNonNull() {
		// expect:
		Assertions.assertNotNull(subject.children());
	}
}
