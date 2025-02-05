/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.hip993;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromByteString;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;
import static com.hedera.node.app.hapi.utils.forensics.OrderedComparison.statusHistograms;
import static com.hedera.services.bdd.spec.HapiPropertySource.asDnsServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiPropertySource.asServiceEndpoint;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.recordStreamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.grouping.GroupingVerbs.getSystemFiles;
import static com.hedera.services.bdd.spec.utilops.streams.assertions.SelectedItemsAssertion.SELECTED_ITEMS_KEY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.NodeAddressBook;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.GenesisHapiTest;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.grouping.SysFileLookups;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItems;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsValidator;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

/**
 * Asserts the synthetic file creations stipulated by HIP-993 match the file contents returned by the gRPC
 * API both before after the network has handled the genesis transaction. (It would be a annoyance for various tools
 * and tests if they needed to ensure a transaction was handled before issuing any {@code FileGetContents} queries
 * or submitting {@code FileUpdate} transactions.)
 */
public class SystemFileExportsTest {
    private static final String DESCRIPTION_PREFIX = "Revision #";

    @GenesisHapiTest
    final Stream<DynamicTest> syntheticAddressBookCreatedAtGenesis() {
        final AtomicReference<Bytes> addressBookContent = new AtomicReference<>();
        return hapiTest(
                recordStreamMustIncludeNoFailuresFrom(visibleItems(
                        validatorSpecificSysFileFor(addressBookContent, "files.addressBook", "genesisTxn"),
                        "genesisTxn")),
                sourcingContextual(spec ->
                        getSystemFiles(spec.startupProperties().getLong("files.addressBook"), addressBookContent::set)),
                cryptoCreate("firstUser").via("genesisTxn"),
                // Assert the first created entity still has the expected number
                withOpContext((spec, opLog) -> assertEquals(
                        spec.startupProperties().getLong("hedera.firstUserEntity"),
                        spec.registry().getAccountID("firstUser").getAccountNum(),
                        "First user entity num doesn't match config")));
    }

    @GenesisHapiTest
    final Stream<DynamicTest> syntheticNodeDetailsCreatedAtGenesis() {
        final AtomicReference<Bytes> addressBookContent = new AtomicReference<>();
        return hapiTest(
                recordStreamMustIncludeNoFailuresFrom(visibleItems(
                        validatorSpecificSysFileFor(addressBookContent, "files.nodeDetails", "genesisTxn"),
                        "genesisTxn")),
                sourcingContextual(spec ->
                        getSystemFiles(spec.startupProperties().getLong("files.nodeDetails"), addressBookContent::set)),
                cryptoCreate("firstUser").via("genesisTxn"),
                // Assert the first created entity still has the expected number
                withOpContext((spec, opLog) -> assertEquals(
                        spec.startupProperties().getLong("hedera.firstUserEntity"),
                        spec.registry().getAccountID("firstUser").getAccountNum(),
                        "First user entity num doesn't match config")));
    }

    @GenesisHapiTest
    final Stream<DynamicTest> syntheticFileCreationsMatchQueries() {
        final AtomicReference<Map<FileID, Bytes>> preGenesisContents = new AtomicReference<>();
        return hapiTest(
                recordStreamMustIncludeNoFailuresFrom(visibleItems(validatorFor(preGenesisContents), "genesisTxn")),
                getSystemFiles(preGenesisContents::set),
                cryptoCreate("firstUser").via("genesisTxn"),
                // Assert the first created entity still has the expected number
                withOpContext((spec, opLog) -> assertEquals(
                        spec.startupProperties().getLong("hedera.firstUserEntity"),
                        spec.registry().getAccountID("firstUser").getAccountNum(),
                        "First user entity num doesn't match config")));
    }

    private static CurrentAndNextFeeSchedule parseFeeSchedule(final byte[] bytes)
            throws InvalidProtocolBufferException {
        return CurrentAndNextFeeSchedule.parseFrom(bytes);
    }

    private static ThrottleDefinitions parseThrottleDefs(final byte[] bytes) throws InvalidProtocolBufferException {
        return ThrottleDefinitions.parseFrom(bytes);
    }

    private static ServicesConfigurationList parseConfigList(final byte[] bytes) throws InvalidProtocolBufferException {
        return ServicesConfigurationList.parseFrom(bytes);
    }

    private interface ParseFunction<T> {
        T parse(@NonNull byte[] bytes) throws InvalidProtocolBufferException;
    }

