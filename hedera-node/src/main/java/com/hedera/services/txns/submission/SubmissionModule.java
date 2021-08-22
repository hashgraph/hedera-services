package com.hedera.services.txns.submission;

import com.hedera.services.txns.submission.annotations.MaxProtoMsgDepth;
import com.hedera.services.txns.submission.annotations.MaxSignedTxnSize;
import com.swirlds.common.Platform;
import dagger.Module;
import dagger.Provides;

import static com.hedera.services.txns.submission.StructuralPrecheck.HISTORICAL_MAX_PROTO_MESSAGE_DEPTH;

@Module
public class SubmissionModule {
	@Provides
	@MaxSignedTxnSize
	public static int provideMaxSignedTxnSize() {
		return Platform.getTransactionMaxBytes();
	}

	@Provides
	@MaxProtoMsgDepth
	public static int provideMaxProtoMsgDepth() {
		return HISTORICAL_MAX_PROTO_MESSAGE_DEPTH;
	}
}
