package com.hedera.services.throttling;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.swirlds.common.AddressBook;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.Supplier;

@Module
public abstract class ThrottlingModule {
	@Provides
	@Singleton
	@HapiThrottle
	public static FunctionalityThrottling provideHapiThrottling(
			Supplier<AddressBook> addressBook,
			GlobalDynamicProperties dynamicProperties
	) {
		final var delegate = new DeterministicThrottling(() -> addressBook.get().getSize(), dynamicProperties);
		return new HapiThrottling(delegate);
	}

	@Provides
	@Singleton
	@HandleThrottle
	public static FunctionalityThrottling provideHandleThrottling(
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties
	) {
		final var delegate = new DeterministicThrottling(() -> 1, dynamicProperties);
		return new TxnAwareHandleThrottling(txnCtx, delegate);
	}
}
