package com.hedera.services.contracts.execution;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.contractvalidation.ContractValidationRule;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

import java.util.List;

public class HederaCreationProcessor extends ContractCreationProcessor {
	public HederaCreationProcessor(
			final GasCalculator gasCalculator,
			final EVM evm,
			final boolean requireCodeDepositToSucceed,
			final List<ContractValidationRule> contractValidationRules,
			final long initialContractNonce
	) {
		super(gasCalculator, evm, requireCodeDepositToSucceed, contractValidationRules, initialContractNonce);
	}


}
