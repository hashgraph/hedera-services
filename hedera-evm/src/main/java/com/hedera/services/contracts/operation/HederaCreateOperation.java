//package com.hedera.services.contracts.operation;
//
///*
// * -
// * ‌
// * Hedera Services Node
// * ​
// * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
// * ​
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *       http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// * ‍
// *
// */
//
//import com.hedera.services.contracts.gascalculator.StorageGasCalculator;
//import com.hedera.services.records.RecordsHistorian;
//import com.hedera.services.state.EntityCreator;
//import com.hedera.services.store.contracts.HederaWorldUpdater;
//import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
//import org.hyperledger.besu.datatypes.Address;
//import org.hyperledger.besu.evm.frame.MessageFrame;
//import org.hyperledger.besu.evm.gascalculator.GasCalculator;
//
//import javax.inject.Inject;
//
///**
// * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.CreateOperation}.
// *
// * Addresses are allocated through {@link HederaWorldUpdater#newContractAddress(Address)}
// *
// * Gas costs are based on the expiry of the parent and the provided storage bytes per hour variable
// */
////TODO: EntityCreator, SyntheticTxnFactory, SyntheticTxnFactory to be supplied by Java Configurator. Only used in hedera-nodes to track changes and manage records
//public class HederaCreateOperation extends AbstractRecordingCreateOperation {
//	private final StorageGasCalculator storageGasCalculator;
//
//	@Inject
//	public HederaCreateOperation(
//			final GasCalculator gasCalculator,
//			final EntityCreator creator,
//			final SyntheticTxnFactory syntheticTxnFactory,
//			final RecordsHistorian recordsHistorian,
//			final StorageGasCalculator storageGasCalculator
//	) {
//		super(
//				0xF0,
//				"ħCREATE",
//				3,
//				1,
//				1,
//				gasCalculator,
//				creator,
//				syntheticTxnFactory,
//				recordsHistorian);
//		this.storageGasCalculator = storageGasCalculator;
//	}
//
//	@Override
//	public long cost(final MessageFrame frame) {
//		final var calculator = gasCalculator();
//		return calculator.createOperationGasCost(frame) + storageGasCalculator.creationGasCost(frame, calculator);
//	}
//
//	@Override
//	protected boolean isEnabled() {
//		return true;
//	}
//
//	@Override
//	protected Address targetContractAddress(final MessageFrame frame) {
//		final var updater = (HederaWorldUpdater) frame.getWorldUpdater();
//		final Address address = updater.newContractAddress(frame.getRecipientAddress());
//		frame.warmUpAddress(address);
//		return address;
//	}
//}
