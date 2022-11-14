package com.hedera.services.yahcli.commands.signedstate;



import static java.lang.Integer.min;
import static java.util.Arrays.copyOf;

import java.util.Optional;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;



@Command(
    name = "decompilecontract",
    subcommands = {picocli.CommandLine.HelpCommand.class},
    description = "Decompiles contract bytecodes")
public class DecompileContractCommand implements Callable<Integer> {
  @ParentCommand
  private SignedStateCommand signedStateCommand;

  @Option(
      names = {"-b", "--bytecode"}, required=true, arity="1", converter = HexStringConverter.class, paramLabel = "BYTECODE_HEX", description = "Contract bytecode as a hex string"
  )
  HexStringConverter.Bytes theContract;

  @Option(
      names = {"-i", "--id"}, paramLabel = "CONTRACT_ID", description = "Contract Id (decimal, optional)"
  )
  Optional<Long> theContractId;

  @Override
  public Integer call() throws Exception {

    final int DISPLAY_LENGTH = 100;
    int contractLength = theContract.bytes.length;
    int displayLength = min(DISPLAY_LENGTH, contractLength);
    short[] displayContract = copyOf(theContract.bytes, displayLength);

    System.out.printf("DecompileContract: contract id %d%n", theContractId.orElse(-1L));
    System.out.printf("   first bytes of contract: %s%n", toHex(displayContract));
    return 0;
  }

  @Contract(pure = true)
  private @NotNull String toHex(short @NotNull [] byteBuffer) {
    final String hexits = "0123456789abcdef";
    StringBuffer sb = new StringBuffer(2 * byteBuffer.length);
    for (short byt : byteBuffer) {
      sb.append(hexits.charAt(byt>>4));
      sb.append(hexits.charAt(byt&0xf));
    }
    return sb.toString();
  }

}
