package com.hedera.services.ledger;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.hedera.test.utils.TxnUtils.withTokenAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PureTransferSemanticChecksTest {
	final private AccountID a = AccountID.newBuilder().setAccountNum(9_999L).build();
	final private AccountID b = AccountID.newBuilder().setAccountNum(8_999L).build();
	final private AccountID c = AccountID.newBuilder().setAccountNum(7_999L).build();
	final private AccountID d = AccountID.newBuilder().setAccountNum(6_999L).build();
	final private TokenID aTId = TokenID.newBuilder().setTokenNum(1_234L).build();
	final private TokenID bTId = TokenID.newBuilder().setTokenNum(2_345L).build();
	final private TokenID cTId = TokenID.newBuilder().setTokenNum(3_456L).build();
	final private TokenID dTId = TokenID.newBuilder().setTokenNum(4_567L).build();
	final PureTransferSemanticChecks subject = new PureTransferSemanticChecks();

	@Test
	void acceptsReasonableTokenTransfersLength() {
		// given:
		List<TokenTransferList> wrapper = withTokenAdjustments(aTId, a, -1, bTId, b, 2, cTId, c, 3);

		// when:
		final var result = subject.validateTokenTransfers(wrapper, 4);

		// expect:
		assertEquals(OK, result);
	}

	@Test
	void acceptsNoTokenTransfers() {
		// given:
		final var result = subject.validateTokenTransfers(Collections.emptyList(), 10);

		// expect:
		assertEquals(OK, result);
	}

	@Test
	void rejectsExceedingTokenTransfersLength() {
		// given:
		List<TokenTransferList> wrapper = withTokenAdjustments(aTId, a, -1, bTId, b, 2, cTId, c, 3);

		// when:
		final var result = subject.validateTokenTransfers(wrapper, 2);

		// then:
		assertEquals(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED, result);
	}

	@Test
	void rejectsExceedingTokenTransfersAccountAmountsLength() {
		// given:
		List<TokenTransferList> wrapper = withTokenAdjustments(aTId, a, -1, bTId, b, 2, cTId, c, 3, dTId, d, -4);

		// when:
		final var result = subject.validateTokenTransfers(wrapper, 4);

		// then:
		assertEquals(TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED, result);
	}

	@Test
	void rejectsEmptyTokenTransferAmounts() {
		// given:
		List<TokenTransferList> wrapper = List.of(TokenTransferList.newBuilder()
				.setToken(aTId)
				.build());

		// when:
		final var result = subject.validateTokenTransfers(wrapper, 10);

		// then:
		assertEquals(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS, result);
	}
}