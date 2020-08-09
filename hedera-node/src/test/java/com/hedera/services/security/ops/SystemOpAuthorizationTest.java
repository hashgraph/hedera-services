package com.hedera.services.security.ops;

import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.security.ops.SystemOpAuthorization.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(JUnitPlatform.class)
class SystemOpAuthorizationTest {
	@Test
	public void haveExpectedStatusRepresentations() {
		// expect:
		assertEquals(OK, UNNECESSARY.asStatus());
		assertEquals(OK, AUTHORIZED.asStatus());
		assertEquals(ENTITY_NOT_ALLOWED_TO_DELETE, IMPERMISSIBLE.asStatus());
		assertEquals(AUTHORIZATION_FAILED, UNAUTHORIZED.asStatus());
	}
}