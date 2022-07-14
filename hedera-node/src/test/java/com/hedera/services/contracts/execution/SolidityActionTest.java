package com.hedera.services.contracts.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.utils.EntityIdUtils;
import java.nio.charset.StandardCharsets;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class SolidityActionTest {

  private static final EntityId sender = EntityId.fromAddress(Address.BLS12_MAP_FP_TO_G1);
  private static final EntityId recipient = EntityId.fromAddress(Address.ALTBN128_ADD);
  private final byte[] input = "input".getBytes();
  private final byte[] output = "output".getBytes(StandardCharsets.UTF_8);
  private final long value = 55L;
  private final long gasUsed = 555L;
  private final long gas = 100L;;

  @Test
  void toGrpcWhenCallingAccountAndRecipientAccountAndOutput() {
    final var actual = new SolidityAction(
        ContractActionType.CALL,
        sender,
        null,
        gas,
        input,
        recipient,
        null,
        value,
        0
    );
    actual.setGasUsed(gasUsed);
    actual.setOutput(output);

    final var expected = ContractAction.newBuilder()
        .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
        .setCallingAccount(EntityIdUtils.asAccount(sender))
        .setGas(gas)
        .setInput(ByteStringUtils.wrapUnsafely(input))
        .setRecipientAccount(EntityIdUtils.asAccount(recipient))
        .setValue(value)
        .setGasUsed(gasUsed)
        .setOutput(ByteStringUtils.wrapUnsafely(output))
        .setCallDepth(0)
        .build();

    assertEquals(expected, actual.toGrpc());
  }

  @Test
  void toGrpcWhenCallingContractAndRecipientContractAndRevertReason() {
    final var actual = new SolidityAction(
        ContractActionType.CALL,
        null,
        sender,
        gas,
        input,
        null,
        recipient,
        value,
        0
    );
    actual.setGasUsed(gasUsed);
    actual.setRevertReason(output);

    final var expected = ContractAction.newBuilder()
        .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
        .setCallingContract(EntityIdUtils.contractIdFromEvmAddress(sender.toEvmAddress()))
        .setGas(gas)
        .setInput(ByteStringUtils.wrapUnsafely(input))
        .setRecipientContract(EntityIdUtils.contractIdFromEvmAddress(recipient.toEvmAddress()))
        .setValue(value)
        .setGasUsed(gasUsed)
        .setRevertReason(ByteStringUtils.wrapUnsafely(output))
        .setCallDepth(0)
        .build();

    assertEquals(expected, actual.toGrpc());
  }

  @Test
  void toGrpcWhenInvalidRecipientAndError() {
    final var actual = new SolidityAction(
        ContractActionType.CALL,
        null,
        sender,
        gas,
        input,
        null,
        null,
        value,
        0
    );
    actual.setGasUsed(gasUsed);
    actual.setError(output);
    actual.setInvalidSolidityAddress(recipient.toEvmAddress().toArrayUnsafe());

    final var expected = ContractAction.newBuilder()
        .setCallType(com.hedera.services.stream.proto.ContractActionType.CALL)
        .setCallingContract(EntityIdUtils.contractIdFromEvmAddress(sender.toEvmAddress()))
        .setGas(gas)
        .setInput(ByteStringUtils.wrapUnsafely(input))
        .setInvalidSolidityAddress(ByteStringUtils.wrapUnsafely(recipient.toEvmAddress().toArrayUnsafe()))
        .setValue(value)
        .setGasUsed(gasUsed)
        .setError(ByteStringUtils.wrapUnsafely(output))
        .setCallDepth(0)
        .build();

    assertEquals(expected, actual.toGrpc());
  }
}