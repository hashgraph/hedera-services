/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.exports;

import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.services.exports.FileCompressionUtils.COMPRESSION_ALGORITHM_EXTENSION;
import static com.hedera.services.exports.FileCompressionUtils.readUncompressedFileBytes;
import static com.hedera.services.state.exports.SignedStateBalancesExporter.SINGLE_ACCOUNT_BALANCES_COMPARATOR;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.EntityNum.fromTokenId;
import static com.hedera.services.utils.EntityNumPair.fromAccountTokenRel;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.stream.proto.AllAccountBalances;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hedera.services.stream.proto.TokenUnitBalance;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

@ExtendWith(LogCaptureExtension.class)
class SignedStateBalancesExporterTest {
    private static final NodeId nodeId = new NodeId(false, 1);
    private final MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
    private final MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
    private final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

    private MerkleToken token;
    private MerkleToken deletedToken;

    private static final long ledgerFloat = 1_000;
    private static final long thisNodeBalance = 400;
    private static final AccountID thisNode = asAccount("0.0.3");
    private static final long anotherNodeBalance = 100;
    private static final AccountID anotherNode = asAccount("0.0.4");
    private static final long firstNonNodeAccountBalance = 250;
    private static final AccountID firstNonNode = asAccount("0.0.1001");
    private static final long secondNonNodeAccountBalance = 250;
    private static final AccountID secondNonNode = asAccount("0.0.1002");
    private static final AccountID deleted = asAccount("0.0.1003");

    private static final TokenID theToken = asToken("0.0.1004");
    private static final long secondNonNodeTokenBalance = 100;
    private static final TokenID theDeletedToken = asToken("0.0.1005");
    private static final long secondNonNodeDeletedTokenBalance = 101;
    private static final TokenID theMissingToken = asToken("0.0.1006");

    private static final byte[] sig = "not-really-a-sig".getBytes();
    private static final byte[] fileHash = "not-really-a-hash".getBytes();

    private MerkleAccount thisNodeAccount;
    private MerkleAccount anotherNodeAccount;
    private MerkleAccount firstNonNodeAccount;
    private MerkleAccount secondNonNodeAccount;
    private MerkleAccount deletedAccount;

    private final MockGlobalDynamicProps dynamicProperties = new MockGlobalDynamicProps();
    private final Instant now = Instant.now();

    private ServicesState state;
    private PropertySource properties;
    private Function<byte[], Signature> signer;
    private SigFileWriter sigFileWriter;
    private FileHashReader hashReader;
    private DirectoryAssurance assurance;
    private SystemExits systemExits;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private SignedStateBalancesExporter subject;

    @BeforeEach
    void setUp() throws ConstructableRegistryException, NoSuchAlgorithmException {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
        final var secondNonNodeDelTokenAssociationKey =
                fromAccountTokenRel(secondNonNode, theDeletedToken);
        final var secondNonNodeTokenAssociationKey = fromAccountTokenRel(secondNonNode, theToken);

        thisNodeAccount = MerkleAccountFactory.newAccount().balance(thisNodeBalance).get();
        anotherNodeAccount = MerkleAccountFactory.newAccount().balance(anotherNodeBalance).get();
        firstNonNodeAccount =
                MerkleAccountFactory.newAccount().balance(firstNonNodeAccountBalance).get();
        secondNonNodeAccount =
                MerkleAccountFactory.newAccount()
                        .balance(secondNonNodeAccountBalance)
                        .tokens(theToken, theDeletedToken, theMissingToken)
                        .get();
        deletedAccount = MerkleAccountFactory.newAccount().deleted(true).get();

        accounts.put(fromAccountId(thisNode), thisNodeAccount);
        accounts.put(fromAccountId(anotherNode), anotherNodeAccount);
        accounts.put(fromAccountId(firstNonNode), firstNonNodeAccount);
        accounts.put(fromAccountId(secondNonNode), secondNonNodeAccount);
        secondNonNodeAccount.setNumPositiveBalances(0);
        secondNonNodeAccount.setHeadTokenId(theToken.getTokenNum());
        secondNonNodeAccount.setNumAssociations(2);
        accounts.put(fromAccountId(deleted), deletedAccount);

        token = mock(MerkleToken.class);
        given(token.isDeleted()).willReturn(false);
        given(token.decimals()).willReturn(1);
        given(token.symbol()).willReturn("existingToken");
        given(token.hasFreezeKey()).willReturn(true);
        given(token.hasKycKey()).willReturn(true);
        given(token.grpcId()).willReturn(theToken);
        deletedToken = mock(MerkleToken.class);
        given(deletedToken.isDeleted()).willReturn(true);
        given(deletedToken.decimals()).willReturn(3);
        given(deletedToken.symbol()).willReturn("deletedToken");
        given(deletedToken.hasFreezeKey()).willReturn(true);
        given(deletedToken.hasKycKey()).willReturn(true);
        given(deletedToken.grpcId()).willReturn(theDeletedToken);
        tokens.put(fromTokenId(theToken), token);
        tokens.put(fromTokenId(theDeletedToken), deletedToken);

        final var secondNonNodeTokenAssociation =
                new MerkleTokenRelStatus(secondNonNodeTokenBalance, false, true, false);
        secondNonNodeTokenAssociation.setKey(secondNonNodeTokenAssociationKey);

        final var secondNonNodeDelTokenAssociation =
                new MerkleTokenRelStatus(secondNonNodeDeletedTokenBalance, false, true, false);
        secondNonNodeDelTokenAssociation.setKey(secondNonNodeDelTokenAssociationKey);
        secondNonNodeDelTokenAssociation.setPrev(theToken.getTokenNum());
        secondNonNodeTokenAssociation.setNext(theDeletedToken.getTokenNum());

        tokenRels.put(secondNonNodeTokenAssociationKey, secondNonNodeTokenAssociation);
        tokenRels.put(secondNonNodeDelTokenAssociationKey, secondNonNodeDelTokenAssociation);

        assurance = mock(DirectoryAssurance.class);

        properties = mock(PropertySource.class);
        given(properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT)).willReturn(ledgerFloat);

