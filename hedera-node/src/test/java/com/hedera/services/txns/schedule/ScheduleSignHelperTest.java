package com.hedera.services.txns.schedule;

import com.google.protobuf.ByteString;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static com.hedera.services.txns.schedule.ScheduleSignHelper.signingOutcome;
import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.pretendKeyStartingWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ScheduleSignHelperTest {

	static String aPrefix = "a", abPrefix = "ab", cPrefix = "c";
	static SignaturePair aPair = SignaturePair.newBuilder()
			.setPubKeyPrefix(ByteString.copyFromUtf8(aPrefix))
			.build();
	static SignatureMap sigMap = SignatureMap.newBuilder().addSigPair(aPair).build();

	static JKey a = new JEd25519Key(pretendKeyStartingWith(aPrefix));
	static JKey both = new JEd25519Key(pretendKeyStartingWith(abPrefix));
	static JKey neither = new JEd25519Key(pretendKeyStartingWith(cPrefix));

	@Mock
	Function<byte[], TransactionSignature> sigsFn;

	@Mock
	ScheduleStore store;

	@Mock
	InHandleActivationHelper activationHelper;

	@Test
	@Disabled
	// WIP
	void shouldSignAsExpected() throws DecoderException {
		final ScheduleID schedule = IdUtils.asSchedule("1.2.3");
		given(activationHelper.currentSigsFn()).willReturn(sigsFn);

		var result = signingOutcome(List.of(a), sigMap, schedule, store, activationHelper);

		assertEquals(ResponseCodeEnum.SUCCESS, result.getLeft());
		assertTrue(result.getRight());

	}
}
