package com.hedera.node.app.spi.workflows;

import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

/**
 * An abstract class for all free queries (no costs, not possible to requests costs)
 */
public abstract class FreeQueryHandler implements QueryHandler {
    
    @Override
    public boolean requiresNodePayment(@NonNull final ResponseType responseType) {
        requireNonNull(responseType);
        return false;
    }

    @Override
    public boolean needsAnswerOnlyCost(@NonNull final ResponseType responseType) {
        requireNonNull(responseType);
        return false;
    }
}
