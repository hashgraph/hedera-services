package com.hedera.services.store.tokens.views;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.hedera.services.store.tokens.views.EmptyUniqueTokenView.EMPTY_UNIQUE_TOKEN_VIEW;
import static org.junit.jupiter.api.Assertions.*;

class EmptyUniqueTokenViewTest {
	@Test
	void emptyViewIsEmpty() {
		// expect:
		assertSame(
				Collections.emptyList(),
				EMPTY_UNIQUE_TOKEN_VIEW.ownedAssociations(AccountID.getDefaultInstance(), 0, Long.MAX_VALUE));
		assertSame(
				Collections.emptyList(),
				EMPTY_UNIQUE_TOKEN_VIEW.typedAssociations(TokenID.getDefaultInstance(), 0, Long.MAX_VALUE));
	}
}