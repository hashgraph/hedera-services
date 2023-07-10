/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class AssessmentResult {
    private Map<TokenID, Map<AccountID, Long>> htsAdjustments;
    // two maps to aggregate all custom fee balance changes. These two maps are used
    // to construct a transaction body that needs to be assessed again for custom fees
    private Map<AccountID, Long> hbarAdjustments;
    // Any debits in this set should not trigger custom fee charging again
    private Set<TokenID> exemptDebits;
    private Set<Pair<AccountID, TokenID>> royaltiesPaid;
    private Map<TokenID, Map<AccountID, Long>> inputTokenAdjustments;

    private Map<AccountID, Long> inputHbarAdjustments;
    /* And for each "assessable change" that can be charged a custom fee, delegate to our
    fee assessor to update the balance changes with the custom fee. */
    private List<AssessedCustomFee> assessedCustomFees;

    public AssessmentResult(
            final List<TokenTransferList> inputTokenTransfers, final List<AccountAmount> inputHbarTransfers) {
        htsAdjustments = new HashMap<>();
        hbarAdjustments = new HashMap<>();
        exemptDebits = new HashSet<>();
        royaltiesPaid = new HashSet<>();
        assessedCustomFees = new ArrayList<>();
        inputTokenAdjustments = buildTokenTransferMap(inputTokenTransfers);
        inputHbarAdjustments = buildHbarTransferMap(inputHbarTransfers);
    }

    public Map<TokenID, Map<AccountID, Long>> getInputTokenAdjustments() {
        return inputTokenAdjustments;
    }

    public void setInputTokenAdjustments(final Map<TokenID, Map<AccountID, Long>> inputTokenAdjustments) {
        this.inputTokenAdjustments = inputTokenAdjustments;
    }

    public Map<AccountID, Long> getHbarAdjustments() {
        return hbarAdjustments;
    }

    public void setHbarAdjustments(final Map<AccountID, Long> hbarAdjustments) {
        this.hbarAdjustments = hbarAdjustments;
    }

    public Map<TokenID, Map<AccountID, Long>> getHtsAdjustments() {
        return htsAdjustments;
    }

    public void setHtsAdjustments(final Map<TokenID, Map<AccountID, Long>> htsAdjustments) {
        this.htsAdjustments = htsAdjustments;
    }

    public Set<TokenID> getExemptDebits() {
        return exemptDebits;
    }

    public void setExemptDebits(final Set<TokenID> exemptDebits) {
        this.exemptDebits = exemptDebits;
    }

    public List<AssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
    }

    public void addAssessedCustomFee(final AssessedCustomFee assessedCustomFee) {
        assessedCustomFees.add(assessedCustomFee);
    }

    public void setAssessedCustomFees(final List<AssessedCustomFee> assessedCustomFees) {
        this.assessedCustomFees = assessedCustomFees;
    }

    public Set<Pair<AccountID, TokenID>> getRoyaltiesPaid() {
        return royaltiesPaid;
    }

    public void setRoyaltiesPaid(final Set<Pair<AccountID, TokenID>> royaltiesPaid) {
        this.royaltiesPaid = royaltiesPaid;
    }

    public void addToRoyaltiesPaid(final Pair<AccountID, TokenID> paid) {
        royaltiesPaid.add(paid);
    }

    public Map<AccountID, Long> getInputHbarAdjustments() {
        return inputHbarAdjustments;
    }

    public void addToInputHbarAdjustments(final AccountID id, final Long amount) {
        inputHbarAdjustments.put(id, amount);
    }

    private Map<TokenID, Map<AccountID, Long>> buildTokenTransferMap(final List<TokenTransferList> tokenTransfers) {
        final var fungibleTransfersMap = new HashMap<TokenID, Map<AccountID, Long>>();
        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var fungibleTokenTransfers = xfer.transfersOrElse(emptyList());
            final var tokenTransferMap = new HashMap<AccountID, Long>();
            for (final var aa : fungibleTokenTransfers) {
                tokenTransferMap.put(aa.accountID(), aa.amount());
            }
            fungibleTransfersMap.put(tokenId, tokenTransferMap);
        }
        return fungibleTransfersMap;
    }

    private Map<AccountID, Long> buildHbarTransferMap(@NonNull final List<AccountAmount> hbarTransfers) {
        for (final var aa : hbarTransfers) {
            hbarAdjustments.put(aa.accountID(), aa.amount());
        }
        return hbarAdjustments;
    }

    public boolean haveAssessedChanges() {
        return !assessedCustomFees.isEmpty() || !hbarAdjustments.isEmpty() || !htsAdjustments.isEmpty();
    }
}