        var firstNodeAddress = mock(Address.class);
        given(firstNodeAddress.getMemo()).willReturn("0.0.3");
        var secondNodeAddress = mock(Address.class);
        given(secondNodeAddress.getMemo()).willReturn("0.0.4");

        var book = mock(AddressBook.class);
        given(book.getSize()).willReturn(2);
        given(book.getAddress(0)).willReturn(firstNodeAddress);
        given(book.getAddress(1)).willReturn(secondNodeAddress);

        state = mock(ServicesState.class);
        given(state.getAccountFromNodeId(nodeId)).willReturn(thisNode);
        given(state.tokens()).willReturn(tokens);
        given(state.accounts()).willReturn(AccountStorageAdapter.fromInMemory(accounts));
        given(state.tokenAssociations()).willReturn(tokenRels);
        given(state.addressBook()).willReturn(book);

        signer = mock(UnaryOperator.class);
        given(signer.apply(fileHash)).willReturn(new Signature(SignatureType.RSA, sig));

        systemExits = mock(SystemExits.class);

        subject =
                new SignedStateBalancesExporter(systemExits, properties, signer, dynamicProperties);

        sigFileWriter = mock(SigFileWriter.class);
        hashReader = mock(FileHashReader.class);
        subject.sigFileWriter = sigFileWriter;
        subject.hashReader = hashReader;
    }

    @Test
    void logsOnIoException() throws NoSuchAlgorithmException {
        final var otherDynamicProperties =
                new MockGlobalDynamicProps() {
                    @Override
                    public String pathToBalancesExportDir() {
                        return "not/a/real/location";
                    }
                };
        subject =
                new SignedStateBalancesExporter(
                        systemExits, properties, signer, otherDynamicProperties);
        subject.directories = assurance;

        subject.exportBalancesFrom(state, now, nodeId);

        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Could not export to")));
    }

    @Test
    void logsOnSigningFailure() {
        final var loc = expectedExportLoc();
        given(sigFileWriter.writeSigFile(any(), any(), any()))
                .willThrow(UncheckedIOException.class);

        subject.exportBalancesFrom(state, now, nodeId);

        assertThat(
                logCaptor.errorLogs(),
                contains(Matchers.startsWith("Could not sign balance file")));

        new File(loc).delete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testExportingTokenBalancesProto(final boolean isCompressed)
            throws NoSuchAlgorithmException {
        // given
        final var captor = ArgumentCaptor.forClass(String.class);
        var loc = expectedExportLoc();
        if (isCompressed) {
            loc += COMPRESSION_ALGORITHM_EXTENSION;
        }
        dynamicProperties.setAccountBalanceCompression(isCompressed);
        final var desiredDebugMsg = "Created balance signature file " + "'" + loc + "_sig'.";
        given(hashReader.readHash(loc)).willReturn(fileHash);
        given(sigFileWriter.writeSigFile(captor.capture(), any(), any())).willReturn(loc + "_sig");
        final var expectedAccountBalances =
                AllAccountBalances.newBuilder()
                        .addAllAllAccounts((subject.summarized(state).orderedBalances()))
                        .setConsensusTimestamp(
                                Timestamp.newBuilder()
                                        .setSeconds(now.getEpochSecond())
                                        .setNanos(now.getNano())
                                        .build());
        final var messageDigest =
                MessageDigest.getInstance(Cryptography.DEFAULT_DIGEST_TYPE.algorithmName());
        final var expectedHash =
                messageDigest.digest(expectedAccountBalances.build().toByteArray());
        given(signer.apply(expectedHash)).willReturn(new Signature(SignatureType.RSA, sig));

        // when
        subject.exportBalancesFrom(state, now, nodeId);

        // then
        final var allAccountBalances = importBalanceProtoFile(loc).get();
        final var accounts = allAccountBalances.getAllAccountsList();
        assertEquals(4, accounts.size());
        for (var account : accounts) {
            if (account.getAccountID().getAccountNum() == firstNonNode.getAccountNum()) {
                assertEquals(250, account.getHbarBalance());
            } else if (account.getAccountID().getAccountNum() == secondNonNode.getAccountNum()) {
                assertEquals(250, account.getHbarBalance());
                final var firstUnitBalances = account.getTokenUnitBalances(0);
                assertEquals(theToken.getTokenNum(), firstUnitBalances.getTokenId().getTokenNum());
                assertEquals(secondNonNodeTokenBalance, firstUnitBalances.getBalance());
                final var secondUnitBalances = account.getTokenUnitBalances(1);
                assertEquals(
                        theDeletedToken.getTokenNum(),
                        secondUnitBalances.getTokenId().getTokenNum());
                assertEquals(secondNonNodeDeletedTokenBalance, secondUnitBalances.getBalance());
            }
        }

        verify(sigFileWriter).writeSigFile(loc, sig, expectedHash);
        assertThat(logCaptor.debugLogs(), contains(desiredDebugMsg));
        new File(loc).delete();
    }

    @Test
    void protoWriteIoException() throws NoSuchAlgorithmException {
        final var otherDynamicProperties =
                new MockGlobalDynamicProps() {
                    @Override
                    public String pathToBalancesExportDir() {
                        return "not/a/real/location";
                    }
                };
        subject =
                new SignedStateBalancesExporter(
                        systemExits, properties, signer, otherDynamicProperties);
        subject.directories = assurance;

        subject.exportBalancesFrom(state, now, nodeId);

        assertThat(logCaptor.errorLogs(), contains(Matchers.startsWith("Could not export to")));
    }

    @Test
    void assuresExpectedProtoFileDir() throws IOException {
        subject.directories = assurance;

        subject.exportBalancesFrom(state, now, nodeId);

        verify(assurance).ensureExistenceOf(expectedExportDir());
    }

    private String expectedExportLoc() {
        return dynamicProperties.pathToBalancesExportDir()
                + File.separator
                + "balance0.0.3"
                + File.separator
                + now.toString().replace(":", "_")
                + "_Balances.pb";
    }

    @Test
    void errorProtoLogsOnIoException() throws IOException {
        subject.directories = assurance;
        final var desiredMsg =
                "Cannot ensure existence of export dir " + "'" + expectedExportDir() + "'!";
        willThrow(IOException.class).given(assurance).ensureExistenceOf(any());

        subject.exportBalancesFrom(state, now, nodeId);

        assertThat(logCaptor.errorLogs(), contains(desiredMsg));
    }

    @Test
    void testSingleAccountBalancingSort() {
        final var expectedBalances = theExpectedBalances();
        List<SingleAccountBalances> sorted = new ArrayList<>();
        sorted.addAll(expectedBalances);

        final var singleAccountBalances = sorted.remove(0);
        sorted.add(singleAccountBalances);
        assertNotEquals(expectedBalances, sorted);

        sorted.sort(SINGLE_ACCOUNT_BALANCES_COMPARATOR);

        assertEquals(expectedBalances, sorted);
    }

    @Test
    void summarizesAsExpected() {
        final var expectedBalances = theExpectedBalances();
        final var desiredWarning =
                "Node '0.0.4' has unacceptably low balance " + anotherNodeBalance + "!";

        final var summary = subject.summarized(state);

        assertEquals(ledgerFloat, summary.totalFloat().longValue());
        assertEquals(expectedBalances, summary.orderedBalances());
        assertThat(logCaptor.warnLogs(), contains(desiredWarning));
    }

    private List<SingleAccountBalances> theExpectedBalances() {
        final var singleAcctBuilder = SingleAccountBalances.newBuilder();
        final var thisNode =
                singleAcctBuilder
                        .setAccountID(asAccount("0.0.3"))
                        .setHbarBalance(thisNodeBalance)
                        .build();
        final var anotherNode =
                singleAcctBuilder
                        .setHbarBalance(anotherNodeBalance)
                        .setAccountID(asAccount("0.0.4"))
                        .build();
        final var firstNon =
                singleAcctBuilder
                        .setAccountID(asAccount("0.0.1001"))
                        .setHbarBalance(firstNonNodeAccountBalance)
                        .build();
        final var nonDeletedTokenUnits =
                TokenUnitBalance.newBuilder()
                        .setTokenId(theToken)
                        .setBalance(secondNonNodeTokenBalance);
        final var deletedTokenUnits =
                TokenUnitBalance.newBuilder()
                        .setTokenId(theDeletedToken)
                        .setBalance(secondNonNodeDeletedTokenBalance);
        final var secondNon =
                singleAcctBuilder
                        .setAccountID(asAccount("0.0.1002"))
                        .setHbarBalance(secondNonNodeAccountBalance)
                        .addTokenUnitBalances(nonDeletedTokenUnits)
                        .addTokenUnitBalances(deletedTokenUnits)
                        .build();

        return List.of(thisNode, anotherNode, firstNon, secondNon);
    }

    @Test
    void assuresExpectedDir() throws IOException {
        subject.directories = assurance;

        subject.exportBalancesFrom(state, now, nodeId);

        verify(assurance).ensureExistenceOf(expectedExportDir());
    }

    @Test
    void throwsOnUnexpectedTotalFloat() throws NegativeAccountBalanceException {
        // setup:
        final var mutableAnotherNodeAccount = accounts.getForModify(fromAccountId(anotherNode));
        final var desiredSuffix = "had total balance 1001 not 1000; exiting";

        // given:
        mutableAnotherNodeAccount.setBalance(anotherNodeBalance + 1);

        // when:
        subject.exportBalancesFrom(state, now, nodeId);

        // then:
        assertThat(logCaptor.errorLogs(), contains(Matchers.endsWith(desiredSuffix)));
        verify(systemExits).fail(1);
    }

    @Test
    void errorLogsOnIoException() throws IOException {
        subject.directories = assurance;
        final var desiredError =
                "Cannot ensure existence of export dir " + "'" + expectedExportDir() + "'!";
        willThrow(IOException.class).given(assurance).ensureExistenceOf(any());

        subject.exportBalancesFrom(state, now, nodeId);

        assertThat(logCaptor.errorLogs(), contains(desiredError));
    }

    private String expectedExportDir() {
        return dynamicProperties.pathToBalancesExportDir()
                + File.separator
                + "balance0.0.3"
                + File.separator;
    }

    @Test
    void initsAsExpected() {
        assertEquals(ledgerFloat, subject.expectedFloat);
    }

    @Test
    void neverTimeToExportIfNotConfigured() {
        // given:
        dynamicProperties.turnOffBalancesExport();

        // expect:
        assertFalse(subject.isTimeToExport(now));
    }

    @Test
    void exportsWhenPeriodSecsHaveElapsed() throws NoSuchAlgorithmException {
        final int exportPeriodInSecs = dynamicProperties.balancesExportPeriodSecs();
        final var startTime = Instant.parse("2021-07-07T08:10:00.000Z");
        subject =
                new SignedStateBalancesExporter(systemExits, properties, signer, dynamicProperties);

        // start from a time within 1 second of boundary time
        var now = startTime.plusNanos(12340);
        assertTrue(subject.isTimeToExport(now));
        assertEquals(startTime.plusSeconds(exportPeriodInSecs), subject.getNextExportTime());

        now = now.plusNanos(1);
        assertFalse(subject.isTimeToExport(now));
        assertEquals(startTime.plusSeconds(exportPeriodInSecs), subject.getNextExportTime());

        now = now.plusSeconds(exportPeriodInSecs);
        assertTrue(subject.isTimeToExport(now));
        assertEquals(startTime.plusSeconds(exportPeriodInSecs * 2), subject.getNextExportTime());

        // start from a random time
        subject =
                new SignedStateBalancesExporter(systemExits, properties, signer, dynamicProperties);
        now = Instant.parse("2021-07-07T08:12:38.123Z");
        assertFalse(subject.isTimeToExport(now));
        assertEquals(startTime.plusSeconds(exportPeriodInSecs), subject.getNextExportTime());

        now = now.plusSeconds(exportPeriodInSecs);
        assertTrue(subject.isTimeToExport(now));
        assertEquals(startTime.plusSeconds(exportPeriodInSecs * 2), subject.getNextExportTime());
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.walk(Path.of("src/test/resources/balance0.0.3"))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    static Optional<AllAccountBalances> importBalanceProtoFile(final String protoLoc) {
        try {
            AllAccountBalances allAccountBalances;
            if (protoLoc.endsWith(COMPRESSION_ALGORITHM_EXTENSION)) {
                allAccountBalances =
                        AllAccountBalances.parseFrom(readUncompressedFileBytes(protoLoc));
            } else {
                final var fin = new FileInputStream(protoLoc);
                allAccountBalances = AllAccountBalances.parseFrom(fin);
            }
            return Optional.ofNullable(allAccountBalances);
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