    private static VisibleItemsValidator nodeUpdatesValidator() {
        return (spec, records) -> {
            final var items = records.get(SELECTED_ITEMS_KEY);
            assertNotNull(items, "No post-upgrade node updates found");
            final Map<Long, Key> newAdminKeys = items.entries().stream()
                    .filter(item -> item.function() == NodeUpdate)
                    .map(item -> item.body().getNodeUpdate())
                    .collect(toMap(NodeUpdateTransactionBody::getNodeId, NodeUpdateTransactionBody::getAdminKey));
            assertEquals(spec.registry().getKey("node0AdminKey"), newAdminKeys.get(0L));
            assertEquals(spec.registry().getKey("node3AdminKey"), newAdminKeys.get(3L));
        };
    }

    private static <T> VisibleItemsValidator sysFileExportValidator(
            @NonNull final String fileNumProperty,
            @NonNull final T expectedValue,
            @NonNull final ParseFunction<T> parser) {
        return (spec, records) -> {
            final var items = records.get(SELECTED_ITEMS_KEY);
            assertNotNull(items, "No post-upgrade txn found");
            final var targetId =
                    new FileID(0, 0, Long.parseLong(spec.startupProperties().get(fileNumProperty)));
            final var updateItem = items.entries().stream()
                    .filter(item -> item.function() == FileUpdate)
                    .filter(item ->
                            toPbj(item.body().getFileUpdate().getFileID()).equals(targetId))
                    .findFirst()
                    .orElse(null);
            assertNotNull(updateItem, "No update for " + fileNumProperty + " found in post-upgrade txn");
            final var synthOp = updateItem.body().getFileUpdate();
            final T actual;
            try {
                actual = parser.parse(synthOp.getContents().toByteArray());
            } catch (InvalidProtocolBufferException e) {
                throw new IllegalStateException(fileNumProperty + " update was not parseable", e);
            }
            assertEquals(expectedValue, actual);
        };
    }

    private static VisibleItemsValidator nodeDetailsExportValidator(
            @NonNull final byte[][] grpcCertHashes,
            @NonNull final AtomicReference<Map<Long, X509Certificate>> gossipCertificates) {
        return (spec, records) -> {
            final var items = requireNonNull(records.get(SELECTED_ITEMS_KEY));
            final var histogram = statusHistograms(items.entries());
            assertEquals(Map.of(SUCCESS, 1), histogram.get(FileUpdate));
            final var updateItem = items.entries().stream()
                    .filter(item -> item.function() == FileUpdate)
                    .findFirst()
                    .orElseThrow();
            final var synthOp = updateItem.body().getFileUpdate();
            final var nodeDetailsId =
                    new FileID(0, 0, Long.parseLong(spec.startupProperties().get("files.nodeDetails")));
            assertEquals(nodeDetailsId, toPbj(synthOp.getFileID()));
            try {
                final var updatedAddressBook = NodeAddressBook.PROTOBUF.parse(
                        Bytes.wrap(synthOp.getContents().toByteArray()));
                var prevNodeId = -1L;
                for (final var address : updatedAddressBook.nodeAddress()) {
                    assertTrue(address.nodeId() > prevNodeId, "Node IDs must be in ascending order");
                    final var expectedCert = gossipCertificates.get().get(address.nodeId());
                    final var expectedPubKey = expectedCert.getPublicKey().getEncoded();
                    final var actualPubKey = unhex(address.rsaPubKey());
                    assertArrayEquals(expectedPubKey, actualPubKey, "node" + address.nodeId() + " has wrong RSA key");

                    final var actualCertHash = address.nodeCertHash().toByteArray();
                    assertArrayEquals(
                            getHexStringBytesFromBytes(grpcCertHashes[(int) address.nodeId()]),
                            actualCertHash,
                            "node" + address.nodeId() + " has wrong cert hash");

                    final var expectedDescription = DESCRIPTION_PREFIX + address.nodeId();
                    assertEquals(expectedDescription, address.description());

                    final var expectedServiceEndpoint = endpointsFor((int) address.nodeId());
                    assertEquals(expectedServiceEndpoint, address.serviceEndpoint());
                    prevNodeId = address.nodeId();
                }
            } catch (ParseException e) {
                Assertions.fail("Update contents was not protobuf " + e.getMessage());
            }
        };
    }

