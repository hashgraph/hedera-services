package com.hedera.services.legacy.services.state;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.TxnFeeChargingPolicy;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.security.ops.SystemOpAuthorization;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.context.domain.trackers.IssEventStatus.NO_KNOWN_ISS;
import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class AwareProcessLogicTest {
	Logger mockLog;
	Transaction platformTxn;
	AddressBook book;
	ServicesContext ctx;
	TransactionContext txnCtx;
	TransactionBody txnBody;
	TransactionBody nonMockTxnBody;
	SmartContractRequestHandler contracts;
	HederaFs hfs;

	AwareProcessLogic subject;

	@BeforeEach
	public void setup() {
		final Transaction txn = mock(Transaction.class);
		final PlatformTxnAccessor txnAccessor = mock(PlatformTxnAccessor.class);
		final HederaLedger ledger = mock(HederaLedger.class);
		final AccountRecordsHistorian historian = mock(AccountRecordsHistorian.class);
		final HederaSigningOrder keyOrder = mock(HederaSigningOrder.class);
		final SigningOrderResult orderResult = mock(SigningOrderResult.class);
		final MiscRunningAvgs runningAvgs = mock(MiscRunningAvgs.class);
		final MiscSpeedometers speedometers = mock(MiscSpeedometers.class);
		final FeeCalculator fees = mock(FeeCalculator.class);
		final TxnIdRecentHistory recentHistory = mock(TxnIdRecentHistory.class);
		final Map<TransactionID, TxnIdRecentHistory> histories = mock(Map.class);
		final BackingStore<AccountID, MerkleAccount> backingAccounts = mock(BackingStore.class);
		final AccountID accountID = mock(AccountID.class);
		final OptionValidator validator = mock(OptionValidator.class);
		final TxnFeeChargingPolicy policy = mock(TxnFeeChargingPolicy.class);
		final SystemOpPolicies policies = mock(SystemOpPolicies.class);
		final TransitionLogicLookup lookup = mock(TransitionLogicLookup.class);
		hfs = mock(HederaFs.class);

		given(histories.get(any())).willReturn(recentHistory);

		txnCtx = mock(TransactionContext.class);
		ctx = mock(ServicesContext.class);
		txnBody = mock(TransactionBody.class);
		contracts = mock(SmartContractRequestHandler.class);
		mockLog = mock(Logger.class);
		nonMockTxnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
								.setAccountID(IdUtils.asAccount("0.0.2"))).build();
		platformTxn = new Transaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder()
				.setBodyBytes(nonMockTxnBody.toByteString())
				.build().toByteArray());

		AwareProcessLogic.log = mockLog;

		var zeroStakeAddress = mock(Address.class);
		given(zeroStakeAddress.getStake()).willReturn(0L);
		var stakedAddress = mock(Address.class);
		given(stakedAddress.getStake()).willReturn(1L);
		book = mock(AddressBook.class);
		given(book.getAddress(1)).willReturn(stakedAddress);
		given(book.getAddress(666L)).willReturn(zeroStakeAddress);
		given(ctx.addressBook()).willReturn(book);
		given(ctx.ledger()).willReturn(ledger);
		given(ctx.txnCtx()).willReturn(txnCtx);
		given(ctx.recordsHistorian()).willReturn(historian);
		given(ctx.backedKeyOrder()).willReturn(keyOrder);
		given(ctx.runningAvgs()).willReturn(runningAvgs);
		given(ctx.speedometers()).willReturn(speedometers);
		given(ctx.fees()).willReturn(fees);
		given(ctx.txnHistories()).willReturn(histories);
		given(ctx.backingAccounts()).willReturn(backingAccounts);
		given(ctx.validator()).willReturn(validator);
		given(ctx.txnChargingPolicy()).willReturn(policy);
		given(ctx.systemOpPolicies()).willReturn(policies);
		given(ctx.transitionLogic()).willReturn(lookup);
		given(ctx.hfs()).willReturn(hfs);
		given(ctx.contracts()).willReturn(contracts);

		given(txnCtx.accessor()).willReturn(txnAccessor);
		given(txnCtx.submittingNodeAccount()).willReturn(accountID);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(txnAccessor.getPlatformTxn()).willReturn(txn);

		given(txn.getSignatures()).willReturn(Collections.emptyList());
		given(keyOrder.keysForPayer(any(), any())).willReturn(orderResult);
		given(keyOrder.keysForOtherParties(any(), any())).willReturn(orderResult);

		final com.hederahashgraph.api.proto.java.Transaction signedTxn = mock(com.hederahashgraph.api.proto.java.Transaction.class);
		final TransactionID txnId = mock(TransactionID.class);

		given(txnAccessor.getSignedTxn()).willReturn(signedTxn);
		given(signedTxn.getSignedTransactionBytes()).willReturn(ByteString.EMPTY);
		given(txnAccessor.getTxn()).willReturn(txnBody);
		given(txnBody.getTransactionID()).willReturn(txnId);
		given(txnBody.getTransactionValidDuration()).willReturn(Duration.getDefaultInstance());

		given(recentHistory.currentDuplicityFor(anyLong())).willReturn(BELIEVED_UNIQUE);
		given(backingAccounts.contains(any())).willReturn(true);

		given(validator.isValidTxnDuration(anyLong())).willReturn(true);
		given(validator.chronologyStatus(any(), any())).willReturn(ResponseCodeEnum.OK);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		given(txnBody.getNodeAccountID()).willReturn(accountID);
		given(policy.apply(any(), any())).willReturn(ResponseCodeEnum.OK);
		given(policies.check(any())).willReturn(SystemOpAuthorization.AUTHORIZED);
		given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());
		given(hfs.exists(any())).willReturn(true);

		subject = new AwareProcessLogic(ctx);
	}

	@AfterEach
	public void cleanup() {
		AwareProcessLogic.log = LogManager.getLogger(AwareProcessLogic.class);
	}

	@Test
	public void shortCircuitsWithWarningOnZeroStakeSubmission() {
		// setup:
		var now = Instant.now();
		var then = now.minusMillis(1L);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(mockLog).warn(argThat((String s) -> s.startsWith("Ignoring a transaction submitted by zero-stake")));
	}

	@Test
	public void shortCircuitsWithErrorOnNonIncreasingConsensusTime() {
		// setup:
		var now = Instant.now();

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(now);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now,1);

		// then:
		verify(mockLog).error(argThat((String s) -> s.startsWith("Catastrophic invariant failure!")));
	}

	@Test
	public void shortCircuitsWithWarningOnZeroStakeSignedTxnSubmission() {
		// setup:
		var now = Instant.now();
		var then = now.minusMillis(1L);
		SignedTransaction signedTxn = SignedTransaction.newBuilder().setBodyBytes(nonMockTxnBody.toByteString()).build();
		Transaction platformSignedTxn = new Transaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder().
				setSignedTransactionBytes(signedTxn.toByteString()).build().toByteArray());

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);

		// when:
		subject.incorporateConsensusTxn(platformSignedTxn, now, 666);

		// then:
		verify(mockLog).warn(argThat((String s) -> s.startsWith("Ignoring a transaction submitted by zero-stake")));
	}
}