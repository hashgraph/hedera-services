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
import com.hedera.services.contracts.sources.TxnAwareSoliditySigsVerifier;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
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
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HTSPrecompiledContractTest {
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
	private TxnAwareSoliditySigsVerifier sigsVerifier;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private EncodingFacade encoder;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private ImpliedTransfersMarshal impliedTransfers;
	@Mock
	private DissociationFactory dissociationFactory;


	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				validator, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder, encoder,
				syntheticTxnFactory, creator, dissociationFactory, impliedTransfers);
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
		given(messageFrame.isStatic()).willReturn(true);

		var result = subject.compute(input, messageFrame);

		verify(messageFrame).setRevertReason(Bytes.of("HTS precompiles are not static".getBytes()));
		assertNull(result);
	}

	@Test
	void computeCallsCorrectImplementationForCryptoTransfer() {
		// given
		final var contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransfer(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferTokens() {
		// given
		final var contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransfer(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferToken() {
		// given
		final var contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKEN);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransfer(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNfts() {
		// given
		final var contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFTS);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransfer(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNft() {
		// given
		final var contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFT);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeTransfer(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForMintToken() {
		// given
		HTSPrecompiledContract contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);

		given(input.getInt(0)).willReturn(ABI_ID_MINT_TOKEN);

		contract.compute(input, messageFrame);

		verify(contract).computeMintToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForBurnToken() {
		HTSPrecompiledContract contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);
		given(input.getInt(0)).willReturn(ABI_ID_BURN_TOKEN);

		contract.compute(input, messageFrame);

		verify(contract).computeBurnToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateTokens() {
		HTSPrecompiledContract contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKENS);

		contract.compute(input, messageFrame);

		verify(contract).computeAssociateTokens(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateToken() {
		// given
		HTSPrecompiledContract contract = mock(HTSPrecompiledContract.class);

		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);

		// when
		contract.compute(input, messageFrame);

		// then
		verify(contract).computeAssociateToken(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateTokens() {
		final var contract = mock(HTSPrecompiledContract.class);
		willCallRealMethod().given(contract).compute(input, messageFrame);
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKENS);

		contract.compute(input, messageFrame);

		verify(contract).computeDissociateTokens(input, messageFrame);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateToken() {
		// given
		HTSPrecompiledContract contract = mock(HTSPrecompiledContract.class);

		willCallRealMethod().given(contract).compute(input, messageFrame);
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
}

