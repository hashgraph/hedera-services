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
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
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
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.DEFAULT_GAS_PRICE;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.associateOp;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.dissociateToken;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleBurn;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungibleMint;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.multiDissociateOp;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
	void noopTreasuryManagersDoNothing() {
		assertDoesNotThrow(() ->
				HTSPrecompiledContract.NOOP_TREASURY_ADDER.perform(null, null));
		assertDoesNotThrow(() ->
				HTSPrecompiledContract.NOOP_TREASURY_REMOVER.removeKnownTreasuryForToken(null, null));
	}

	@Test
	void gasRequirementReturnsCorrectValueForInvalidInput() {
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		var gas = subject.gasRequirement(input);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), gas);
	}

	@Test
	void gasRequirementReturnsCorrectValueForSingleCryptoTransfer() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForMultipleCryptoTransfers() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(
						CryptoTransferTransactionBody.newBuilder()
								.addTokenTransfers(TokenTransferList.newBuilder().build())
								.addTokenTransfers(TokenTransferList.newBuilder().build())
								.addTokenTransfers(TokenTransferList.newBuilder().build())));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of((DEFAULT_GAS_PRICE / 2) * 3), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferMultipleTokens() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferSingleToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKEN);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferNfts() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFTS);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForTransferNft() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFT);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForMintToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_MINT_TOKEN);
		given(decoder.decodeMint(any())).willReturn(fungibleMint);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForBurnToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_BURN_TOKEN);
		given(decoder.decodeBurn(any())).willReturn(fungibleBurn);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForAssociateTokens() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKENS);
		final var builder = TokenAssociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createAssociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForAssociateToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(any())).willReturn(associateOp);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForDissociateTokens() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKENS);
		given(decoder.decodeMultipleDissociations(any())).willReturn(multiDissociateOp);
		final var builder = TokenDissociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createDissociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenDissociate(builder));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
	}

	@Test
	void gasRequirementReturnsCorrectValueForDissociateToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKEN);
		given(decoder.decodeDissociate(any())).willReturn(dissociateToken);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// then
		assertEquals(Gas.of(DEFAULT_GAS_PRICE), subject.gasRequirement(input));
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
		given(input.getInt(0)).willReturn(ABI_ID_CRYPTO_TRANSFER);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferTokens() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKENS);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_TOKEN);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNfts() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFTS);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForTransferNft() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_TRANSFER_NFT);
		given(syntheticTxnFactory.createCryptoTransfer(any()))
				.willReturn(TransactionBody.newBuilder().setCryptoTransfer(CryptoTransferTransactionBody.newBuilder()));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.TransferPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForMintToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_MINT_TOKEN);
		given(decoder.decodeMint(any())).willReturn(fungibleMint);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.MintPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForBurnToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_BURN_TOKEN);
		given(decoder.decodeBurn(any())).willReturn(fungibleBurn);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.BurnPrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateTokens() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKENS);
		final var builder = TokenAssociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createAssociate(any()))
				.willReturn(TransactionBody.newBuilder().setTokenAssociate(builder));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.MultiAssociatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForAssociateToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_ASSOCIATE_TOKEN);
		given(decoder.decodeAssociation(any())).willReturn(associateOp);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.AssociatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateTokens() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKENS);
		given(decoder.decodeMultipleDissociations(any())).willReturn(multiDissociateOp);
		final var builder = TokenDissociateTransactionBody.newBuilder();
		builder.setAccount(multiDissociateOp.accountId());
		builder.addAllTokens(multiDissociateOp.tokenIds());
		given(syntheticTxnFactory.createDissociate(any())).willReturn(
				TransactionBody.newBuilder().setTokenDissociate(builder));
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.MultiDissociatePrecompile);
	}

	@Test
	void computeCallsCorrectImplementationForDissociateToken() {
		// given
		given(input.getInt(0)).willReturn(ABI_ID_DISSOCIATE_TOKEN);
		given(decoder.decodeDissociate(any())).willReturn(dissociateToken);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);

		// then
		assertTrue(subject.getPrecompile() instanceof HTSPrecompiledContract.DissociatePrecompile);
	}

	@Test
	void computeReturnsNullForWrongInput() {
		// given
		given(input.getInt(0)).willReturn(0x00000000);
		given(dynamicProperties.htsDefaultGasCost()).willReturn(DEFAULT_GAS_PRICE);

		// when
		subject.gasRequirement(input);
		var result = subject.compute(input, messageFrame);

		// then
		assertNull(result);
	}
}

