package com.hedera.services.store.tokens.views;

import org.junit.jupiter.api.Test;

import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.services.store.tokens.views.EmptyUniqueTokenView.EMPTY_UNIQUE_TOKEN_VIEW;
import static org.junit.jupiter.api.Assertions.*;

class EmptyUniqTokenViewFactoryTest {
	@Test
	void alwaysCreatesEmptyView() {
		// expect:
		assertSame(
				EMPTY_UNIQUE_TOKEN_VIEW,
				EMPTY_UNIQ_TOKEN_VIEW_FACTORY.viewFor(null, null, null, null, null, null));
	}
}