/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.order;

import com.hedera.services.legacy.core.jproto.JKey;
import java.util.List;

/**
 * Define a type that knows how to create {@link SigningOrderResult} instances for each possible
 * outcome of an attempt to list, in canonical order, the Hedera keys that must have active
 * signatures for some gRPC transaction to be valid.
 *
 * <p><b>NOTE:</b> Implementations of this factory may or may not be injected with additional
 * context about the gRPC transaction being evaluated. This will depend on the level of detail
 * required by the type of error report.
 *
 * @param <T> the type of error report this factory produces.
 * @see SigRequirements
 */
public interface SigningOrderResultFactory<T> {
    /**
     * Wrap the (successful) determination of a signing order in a {@link SigningOrderResult}.
     *
     * @param keys a known signing order.
     * @return the wrapper object.
     */
    SigningOrderResult<T> forValidOrder(List<JKey> keys);

    /**
     * Report a non-specific error that occurred when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forGeneralError();

    /**
     * Report an invalid account encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forInvalidAccount();

    /**
     * Report an invalid smart contract encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forInvalidContract();

    /**
     * Report a smart contract with no admin key that was encountered when listing signing keys for
     * some txn. (The current semantics of {@link SigRequirements} mean it is never valid to
     * reference such smart contracts in a transaction.)
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forImmutableContract();

    /**
     * Report a missing file encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forMissingFile();

    /**
     * Report a missing account encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forMissingAccount();

    /**
     * Report a missing token encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forMissingToken();

    /**
     * Report a missing schedule encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forMissingSchedule();

    /**
     * Report a non-specific payer error that occurred when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forGeneralPayerError();

    /**
     * Report a missing topic occurring during listing signing keys for a txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forMissingTopic();

    /**
     * Report a missing auto renew account encountered when listing signing keys for some txn.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forInvalidAutoRenewAccount();

    /**
     * Report a failure resolving required signers for a scheduled transaction.
     *
     * @return the error summary.
     */
    SigningOrderResult<T> forUnresolvableRequiredSigners();

    /**
     * Report an invalid attempt to schedule a ScheduleCreate transaction.
     *
     * @return the error summary
     */
    SigningOrderResult<T> forUnschedulableTxn();

    /**
     * Report an invalid fee collection account in a TokenCreate or TokenUpdate.
     *
     * @return the error summary
     */
    SigningOrderResult<T> forInvalidFeeCollector();

    /**
     * Report an invalid owner provided in a CryptoApproveAllowance
     *
     * @return the error summary
     */
    SigningOrderResult<T> forInvalidAllowanceOwner();

    /**
     * Report an invalid delegating Spender provided in a CryptoApproveAllowance for granting NFT
     * Allowance
     *
     * @return the error summary
     */
    SigningOrderResult<T> forInvalidDelegatingSpender();
}