    private static byte[] getHexStringBytesFromBytes(final byte[] rawBytes) {
        final String hexString = HexFormat.of().formatHex(rawBytes);
        return Normalizer.normalize(hexString, Normalizer.Form.NFD).getBytes(UTF_8);
    }

    private static VisibleItemsValidator addressBookExportValidator(
            @NonNull final String fileNumProperty, @NonNull final byte[][] grpcCertHashes) {
        return (spec, records) -> {
            final var items = records.get(SELECTED_ITEMS_KEY);
            assertNotNull(items, "No post-upgrade txn found");
            final var targetId =
                    new FileID(0, 0, Long.parseLong(spec.startupProperties().get(fileNumProperty)));
            final var updateItem = items.entries().stream()
                    .filter(item -> item.function() == FileUpdate)
                    .filter(item ->
                            toPbj(item.body().getFileUpdate().getFileID()).equals(targetId))
                    .findFirst()
                    .orElse(null);
            assertNotNull(updateItem, "No update for " + fileNumProperty + " found in post-upgrade txn");
            final var synthOp = updateItem.body().getFileUpdate();
            final var addressBookId =
                    new FileID(0, 0, Long.parseLong(spec.startupProperties().get(fileNumProperty)));
            assertEquals(addressBookId, toPbj(synthOp.getFileID()));
            try {
                final var updatedAddressBook = NodeAddressBook.PROTOBUF.parse(
                        Bytes.wrap(synthOp.getContents().toByteArray()));
                var prevNodeId = -1L;
                for (final var address : updatedAddressBook.nodeAddress()) {
                    assertTrue(address.nodeId() > prevNodeId, "Node IDs must be in ascending order");
                    final var actualCertHash = address.nodeCertHash().toByteArray();
                    assertArrayEquals(
                            getHexStringBytesFromBytes(grpcCertHashes[(int) address.nodeId()]),
                            actualCertHash,
                            "node" + address.nodeId() + " has wrong cert hash");

                    final var expectedServiceEndpoint = endpointsFor((int) address.nodeId());
                    assertEquals(expectedServiceEndpoint, address.serviceEndpoint());
                    prevNodeId = address.nodeId();
                }
            } catch (ParseException e) {
                Assertions.fail("Update contents was not protobuf " + e.getMessage());
            }
        };
    }

    private static VisibleItemsValidator validatorFor(
            @NonNull final AtomicReference<Map<FileID, Bytes>> preGenesisContents) {
        return (spec, records) -> validateSystemFileExports(spec, records, preGenesisContents.get());
    }

    private static void validateSystemFileExports(
            @NonNull final HapiSpec spec,
            @NonNull final Map<String, VisibleItems> genesisRecords,
            @NonNull final Map<FileID, Bytes> preGenesisContents) {
        final var items = requireNonNull(genesisRecords.get("genesisTxn"));
        final var histogram = statusHistograms(items.entries());
        final var systemFileNums =
                SysFileLookups.allSystemFileNums(spec).boxed().toList();
        assertEquals(Map.of(SUCCESS, systemFileNums.size()), histogram.get(FileCreate));
        // Also check we export a node stake update at genesis
        assertEquals(Map.of(SUCCESS, 1), histogram.get(NodeStakeUpdate));
        final var postGenesisContents = SysFileLookups.getSystemFileContents(spec, fileNum -> true);
        items.entries().stream().filter(item -> item.function() == FileCreate).forEach(item -> {
            final var fileId = item.createdFileId();
            final var preContents = requireNonNull(
                    preGenesisContents.get(item.createdFileId()), "No pre-genesis contents for " + fileId);
            final var postContents = requireNonNull(
                    postGenesisContents.get(item.createdFileId()), "No post-genesis contents for " + fileId);
            final var exportedContents =
                    fromByteString(item.body().getFileCreate().getContents());
            if (fileId.fileNum()
                    != 102) { // for nodedetail, the node's weight changed between preContent and exportedContents
                assertEquals(exportedContents, preContents, fileId + " contents don't match pre-genesis query");
            }
            assertEquals(exportedContents, postContents, fileId + " contents don't match post-genesis query");
        });
    }

    private static VisibleItemsValidator validatorSpecificSysFileFor(
            @NonNull final AtomicReference<Bytes> fileContent,
            @NonNull final String fileNumProperty,
            @NonNull final String specTxnIds) {
        return (spec, records) ->
                specificSysFileValidator(spec, records, fileContent.get(), fileNumProperty, specTxnIds);
    }

