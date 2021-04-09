package com.hedera.services.context.domain.security;

import com.hedera.services.config.MockAccountNumbers;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class HapiOpPermissionsTest {
	private LogCaptor logCaptor;

	@LoggingSubject
	private HapiOpPermissions subject;

	@BeforeEach
	void setUp() {
		subject = new HapiOpPermissions(new MockAccountNumbers());
	}

	@Test
	void hasExpectedPermissions() {
		// setup:
		var treasury = IdUtils.asAccount("0.0.2");
		var sysadmin = IdUtils.asAccount("0.0.50");
		var luckyCivilian = IdUtils.asAccount("0.0.1234");
		var unluckyCivilian = IdUtils.asAccount("0.0.1235");

		// given:
		var config = configWith(
				Pair.of("uncheckedSubmit", "50"),
				Pair.of("cryptoTransfer", "3-1234"),
				Pair.of("tokenMint", "666-1234")
		);

		// when:
		subject.reloadFrom(config);

		// then:
		Assertions.assertEquals(OK, subject.permissibilityOf(UncheckedSubmit, treasury));
		Assertions.assertEquals(OK, subject.permissibilityOf(UncheckedSubmit, sysadmin));
		Assertions.assertEquals(OK, subject.permissibilityOf(CryptoTransfer, treasury));
		Assertions.assertEquals(OK, subject.permissibilityOf(CryptoTransfer, luckyCivilian));
		Assertions.assertEquals(OK, subject.permissibilityOf(CryptoTransfer, sysadmin));
		Assertions.assertEquals(NOT_SUPPORTED, subject.permissibilityOf(CryptoTransfer, unluckyCivilian));
		Assertions.assertEquals(OK, subject.permissibilityOf(TokenMint, treasury));
		Assertions.assertEquals(OK, subject.permissibilityOf(TokenMint, sysadmin));
		Assertions.assertEquals(OK, subject.permissibilityOf(TokenMint, luckyCivilian));
		Assertions.assertEquals(NOT_SUPPORTED, subject.permissibilityOf(TokenMint, unluckyCivilian));
	}

	@Test
	void reloadsAsExpected() {
		// given:
		var config = configWith(
				Pair.of("doesntExist", "0-*"),
				Pair.of("uncheckedSubmit", "50"),
				Pair.of("cryptoTransfer", "3-1234"),
				Pair.of("tokenMint", "666-*"),
				Pair.of("tokenBurn", "abcde")
		);

		// when:
		subject.reloadFrom(config);
		// and:
		var permissions = subject.getPermissions();

		// then:
		assertRangeProps(permissions.get(UncheckedSubmit), 50L, null);
		assertRangeProps(permissions.get(CryptoTransfer), 3L, 1234L);
		assertRangeProps(permissions.get(TokenMint), 666L, Long.MAX_VALUE);
		// and:
		assertThat(
				logCaptor.warnLogs(),
				contains(
						equalTo(String.format(HapiOpPermissions.MISSING_OP_TPL, "doesntExist")),
						equalTo(String.format(HapiOpPermissions.UNPARSEABLE_RANGE_TPL, TokenBurn, "abcde"))));
	}

	private void assertRangeProps(PermissionedAccountsRange range, Long l, Long r) {
		Assertions.assertEquals(range.from(), l);
		Assertions.assertEquals(range.inclusiveTo(), r);
	}

	@SafeVarargs
	private ServicesConfigurationList configWith(Pair<String, String>... entries) {
		var config = ServicesConfigurationList.newBuilder();
		for (var entry : entries) {
			config.addNameValue(Setting.newBuilder()
					.setName(entry.getLeft())
					.setValue(entry.getRight()));
		}
		return config.build();
	}
}