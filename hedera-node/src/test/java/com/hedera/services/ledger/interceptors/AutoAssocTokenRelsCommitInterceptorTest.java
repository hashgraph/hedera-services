package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AutoAssocTokenRelsCommitInterceptorTest {
	@Mock
	protected SideEffectsTracker sideEffectsTracker;

	private AutoAssocTokenRelsCommitInterceptor subject;

	@BeforeEach
	void setUp() {
		subject = AutoAssocTokenRelsCommitInterceptor.forKnownAutoAssociatingOp(sideEffectsTracker);
	}

	@Test
	void recordsOnlyNewAssociations() {
		final var changes = new EntityChangeSet<Pair<AccountID, TokenID>, MerkleTokenRelStatus, TokenRelProperty>();
		changes.include(Pair.of(aAccountId, alreadyAssocTokenId), extantRel, Map.of());
		changes.include(Pair.of(aAccountId, newAssocTokenId), null, Map.of());

		subject.preview(changes);

		verify(sideEffectsTracker).trackAutoAssociation(newAssocTokenId, aAccountId);
	}

	final AccountID aAccountId = AccountID.newBuilder().setAccountNum(1234).build();
	final TokenID alreadyAssocTokenId = TokenID.newBuilder().setTokenNum(1235).build();
	final TokenID newAssocTokenId = TokenID.newBuilder().setTokenNum(1236).build();
	final MerkleTokenRelStatus extantRel = new MerkleTokenRelStatus();
}