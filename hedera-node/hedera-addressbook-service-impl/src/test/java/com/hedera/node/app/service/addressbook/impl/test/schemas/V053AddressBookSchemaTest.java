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

package com.hedera.node.app.service.addressbook.impl.test.schemas;

import static com.hedera.node.app.service.addressbook.AddressBookHelper.NODES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.ACCOUNTS_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.FILES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.endpointFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddress;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.info.NodeInfoImpl;
import com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema;
import com.hedera.node.app.service.addressbook.impl.test.handlers.AddressBookTestBase;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.MigrationContext;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class V053AddressBookSchemaTest extends AddressBookTestBase {
    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private MigrationContext migrationContext;

    @Mock
    private NetworkInfo networkInfo;

    @LoggingSubject
    private V053AddressBookSchema subject;

    private final Map<AccountID, Account> accounts = new HashMap<>();
    private final MapWritableKVState<AccountID, Account> writableAccounts =
            new MapWritableKVState<>(ACCOUNTS_KEY, accounts);

    private final Map<EntityNumber, Node> nodes = new HashMap<>();
    private final MapWritableKVState<EntityNumber, Node> writableNodes = new MapWritableKVState<>(NODES_KEY, nodes);

    private final Map<FileID, File> files = new HashMap<>();
    private final MapWritableKVState<FileID, File> writableFiles = new MapWritableKVState<>(FILES_KEY, files);

    private MapWritableStates writableStates = null;

    private String testFile =
            "\\n\\342\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a02820181009f1f8a121c2fd6c76fd508d3e429f0c64bcb44c82a70573552aadcad071569e721958f5a5d09f9587ffafcfbe5341a2f0114acae346ef3c90213d3436ebb27f4350c990c5c8c3f8e1e36707bc08d42560823e3f24e09a03ad0955a5098019629dd04b27b251dce055f3ddcb0a41d66f0941b0b87cdfe3498d46038ab5df06f62a5ade08598573a88c8f5860dc1492a6e186485a9b13250e6d17b80cd39c5c819109e73ca732db23ef8baa776ec85ce0091becb2edefbaa5ed3e5dbfbd1f885a4fa881af3f144a8a565853533d89393592086b2d1d362e45bfe1fb45683aba6c640979ad6b46877184726c6ebd58b2eae85c7cfe3fbabef5f6cced850034b3847206c2d678c361876026b8d351e002af5e0ffe6f5b1f295fdc2f469caa2d2381ea0b48ca987cc2c8e635e8b19ce5e172a93761a8d490a9a4518d7255880a14d77b7ba774892b92a40bb81362e34fc6d5178d9b30112934205cb77fb9a282427394564a8554ea47286a47f86239e75c94789ce98c99844782462944f613167d7b502030100012\\002\\030\\003B\\v\\n\\a1.0.0.0\\020\\001P\\n\\n\\344\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c557af579fa83501be899b28907765bfdfcd52ab432b0195a1f1ecd86fc00ab6c5509b0fdd97edd3cb5cea56a295f312abb550831dbf963f450118b4fcc6e22cf4676200ce9cc8edfbbf558dc69f024264ad7d3dab23bed2133c274e6934489155db1087f90370905c64185a6211dc742fb9a6909d82186947b277463dfb3ff0acd47eff12ead1f6972ef2c1203793c45e77575be4fa110c7e40fa8db9c6187d113f4704014179071abf59be7d2b0de82de4215dc25506b1c9c26e4917401c997506e377e6bf03b688727e7940fad69c5e0da3cd5cbd2be777350aea2d0d47e97a448c84be6ce134d64bee0985c29162f4c1e567cca93d06a3c1be8abce35b557fb77f4fe671a66dec790756d0e8818165f2bacaa891aae7ac7437fc7175b6eb6deb7472378751bb6bf9b0e1483f9668e9fdbd5604c39b14d9e2bedeec846a980d704d171e7ba4b7fcd1a30d945ca12f47a325d9398aa18f97066054d4d15fc8994e2debe73e9271d548683f61ea44fb25071e3518a78ed3eb37e71a0691f2670203010001(\\0012\\002\\030\\004B\\v\\n\\a1.0.0.0\\020\\001P\\n\\n\\344\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a02820181009ba457b73305f04a91cc46b1b965c4e841751abc8b1415a0badfd1f32c2482386a22725eb7ec74dea21e50617d648ea5ac393741ab01b8efb321239b8d4fdb1dfbeb9e3f39aa46580dd045d18ca44d002c37ddb527cce4ddc32bfc73419671f4ca4464a3f2a84fc85c71acf0e5a89626df69a81474ed16529f801a8afa97e435c4e04a964a357527288843e58f0a05cf5153ee4507b2c68b3d7fb54ae6a95a959c87a12f630e95c7b1b3c3695e858662417926d76c16983faf61225038745907e9cf13d67c2acd503ca451c85933ac4118acc279801cb968349903145ced27629dd08916317093587a77c2205cfa52543b53c3b6ea15b84e3d2c30c1ed752a4633c36b25b9893ea02ad562eb9b7868b3b4f47f4a25e356064962ac7b25e582944f00d30798a262f9214d8c5e74d0a8376cc2d6ba64e18f5e4a40afac625062d2ca23cd2800708321d3834314f0e5844859232673a32e70ae0d711e310581bcdb14e87134694c6e0930f46b37b96d49a64573947331e7e507d9e56de5e6146f2f0203010001(\\0022\\002\\030\\005B\\v\\n\\a1.0.0.0\\020\\001P\\n\\n\\344\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c42ccac5fbc691fbbebda87ffd1e75bdcd8922494cf44fdbccee49788521c378bf77db0934ec0d2183d7c51db66f864c11ab7de1ac3c4cfdc1f093a2d6f37e2b34cbe4c8131f9683ad42878c83d3554c645aa167bcfb064a83dc45c5b1158499f9d92587fff7abcd5f221cd8150548413000fa6e5659089b1dfd65766ea78eaedfca6b45455fd8ab5984dbe35e5795d2c635ea7974d43e8eae4febffe492e707b48b1b0fc6481ae9e09d39133009b7d26402e6e52e5e91b2b380d88f0be7fb4b303e70219785057aa94ce924c4926e916569286e86b3ba651ca2a0a63df4f6907fefe3483d93b4ce1d4d03c7142111375b2c2c51d4eb839e37af530b2cbd6f50d4cb36e27937170d9cddac0ace2cc24b804b0a27351cf830b76525e26dfb9dbf49a056624a76862494e7263d0d70cebae952943e55842f5cad13fcf60a2e6dcf7a1d533f3a5bb54ec21918c76e525ba29146675831e17e36c61fe85498828d09b762015412b2e527849baec1cffc77de4c294c550811e598ff24da15a34569dd0203010001(\\0032\\002\\030\\006B\\v\\n\\a1.0.0.0\\020\\001P\\n\n\\344\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a0282018100902f0490a9b7f5d2cd1c0d96c6a6990f573b5f0eb5bdbba39661ef023092419344669969a68a4c7071d329990fb1792e9001cb5598ea71c2d6676824320ee4cabf1dd357ae7f2adbedc1b1b0a9d95623779b4c4c7b47c4787a16ee7188c7217177624a9264ab39c41f7ff0b45a89bda40c4ad07c4d596d5f09d7056bcb5a35f44f95a59c266e09892dcbe46ad51f2d2b3e991a8f6658e1f2cb94c773eb44c44e892d1e55c1076f1608319ee657e40f192967543ab42ab222386d17586e253748dabd025e50b50ae6050720e239d64ee6fb4507c0614dd4be7afdb1330890ff3a6e176527c3116af129a9ac5e336d9f601e7127a6d7d820ad2f902dac9b248668a1bab08d10342ea69a7097132ff7120cc64fcde7840c656ba1732ba95e9c36751175e4ec3d84a7e0d28842b41bbbbd6f28e46c3a6633e1827965c55820d50dae2b0465cc0d42e195b9d1532e6225eb998d6a49079a8a1cd4d0175de3c87f97614847b3cbb17aa34be820b7b3ad98ac3faef993a6778974782c0c4ae3fabbcc430203010001(\\0042\\002\\030\\aB\\v\n\\a1.0.0.0\\020\\001P\\n\n\\344\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a028201810091d7dfff78f4efbe5890450c5bc9e3534bffadad93fb7afb15bc7bcf67d3d3b413bd99940dd82564ada04ab2e4edf0a1c0b8fb7e1a8092e9138e960be2cc68b5b97f57d281c5872e97a479fc848363160e3863b57b33e4869b185ace5e36bd43ae5fa678c9eb66f1f4014786826b2f8fa7e0060f4405c0a8f9da7205ff4683a243fa0f315f1afbb4a4d140d02234e4473fb92fcb38f3eb28c60cf7cbfb64e069c18086e4dd61938920ae0fd7c193e6e104e65b817ed9398e232237fdf08322c9cec09d4099272a7c015d22b4dcc969f6ea1f518902105df60092b55a41b4f32b957b57d84e5b223905e8698951733ea9f2e2461ec0d6522ee816d5850facfeb412cff9b99943a87dc0d046447ce93b97e16d73b96b4263962f81fcf9458e57577c780a6f1615aa7a12326738e269bb731f89e891622e577ea54420bf0ca46be6fc4f71cf2681ac0252aa885e13be672cd284590427dcd137cf311625e8bee3b08fdcaaf465b387ce7cb33816f2c14a6b99ac7d734318cfc59b7ed939bafef8790203010001(\\0052\\002\\030\\bB\\v\\n\\a1.0.0.0\\020\\001P\\n\\n\\344\\006\"\\314\\006308201a2300d06092a864886f70d01010105000382018f003082018a0282018100c6e18c8fbf4cd4eb104542cb20aaaa252d95f052f1086d581c44ad737bf6676c0c3f789af5265b8afb79b50912da84e0afcf7547cb1fff08d0527017eb6dc5cdf83b51969d44336a6387cd70b94bf4c9baf2029840e5f4f863d7081f0fa81e0863adedb8b89a5dac2bb552d6e7b9fba222ac28c57075538fc957992942d341fa2876e6b507e9ce7ed572e8cfda5defa364fdf8d8e23829a4ccbb478f11eee3b32ab85e072951c5d9420115fba327073494f43b5f6bebf84152e356e7b16ba764b7a3b52cb2734640163be1465e6d1fa4c6e6f66684a635c9a556aa7100dbe645df8f4c423ae45a08cb35b4bc187886e2299b5c0210a5fba3b9449f483ef94ed922e1e98c113be166b89c73582243135d442306abe5a71b77018ff335d6dd79542697b168238b96727fd1339b5f82a3b6a597d976037ae2506456c8b34e9fbf3bc32410441c4bfc8eba58597254efebfaa78809a5c8854729a5ba78ece19fc8407dd8894a6bc7844037d878cace6c152c2e89e8a64b068a6c237e09993be806890203010001(\\0062\\002\\030\\tB\\v\\n\\a1.0.0.0\\020\\001P\\n";

    @BeforeEach
    void setUp() {
        subject = new V053AddressBookSchema();
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate).hasSize(1);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(NODES_KEY, iter.next());
    }

    @Test
    void migrateAsExpected() {
        setupMigrationContext();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("AccountStore is not found, can be ignored.");
        assertThat(logCaptor.infoLogs()).contains("FileStore is not found, can be ignored.");
        assertThat(logCaptor.infoLogs()).contains("Migrated 3 nodes from address book");
    }

    @Test
    void migrateAsExpected2() {
        setupMigrationContext2();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("FileStore is not found, can be ignored.");
        assertThat(logCaptor.infoLogs()).contains("Migrated 3 nodes from address book");
        assertEquals(
                Node.newBuilder()
                        .nodeId(1)
                        .accountId(payerId)
                        .description("memo1")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.1", 123), endpointFor("23.45.34.245", 22)))
                        .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                        .weight(0)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(1).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(2)
                        .accountId(accountId)
                        .description("memo2")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.2", 123), endpointFor("23.45.34.240", 23)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(1)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(2).build()));
    }

    @Test
    void migrateAsExpected3() {
        setupMigrationContext3();

        assertThatCode(() -> subject.migrate(migrationContext)).doesNotThrowAnyException();
        assertThat(logCaptor.infoLogs()).contains("Started migrating nodes from address book");
        assertThat(logCaptor.infoLogs()).contains("Migrated 3 nodes from address book");
        assertEquals(
                Node.newBuilder()
                        .nodeId(1)
                        .accountId(payerId)
                        .description("memo1")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.1", 123), endpointFor("23.45.34.245", 22)))
                        .gossipCaCertificate(Bytes.wrap(gossipCaCertificate))
                        .weight(0)
                        .adminKey(anotherKey)
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(1).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(2)
                        .accountId(accountId)
                        .description("memo2")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.2", 123), endpointFor("23.45.34.240", 23)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(1)
                        .adminKey(anotherKey)
                        .grpcCertificateHash(Bytes.wrap("grpcCertificateHash1"))
                        .serviceEndpoint(List.of(endpointFor("127.1.0.1", 1234), endpointFor("127.1.0.2", 1234)))
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(2).build()));
        assertEquals(
                Node.newBuilder()
                        .nodeId(3)
                        .accountId(accountId)
                        .description("memo3")
                        .gossipEndpoint(List.of(endpointFor("127.0.0.3", 124), endpointFor("23.45.34.243", 45)))
                        .gossipCaCertificate(Bytes.wrap(grpcCertificateHash))
                        .weight(10)
                        .adminKey(anotherKey)
                        .grpcCertificateHash(Bytes.wrap("grpcCertificateHash2"))
                        .serviceEndpoint(
                                List.of(endpointFor("domain.test1.com", 1234), endpointFor("domain.test2.com", 5678)))
                        .build(),
                writableNodes.get(EntityNumber.newBuilder().number(3).build()));
    }

    private void setupMigrationContext() {
        writableStates = MapWritableStates.builder().state(writableNodes).build();
        given(migrationContext.newStates()).willReturn(writableStates);

        final var nodeInfo1 = new NodeInfoImpl(
                1,
                payerId,
                0,
                "23.45.34.245",
                22,
                "127.0.0.1",
                123,
                "pubKey1",
                "memo1",
                Bytes.wrap(gossipCaCertificate),
                "memo1");
        final var nodeInfo2 = new NodeInfoImpl(
                2,
                accountId,
                1,
                "23.45.34.240",
                23,
                "127.0.0.2",
                123,
                "pubKey2",
                "memo2",
                Bytes.wrap(grpcCertificateHash),
                "memo2");
        final var nodeInfo3 = new NodeInfoImpl(
                3,
                accountId,
                10,
                "23.45.34.243",
                45,
                "127.0.0.3",
                124,
                "pubKey3",
                "memo3",
                Bytes.wrap(grpcCertificateHash),
                "memo3");
        given(networkInfo.addressBook()).willReturn(List.of(nodeInfo1, nodeInfo2, nodeInfo3));
        given(migrationContext.networkInfo()).willReturn(networkInfo);
        final var config = HederaTestConfigBuilder.create()
                .withValue("bootstrap.genesisPublicKey", defauleAdminKeyBytes)
                .getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);
    }

    private void setupMigrationContext2() {
        setupMigrationContext();
        accounts.put(
                AccountID.newBuilder().accountNum(55).build(),
                Account.newBuilder().key(anotherKey).build());
        writableStates = MapWritableStates.builder()
                .state(writableAccounts)
                .state(writableNodes)
                .build();
        given(migrationContext.newStates()).willReturn(writableStates);

        final var config = HederaTestConfigBuilder.create()
                .withValue("bootstrap.genesisPublicKey", defauleAdminKeyBytes)
                .withValue("accounts.addressBookAdmin", "55")
                .getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);
    }

    private void setupMigrationContext3() {
        setupMigrationContext2();
        final var nodeDetails = new ArrayList<NodeAddress>();
        nodeDetails.addAll(List.of(
                NodeAddress.newBuilder()
                        .nodeId(2)
                        .nodeCertHash(Bytes.wrap("grpcCertificateHash1"))
                        .serviceEndpoint(List.of(endpointFor("127.1.0.1", 1234), endpointFor("127.1.0.2", 1234)))
                        .build(),
                NodeAddress.newBuilder()
                        .nodeId(3)
                        .nodeCertHash(Bytes.wrap("grpcCertificateHash2"))
                        .serviceEndpoint(
                                List.of(endpointFor("domain.test1.com", 1234), endpointFor("domain.test2.com", 5678)))
                        .build()));
        final Bytes fileContent = NodeAddressBook.PROTOBUF.toBytes(
                NodeAddressBook.newBuilder().nodeAddress(nodeDetails).build());
        files.put(
                FileID.newBuilder().fileNum(102).build(),
                File.newBuilder().contents(fileContent).build());
        writableStates = MapWritableStates.builder()
                .state(writableAccounts)
                .state(writableNodes)
                .state(writableFiles)
                .build();
        given(migrationContext.newStates()).willReturn(writableStates);

        final var config = HederaTestConfigBuilder.create()
                .withValue("bootstrap.genesisPublicKey", defauleAdminKeyBytes)
                .withValue("accounts.addressBookAdmin", "55")
                .withValue("files.nodeDetails", "102")
                .getOrCreateConfig();
        given(migrationContext.configuration()).willReturn(config);
    }
}
