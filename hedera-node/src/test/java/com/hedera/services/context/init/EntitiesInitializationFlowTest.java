package com.hedera.services.context.init;

import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.swirlds.blob.BinaryObjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class EntitiesInitializationFlowTest {
	@Mock
	private ExpiryManager expiryManager;
	@Mock
	private NetworkCtxManager networkCtxManager;
	@Mock
	private BinaryObjectStore binaryObjectStore;

	private EntitiesInitializationFlow subject;

	@BeforeEach
	void setUp() {
		subject = new EntitiesInitializationFlow(expiryManager, networkCtxManager, () -> binaryObjectStore);
	}

	@Test
	void runsAsExpectedWhenStoreNotInitializing() {
		// when:
		subject.run();

		// then:
		verify(expiryManager).reviewExistingPayerRecords();
		verify(expiryManager).reviewExistingShortLivedEntities();
		verify(networkCtxManager).setObservableFilesNotLoaded();
		verify(networkCtxManager).loadObservableSysFilesIfNeeded();
	}

	@Test
	void runsAsExpectedWhenStoreInitializing() {
		given(binaryObjectStore.isInitializing()).willReturn(true);

		// when:
		subject.run();

		// then:
		verify(expiryManager).reviewExistingPayerRecords();
		verify(expiryManager).reviewExistingShortLivedEntities();
		verify(networkCtxManager).setObservableFilesNotLoaded();
		verify(networkCtxManager, never()).loadObservableSysFilesIfNeeded();
	}
}