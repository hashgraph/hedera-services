// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.hints.handlers;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Bundles the handlers for the {@link com.hedera.node.app.hints.HintsService}.
 * @param keyPublicationHandler the handler for key publication
 * @param preprocessingVoteHandler the handler for preprocessing votes
 * @param partialSignatureHandler the handler for partial signatures
 */
public record HintsHandlers(
        @NonNull HintsKeyPublicationHandler keyPublicationHandler,
        @NonNull HintsPreprocessingVoteHandler preprocessingVoteHandler,
        @NonNull HintsPartialSignatureHandler partialSignatureHandler) {
    public HintsHandlers {
        requireNonNull(keyPublicationHandler);
        requireNonNull(preprocessingVoteHandler);
        requireNonNull(partialSignatureHandler);
    }
}
