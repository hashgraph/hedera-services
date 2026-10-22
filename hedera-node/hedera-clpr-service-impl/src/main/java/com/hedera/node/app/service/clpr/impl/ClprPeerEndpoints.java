// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.clpr.impl;

import static com.hedera.pbj.runtime.ProtoWriterTools.sizeOfUnsignedVarInt32;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.clpr.ClprEndpoint;
import com.hedera.hapi.node.state.clpr.ClprEndpointManifest;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Rules for which peer endpoints this ledger stores on a Channel and dials.
 *
 * <p>A peer's endpoint manifest reaches this ledger through the Channel's verifier contract, which whoever completes
 * the Channel chooses. Nothing in it is known to describe a real peer node, yet every node dials the stored endpoints
 * on each sync tick. An endpoint aimed at loopback, an internal network or a cloud metadata service would have the
 * network connect to services it should never reach, and an oversized endpoint would grow state. These rules keep
 * only endpoints with a sane address, port and size.
 */
public final class ClprPeerEndpoints {
    /** Largest TLS certificate kept on an endpoint; a single DER-encoded CA certificate is well under this. */
    public static final int MAX_TLS_CERTIFICATE_BYTES = 4096;

    /** Largest account id kept on an endpoint; a serialized {@code AccountID} or EVM address is well under this. */
    public static final int MAX_ACCOUNT_ID_BYTES = 64;

    /** Longest textual IP address: a fully written IPv6 address with an embedded IPv4 suffix. */
    private static final int MAX_IP_LITERAL_LENGTH = 45;

    /** Longest DNS name; bounds the host when non-public endpoints are allowed. */
    private static final int MAX_HOST_LENGTH = 253;

    private static final int MAX_PORT = 65_535;

    /** Wire size of the tag of {@code ClprEndpointManifest.endpoints} (field 3, length-delimited). */
    private static final int ENDPOINTS_TAG_BYTES = 1;

    private ClprPeerEndpoints() {}

    /**
     * Returns the manifest to store for a peer: unusable endpoints removed, the rest truncated to
     * {@code maxPeerEndpoints}, and then any endpoints that would take the serialized manifest past
     * {@code maxManifestBytes} dropped in declared order.
     *
     * @param manifest the peer's verified manifest
     * @param publicOnly whether endpoints must have a publicly routable IP literal address
     * @param maxPeerEndpoints this ledger's {@code max_peer_endpoints}; zero means no limit
     * @param maxManifestBytes the largest serialized manifest to store
     * @return the manifest to store, or {@code null} if it is larger than {@code maxManifestBytes} with no endpoints
     */
    @Nullable
    public static ClprEndpointManifest boundedManifest(
            @NonNull final ClprEndpointManifest manifest,
            final boolean publicOnly,
            final int maxPeerEndpoints,
            final int maxManifestBytes) {
        requireNonNull(manifest);
        final var usable = usableOnly(manifest.endpoints(), publicOnly);
        final var truncated =
                maxPeerEndpoints > 0 && usable.size() > maxPeerEndpoints ? usable.subList(0, maxPeerEndpoints) : usable;
        final var withoutEndpoints = manifest.copyBuilder().endpoints(List.of()).build();
        long size = ClprEndpointManifest.PROTOBUF.measureRecord(withoutEndpoints);
        if (size > maxManifestBytes) {
            return null;
        }
        int kept = 0;
        for (final var endpoint : truncated) {
            final int endpointSize = ClprEndpoint.PROTOBUF.measureRecord(endpoint);
            size += ENDPOINTS_TAG_BYTES + sizeOfUnsignedVarInt32(endpointSize) + endpointSize;
            if (size > maxManifestBytes) {
                break;
            }
            kept++;
        }
        return manifest.copyBuilder().endpoints(truncated.subList(0, kept)).build();
    }

