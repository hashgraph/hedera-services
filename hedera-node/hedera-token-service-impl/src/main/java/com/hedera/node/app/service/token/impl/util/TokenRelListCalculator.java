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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.node.app.service.token.impl.util.IdConvenienceUtils.fromAccountNum;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Class that computes the expected results of a token relation operation. That is to say, given a
 * collection of token relations linked together through some combination of {@code prev()} and
 * {@code next()} pointers, this class computes the expected results of modifying these token
 * relationships in some way
 */
public class TokenRelListCalculator {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TokenRelListCalculator.class);

    private final ReadableTokenRelationStore tokenRelStore;

    public TokenRelListCalculator(@NonNull final ReadableTokenRelationStore tokenRelStore) {
        this.tokenRelStore = requireNonNull(tokenRelStore);
    }

    /**
     * Simulates removal of the given token relations from the given account's list of token rel links,
     * by computing the updates to all relevant objects
     *
     * <p>
     * <b>NOTE: This method does NOT alter any state!</b>. It merely takes the inputs and computes the
     * expected outputs of the operation. <b>The returned objects from this method should then be saved
     * to – or, in the case of the token rels to remove, removed from – their corresponding account
     * or token rel store objects</b>
     *
     * <p>
     * <b>Removal Examples</b>
     * <p>
     * Assume that valid account A has head token number 1, and that the following list of token
     * relations ({@code TR}'s) exists for account A:
     * <ol>
     *     <li>TR(Account A, Token 1, prevToken = -1, nextToken = 2)</li>
     *     <li>TR(Account A, Token 2, prevToken = 1, nextToken = 3)</li>
     *     <li>TR(Account A, Token 3, prevToken = 2, nextToken = 4)</li>
     *     <li>TR(Account A, Token 4, prevToken = 3, nextToken = -1)</li>
     * </ol>
     *
     * <p> <i>Case 1: removing a token relation in the middle of the list</i>
     *     Now let's say we want to remove {@code TR(Account A, Token 2)} from the linked list of
     *     token relations. That is to say, {@code TR(Account A, Token 2} is the "target token
     *     relation" to remove. To do so, we need to update {@code TR(Account A, token 1).nextToken()}
     *     to point to {@code Token 3}, and {@code (Account A, token 3).prevToken()} to point to
     *     {@code Token 1}. After this method performs its operation, the resulting list of token
     *     relations will be:
     * <ol>
     *     <li>{@code TR(Account A, Token 1, prevToken = -1, nextToken = 3)}</li>
     *     <li>{@code TR(Account A, Token 3, prevToken = 1, nextToken = 4)}</li>
     *     <li>{@code TR(Account A, Token 4, prevToken = 3, nextToken = -1)}</li>
     * </ol>
     *
     * TR(Account A, Token 2) is now removed from the list, and the prev/next pointers of the remaining
     * token relations are updated accordingly (note that {@code TR(Account A, Token 4)} remains
     * unchanged).
     *
     * <p> <i>Case 2: removing the head token relation</i>
     *     If we remove the token relation {@code TR(Account A, Token 1)} at the head of the account's
     *     token rel list, then the resulting list of token relations will be unchanged except for
     *     {@code TR(Account A, Token 1)} which has been removed from the list, and
     *     {@code TR(Account A, Token 2)}, which will now be the head of the list (i.e. it will have a
     *     {@code prevToken} value of -1)
     *
     * <p> <i>Case 3: removing the last/tail-end token relation</i>
     *     Finally, if we remove the token relation {@code TR(Account A, Token 4)} at the end of the
     *     account's token rel list, then the resulting list of token relations will also be unchanged
     *     except for {@code TR(Account A, Token 5)}, which has been removed from the list, and
     *     {@code TR(Account A, Token 4}), which is now the end of the list (i.e. it has a
     *     {@code nextToken} value of -1)
     *
     * @param account the account to remove the token relations from
     * @param tokenRelsToDelete the token relations to remove
     * @return new objects that represent the updated account and token relations, as well as the token relations to delete.
     * @throws IllegalArgumentException if any of the token relations don't have the same {@link AccountID} as the given account object
     */
    @NonNull
    public TokenRelsRemovalResult removeTokenRels(
            @NonNull final Account account, @NonNull final List<TokenRelation> tokenRelsToDelete) {
        // Precondition: verify all token relation objects have the same account number as the given account object
        if (tokenRelsToDelete.stream()
                .anyMatch(tokenRel -> tokenRel != null && tokenRel.accountNumber() != account.accountNumber())) {
            throw new IllegalArgumentException("All token relations must be for the same account");
        }

        // Data Preprocessing: remove nulls and duplicate token rels
        final var cleanedTokenRelsToDelete = filterNullsAndDuplicates(tokenRelsToDelete);

        final var currentHeadTokenNum = account.headTokenNumber();
        final var accountId = fromAccountNum(account.accountNumber());

        // We'll create this mapping of (tokenId -> tokenRel) to make it easier to check if a token rel is in the list
        // of token rels to delete. It's only for ease of lookup and doesn't affect the algorithm
        final var tokenRelsToDeleteByTokenId = cleanedTokenRelsToDelete.stream()
                .collect(Collectors.toMap(TokenRelation::tokenNumber, tokenRel -> tokenRel));

        // Recreate all the token relations updated prev and next pointers. This includes the token relations that will
        // be deleted, but these will be filtered out later
        final var updatedTokenRels = new HashMap<Long, TokenRelation>();
        for (final TokenRelation currentTokenRelToDelete : cleanedTokenRelsToDelete) {
            // Grab the current, previous, and next token relations <b>with any updates</b> that were made to them in
            // previous iterations
            final var currentTokenRel = requireNonNull(getInPriorityOrder(
                    updatedTokenRels, tokenRelsToDeleteByTokenId, accountId, currentTokenRelToDelete.tokenNumber()));
            final var currentPrevTokenRel = getInPriorityOrder(
                    updatedTokenRels, tokenRelsToDeleteByTokenId, accountId, currentTokenRel.previousToken());
            final var currentNextTokenRel = getInPriorityOrder(
                    updatedTokenRels, tokenRelsToDeleteByTokenId, accountId, currentTokenRel.nextToken());

            // The `updatedTokenRelsSurroundingCurrentTokenRel` var will contain the updated token rels that were
            // updated to point AWAY from the target token rel that we're removing, i.e. we're getting the updates to
            // the token relations to the previous() and next() of the current target token relation, which target token
            // relation we want to remove
            final var updatedTokenRelsSurroundingCurrentTokenRel =
                    updatePointersSurroundingTargetTokenRel(currentPrevTokenRel, currentNextTokenRel);

            final var updatedPrevTokenRel = updatedTokenRelsSurroundingCurrentTokenRel.updatedPrevTokenRel();
            // Note: even though we might delete the token relation represented by `updatedPrevTokenRel` later in this
            // loop, we still need to have an updated token rel object since the algorithm removes one token rel at a
            // time. Otherwise, the resulting pointers would be not always be correct
            if (updatedPrevTokenRel != null)
                updatedTokenRels.put(updatedPrevTokenRel.tokenNumber(), updatedPrevTokenRel);

            final var updatedNextTokenRel = updatedTokenRelsSurroundingCurrentTokenRel.updatedNextTokenRel();
            // Likewise with `updatedNextTokenRel`, we need to update this token rel for now, even if we might delete it
            // later in this loop
            if (updatedNextTokenRel != null)
                updatedTokenRels.put(updatedNextTokenRel.tokenNumber(), updatedNextTokenRel);
        }

        // Now, filter out all the token rels that are in the list of token rels to delete from `updatedTokenRels`:
        final var updatedTokenRelsToKeep = updatedTokenRels.values().stream()
                .filter(tokenRel -> !tokenRelsToDeleteByTokenId.containsKey(tokenRel.tokenNumber()))
                .toList();

        // Calculate the account's new head token number, given the token relations to delete
        final var updatedHeadTokenNum = calculateHeadTokenAfterDeletions(
                currentHeadTokenNum, account, updatedTokenRels, tokenRelsToDeleteByTokenId);

        return new TokenRelsRemovalResult(updatedHeadTokenNum, updatedTokenRelsToKeep);
    }

    @NonNull
    private List<TokenRelation> filterNullsAndDuplicates(final List<TokenRelation> tokenRelsToDelete) {
        // We could use a simple .stream() for this functionality, but don't do so in order to avoid the performance
        // overhead
        final var cleaned = new ArrayList<TokenRelation>(tokenRelsToDelete.size());
        for (final TokenRelation tokenRel : tokenRelsToDelete) {
            if (tokenRel != null && !cleaned.contains(tokenRel)) {
                cleaned.add(tokenRel);
            }
        }
        return cleaned;
    }

    /**
     * Getter method that prioritizes current updates to token relations, and falls back to other
     * sources if no updates for that token relation yet exist
     *
     * @param updatedTokenRels the collection of token relations that have been updated from their
     *                         original state. Passed in as a map of Token ID -> Token Relation for
     *                         convenience in looking up token relations
     * @param tokenRelsToDeleteByTokenId the collection of token relations that are to be deleted.
     *                                   Also passed in as a map of Token ID -> Token Relation
     *                                   for convenience
     * @param accountId the account ID of the account that the token relations belong to (all token
     *                 relation account IDs must match this value)
     * @param tokenNumToLookup the token ID of the token relation to retrieve
     */
    @Nullable
    private TokenRelation getInPriorityOrder(
            @NonNull final Map<Long, TokenRelation> updatedTokenRels,
            @NonNull final Map<Long, TokenRelation> tokenRelsToDeleteByTokenId,
            @NonNull final AccountID accountId,
            final long tokenNumToLookup) {
        // First we check for the token rel (accountId, token ID) in the updated token relations. This way we get the
        // most recent prev/next pointer changes even though these changes haven't been committed to any store
        final var updatedTokenRelsValue = updatedTokenRels.get(tokenNumToLookup);
        if (updatedTokenRelsValue != null) return updatedTokenRelsValue;

        // Next we check for the token rel in our already-loaded collection of token relations to delete
        final var tokensToDeleteRelsValue = tokenRelsToDeleteByTokenId.get(tokenNumToLookup);
        if (tokensToDeleteRelsValue != null) return tokensToDeleteRelsValue;

        // Finally, if we haven't found the token rel already, we resort to the token relation store to retrieve it (if
        // it exists)
        return tokenRelStore.get(
                accountId, TokenID.newBuilder().tokenNum(tokenNumToLookup).build());
    }

    /**
     * This method's only job is to create new token relation objects with updated pointers. Given a
     * target token rel (which, by the way, is NOT passed in as an argument), this computes the previous
     * and next token relation objects with updated pointers that will NOT point to the target token
     * relation to remove
     *
     * @param prevTokenRel the previous token relation pointing forward to the target token relation
     * @param nextTokenRel the next token relation pointing back to the target token relation
     * @return a {@link TokenRelPointerUpdateResult} object containing the token relation objects
     * with updated previous and next pointers
     */
    @NonNull
    private TokenRelPointerUpdateResult updatePointersSurroundingTargetTokenRel(
            @Nullable TokenRelation prevTokenRel, @Nullable final TokenRelation nextTokenRel) {
        final var prevTokenRelTokenNum = prevTokenRel != null ? prevTokenRel.tokenNumber() : -1;
        final var nextTokenRelTokenNum = nextTokenRel != null ? nextTokenRel.tokenNumber() : -1;

        // Create a copy of `prevTokenRel` with `prevTokenRel.nextToken()` now pointing to `nextTokenRel.tokenNumber()`
        // instead of `targetTokenRel.tokenNumber()`. If `prevTokenRel` is null, then no updated token relation will be
        // created, indicating that there is/was no previous token relation to be updated
        final TokenRelation newPrevTokenRel = prevTokenRel != null
                ? prevTokenRel.copyBuilder().nextToken(nextTokenRelTokenNum).build()
                : null;

        // Likewise, create a copy of nextTokenRel that points to `prevTokenRel` instead of `targetTokenRel`. Like
        // `prevTokenRel`, if `nextTokenRel` is null, then `newNextTokenRel` will be passed as null to the {@code Pair}
        // return value
        final TokenRelation newNextTokenRel = nextTokenRel != null
                ? nextTokenRel.copyBuilder().previousToken(prevTokenRelTokenNum).build()
                : null;

        return new TokenRelPointerUpdateResult(newPrevTokenRel, newNextTokenRel);
    }

    /**
     * Given an account's current head token number and a collection of token relations to remove,
     * this method computes the expected new head token number for the account.
     *
     * <p>
     * <b>Note:</b> if the given token rels are in an illegal state, a fallback value of -1 will be returned
     *
     * @param currentHeadTokenNum the account's current head token number, i.e. the head token number that may change
     * @param account the account (object, not ID) that the token is related to
     * @param tokenRelsToDeleteByTokenId a map of token relations to delete, keyed by token ID for convenience of lookup
     * @return the new head token number for the account
     */
    private long calculateHeadTokenAfterDeletions(
            final long currentHeadTokenNum,
            @NonNull final Account account,
            @NonNull final Map<Long, TokenRelation> updatedTokenRels,
            @NonNull final Map<Long, TokenRelation> tokenRelsToDeleteByTokenId) {
        final var accountId = IdConvenienceUtils.fromAccountNum(account.accountNumber());

        // Calculate the new head token number by walking the linked token rels until we find a token rel that is not in
        // the list of token rels to delete
        var currentTokenNum = currentHeadTokenNum;
        // We use a safety counter to prevent infinite loops in case of a bug
        var safetyCounter = 0;
        TokenRelation currentWalkedTokenRel;
        do {
            currentWalkedTokenRel = updatedTokenRels.containsKey(currentTokenNum)
                    ? updatedTokenRels.get(currentTokenNum)
                    : tokenRelStore.get(
                            accountId,
                            TokenID.newBuilder().tokenNum(currentTokenNum).build());
            if (currentWalkedTokenRel != null) {
                if (!tokenRelsToDeleteByTokenId.containsKey(currentWalkedTokenRel.tokenNumber())) {
                    // we found the first existing token rel that is not in the list of token rels to delete
                    break;
                } else {
                    // we found a non-null token rel, but it is in the list of token rels to delete; we therefore
                    // continue walking the linked token rels
                    currentTokenNum = currentWalkedTokenRel.nextToken();
                }
            } else {
                // We reached the end of the linked token rel pointers chain; there is no token rel that will qualify as
                // the new head token number. We therefore set the new head token number to -1 and exit the do-while
                // loop (since `currentWalkedTokenRel` is null)
                currentTokenNum = -1;
            }

            // Default to a null pointer (value of -1) for infinite looping cases
            if (safetyCounter++ > account.numberAssociations()) {
                log.error(
                        "Encountered token rels list that exceeds total token associations for account {}",
                        account.accountNumber());
                return -1;
            }
        } while (currentWalkedTokenRel != null);

        // At this point, `currentTokenNum` is either -1 (if we reached the end of the linked token rel pointers chain),
        // zero if a token rel's previous or next pointer was incorrectly set to zero (e.g. initialized by default to
        // zero and not set), or the token number of the first token rel that will NOT be deleted. In the first two
        // cases, this value is the account's new head token number. Otherwise, return a fallback of number of -1
        return currentTokenNum > 0 ? currentTokenNum : -1;
    }

    /**
     * This record contains the <b>overall</b> (simulated) results of removing token relations from
     * an account's token relations list. <b>If any change to state must be made as a result of token
     * relation removals, these objects must all be saved to their appropriate entities and stores
     * after obtaining this result</b>
     *
     * @param updatedHeadTokenId the new head token ID for the account. Save this as the account
     *                           entity's new head token number
     * @param updatedTokenRelsStillInChain the updated token relations that are still in the account's
     *                                     token relations list. None of the previous and next pointers
     *                                     on these token relations should point to any of the token
     *                                     relations in {@code tokenRelsToDelete}
     */
    public record TokenRelsRemovalResult(
            @Nullable Long updatedHeadTokenId, @NonNull List<TokenRelation> updatedTokenRelsStillInChain) {}

    private record TokenRelPointerUpdateResult(
            @Nullable TokenRelation updatedPrevTokenRel, @Nullable TokenRelation updatedNextTokenRel) {}
}
