package com.hedera.services.contracts.operation;

import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import javax.inject.Inject;

import static com.hedera.services.contracts.operation.HederaCreateOperation.storageAndMemoryGasForCreation;
import static com.hedera.services.sigs.utils.MiscCryptoUtils.keccak256DigestOf;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class HederaCreate2Operation extends AbstractRecordingCreateOperation {
	private static final Bytes PREFIX = Bytes.fromHexString("0xFF");

	@Inject
	public HederaCreate2Operation(
			final GasCalculator gasCalculator,
			final EntityCreator creator,
			final SyntheticTxnFactory syntheticTxnFactory,
			final AccountRecordsHistorian recordsHistorian
	) {
		super(
				0xF5,
				"ħCREATE2",
				4,
				1,
				1,
				gasCalculator,
				creator,
				syntheticTxnFactory,
				recordsHistorian);
	}

	@Override
	protected Gas cost(MessageFrame frame) {
		final var effGasCalculator = gasCalculator();

		return effGasCalculator
				.create2OperationGasCost(frame)
				.plus(storageAndMemoryGasForCreation(frame, effGasCalculator));
	}

	@Override
	protected Address targetContractAddress(final MessageFrame frame) {
		final var sender = frame.getRecipientAddress();
		final var offset = clampedToLong(frame.getStackItem(1));
		final var length = clampedToLong(frame.getStackItem(2));

		final Bytes32 salt = UInt256.fromBytes(frame.getStackItem(3));
		final var initCode = frame.readMutableMemory(offset, length);
		final var hash = keccak256(Bytes.concatenate(PREFIX, sender, salt, keccak256(initCode)));

		final var address = Address.wrap(hash.slice(12, 20));
		frame.warmUpAddress(address);
		return address;
	}

	private static Bytes32 keccak256(final Bytes input) {
		return Bytes32.wrap(keccak256DigestOf(input.toArrayUnsafe()));
	}
}
