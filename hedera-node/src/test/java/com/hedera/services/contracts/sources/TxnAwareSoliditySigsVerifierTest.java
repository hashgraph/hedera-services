package com.hedera.services.contracts.sources;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.ActivationTest;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.merkle.map.MerkleMap;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.hedera.services.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.services.utils.EntityIdUtils.asTypedSolidityAddress;
import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TxnAwareSoliditySigsVerifierTest {
	private static final Address PRETEND_RECIPIENT_ADDR = Address.ALTBN128_ADD;
	private static final Address PRETEND_CONTRACT_ADDR = Address.ALTBN128_MUL;
	private static final Address PRETEND_SENDER_ADDR = Address.ALTBN128_PAIRING;
	private static final Id tokenId = new Id(0, 0, 666);
	private static final Id accountId = new Id(0, 0, 1234);
	private static final Address PRETEND_TOKEN_ADDR = tokenId.asEvmAddress();
	private static final Address PRETEND_ACCOUNT_ADDR = accountId.asEvmAddress();
	private final AccountID payer = IdUtils.asAccount("0.0.2");
	private final AccountID sigRequired = IdUtils.asAccount("0.0.555");
	private final AccountID smartContract = IdUtils.asAccount("0.0.666");
	private final AccountID noSigRequired = IdUtils.asAccount("0.0.777");

	private JKey expectedKey;

	@Mock
	private MerkleAccount contract;
	@Mock
	private MerkleAccount sigReqAccount;
	@Mock
	private MerkleAccount noSigReqAccount;
	@Mock
	private BiPredicate<JKey, TransactionSignature> cryptoValidity;
	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private ActivationTest activationTest;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private Function<byte[], TransactionSignature> pkToCryptoSigsFn;

	private TxnAwareSoliditySigsVerifier subject;

	@BeforeEach
	private void setup() throws Exception {
		expectedKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKey();

		subject = new TxnAwareSoliditySigsVerifier(activationTest, txnCtx, () -> tokens, () -> accounts,
				cryptoValidity);
	}

	@Test
	void throwsIfAskedToVerifyMissingToken() {
		given(tokens.get(tokenId.asEntityNum())).willReturn(null);

		assertFailsWith(() ->
						subject.hasActiveSupplyKey(
								PRETEND_TOKEN_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR),
				INVALID_TOKEN_ID);
	}

	@Test
	void throwsIfAskedToVerifyTokenWithoutSupplyKey() {
		final var token = mock(MerkleToken.class);

		given(tokens.get(tokenId.asEntityNum())).willReturn(token);
		given(token.hasSupplyKey()).willReturn(false);

		assertFailsWith(() ->
						subject.hasActiveSupplyKey(
								PRETEND_TOKEN_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR),
				TOKEN_HAS_NO_SUPPLY_KEY);
	}

	@Test
	void testsSupplyKeyIfPresent() {
		given(txnCtx.accessor()).willReturn(accessor);
		final var token = mock(MerkleToken.class);
		given(tokens.get(tokenId.asEntityNum())).willReturn(token);
		given(token.hasSupplyKey()).willReturn(true);
		given(token.getSupplyKey()).willReturn(expectedKey);
		given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
		given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

		final var verdict = subject.hasActiveSupplyKey(PRETEND_TOKEN_ADDR, PRETEND_RECIPIENT_ADDR,
				PRETEND_CONTRACT_ADDR,
				PRETEND_SENDER_ADDR);

		assertTrue(verdict);
	}

	@Test
	void throwsIfAskedToVerifyMissingAccount() {
		given(accounts.get(accountId.asEntityNum())).willReturn(null);

		assertFailsWith(() ->
				subject.hasActiveKey(
						PRETEND_ACCOUNT_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR),
				INVALID_ACCOUNT_ID);
	}

	@Test
	void testsAccountKeyIfPresent() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(accounts.get(accountId.asEntityNum())).willReturn(sigReqAccount);
		given(sigReqAccount.getAccountKey()).willReturn(expectedKey);
		given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);
		given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

		final var verdict = subject.hasActiveKey(PRETEND_ACCOUNT_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR,
				PRETEND_SENDER_ADDR);

		assertTrue(verdict);
	}

	@Test
	void filtersContracts() {
		given(txnCtx.activePayer()).willReturn(payer);
		given(contract.isSmartContract()).willReturn(true);
		given(accounts.get(EntityNum.fromAccountId(smartContract))).willReturn(contract);

		final var contractFlag = subject.hasActiveKeyOrNoReceiverSigReq(
				asTypedSolidityAddress(smartContract), PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR,
				PRETEND_SENDER_ADDR);

		assertTrue(contractFlag);
		verify(activationTest, never()).test(any(), any(), any());
	}

	@Test
	void filtersNoSigRequired() {
		given(txnCtx.activePayer()).willReturn(payer);
		given(accounts.get(EntityNum.fromAccountId(noSigRequired))).willReturn(noSigReqAccount);

		final var noSigRequiredFlag = subject.hasActiveKeyOrNoReceiverSigReq(
				asTypedSolidityAddress(noSigRequired), PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR,
				PRETEND_SENDER_ADDR);

		assertTrue(noSigRequiredFlag);
		verify(activationTest, never()).test(any(), any(), any());
	}

	@Test
	void testsWhenReceiverSigIsRequired() {
		givenAccessorInCtx();
		given(sigReqAccount.isReceiverSigRequired()).willReturn(true);
		given(sigReqAccount.getAccountKey()).willReturn(expectedKey);
		given(accounts.get(EntityNum.fromAccountId(sigRequired))).willReturn(sigReqAccount);
		given(accessor.getRationalizedPkToCryptoSigFn()).willReturn(pkToCryptoSigsFn);

		given(activationTest.test(eq(expectedKey), eq(pkToCryptoSigsFn), any())).willReturn(true);

		boolean sigRequiredFlag = subject.hasActiveKeyOrNoReceiverSigReq(
				asTypedSolidityAddress(sigRequired), PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR,
				PRETEND_SENDER_ADDR);

		assertTrue(sigRequiredFlag);
	}

	@Test
	void filtersPayerSinceSigIsGuaranteed() {
		given(txnCtx.activePayer()).willReturn(payer);

		boolean payerFlag = subject.hasActiveKeyOrNoReceiverSigReq(
				asTypedSolidityAddress(payer), PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR);

		assertTrue(payerFlag);

		verify(activationTest, never()).test(any(), any(), any());
	}


	@Test
	void createsValidityTestThatOnlyAcceptsContractIdKeyWhenBothRecipientAndContractAreActive() {
		final var uncontrolledId = EntityIdUtils.contractParsedFromSolidityAddress(Address.BLS12_G1ADD);
		final var controlledId = EntityIdUtils.contractParsedFromSolidityAddress(PRETEND_SENDER_ADDR);
		final var controlledKey = new JContractIDKey(controlledId);
		final var uncontrolledKey = new JContractIDKey(uncontrolledId);

		final var validityTestForNormalCall =
				subject.validityTestFor(PRETEND_RECIPIENT_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_SENDER_ADDR);
		final var validityTestForDelegateCall =
				subject.validityTestFor(PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR);

		assertTrue(validityTestForNormalCall.test(controlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(controlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForNormalCall.test(uncontrolledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(uncontrolledKey, INVALID_MISSING_SIG));
	}

	@Test
	void createsValidityTestThatAcceptsDelegateContractIdKeyWithJustRecipientActive() {
		final var uncontrolledId = EntityIdUtils.contractParsedFromSolidityAddress(Address.BLS12_G1ADD);
		final var controlledId = EntityIdUtils.contractParsedFromSolidityAddress(PRETEND_SENDER_ADDR);
		final var controlledKey = new JDelegatableContractIDKey(controlledId);
		final var uncontrolledKey = new JContractIDKey(uncontrolledId);

		final var validityTestForNormalCall =
				subject.validityTestFor(PRETEND_RECIPIENT_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_SENDER_ADDR);
		final var validityTestForDelegateCall =
				subject.validityTestFor(PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR);

		assertTrue(validityTestForNormalCall.test(controlledKey, INVALID_MISSING_SIG));
		assertTrue(validityTestForDelegateCall.test(controlledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForNormalCall.test(uncontrolledKey, INVALID_MISSING_SIG));
		assertFalse(validityTestForDelegateCall.test(uncontrolledKey, INVALID_MISSING_SIG));
	}

	@Test
	void validityTestsRelyOnCryptoValidityOtherwise() {
		final var mockSig = mock(TransactionSignature.class);
		final var mockKey = new JEd25519Key("01234567890123456789012345678901".getBytes());
		given(cryptoValidity.test(mockKey, mockSig)).willReturn(true);

		final var validityTestForNormalCall =
				subject.validityTestFor(PRETEND_RECIPIENT_ADDR, PRETEND_RECIPIENT_ADDR, PRETEND_SENDER_ADDR);
		final var validityTestForDelegateCall =
				subject.validityTestFor(PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_SENDER_ADDR);

		assertTrue(validityTestForNormalCall.test(mockKey, mockSig));
		assertTrue(validityTestForDelegateCall.test(mockKey, mockSig));
	}

	private void givenAccessorInCtx() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(payer);
	}
}
