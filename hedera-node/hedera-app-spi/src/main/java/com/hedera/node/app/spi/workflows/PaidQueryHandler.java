package com.hedera.node.app.spi.workflows;

import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.NonNull;

import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_STATE_PROOF;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static java.util.Objects.requireNonNull;

/**
 * An abstract class for all queries that are not free. If payment is required depends on the {@link ResponseType}
 */
public abstract class PaidQueryHandler implements QueryHandler {

    @Override
    public boolean requiresNodePayment(@NonNull final ResponseType responseType) {
        requireNonNull(responseType);
        return responseType == ANSWER_ONLY || responseType == ANSWER_STATE_PROOF;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull final ResponseType responseType) {
        requireNonNull(responseType);
        return COST_ANSWER == responseType;
    }

}
