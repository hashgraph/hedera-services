package com.hedera.services.pricing;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

public class CryptoFeeSchedulesTest extends FeeSchedulesTestHelper {
	@Test
	void computesExpectedPriceForCryptoTransferSubyptes() throws IOException {
		testExpectedPriceFor(CryptoTransfer, DEFAULT);
		testExpectedPriceFor(CryptoTransfer, TOKEN_FUNGIBLE_COMMON);
		testExpectedPriceFor(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES);
		testExpectedPriceFor(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE);
		testExpectedPriceFor(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES);
	}
}
