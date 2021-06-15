package com.hedera.services.ledger;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ImpliedTransfersTest {
	private CryptoTransferTransactionBody op;

	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private PureTransferSemanticChecks transferSemanticChecks;

	private ImpliedTransfers subject;

	private final Id aModel = new Id(1, 2, 3);
	private final Id bModel = new Id(2, 3, 4);
	private final Id cModel = new Id(3, 4, 5);
	private final Id token = new Id(0, 0, 75231);
	private final Id anotherToken = new Id(0, 0, 75232);
	private final Id yetAnotherToken = new Id(0, 0, 75233);
	private final TokenID anId = asToken("0.0.75231");
	private final TokenID anotherId = asToken("0.0.75232");
	private final TokenID yetAnotherId = asToken("0.0.75233");
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");

	private final long aHbarChange = -100L;
	private final long bHbarChange = +50L;
	private final long cHbarChange = +50L;
	private final long aAnotherTokenChange = -50L;
	private final long bAnotherTokenChange = +25L;
	private final long cAnotherTokenChange = +25L;
	private final long bTokenChange = -100L;
	private final long cTokenChange = +100L;
	private final long aYetAnotherTokenChange = -15L;
	private final long bYetAnotherTokenChange = +15L;

	private final int maxExplicitHbarAdjusts = 5;
	private final int maxExplicitTokenAdjusts = 50;

	@BeforeEach
	void setUp() {
		subject = new ImpliedTransfers(dynamicProperties, transferSemanticChecks);
	}

	@Test
	void validatesXfers() {
		setupFixtureOp();

		given(dynamicProperties.maxTransferListSize()).willReturn(2);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		// and:
		final var expectedMeta = new ImpliedTransfers.Meta(
				2, maxExplicitTokenAdjusts, TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

		// when:
		final var result = subject.parseFromGrpc(op);

		// then:
		assertEquals(result.getRight(), expectedMeta);
	}

	@Test
	void getsExpectedList() {
		setupFixtureOp();
		// and:
		final List<BalanceChange> expectedChanges = List.of(new BalanceChange[] {
						BalanceChange.hbarAdjust(aModel, aHbarChange),
						BalanceChange.hbarAdjust(bModel, bHbarChange),
						BalanceChange.hbarAdjust(cModel, cHbarChange),
						BalanceChange.tokenAdjust(anotherToken, aModel, aAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, bModel, bAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, cModel, cAnotherTokenChange),
						BalanceChange.tokenAdjust(token, bModel, bTokenChange),
						BalanceChange.tokenAdjust(token, cModel, cTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, aModel, aYetAnotherTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, bModel, bYetAnotherTokenChange),
				}
		);
		// and:
		final var expectedMeta = new ImpliedTransfers.Meta(maxExplicitHbarAdjusts, maxExplicitTokenAdjusts, OK);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);

		// when:
		final var result = subject.parseFromGrpc(op);

		// then:
		assertEquals(result.getRight(), expectedMeta);
		assertEquals(result.getLeft(), expectedChanges);
	}

	@Test
	void metaObjectContractSanityChecks() {
		// given:
		final var oneMeta = new ImpliedTransfers.Meta(3, 4, OK);
		final var twoMeta = new ImpliedTransfers.Meta(1, 2, TOKEN_WAS_DELETED);
		// and:
		final var oneRepr = "Meta{code=OK, maxExplicitHbarAdjusts=3, maxExplicitTokenAdjusts=4}";
		final var twoRepr = "Meta{code=TOKEN_WAS_DELETED, maxExplicitHbarAdjusts=1, maxExplicitTokenAdjusts=2}";

		// expect:
		assertNotEquals(oneMeta, twoMeta);
		assertNotEquals(oneMeta.hashCode(), twoMeta.hashCode());
		// and:
		assertEquals(oneRepr, oneMeta.toString());
		assertEquals(twoRepr, twoMeta.toString());
	}

	private void setupFixtureOp() {
		var hbarAdjusts = TransferList.newBuilder()
				.addAccountAmounts(adjustFrom(a, -100))
				.addAccountAmounts(adjustFrom(b, 50))
				.addAccountAmounts(adjustFrom(c, 50))
				.build();
		op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(hbarAdjusts)
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anId)
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(yetAnotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();
	}
}