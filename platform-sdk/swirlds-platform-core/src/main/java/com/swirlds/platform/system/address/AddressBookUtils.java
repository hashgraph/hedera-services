/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.address;

import static com.swirlds.platform.util.BootstrapUtils.detectSoftwareUpgrade;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.formatting.TextTable;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.roster.RosterRetriever;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.address.AddressBookInitializer;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A utility class for AddressBook functionality.
 * <p>
 * Each line in the config.txt address book contains the following comma separated elements:
 * <ul>
 *     <li>the keyword "address"</li>
 *     <li>node id</li>
 *     <li>nickname</li>
 *     <li>self name</li>
 *     <li>weight</li>
 *     <li>internal IP address</li>
 *     <li>internal port</li>
 *     <li>external IP address</li>
 *     <li>external port</li>
 *     <li>memo field (optional)</li>
 * </ul>
 * Example: `address, 22, node22, node22, 1, 10.10.11.12, 5060, 212.25.36.123, 5060, memo for node 22`
 */
public class AddressBookUtils {

    public static final String ADDRESS_KEYWORD = "address";
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");

    private AddressBookUtils() {}

    /**
     * Serializes an AddressBook to text in the form used by config.txt.
     *
     * @param addressBook the address book to serialize.
     * @return the config.txt compatible text representation of the address book.
     */
    @NonNull
    public static String addressBookConfigText(@NonNull final AddressBook addressBook) {
        Objects.requireNonNull(addressBook, "The addressBook must not be null.");
        final TextTable table = new TextTable().setBordersEnabled(false);
        for (final Address address : addressBook) {
            final String memo = address.getMemo();
            final boolean hasMemo = !memo.trim().isEmpty();
            final boolean hasInternalIpv4 = address.getHostnameInternal() != null;
            final boolean hasExternalIpv4 = address.getHostnameExternal() != null;
            table.addRow(
                    "address,",
                    address.getNodeId() + ",",
                    address.getNickname() + ",",
                    address.getSelfName() + ",",
                    address.getWeight() + ",",
                    (hasInternalIpv4 ? address.getHostnameInternal() : "") + ",",
                    address.getPortInternal() + ",",
                    (hasExternalIpv4 ? address.getHostnameExternal() : "") + ",",
                    address.getPortExternal() + (hasMemo ? "," : ""),
                    memo);
        }
        return table.render();
    }

