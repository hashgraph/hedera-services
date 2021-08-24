package com.hedera.services.context;

import com.hedera.services.ServicesApp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.context.AppsManager.APPS;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AppsManagerTest {
	private final long nodeIdA = 1L;
	private final long nodeIdB = 2L;

	@Mock
	private ServicesApp app;

	@Test
	void throwsIfNotInit() {
		// expect:
		assertThrows(IllegalArgumentException.class, () -> APPS.getInit(nodeIdA));
	}

	@Test
	void getsIfInit() {
		// given:
		APPS.init(nodeIdA, app);

		// expect:
		assertSame(app, APPS.getInit(nodeIdA));
	}

	@Test
	void recognizesInit() {
		// given:
		APPS.init(nodeIdA, app);

		// expect;
		assertTrue(APPS.isInit(nodeIdA));
		assertFalse(APPS.isInit(nodeIdB));
	}
}