/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.tss.handlers;

import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TssCryptographyManager {

    public static final long THRESHOLD_DENOMINATOR_1_2 = 2L;
    private Bytes activeRosterHash;
    private Bytes candidateRosterHash;
    private boolean createNewLedgerId = false;
    private Map<Bytes, List<TssMessageTransactionBody>> tssMessages = new HashMap<>();
    private Map<Bytes, List<TssVoteTransactionBody>> tssVotes = new HashMap<>();
    private Set<Bytes> votingClosed = new HashSet<>();
    private final Executor cryptographyManagerExecutor;

    @Inject
    public TssCryptographyManager(@NonNull final Executor cryptographyManagerExecutor) {
        this.cryptographyManagerExecutor = cryptographyManagerExecutor;
    }

    public CompletableFuture<Void> onTssVoteTransaction(
            @NonNull final TssVoteTransactionBody tssVoteTransactionBody, @NonNull final HandleContext context) {
        return CompletableFuture.runAsync(
                () -> {
                    // 1. If voting is closed for the target roster or the vote is a second vote from the originating
                    // node, do nothing.
                    // 2. Add the TssVoteTransaction to the list for the target roster.
                    // 3. If the voting threshold is met by at least 1/2 consensus weight voting yes
                    //  i. add the target roster hash to the votingClosed` set.
                    //   ii. Non-Dynamic Address Book Semantics
                    //      a. if keyActiveRoster is false, do nothing here, rely on the startup logic to rotate the
                    // candidate roster to the active roster.
                    //      b. if keyActiveRoster is true, adopt the ledger id in the state.
                    //          Since this computation is not on the transaction handling thread, adoption of the ledger
                    // id must be scheduled for the TssStateManager to handle on the next round.

                    // 1. If voting is closed for the target roster or the vote is a second vote from the originating
                    // node, do nothing.
                    if (votingClosed.contains(tssVoteTransactionBody.targetRosterHash())
                            || tssVotes.get(tssVoteTransactionBody.targetRosterHash())
                                    .contains(tssVoteTransactionBody)) {
                        return;
                    }

                    // 2. Add the TssVoteTransaction to the list for the target roster.
                    tssVotes.get(tssVoteTransactionBody.targetRosterHash()).add(tssVoteTransactionBody);

                    // 3. If the voting threshold is met by at least 1/2 consensus weight voting yes
                    //  i. add the target roster hash to the votingClosed` set.
                    //   ii. Non-Dynamic Address Book Semantics
                    //      a. if keyActiveRoster is false, do nothing here, rely on the startup logic to rotate the
                    // candidate roster to the active roster.
                    //      b. if keyActiveRoster is true, adopt the ledger id in the state.
                    //          Since this computation is not on the transaction handling thread, adoption of the ledger
                    // id must be scheduled for the TssStateManager to handle on the next round.
                    if (TssVoteHandler.hasReachedThreshold(
                            tssVoteTransactionBody, context, THRESHOLD_DENOMINATOR_1_2)) {
                        votingClosed.add(tssVoteTransactionBody.targetRosterHash());

                        final var tssConfig = context.configuration().getConfigData(TssConfig.class);
                        if (tssConfig.keyActiveRoster()) {
                            // Signal TssStateMangager to adopt the ledger id in the state on the next round.
                        } else {
                            // Do nothing here, rely on the startup logic to rotate the candidate roster to the active
                            // roster.
                        }
                    }
                },
                cryptographyManagerExecutor);
    }

    public Set<Bytes> getVotingClosed() {
        return votingClosed;
    }

    public Map<Bytes, List<TssMessageTransactionBody>> getTssMessages() {
        return tssMessages;
    }

    public Map<Bytes, List<TssVoteTransactionBody>> getTssVotes() {
        return tssVotes;
    }
}
