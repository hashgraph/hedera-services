package com.hedera.services.utils.accessors.custom;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import org.jetbrains.annotations.Nullable;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class SubmitMessageAccessor extends SignedTxnAccessor {
	private final ConsensusSubmitMessageTransactionBody body;

	public SubmitMessageAccessor(
			final byte[] signedTxnWrapperBytes,
			@Nullable final Transaction txn) throws InvalidProtocolBufferException {
		super(signedTxnWrapperBytes, txn);
		body = getTxn().getConsensusSubmitMessage();
		setSubmitUsageMeta();
	}

	@Override
	public boolean supportsPrecheck() {
		return true;
	}

	@Override
	public ResponseCodeEnum doPrecheck() {
		return validateSyntax();
	}

	public ResponseCodeEnum validateSyntax() {
		return OK;
	}

	public ByteString message(){
		return body.getMessage();
	}

	public TopicID topicId(){
		return body.getTopicID();
	}

	public boolean hasChunkInfo(){
		return body.hasChunkInfo();
	}

	public ConsensusMessageChunkInfo chunkInfo(){
		return body.getChunkInfo();
	}

	private void setSubmitUsageMeta() {
		getSpanMapAccessor().setSubmitMessageMeta(this, new SubmitMessageMeta(body.getMessage().size()));
	}
}
