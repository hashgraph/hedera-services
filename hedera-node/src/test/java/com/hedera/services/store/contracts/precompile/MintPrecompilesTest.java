package com.hedera.services.store.contracts.precompile;

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.token.MintLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MintPrecompilesTest {
	private static final Bytes pretendArguments = Bytes.fromBase64String("ABCDEF");

	@Mock
	private HederaLedger ledger;
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
	private MessageFrame frame;
	@Mock
	private SoliditySigsVerifier sigsVerifier;
	@Mock
	private AccountRecordsHistorian recordsHistorian;
	@Mock
	private DecodingFacade decoder;
	@Mock
	private HTSPrecompiledContract.MintLogicFactory mintLogicFactory;
	@Mock
	private MintLogic mintLogic;
	@Mock
	private TransactionBody.Builder mockBuilder;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private AbstractLedgerWorldUpdater worldUpdater;
	@Mock
	private WorldLedgers wrappedLedgers;

	private HTSPrecompiledContract subject;

	@BeforeEach
	void setUp() {
		subject = new HTSPrecompiledContract(
				ledger, accountStore, validator,
				tokenStore, dynamicProperties, gasCalculator,
				recordsHistorian, sigsVerifier, decoder,
				syntheticTxnFactory);
		subject.setMintLogicFactory(mintLogicFactory);
	}


	@Test
	void nftMintHappyPathWorks() {
		givenFrameContext();

		given(decoder.decodeMint(pretendArguments)).willReturn(nftMint);
		given(syntheticTxnFactory.createNonFungibleMint(nftMint)).willReturn(mockBuilder);
		given(sigsVerifier.hasActiveSupplyKey(Id.fromGrpcToken(nonFungible), recipientAddr, contractAddr)).willReturn(true);
		
	}

	private void givenFrameContext() {
		given(frame.getContractAddress()).willReturn(contractAddr);
		given(frame.getRecipientAddress()).willReturn(recipientAddr);
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(worldUpdater.wrappedTrackingLedgers()).willReturn(wrappedLedgers);
	}

	private static final TokenID nonFungible = IdUtils.asToken("0.0.777");
	private static final List<ByteString> newMetadata = List.of(
			ByteString.copyFromUtf8("AAA"), ByteString.copyFromUtf8("BBB"), ByteString.copyFromUtf8("CCC"));
	private static final SyntheticTxnFactory.NftMint nftMint = new SyntheticTxnFactory.NftMint(nonFungible, newMetadata);
	private static final Address recipientAddr = Address.ALTBN128_ADD;
	private static final Address contractAddr = Address.ALTBN128_MUL;
}