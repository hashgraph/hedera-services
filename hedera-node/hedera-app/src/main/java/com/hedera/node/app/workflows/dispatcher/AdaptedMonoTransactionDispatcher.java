package com.hedera.node.app.workflows.dispatcher;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.util.Objects.requireNonNull;

@Singleton
public class AdaptedMonoTransactionDispatcher extends TransactionDispatcher {
    private final TransactionContext txnCtx;
    private final UsageLimits usageLimits;

    @Inject
    public AdaptedMonoTransactionDispatcher(
            @NonNull HandleContext handleContext,
            @NonNull TransactionContext txnCtx,
            @NonNull TransactionHandlers handlers,
            @NonNull HederaAccountNumbers accountNumbers,
            @NonNull GlobalDynamicProperties dynamicProperties,
            @NonNull UsageLimits usageLimits) {
        super(handleContext, handlers, accountNumbers, dynamicProperties);
        this.txnCtx = requireNonNull(txnCtx);
        this.usageLimits = requireNonNull(usageLimits);
    }

    @Override
    protected void finishConsensusCreateTopic(
            @NonNull final ConsensusCreateTopicRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        // Adapt the record builder outcome for mono-service
        txnCtx.setCreated(PbjConverter.fromPbj(
                TopicID.newBuilder().topicNum(recordBuilder.getCreatedTopic()).build()));
        // Adapt the metric impact for mono-service
        usageLimits.refreshTopics();
        topicStore.commit();
    }

    @Override
    protected void finishConsensusUpdateTopic(@NonNull WritableTopicStore topicStore) {
        topicStore.commit();
    }

    @Override
    protected void finishConsensusDeleteTopic(@NonNull WritableTopicStore topicStore) {
        topicStore.commit();
    }

    @Override
    protected void finishConsensusSubmitMessage(
            @NonNull final ConsensusSubmitMessageRecordBuilder recordBuilder,
            @NonNull final WritableTopicStore topicStore) {
        // Adapt the record builder outcome for mono-service
        txnCtx.setTopicRunningHash(
                recordBuilder.getNewTopicRunningHash(),
                recordBuilder.getNewTopicSequenceNumber());
        topicStore.commit();
    }
}