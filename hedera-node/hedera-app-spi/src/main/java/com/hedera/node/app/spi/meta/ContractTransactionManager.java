package com.hedera.node.app.spi.meta;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.common.Int256Value;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.function.UnaryOperator;

/**
 * Provides distributed transaction management for smart contract execution.
 *
 * <p>That is, allows the {@code ContractService} to make atomic changes within
 * an EVM message frame across multiple parts of system state, even though the
 * {@code ContractService} cannot directly mutate most of that state itself.
 *
 * <p>These parts of system state include:
 * <ol>
 *  <li>Contract storage and bytecode.</li>
 *  <li>Accounts and contracts (balances, expiration and deletion metadata, keys, and so on).</li>
 *  <li>Tokens, token balances, and token associations.</li>
 *  <li>Records.</li>
 *  <li>Entity ids.</li>
 * </ol>
 *
 * <p>Only the first part is under direct control of the {@code ContractService}.
 */
public interface ContractTransactionManager {
    /**
     * Returns a new {@link Session} that is a child of the current {@link Session}. Changes
     * made in the child will not be visible to the parent until the child calls {@link Session#commit()}.
     *
     * @return a new {@link Session}
     */
    Session begin();

    /**
     * The unit of atomicity for all changes the {@code ContractService} can make, either directly
     * via its {@link WritableStates} or indirectly by {@link Dispatch} methods.
     *
     * <p>Each new {@link Session} is a child of the previous {@link Session}, and the parent
     * {@link Session} absorbs all changes made when a child calls {@link Session#commit()}.
     */
    interface Session {
        /**
         * Returns the {@link WritableStates} the {@code ContractService} can use to update
         * its own state within this {@link Session}.
         *
         * @return the contract state reflecting all changes made up to this {@link Session}
         */
        WritableStates contractState();

        /**
         * Returns a {@link Dispatch} that reflects all changes made up to, and within, this
         * {@link Session}. If a dispatch has side effects on state such as records or entity ids,
         * they remain limited to the scope of this session until the session is committed.
         *
         * @return a dispatch reflecting all changes made up to, and within, this {@link Session}
         */
        Dispatch dispatch();

        /**
         * Returns the {@link Fees} that reflect all changes up to and including this {@link Session}.
         *
         * @return the fees reflecting all changes made up to this {@link Session}
         */
        Fees fees();

        /**
         * Commits all changes made within this {@link Session} to the parent {@link Session}. For
         * everything except records, these changes will only affect state if every ancestor up to
         * and including the root {@link Session} is also committed. Records are a bit different,
         * as even if the root {@link Session} reverts, any records created within this
         * {@link Session} will still appear in state; but those with status {@code SUCCESS} will
         * have with their stateful effects cleared from the record and their status replaced with
         * {@code REVERTED_SUCCESS}.
         */
        void commit();

        /**
         * Reverts all changes made within this {@link Session} and any committed child {@link Session}s
         * to the parent {@link Session}, with the possible exception of records, as described above.
         */
        void revert();
    }

    interface NonCryptographicSignatureVerification {
        enum Decision { VALID, INVALID, DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION }

        enum KeyRole { TOKEN_ADMIN, TOKEN_TREASURY, TOKEN_AUTO_RENEW_ACCOUNT, TOKEN_FEE_COLLECTOR, OTHER }

        Decision maybeVerifySignature(
                @NonNull Key key,
                @NonNull KeyRole keyRole,
                @NonNull UnaryOperator<TransactionBody> dispatchTransformation);
    }

    interface Dispatch {
        @Nullable
        Long getEntityNumberOf(@NonNull Bytes evmAddress);
        @Nullable
        Account getAccount(long number);
        @Nullable
        Token getToken(long number);
        // Other read-only methods with no side-effects...

        Token getTokenAndExternalizeAsCallResult(long number, long callingContractNumber);
        // Other logical read methods with side effects limited to record creation

        void createHollowAccount(@NonNull Bytes evmAddress);
        void finalizeHollowAccountAsContract(@NonNull Bytes evmAddress);
        void transferValue(long amount, long fromContractNumber, long toEntityNumber);
        void setAlias(@NonNull Bytes evmAddress, long entityNumber);
        void clearAlias(@NonNull Bytes evmAddress);
        void updateStorageMetadata(long contractNumber, @Nullable Int256Value firstKey, int slotsUsed);
        void chargeStorageRent(long contractNumber, long amount, boolean itemizeStoragePayments);
        // Other indirect mutations that either have no corresponding TransactionBody, or would
        // be too awkward to implement as a TransactionBody dispatch; e.g., because they should
        // not have signing requirements enforced or records created

        ResponseCodeEnum dispatch(
                @NonNull TransactionBody syntheticTransaction,
                @NonNull NonCryptographicSignatureVerification nonCryptographicSignatureVerification);
    }

    interface Fees {
        long gasPrice();
        long lazyCreationCostInGas();
        long transactionFeeInGas(@NonNull TransactionBody syntheticTransaction);
    }
}
