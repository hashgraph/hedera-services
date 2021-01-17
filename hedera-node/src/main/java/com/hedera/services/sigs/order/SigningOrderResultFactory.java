package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.jproto.JKey;

import java.util.List;

/**
 * Define a type that knows how to create {@link SigningOrderResult} instances for
 * each possible outcome of an attempt to list, in canonical order, the Hedera keys
 * that must have active signatures for some gRPC transaction to be valid.
 *
 * <b>NOTE:</b> Implementations of this factory may or may not be injected with
 * additional context about the gRPC transaction being evaluated. This will depend
 * on the level of detail required by the type of error report.
 *
 * @param <T> the type of error report this factory produces.
 * @author Michael Tinker
 * @see HederaSigningOrder
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
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forGeneralError(TransactionID txnId);

	/**
	 * Report an invalid account encountered when listing signing keys for some txn.
	 *
	 * @param account the invalid account.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forInvalidAccount(AccountID account, TransactionID txnId);

	/**
	 * Report an invalid smart contract encountered when listing signing keys for some txn.
	 *
	 * @param contract the invalid account.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forInvalidContract(ContractID contract, TransactionID txnId);

	/**
	 * Report a smart contract with no admin key that was encountered when listing signing
	 * keys for some txn. (The current semantics of {@link HederaSigningOrder} mean it is
	 * never valid to reference such smart contracts in a transaction.)
	 *
	 * @param contract the invalid contract.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forImmutableContract(ContractID contract, TransactionID txnId);

	/**
	 * Report a missing file encountered when listing signing keys for some txn.
	 *
	 * @param file the missing file.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forMissingFile(FileID file, TransactionID txnId);

	/**
	 * Report a missing account encountered when listing signing keys for some txn.
	 *
	 * @param account the missing account.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forMissingAccount(AccountID account, TransactionID txnId);

	/**
	 * Report a missing token encountered when listing signing keys for some txn.
	 *
	 * @param id the missing token.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forMissingToken(TokenID id, TransactionID txnId);

	/**
	 * Report a missing schedule encountered when listing signing keys for some txn.
	 *
	 * @param id the missing schedule.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forMissingSchedule(ScheduleID id, TransactionID txnId);

	/**
	 * Report a non-specific payer error that occurred when listing signing keys for some txn.
	 *
	 * @param payer the problematic payer.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forGeneralPayerError(AccountID payer, TransactionID txnId);

	/**
	 * Report a missing topic occurring during listing signing keys for a txn.
	 *
	 * @param topic the missing topic
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forMissingTopic(TopicID topic, TransactionID txnId);

	/**
	 * Report a missing auto renew account encountered when listing signing keys for some txn.
	 *
	 * @param account the missing account.
	 * @param txnId the {@link TransactionID} of the problematic txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forMissingAutoRenewAccount(AccountID account, TransactionID txnId);

	/**
	 * Report a failure resolving required signers for a scheduled transaction.
	 *
	 * @param scheduled the transaction that was attempted to be scheduled.
	 * @param txnId the {@link TransactionID} of the problematic {@code ScheduleCreate} or {@code ScheduleSign} txn.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forUnresolvableRequiredSigners(
			TransactionBody scheduled,
			TransactionID txnId,
			T errorReport);

	/**
	 * Report a failure parsing bytes that were to represent a scheduled txn.
	 *
	 * @param txnId the {@link TransactionID} of the problematic {@code ScheduleCreate}.
	 * @return the error summary.
	 */
	SigningOrderResult<T> forUnparseableScheduledTxn(TransactionID txnId);
}
