package com.hedera.services.txns.schedule;

import com.google.protobuf.ByteString;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static com.hedera.services.txns.schedule.ScheduleSignHelper.signingOutcome;
import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.VALID_SIG;
import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.pretendKeyStartingWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleSignHelperTest {

	static String aPrefix = "a";
	static SignaturePair aPair = SignaturePair.newBuilder()
			.setPubKeyPrefix(ByteString.copyFromUtf8(aPrefix))
			.build();
	static SignatureMap sigMap = SignatureMap.newBuilder().addSigPair(aPair).build();
	private TransactionBody scheduledTxn = TransactionBody.getDefaultInstance();

	static JKey a = new JEd25519Key(pretendKeyStartingWith(aPrefix));

	@Mock
	Function<byte[], TransactionSignature> sigsFn;
	@Mock
	ScheduleStore store;
	@Mock
	InHandleActivationHelper activationHelper;
	@Mock
	MerkleSchedule schedule;

	@Test
	void shouldSignAsExpected() {
		final ScheduleID id = IdUtils.asSchedule("1.2.3");
		given(activationHelper.currentSigsFn()).willReturn(sigsFn);
		given(activationHelper.areScheduledPartiesActive(any(), any())).willReturn(true);
		given(sigsFn.apply(a.getEd25519())).willReturn(VALID_SIG);
		given(store.get(id)).willReturn(schedule);
		given(schedule.ordinaryViewOfScheduledTxn()).willReturn(scheduledTxn);


		var result = signingOutcome(List.of(a), sigMap, id, store, activationHelper);

		assertEquals(ResponseCodeEnum.OK, result.getLeft());
		assertTrue(result.getRight());

	}
}
