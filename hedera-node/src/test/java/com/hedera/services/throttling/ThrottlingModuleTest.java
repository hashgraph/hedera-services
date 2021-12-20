package com.hedera.services.throttling;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.swirlds.common.AddressBook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
class ThrottlingModuleTest {
	private static final GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	@Mock
	private AddressBook addressBook;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private TransactionContext txnCtx;

	@Test
	void constructsHapiAndHandleThrottlesAsExpected() {
		final var hapiThrottle = ThrottlingModule.provideHapiThrottling(
				aliasManager, () -> addressBook, dynamicProperties);
		final var handleThrottle = ThrottlingModule.provideHandleThrottling(
				aliasManager, txnCtx, dynamicProperties);

		assertThat(hapiThrottle, Matchers.instanceOf(HapiThrottling.class));
		assertThat(handleThrottle, Matchers.instanceOf(TxnAwareHandleThrottling.class));
	}
}