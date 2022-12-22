package com.hedera.node.app.service.token.impl.test;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.impl.CryptoServiceImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CryptoServiceImplTest {

	@Test
	void testSpi() {
		// when
		final CryptoService service = CryptoService.getInstance();

		// then
		Assertions.assertNotNull(service, "We must always receive an instance");
		Assertions.assertEquals(
				CryptoServiceImpl.class,
				service.getClass(),
				"We must always receive an instance of type " + CryptoServiceImpl.class.getName());
	}
}
