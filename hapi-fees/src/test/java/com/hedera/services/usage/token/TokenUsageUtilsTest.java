package com.hedera.services.usage.token;

import com.hedera.services.test.KeyUtils;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.test.KeyUtils.B_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.usage.token.TokenEntitySizes.*;

@RunWith(JUnitPlatform.class)
public class TokenUsageUtilsTest {
	@Test
	public void usesExpectedEstimate() {
		// given:
		var op = TokenCreation.newBuilder().setAdminKey(B_COMPLEX_KEY).build();

		// when:
		var actual = TokenUsageUtils.keySizeIfPresent(op, TokenCreation::hasAdminKey, TokenCreation::getAdminKey);

		// then:
		assertEquals(FeeBuilder.getAccountKeyStorageSize(B_COMPLEX_KEY), actual);
	}
}
