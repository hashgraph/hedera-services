package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_BURN_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_CRYPTO_TRANSFER;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DISSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_DISSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_MINT_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_NFT;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_NFTS;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_TOKEN;
import static com.hedera.services.store.contracts.precompile.HTSPrecompiledContract.ABI_ID_TRANSFER_TOKENS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HTSPrecompiledContractTest {
	@Mock
	private AccountStore accountStore;
	@Mock
	private TypedTokenStore tokenStore;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private OptionValidator validator;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private Bytes input;
	@Mock
	private MessageFrame messageFrame;
	@Mock
	private Account account;
	@Mock
	private Token token;
	@Mock
	private TokenRelationship tokenRelationship;
	@Mock
	private SoliditySigsVerifier sigsVerifier;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private EntityCreator creator;
	@Mock
	private DissociationFactory dissociationFactory;


	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory);
	}

	@Test
	void gasRequirementReturnsCorrectValue() {
		// when
		var gas = subject.gasRequirement(input);

		// then
		assertEquals(Gas.of(10_000L), gas);
	}

	@Test
	void computeRevertsTheFrameIfTheFrameIsStatic() {
		// given
		given(messageFrame.isStatic()).willReturn(true);

		// when
		var result = subject.compute(input, messageFrame);

		// then
		verify(messageFrame).setRevertReason(Bytes.of("Cannot interact with HTS in a static call".getBytes(StandardCharsets.UTF_8)));
		assertNull(result);
	}

	@Test
	void computeCallsCorrectImplementationForCryptoTransfer() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeCryptoTransfer(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferTokens() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransferTokens(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferToken() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKEN);
		given(input.slice(16, 20)).willReturn(Bytes.of("0x000000000000000001".getBytes()));
		given(input.slice(48, 20)).willReturn(Bytes.of("0x000000000000000002".getBytes()));
		given(input.slice(80, 20)).willReturn(Bytes.of("0x000000000000000003".getBytes()));
		given(input.slice(100, 32)).willReturn(Bytes.of("0x000000000000000000000000000342".getBytes()));

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransferToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNfts() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFTS);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransferNfts(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNft() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFT);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransferNft(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForMintToken() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_MINT_TOKEN);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeMintToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForBurnToken() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_BURN_TOKEN);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeBurnToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateTokens() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKENS);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeAssociateTokens(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateToken() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(input.slice(16, 20)).willReturn(Bytes.of("0x000000000000000001".getBytes()));
		given(input.slice(48, 20)).willReturn(Bytes.of("0x000000000000000002".getBytes()));
		given(accountStore.loadAccount(any())).willReturn(account);
		given(tokenStore.loadToken(any())).willReturn(token);
		given(token.newRelationshipWith(account, false)).willReturn(tokenRelationship);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeAssociateToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateTokens() {
		// given
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKENS);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeDissociateTokens(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateToken() {
		// given
		given(input.slice(16, 20)).willReturn(Bytes.of("0x000000000000000001".getBytes()));
		given(input.slice(48, 20)).willReturn(Bytes.of("0x000000000000000002".getBytes()));
		given(accountStore.loadAccount(any())).willReturn(account);
		given(tokenStore.loadPossiblyDeletedOrAutoRemovedToken(any())).willReturn(token);
		given(tokenStore.loadTokenRelationship(token, account)).willReturn(tokenRelationship);
		HTSPrecompiledContract contract = Mockito.spy(new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory, creator, dissociationFactory));
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKEN);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeDissociateToken(input, messageFrame);
	}

	@Test
	void computeReturnsNullForWrongInput() {
		// given
		given(input.getInt(0)).willReturn(0x00000000);

		// when
		var result = subject.compute(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeCryptoTransfer() {
		// given

		// when
		var result = subject.computeCryptoTransfer(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeTransferTokens() {
		// given

		// when
		var result = subject.computeTransferTokens(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeTransferNfts() {
		// given

		// when
		var result = subject.computeTransferNfts(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeTransferNft() {
		// given

		// when
		var result = subject.computeTransferNft(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeMintToken() {
		// given

		// when
		var result = subject.computeMintToken(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeBurnToken() {
		// given

		// when
		var result = subject.computeBurnToken(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeAssociateTokens() {
		// given

		// when
		var result = subject.computeAssociateTokens(input, messageFrame);

		// then
		assertNull(result);
	}

	@Test
	void verifyComputeDissociateTokens() {
		// given

		// when
		var result = subject.computeDissociateTokens(input, messageFrame);

		// then
		assertNull(result);
	}
}

