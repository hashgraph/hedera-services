package com.hedera.services.store.contracts.world;

import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.vm.DataWord;

public class DWUtil {

	public static DataWord fromUInt256(UInt256 uInt256) {
		return DataWord.of(uInt256.toArray());
	}

	public static UInt256 fromDataWord(DataWord dataWord) {
		return UInt256.fromBytes(Bytes32.wrap(dataWord.getData()));
	}
}
