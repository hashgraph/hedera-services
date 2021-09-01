package contract;

import com.hedera.hashgraph.sdk.*;
import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for executing various types of contracts. This particular suite of tests
 * exist to try to determine what the worst case scenarios are for contract execution
 * speed. We're trying to eliminate as much of the other platform overhead as possible,
 * so that we're really measuring the overhead of the EVM itself. This also gives us
 * a framework for measuring performance of different optimizations or EVM implementations.
 */
@SuppressWarnings({"DefaultAnnotationParam", "FieldCanBeLocal", "unused"})
@State(Scope.Thread)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 30, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ContractExecutionBench {
    // These keys are the standard ones used for local development, they are
    // NOT USED anywhere else, ever.
    private static final String PUBLIC_KEY = "0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    private static final String PRIVATE_KEY = "91132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137";

    private static final byte[] CONTRACT_CODE = ("608060405234801561001057600080fd5b5061027d806100206000396000f3fe" +
            "608060405234801561001057600080fd5b50600436106100415760003560e01c806307c49b441461004657806362bcc7ce1461" +
            "00505780637c82ea4f1461005a575b600080fd5b61004e610064565b005b61005861009a565b005b6100626100c6565b005b61" +
            "03e86000819055505b6001156100985760026000546100849190610185565b6000819055506103e860008190555061006e565b" +
            "565b60016000819055505b6001156100c45760016000546100b991906100fe565b6000819055506100a3565b565b6298968060" +
            "00819055505b6001156100fc5760026000546100e79190610154565b600081905550629896806000819055506100d1565b565b" +
            "6000610109826101df565b9150610114836101df565b9250827fffffffffffffffffffffffffffffffffffffffffffffffffff" +
            "ffffffffffffff03821115610149576101486101e9565b5b828201905092915050565b600061015f826101df565b915061016a" +
            "836101df565b92508261017a57610179610218565b5b828204905092915050565b6000610190826101df565b915061019b8361" +
            "01df565b9250817fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff04831182151516156101d4" +
            "576101d36101e9565b5b828202905092915050565b6000819050919050565b7f4e487b71000000000000000000000000000000" +
            "00000000000000000000000000600052601160045260246000fd5b7f4e487b7100000000000000000000000000000000000000" +
            "000000000000000000600052601260045260246000fdfea26469706673582212207eee7aee182eb85282c09005c426c185b4ff" +
            "1bff1f8e3abcdedd75bdd8d789eb64736f6c63430008010033").getBytes(StandardCharsets.UTF_8);

    // This public/private key pair are the same for all accounts < 0.0.100
    private PrivateKey privateKey;
    private PublicKey pubKey;

    private AccountId alice;

    // This is the main treasury account where all the money is.
    private AccountId treasuryAccount;
    private Client treasuryClient;

    // This account is admin for the contract
    private AccountId contractAccount;
    private Client contractClient;
    private FileId contractFileId;
    private ContractId contractId;

    // This account is the customer who will send tokens to/from
    // the contract account in order to exchange tokens
    private AccountId customerAccount;
    private Client customerClient;

    @Setup
    public void prepare() {
        privateKey = PrivateKey.fromString(PRIVATE_KEY);
        pubKey = PublicKey.fromString(PUBLIC_KEY);

        alice = AccountId.fromString("0.0.3");

        final var network = new HashMap<String, AccountId>();
        network.put("127.0.0.1:50211", new AccountId(3));

        treasuryAccount = AccountId.fromString("0.0.2");
        treasuryClient = Client.forNetwork(network);
        treasuryClient.setOperator(treasuryAccount, privateKey);

        contractAccount = AccountId.fromString("0.0.40");
        contractClient = Client.forNetwork(network);
        contractClient.setOperator(contractAccount, privateKey);

        customerAccount = AccountId.fromString("0.0.50");
        customerClient = Client.forNetwork(network);
        customerClient.setOperator(customerAccount, privateKey);

        setupAccounts();
        uploadContractCode();
        setupContract();
    }

    private void setupAccounts() {
        // Transfer funds from the treasury into the other accounts
        try {
            System.out.println("Fund Transfer: " + new TransferTransaction()
                    .addHbarTransfer(treasuryAccount, Hbar.from(-2000000))
                    .addHbarTransfer(contractAccount, Hbar.from(1000000))
                    .addHbarTransfer(customerAccount, Hbar.from(1000000))
                    .execute(treasuryClient)
                    .getReceipt(treasuryClient)
                    .status);
        } catch (Exception e) {
            System.err.println("Failed to transfer funds");
            throw new RuntimeException(e);
        }
    }

    private void uploadContractCode() {
        try {

            // Create it. Is this idempotent? I don't think so!
            final var receipt = new FileCreateTransaction()
                    .setContents(CONTRACT_CODE)
                    .setKeys(pubKey)
                    .execute(contractClient)
                    .getReceipt(contractClient);

            contractFileId = receipt.fileId;
            System.out.println("Code uploaded: " + receipt.status);
        } catch (Exception e) {
            System.err.println("Failed to setup contract");
            throw new RuntimeException(e);
        };
    }

    private void setupContract() {
        try {
            final var receipt = new ContractCreateTransaction()
                    .setAdminKey(pubKey)
                    .setProxyAccountId(contractAccount) // Partial Token transfers to this account will hit the contract
                    .setBytecodeFileId(contractFileId)
                    .setGas(1000)
                    .execute(contractClient)
                    .getReceipt(contractClient);

            contractId = receipt.contractId;
            System.out.println("Contract creation: " + receipt.status);
        } catch (Exception e) {
            System.err.println("Failed to setup contract");
        };
    }

    @Benchmark
    public void addNumbers() {
        final int gas = 10_000;
        final String test = "runAdd";
        try {
            new ContractExecuteTransaction()
                    .setGas(gas)
                    .setFunction(test)
                    .setContractId(contractId)
                    .execute(customerClient);
        } catch (Exception e) {
            System.err.println("Encountered error: " + e.getMessage());
        }
    }
}
