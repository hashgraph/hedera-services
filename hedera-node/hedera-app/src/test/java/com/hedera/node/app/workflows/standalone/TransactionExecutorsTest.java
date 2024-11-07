/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.workflows.standalone;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.spi.AppContext.Gossip.UNAVAILABLE_GOSSIP;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.util.FileUtilities.createFileID;
import static com.hedera.node.app.workflows.standalone.TransactionExecutors.TRANSACTION_EXECUTORS;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.FeeService;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.addressbook.impl.AddressBookServiceImpl;
import com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl;
import com.hedera.node.app.service.networkadmin.impl.NetworkServiceImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.service.util.impl.UtilServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.internal.CryptoUtils;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.lifecycle.info.NodeInfo;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the ability to execute transactions against a standalone state by,
 * <ol>
 *   <li>Constructing a {@link FakeState} that fully implements the {@link State} API, with {@link WritableStates}
 *   that are all {@link CommittableWritableStates}; and hence accumulate changes as multiple transactions are
 *   executed.</li>
 *   <li>Executing a {@link HederaFunctionality#FILE_CREATE} to upload some contract initcode.</li>
 *   <li>Executing a {@link HederaFunctionality#CONTRACT_CREATE} to create an instance of the contract.</li>
 *   <li>Executing a {@link HederaFunctionality#CONTRACT_CALL} to call a function on the contract, and capturing
 *   the output of a {@link StandardJsonTracer} that is passed in as an extra argument.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TransactionExecutorsTest {
    private static final long GAS = 100_000L;
    private static final long EXPECTED_LUCKY_NUMBER = 42L;
    private static final AccountID TREASURY_ID =
            AccountID.newBuilder().accountNum(2).build();
    private static final AccountID NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(3).build();
    private static final FileID EXPECTED_INITCODE_ID =
            FileID.newBuilder().fileNum(1001).build();
    private static final ContractID EXPECTED_CONTRACT_ID =
            ContractID.newBuilder().contractNum(1002).build();
    private static final com.esaulpaugh.headlong.abi.Function PICK_FUNCTION =
            new com.esaulpaugh.headlong.abi.Function("pick()", "(uint32)");
    private static final String EXPECTED_TRACE_START =
            "{\"pc\":0,\"op\":96,\"gas\":\"0x13458\",\"gasCost\":\"0x3\",\"memSize\":0,\"depth\":1,\"refund\":0,\"opName\":\"PUSH1\"}";

    @Mock
    private SignatureVerifier signatureVerifier;

    @Test
    void executesTransactionsAsExpected() {
        // Construct a full implementation of the consensus node State API with all genesis accounts and files
        final var state = genesisState();

        // Get a standalone executor based on this state, with an override to allow slightly longer memos
        final var executor =
                TRANSACTION_EXECUTORS.newExecutor(state, Map.of("hedera.transaction.maxMemoUtf8Bytes", "101"), null);

        // Execute a FileCreate that uploads the initcode for the Multipurpose.sol contract
        final var uploadOutput = executor.execute(uploadMultipurposeInitcode(), Instant.EPOCH);
        final var uploadReceipt = uploadOutput.getFirst().transactionRecord().receiptOrThrow();
        assertThat(uploadReceipt.fileIDOrThrow()).isEqualTo(EXPECTED_INITCODE_ID);

        // Execute a ContractCreate that creates a Multipurpose contract instance
        final var creationOutput = executor.execute(createMultipurposeContract(), Instant.EPOCH);
        final var creationReceipt =
                creationOutput.getFirst().transactionRecord().receiptOrThrow();
        assertThat(creationReceipt.contractIDOrThrow()).isEqualTo(EXPECTED_CONTRACT_ID);

        // Now execute a ContractCall against the contract, with an extra StandardJsonTracer whose output we
        // capture in a StringWriter for later inspection
        final var stringWriter = new StringWriter();
        final var printWriter = new PrintWriter(stringWriter);
        final var addOnTracer = new StandardJsonTracer(printWriter, false, false, false, false);
        final var callOutput = executor.execute(contractCallMultipurposePickFunction(), Instant.EPOCH, addOnTracer);
        final var callRecord = callOutput.getFirst().transactionRecord();
        final var callResult = callRecord.contractCallResultOrThrow().contractCallResult();
        final long luckyNumber =
                PICK_FUNCTION.getOutputs().decode(callResult.toByteArray()).get(0);
        assertThat(luckyNumber).isEqualTo(EXPECTED_LUCKY_NUMBER);
        printWriter.flush();
        assertThat(stringWriter.toString()).startsWith(EXPECTED_TRACE_START);
    }

    private TransactionBody contractCallMultipurposePickFunction() {
        final var callData = PICK_FUNCTION.encodeCallWithArgs();
        return newBodyBuilder()
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .contractID(EXPECTED_CONTRACT_ID)
                        .functionParameters(Bytes.wrap(callData.array()))
                        .gas(GAS)
                        .build())
                .build();
    }

    private TransactionBody createMultipurposeContract() {
        final var maxLifetime =
                DEFAULT_CONFIG.getConfigData(EntitiesConfig.class).maxLifetime();
        return newBodyBuilder()
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .fileID(EXPECTED_INITCODE_ID)
                        .autoRenewPeriod(new Duration(maxLifetime))
                        .gas(GAS)
                        .build())
                .build();
    }

    private TransactionBody uploadMultipurposeInitcode() {
        final var maxLifetime =
                DEFAULT_CONFIG.getConfigData(EntitiesConfig.class).maxLifetime();
        return newBodyBuilder()
                .fileCreate(FileCreateTransactionBody.newBuilder()
                        .contents(resourceAsBytes("initcode/Multipurpose.bin"))
                        .keys(IMMUTABILITY_SENTINEL_KEY.keyListOrThrow())
                        .expirationTime(new Timestamp(maxLifetime, 0))
                        .build())
                .build();
    }

    private TransactionBody.Builder newBodyBuilder() {
        final var minValidDuration =
                DEFAULT_CONFIG.getConfigData(HederaConfig.class).transactionMinValidDuration();
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .transactionValidStart(new Timestamp(0, 0))
                        .accountID(TREASURY_ID)
                        .build())
                .memo(
                        "This memo is 101 characters long, which with default settings would die with the status MEMO_TOO_LONG")
                .nodeAccountID(NODE_ACCOUNT_ID)
                .transactionValidDuration(new Duration(minValidDuration));
    }

    private State genesisState() {
        final var state = new FakeState();
        final var networkInfo = fakeNetworkInfo();
        final var servicesRegistry = new FakeServicesRegistry();
        registerServices(servicesRegistry);
        final var migrator = new FakeServiceMigrator();
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                networkInfo,
                new NoOpMetrics());
        final var writableStates = state.getWritableStates(FileService.NAME);
        final var files = writableStates.<FileID, File>get(V0490FileSchema.BLOBS_KEY);
        genesisContentProviders(networkInfo, DEFAULT_CONFIG).forEach((fileNum, provider) -> {
            final var fileId = createFileID(fileNum, DEFAULT_CONFIG);
            files.put(
                    fileId,
                    File.newBuilder()
                            .fileId(fileId)
                            .keys(KeyList.DEFAULT)
                            .contents(provider.apply(DEFAULT_CONFIG))
                            .build());
        });
        ((CommittableWritableStates) writableStates).commit();
        return state;
    }

    private Map<Long, Function<Configuration, Bytes>> genesisContentProviders(
            @NonNull final NetworkInfo networkInfo, @NonNull final Configuration config) {
        final var genesisSchema = new V0490FileSchema();
        final var filesConfig = config.getConfigData(FilesConfig.class);
        return Map.of(
                filesConfig.addressBook(), ignore -> genesisSchema.genesisAddressBook(networkInfo),
                filesConfig.nodeDetails(), ignore -> genesisSchema.genesisNodeDetails(networkInfo),
                filesConfig.feeSchedules(), genesisSchema::genesisFeeSchedules,
                filesConfig.exchangeRates(), genesisSchema::genesisExchangeRates,
                filesConfig.networkProperties(), genesisSchema::genesisNetworkProperties,
                filesConfig.hapiPermissions(), genesisSchema::genesisHapiPermissions,
                filesConfig.throttleDefinitions(), genesisSchema::genesisThrottleDefinitions);
    }

    private void registerServices(@NonNull final ServicesRegistry servicesRegistry) {
        // Register all service schema RuntimeConstructable factories before platform init
        Set.of(
                        new EntityIdService(),
                        new ConsensusServiceImpl(),
                        new ContractServiceImpl(new AppContextImpl(
                                InstantSource.system(),
                                signatureVerifier,
                                UNAVAILABLE_GOSSIP,
                                HederaTestConfigBuilder.createConfig(),
                                () -> 3L)),
                        new FileServiceImpl(),
                        new FreezeServiceImpl(),
                        new ScheduleServiceImpl(),
                        new TokenServiceImpl(),
                        new UtilServiceImpl(),
                        new RecordCacheService(),
                        new BlockRecordService(),
                        new FeeService(),
                        new CongestionThrottleService(),
                        new NetworkServiceImpl(),
                        new AddressBookServiceImpl())
                .forEach(servicesRegistry::register);
    }

    private NetworkInfo fakeNetworkInfo() {
        final var addressBook = new AddressBook(StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                                RandomAddressBookBuilder.create(new Random())
                                        .withSize(1)
                                        .withRealKeysEnabled(true)
                                        .build()
                                        .iterator(),
                                0),
                        false)
                .map(address ->
                        address.copySetMemo("0.0." + (address.getNodeId().id() + 3)))
                .toList());
        return new NetworkInfo() {
            @NonNull
            @Override
            public Bytes ledgerId() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @NonNull
            @Override
            public NodeInfo selfNodeInfo() {
                return new NodeInfoImpl(0, AccountID.DEFAULT, 0, List.of(), getCertBytes(randomX509Certificate()));
            }

            @NonNull
            @Override
            public List<NodeInfo> addressBook() {
                return List.of(
                        new NodeInfoImpl(0, AccountID.DEFAULT, 0, List.of(), getCertBytes(randomX509Certificate())));
            }

            @Nullable
            @Override
            public NodeInfo nodeInfo(final long nodeId) {
                return new NodeInfoImpl(0, AccountID.DEFAULT, 0, List.of(), Bytes.EMPTY);
            }

            @Override
            public boolean containsNode(final long nodeId) {
                return addressBook.contains(NodeId.of(nodeId));
            }

            @Override
            public void updateFrom(final State state) {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public Roster roster() {
                return buildRoster(addressBook);
            }
        };
    }

    private Bytes resourceAsBytes(@NonNull final String loc) {
        try {
            try (final var in = TransactionExecutorsTest.class.getClassLoader().getResourceAsStream(loc)) {
                final var bytes = Objects.requireNonNull(in).readAllBytes();
                return Bytes.wrap(bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static X509Certificate randomX509Certificate() {
        try {
            final SecureRandom secureRandom = CryptoUtils.getDetRandom();

            final KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(3072, secureRandom);
            final KeyPair rsaKeyPair1 = rsaKeyGen.generateKeyPair();

            final String name = "CN=Bob";
            return CryptoStatic.generateCertificate(name, rsaKeyPair1, name, rsaKeyPair1, secureRandom);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Bytes getCertBytes(X509Certificate certificate) {
        try {
            return Bytes.wrap(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
