package com.hedera.services.state.submerkle;

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

@ExtendWith(MockitoExtension.class)
class ExpirableTxnRecordBuilderTest {
	@Mock
	private TxnReceipt.Builder receiptBuilder;

	private ExpirableTxnRecord.Builder subject;

	@BeforeEach
	void setUp() {
		subject = ExpirableTxnRecord.newBuilder();
	}

	@Test
	void clearsAllSideEffects() {
		subject.setTokens(List.of(MISSING_ENTITY_ID));
		subject.setTransferList(new CurrencyAdjustments(new long[] { 1 }, List.of(MISSING_ENTITY_ID)));
		subject.setReceiptBuilder(receiptBuilder);
		subject.setTokenAdjustments(List.of(new CurrencyAdjustments(new long[] { 1 }, List.of(MISSING_ENTITY_ID))));
		subject.setContractCallResult(new SolidityFnResult());
		subject.setNftTokenAdjustments(List.of(new NftAdjustments()));
		subject.setContractCreateResult(new SolidityFnResult());
		subject.setNewTokenAssociations(List.of(new FcTokenAssociation(1, 2)));
		subject.setCustomFeesCharged(List.of(new FcAssessedCustomFee(MISSING_ENTITY_ID, 1, new long[] { 1L })));

		subject.clear();

//		verify(receiptBuilder).
	}
}