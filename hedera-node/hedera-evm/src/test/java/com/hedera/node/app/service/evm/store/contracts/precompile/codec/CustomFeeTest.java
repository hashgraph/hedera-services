package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

public class CustomFeeTest {

      @Test
    void testCustomFee() {
        final var customfee = customFees();
        final var customfee2 = customFees();

        assertEquals(customfee, customfee2);
        assertEquals(customfee.hashCode(), customfee2.hashCode());
    }

  private List<CustomFee>
  customFees() {
    final var payerAccount = Address.wrap(
        Bytes.fromHexString("0x00000000000000000000000000000000000005ce"));
    List<com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee>
        customFees = new ArrayList<>();
    FixedFee fixedFeeInHbar =
        new FixedFee(
            100, null, true, false, payerAccount);
    FixedFee fixedFeeInHts =
        new FixedFee(
            100,
            Address.wrap(
                Bytes.fromHexString("0x00000000000000000000000000000000000005ca")),
            false,
            false,
            payerAccount);
    FixedFee fixedFeeSameToken =
        new FixedFee(
            50, null, true, false, payerAccount);
    FractionalFee fractionalFee =
        new FractionalFee(
            15, 100, 10, 50, false, payerAccount);

    com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee1 =
        new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
    customFee1.setFixedFee(fixedFeeInHbar);
    com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee2 =
        new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
    customFee2.setFixedFee(fixedFeeInHts);
    com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee3 =
        new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
    customFee3.setFixedFee(fixedFeeSameToken);
    com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee customFee4 =
        new com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee();
    customFee4.setFractionalFee(fractionalFee);

    return List.of(customFee1, customFee2, customFee3, customFee4);
  }

}