    /**
     * Parses an address book from text in the form described by config.txt.  Comments are ignored.
     *
     * @param addressBookText the config.txt compatible serialized address book to parse.
     * @return a parsed AddressBook.
     * @throws ParseException if any Address throws a ParseException when being parsed.
     */
    @NonNull
    public static AddressBook parseAddressBookText(@NonNull final String addressBookText) throws ParseException {
        Objects.requireNonNull(addressBookText, "The addressBookText must not be null.");
        final AddressBook addressBook = new AddressBook();
        for (final String line : addressBookText.split("\\r?\\n")) {
            final String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()
                    || trimmedLine.startsWith("#")
                    || trimmedLine.startsWith("swirld")
                    || trimmedLine.startsWith("app")) {
                continue;
            }
            if (trimmedLine.startsWith(ADDRESS_KEYWORD)) {
                final Address address = parseAddressText(trimmedLine);
                if (address != null) {
                    addressBook.add(address);
                }
            } else if (trimmedLine.startsWith("nextNodeId")) {
                // As of release 0.56, nextNodeId is not used and ignored.
                // CI/CD pipelines need to be updated to remove this field from files.
                // Future Work: remove this case and hard fail when nextNodeId is no longer present in CI/CD pipelines.
            } else {
                throw new ParseException(
                        "The line [%s] does not start with `%s`."
                                .formatted(line.substring(0, Math.min(line.length(), 30)), ADDRESS_KEYWORD),
                        0);
            }
        }
        return addressBook;
    }

    /**
     * Parse an address from a single line of text, if it exists.  Address lines may have comments which start with the
     * `#` character.  Comments are ignored.  Lines which are just comments return null.  If there is content prior to a
     * `#` character, parsing the address is attempted.  Any failure to generate an address will result in throwing a
     * parse exception.  The address parts are comma separated.   The format of text addresses prevent the use of `#`
     * and `,` characters in any of the text based fields, including the memo field.
     *
     * @param addressText the text to parse.
     * @return the parsed address or null if the line is a comment.
     * @throws ParseException if there is any problem with parsing the address.
     */
    @Nullable
    public static Address parseAddressText(@NonNull final String addressText) throws ParseException {
        Objects.requireNonNull(addressText, "The addressText must not be null.");
        // lines may have comments which start with the first # character.
        final String[] textAndComment = addressText.split("#");
        if (textAndComment.length == 0
                || textAndComment[0] == null
                || textAndComment[0].trim().isEmpty()) {
            return null;
        }
        final String[] parts = addressText.split(",");
        if (parts.length < 9 || parts.length > 10) {
            throw new ParseException("Incorrect number of parts in the address line to parse correctly.", parts.length);
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }
        if (!parts[0].equals(ADDRESS_KEYWORD)) {
            throw new ParseException("The address line must start with 'address' and not '" + parts[0] + "'", 0);
        }
        final NodeId nodeId;
        try {
            nodeId = NodeId.of(Long.parseLong(parts[1]));
        } catch (final Exception e) {
            throw new ParseException("Cannot parse node id from '" + parts[1] + "'", 1);
        }
        final String nickname = parts[2];
        final String selfname = parts[3];
        final long weight;
        try {
            weight = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse value of weight from '" + parts[4] + "'", 4);
        }
        // FQDN Support: The original string value is preserved, whether it is an IP Address or a FQDN.
        final String internalHostname = parts[5];
        final int internalPort;
        try {
            internalPort = Integer.parseInt(parts[6]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse ip port from '" + parts[6] + "'", 6);
        }
        // FQDN Support: The original string value is preserved, whether it is an IP Address or a FQDN.
        final String externalHostname = parts[7];
        final int externalPort;
        try {
            externalPort = Integer.parseInt(parts[8]);
        } catch (NumberFormatException e) {
            throw new ParseException("Cannot parse ip port from '" + parts[8] + "'", 8);
        }
        final String memoToUse = parts.length == 10 ? parts[9] : "";

        return new Address(
                nodeId,
                nickname,
                selfname,
                weight,
                internalHostname,
                internalPort,
                externalHostname,
                externalPort,
                null,
                null,
                memoToUse);
    }

    /**
     * Verifies that all addresses and the nextNodeId are the same between the two address books, otherwise an
     * IllegalStateException is thrown.  All other fields in the address book are intentionally ignored. This comparison
     * is used during reconnect to verify that the address books align enough to proceed.
     *
     * @param addressBook1 the first address book to compare.
     * @param addressBook2 the second address book to compare.
     * @throws IllegalStateException if the address books are not compatible for reconnect.
     */
    public static void verifyReconnectAddressBooks(
            @NonNull final AddressBook addressBook1, @NonNull final AddressBook addressBook2)
            throws IllegalStateException {
        final int addressCount = addressBook1.getSize();
        if (addressCount != addressBook2.getSize()) {
            throw new IllegalStateException("The address books do not have the same number of addresses.");
        }
        for (int i = 0; i < addressCount; i++) {
            final NodeId nodeId1 = addressBook1.getNodeId(i);
            final NodeId nodeId2 = addressBook2.getNodeId(i);
            if (!nodeId1.equals(nodeId2)) {
                throw new IllegalStateException("The address books do not have the same node ids.");
            }
            final Address address1 = addressBook1.getAddress(nodeId1);
            final Address address2 = addressBook2.getAddress(nodeId2);

            // With a switch from AddressBook to Roster, only a subset of fields in Address are truly comparable
            // because the AddressBook instance that the PlatformBuilder passes to the reconnect classes is built
            // from a Roster which is missing certain fields (custom names, memos, etc.)
            // When the AB to Roster refactoring is complete, and specifically when the reconnect code migrates
            // to using rosters, this method will be replaced with the one comparing the Rosters directly.
            // For now, we're modifying the implementation here to only compare the fields in Address that are present
            // in the Roster.
            final RosterEntry rosterEntry1 = RosterRetriever.buildRosterEntry(address1);
            final RosterEntry rosterEntry2 = RosterRetriever.buildRosterEntry(address2);
            if (!rosterEntry1.equals(rosterEntry2)) {
                throw new IllegalStateException("The address books do not have the same addresses.");
            }
        }
    }

    /**
     * Given a host and port, creates a {@link ServiceEndpoint} object with either an IP address or domain name
     * depending on the given host.
     *
     * @param host the host
     * @param port the port
     * @return the {@link ServiceEndpoint} object
     */
    public static ServiceEndpoint endpointFor(@NonNull final String host, final int port) {
        final var builder = ServiceEndpoint.newBuilder().port(port);
        if (IPV4_ADDRESS_PATTERN.matcher(host).matches()) {
            final var octets = host.split("[.]");
            builder.ipAddressV4(Bytes.wrap(new byte[] {
                (byte) Integer.parseInt(octets[0]),
                (byte) Integer.parseInt(octets[1]),
                (byte) Integer.parseInt(octets[2]),
                (byte) Integer.parseInt(octets[3])
            }));
        } else {
            builder.domainName(host);
        }
        return builder.build();
    }

    /**
     * Extracts the internal and external service endpoints from the given address. If the internal or external
     * service endpoint is null, it is not added to the list of service endpoints. Since the {@link Address} object
     * does not differentiate between IP addresses and domain names, the corresponding conversion of a hostname to
     * its appropriate IP or domain name is also handled.
     * <p>
     * NOTE: this method assumes that the internal service endpoint is at index 0 and the external service endpoint
     * is at index 1 per address book semantics. This order is maintained in the returned data.
     * @param address the address to extract the service endpoints from
     * @return a list of (non-null) service endpoints, with the internal service endpoint remaining at index 0 and the
     * external service endpoint remaining at index 1.
     */
    public static List<ServiceEndpoint> endpointsFor(@NonNull final Address address) {
        requireNonNull(address);
        final List<ServiceEndpoint> serviceEndpoints = new ArrayList<>(2);
        if (address.getHostnameInternal() != null) {
            serviceEndpoints.add(endpointFor(address.getHostnameInternal(), address.getPortInternal()));
        }
        if (address.getHostnameExternal() != null) {
            serviceEndpoints.add(endpointFor(address.getHostnameExternal(), address.getPortExternal()));
        }

        return serviceEndpoints;
    }

    /**
     * Initializes the address book from the configuration and platform saved state.
     *
     * @param selfId               the node ID of the current node
     * @param version              the software version of the current node
     * @param initialState         the initial state of the platform
     * @param bootstrapAddressBook the bootstrap address book
     * @param platformContext      the platform context
     * @return the initialized address book
     */
    public static @NonNull AddressBook initializeAddressBook(
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion version,
            @NonNull final ReservedSignedState initialState,
            @NonNull final AddressBook bootstrapAddressBook,
            @NonNull final PlatformContext platformContext) {
        final boolean softwareUpgrade = detectSoftwareUpgrade(version, initialState.get());
        // Initialize the address book from the configuration and platform saved state.
        final AddressBookInitializer addressBookInitializer = new AddressBookInitializer(
                selfId, version, softwareUpgrade, initialState.get(), bootstrapAddressBook.copy(), platformContext);

        final boolean useRosterLifecycle = platformContext
                .getConfiguration()
                .getConfigData(AddressBookConfig.class)
                .useRosterLifecycle();
        if (!useRosterLifecycle && addressBookInitializer.hasAddressBookChanged()) {
            final MerkleRoot state = initialState.get().getState();
            // Update the address book with the current address book read from config.txt.
            // Eventually we will not do this, and only transactions will be capable of
            // modifying the address book.
            final PlatformStateModifier platformState = state.getWritablePlatformState();
            platformState.bulkUpdate(v -> {
                v.setAddressBook(addressBookInitializer.getCurrentAddressBook().copy());
                v.setPreviousAddressBook(
                        addressBookInitializer.getPreviousAddressBook() == null
                                ? null
                                : addressBookInitializer
                                        .getPreviousAddressBook()
                                        .copy());
            });
        }

        // At this point the initial state must have the current address book set.  If not, something is wrong.
        final AddressBook addressBook =
                initialState.get().getState().getReadablePlatformState().getAddressBook();
        if (addressBook == null) {
            throw new IllegalStateException("The current address book of the initial state is null.");
        }
        return addressBook;
    }

    /**
     * Format a "consensusEventStreamName" using the "memo" field from the self-Address.
     *
     * !!! IMPORTANT !!!: It's imperative to retain the logic that is based on the current content of the "memo" field,
     * even if the code is updated to source the content of "memo" from another place. The "consensusEventStreamName" is used
     * as a directory name to save some files on disk, and the directory name should remain unchanged for now.
     * <p>
     * Per @lpetrovic05 : "As far as I know, CES isn't really used for anything.
     * It is however, uploaded to google storage, so maybe the name change might affect the uploader."
     * <p>
     * This logic could and should eventually change to use the nodeId only (see the else{} branch below.)
     * However, this change needs to be coordinated with DevOps and NodeOps to ensure the data continues to be uploaded.
     * Replacing the directory and starting with an empty one may or may not affect the DefaultConsensusEventStream
     * which will need to be tested when this change takes place.
     *
     * @param addressBook an AddressBook
     * @param selfId a NodeId for self
     * @return consensusEventStreamName
     */
    @NonNull
    public static String formatConsensusEventStreamName(
            @NonNull final AddressBook addressBook, @NonNull final NodeId selfId) {
        // !!!!! IMPORTANT !!!!! Read the javadoc above and the comment below before modifying this code.
        // Required for conformity with legacy behavior. This sort of funky logic is normally something
        // we'd try to move away from, but since we will be removing the CES entirely, it's simpler
        // to just wait until the entire component disappears.
        final Address address = addressBook.getAddress(selfId);
        if (!address.getMemo().isEmpty()) {
            return address.getMemo();
        } else {
            return String.valueOf(selfId);
        }
    }

    /**
     * Extracts the bytes of the given signing certificate. If the certificate is null, an empty byte
     * array is returned.
     *
     * @param signingCert the signing certificate
     * @return the bytes of the signing certificate wrapped in a {@link Bytes} object
     */
    public static Bytes extractSigCertBytes(@Nullable final X509Certificate signingCert) {
        Bytes signingCertificateBytes;
        try {
            signingCertificateBytes = signingCert == null ? Bytes.EMPTY : Bytes.wrap(signingCert.getEncoded());
        } catch (final CertificateEncodingException e) {
            signingCertificateBytes = Bytes.EMPTY;
        }

        return signingCertificateBytes;
    }
}