    private static void specificSysFileValidator(
            @NonNull final HapiSpec spec,
            @NonNull final Map<String, VisibleItems> genesisRecords,
            @NonNull final Bytes fileContent,
            @NonNull final String fileNumProperty,
            @NonNull final String specTxnIds) {
        final var items = requireNonNull(genesisRecords.get(specTxnIds));
        final long fileNumb = spec.startupProperties().getLong(fileNumProperty);
        final var histogram = statusHistograms(items.entries());
        final var systemFileNums =
                SysFileLookups.allSystemFileNums(spec).boxed().toList();
        assertEquals(Map.of(SUCCESS, systemFileNums.size()), histogram.get(FileCreate));
        // Also check we export a node stake update at genesis
        assertEquals(Map.of(SUCCESS, 1), histogram.get(NodeStakeUpdate));
        final var fileItem = items.entries().stream()
                .filter(item -> item.function() == FileCreate)
                .filter(item -> item.createdFileId().equals(new FileID(0, 0, fileNumb)))
                .findFirst()
                .orElse(null);

        assertNotNull(fileItem, "No create item for " + fileNumProperty + " found in " + specTxnIds + " txn");
        final var fileCreateContents = fileItem.body().getFileCreate().getContents();
        assertNotNull(
                fileCreateContents, "No create content for " + fileNumProperty + " found in " + specTxnIds + " txn");
        if (fileNumProperty.equals("files.nodeDetails")) {
            try {
                final var addressBook = NodeAddressBook.PROTOBUF.parse(fileContent);
                final var updatedAddressBook =
                        NodeAddressBook.PROTOBUF.parse(Bytes.wrap(fileCreateContents.toByteArray()));
                assertEquals(
                        addressBook.nodeAddress().size(),
                        updatedAddressBook.nodeAddress().size(),
                        "address book size mismatch");

                for (int i = 0;
                        i < addressBook.nodeAddress().size();
                        i++) { // only stake not matching because of recalculating
                    final var address = updatedAddressBook.nodeAddress().get(i);
                    final var updatedAddress = updatedAddressBook.nodeAddress().get(i);
                    assertEquals(address.nodeId(), updatedAddress.nodeId(), "nodeId mismatch");
                    assertEquals(address.nodeAccountId(), updatedAddress.nodeAccountId(), "nodeAccountId mismatch");
                    assertEquals(address.nodeCertHash(), updatedAddress.nodeCertHash(), "nodeCertHash mismatch");
                    assertEquals(address.description(), updatedAddress.description(), "description mismatch");
                    assertEquals(address.rsaPubKey(), updatedAddress.rsaPubKey(), "rsaPubKey mismatch");
                    assertEquals(
                            address.serviceEndpoint(), updatedAddress.serviceEndpoint(), "serviceEndpoint mismatch");
                }
            } catch (ParseException e) {
                Assertions.fail("Update contents was not protobuf " + e.getMessage());
            }
        } else {
            assertEquals(
                    fileContent, fromByteString(fileCreateContents), fileNumb + " contents don't match genesis query");
        }
    }

    private static Map<Long, X509Certificate> generateCertificates(final int n) {
        final var randomAddressBook = RandomAddressBookBuilder.create(new Random())
                .withSize(n)
                .withRealKeysEnabled(true)
                .build();
        final var nextNodeId = new AtomicLong();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(randomAddressBook.iterator(), 0), false)
                .map(Address::getSigCert)
                .collect(Collectors.toMap(cert -> nextNodeId.getAndIncrement(), cert -> cert));
    }

    private static byte[] derEncoded(final X509Certificate cert) {
        try {
            return cert.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException("Failed to DER encode cert", e);
        }
    }

    private static List<ServiceEndpoint> endpointsFor(final int i) {
        if (i % 2 == 0) {
            return List.of(asServiceEndpoint("127.0.0." + (i * 2 + 1) + ":" + (80 + i)));
        } else {
            return List.of(asDnsServiceEndpoint("host" + i + ":" + (80 + i)));
        }
    }

    private static String toJson(@NonNull final Map<Long, Key> nodeAdminKeys) {
        final var mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(nodeAdminKeys.entrySet().stream()
                    .collect(toMap(
                            entry -> entry.getKey().toString(),
                            entry -> CommonUtils.hex(
                                    entry.getValue().getEd25519().toByteArray()))));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize node admin keys", e);
        }
    }
}
