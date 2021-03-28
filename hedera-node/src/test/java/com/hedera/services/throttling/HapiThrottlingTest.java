package com.hedera.services.throttling;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HapiThrottlingTest {
	@Mock
	TimedFunctionalityThrottling delegate;

	HapiThrottling subject;

	@BeforeEach
	void setUp() {
		subject = new HapiThrottling(delegate);
	}

	@Test
	void delegatesWithSomeInstant() {
		given(delegate.shouldThrottle(any(), any())).willReturn(true);

		// when:
		var ans = subject.shouldThrottle(CryptoTransfer);

		// then:
		assertTrue(ans);
		verify(delegate).shouldThrottle(eq(CryptoTransfer), any());
	}

	@Test
	void unsupportedMethodsThrow() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.activeThrottlesFor(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.allActiveThrottles());
	}

	@Test
	void delegatesRebuild() {
		// setup:
		ThrottleDefinitions defs = new ThrottleDefinitions();

		// when:
		subject.rebuildFor(defs);

		// then:
		verify(delegate).rebuildFor(defs);
	}

	@Test
	public void hmm() throws InvalidProtocolBufferException, TextFormat.InvalidEscapeSequenceException {
		var fl = "\\n\\212\\001\\n\\020\\n\\t\\b\\246\\312\\376\\202\\006\\020\\342A\\022\\003\\030\\351\\a\\022\\002" +
				"\\030\\003\\030\\365\\312\\264\\002\\\"\\002\\bx2 " +
				"\\303\\203\\302\\256\\303\\202\\302\\267\\303\\203\\302\\271tF8\\303\\202\\302\\256J\\303\\203\\302" +
				"\\213\\303\\203\\302\\220\\303\\203\\302\\216\\322\\002F\\nD\\b\\215\\204\\002\\022 " +
				"\\303\\203\\302\\256\\303\\202\\302\\267\\303\\203\\302\\271tF8\\303\\202\\302\\256J\\303\\203\\302" +
				"\\213\\303\\203\\302\\220\\303\\203\\302\\216J\\034\\n\\032\\n\\a\\n\\003\\030\\317\\f\\020\\001\\n\\a" +
				"\\n\\003\\030\\320\\f\\020\\001\\n\\006\\n\\002\\0307\\020\\004\\022G\\nE\\n\\001\\n\\032@\\033\\226" +
				"\\373\\002\\334 \\265\\357\\224:F[\\206Qq\\361\\310\\0241oJN\\031\\024\\350sC\\255\\322\\337a\\371W" +
				"\\030[p\\317\\022B1\\273!\\006D\\264J5\\314\\331\\272t\\365\\244\\036D\\355^\\202\\\"\\311\\'b\\236\\t";
		ByteString bs = TextFormat.unescapeBytes(fl);
		Transaction t = Transaction
				.newBuilder()
				.setSignedTransactionBytes(bs)
				.build();
		System.out.println(CommonUtils.extractTransactionBody(t));
	}
}