    /**
     * Returns the endpoints that pass {@link #isUsable}, in their original order.
     *
     * @param endpoints the endpoints to filter
     * @param publicOnly whether endpoints must have a publicly routable IP literal address
     * @return the usable endpoints
     */
    @NonNull
    public static List<ClprEndpoint> usableOnly(@NonNull final List<ClprEndpoint> endpoints, final boolean publicOnly) {
        requireNonNull(endpoints);
        final var usable = new ArrayList<ClprEndpoint>(endpoints.size());
        for (final var endpoint : endpoints) {
            if (isUsable(endpoint, publicOnly)) {
                usable.add(endpoint);
            }
        }
        return usable;
    }

    /**
     * Returns whether this ledger may store and dial the given endpoint. It must have a service endpoint with a port
     * in {@code [1, 65535]}, a TLS certificate and account id within their size limits, and an address that is a
     * publicly routable IPv4 or IPv6 literal. When {@code publicOnly} is false any non-empty host is accepted instead.
     *
     * @param endpoint the endpoint to check
     * @param publicOnly whether the address must be a publicly routable IP literal
     * @return whether the endpoint is usable
     */
    public static boolean isUsable(@NonNull final ClprEndpoint endpoint, final boolean publicOnly) {
        requireNonNull(endpoint);
        final var serviceEndpoint = endpoint.serviceEndpoint();
        if (serviceEndpoint == null
                || serviceEndpoint.port() < 1
                || serviceEndpoint.port() > MAX_PORT
                || endpoint.tlsCertificate().length() > MAX_TLS_CERTIFICATE_BYTES
                || endpoint.accountId().length() > MAX_ACCOUNT_ID_BYTES) {
            return false;
        }
        final var host = serviceEndpoint.ipAddress();
        if (!publicOnly) {
            return !host.isEmpty() && host.length() <= MAX_HOST_LENGTH;
        }
        final var address = parseIpLiteral(host);
        return address != null && isPublic(address);
    }

    /**
     * Parses an IPv4 or IPv6 literal without any name lookup. IPv4 must be in canonical dotted-quad form, so that
     * shorthand such as {@code 127.1} or an IPv4-mapped IPv6 literal cannot disguise the real address.
     *
     * @param host the text to parse
     * @return the address, or {@code null} if {@code host} is not an accepted IP literal
     */
    @Nullable
    static InetAddress parseIpLiteral(@NonNull final String host) {
        // A zone id names an interface on this host, and brackets are URI syntax; neither belongs in a peer address.
        if (host.isEmpty()
                || host.length() > MAX_IP_LITERAL_LENGTH
                || host.indexOf('%') >= 0
                || host.indexOf('[') >= 0) {
            return null;
        }
        final InetAddress address;
        try {
            address = InetAddress.ofLiteral(host);
        } catch (final IllegalArgumentException e) {
            return null;
        }
        if (address instanceof Inet4Address && !address.getHostAddress().equals(host)) {
            return null;
        }
        return address;
    }

    /**
     * Returns whether the address is publicly routable: not wildcard, loopback, link-local (which includes the
     * {@code 169.254.169.254} cloud metadata service), private, multicast, carrier-grade NAT, or reserved.
     *
     * @param address the address to check
     * @return whether the address is public
     */
    static boolean isPublic(@NonNull final InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        final byte[] bytes = address.getAddress();
        final int first = bytes[0] & 0xff;
        if (address instanceof Inet6Address inet6Address) {
            // isSiteLocalAddress() only covers the deprecated fec0::/10, so check unique-local fc00::/7 here
            return (first & 0xfe) != 0xfc && !inet6Address.isIPv4CompatibleAddress();
        }
        final int second = bytes[1] & 0xff;
        // 0.0.0.0/8 ("this network"), 100.64.0.0/10 (carrier-grade NAT), 240.0.0.0/4 (reserved and broadcast)
        return first != 0 && !(first == 100 && (second & 0xc0) == 64) && first < 240;
    }
}
