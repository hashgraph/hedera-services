// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.clpr.impl.test;

import static com.hedera.node.app.service.clpr.impl.ClprPeerEndpoints.MAX_ACCOUNT_ID_BYTES;
import static com.hedera.node.app.service.clpr.impl.ClprPeerEndpoints.MAX_TLS_CERTIFICATE_BYTES;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.state.clpr.ClprEndpoint;
import com.hedera.hapi.node.state.clpr.ClprEndpointManifest;
import com.hedera.hapi.node.state.clpr.ClprServiceEndpoint;
import com.hedera.node.app.service.clpr.impl.ClprPeerEndpoints;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClprPeerEndpointsTest {
    private static final int PORT = 50211;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "8.8.8.8",
                "203.0.113.7",
                "100.63.255.255",
                "100.128.0.1",
                "172.15.255.255",
                "172.32.0.1",
                "223.255.255.254",
                "2001:4860:4860::8888",
                "2606:4700:4700::1111"
            })
    void acceptsPublicIpLiterals(final String host) {
        assertThat(ClprPeerEndpoints.isUsable(endpoint(host, PORT), true)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // loopback, wildcard and "this network"
                "127.0.0.1",
                "127.255.255.254",
                "0.0.0.0",
                "0.1.2.3",
                "::1",
                "::",
                // private and carrier-grade NAT
                "10.1.2.3",
                "172.16.0.1",
                "172.31.255.255",
                "192.168.1.1",
                "100.64.0.1",
                "100.127.255.255",
                "fc00::1",
                "fd12:3456:789a::1",
                "fec0::1",
                // link-local, including the cloud metadata service
                "169.254.169.254",
                "fe80::1",
                // multicast, reserved and broadcast
                "224.0.0.1",
                "239.255.255.250",
                "240.0.0.1",
                "255.255.255.255",
                "ff02::1",
                // IPv4 inside IPv6 forms
                "::ffff:127.0.0.1",
                "::ffff:8.8.8.8",
                "::127.0.0.1",
                // non-canonical IPv4 spellings
                "127.1",
                "2130706433",
                "010.0.0.1",
                "8.8.8.08",
                // not IP literals
                "localhost",
                "peer.example.com",
                "",
                " 8.8.8.8",
                "8.8.8.8 ",
                "1.2.3.4.5",
                "fe80::1%eth0",
                "[2001:4860:4860::8888]"
            })
    void rejectsAddressesThatAreNotPublicIpLiterals(final String host) {
        assertThat(ClprPeerEndpoints.isUsable(endpoint(host, PORT), true)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65_536, Integer.MAX_VALUE})
    void rejectsPortsOutOfRange(final int port) {
        assertThat(ClprPeerEndpoints.isUsable(endpoint("8.8.8.8", port), true)).isFalse();
        assertThat(ClprPeerEndpoints.isUsable(endpoint("127.0.0.1", port), false))
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 65_535})
    void acceptsPortsAtTheBounds(final int port) {
        assertThat(ClprPeerEndpoints.isUsable(endpoint("8.8.8.8", port), true)).isTrue();
    }

    @Test
    void rejectsEndpointWithoutServiceEndpoint() {
        assertThat(ClprPeerEndpoints.isUsable(ClprEndpoint.DEFAULT, true)).isFalse();
        assertThat(ClprPeerEndpoints.isUsable(ClprEndpoint.DEFAULT, false)).isFalse();
    }

    @Test
    void boundsTlsCertificateAndAccountIdSizes() {
        final var base = endpoint("8.8.8.8", PORT);
        assertThat(ClprPeerEndpoints.isUsable(
                        base.copyBuilder()
                                .tlsCertificate(Bytes.wrap(new byte[MAX_TLS_CERTIFICATE_BYTES]))
                                .accountId(Bytes.wrap(new byte[MAX_ACCOUNT_ID_BYTES]))
                                .build(),
                        true))
                .isTrue();
        assertThat(ClprPeerEndpoints.isUsable(
                        base.copyBuilder()
                                .tlsCertificate(Bytes.wrap(new byte[MAX_TLS_CERTIFICATE_BYTES + 1]))
                                .build(),
                        true))
                .isFalse();
        assertThat(ClprPeerEndpoints.isUsable(
                        base.copyBuilder()
                                .accountId(Bytes.wrap(new byte[MAX_ACCOUNT_ID_BYTES + 1]))
                                .build(),
                        true))
                .isFalse();
    }

    @Test
    void acceptsAnyBoundedHostWhenNotPublicOnly() {
        assertThat(ClprPeerEndpoints.isUsable(endpoint("127.0.0.1", PORT), false))
                .isTrue();
        assertThat(ClprPeerEndpoints.isUsable(endpoint("10.0.0.1", PORT), false))
                .isTrue();
        assertThat(ClprPeerEndpoints.isUsable(endpoint("network-node1-svc.solo.svc.cluster.local", PORT), false))
                .isTrue();
        assertThat(ClprPeerEndpoints.isUsable(endpoint("", PORT), false)).isFalse();
        assertThat(ClprPeerEndpoints.isUsable(endpoint("a".repeat(254), PORT), false))
                .isFalse();
    }

    @Test
    void usableOnlyKeepsOrder() {
        final var first = endpoint("8.8.8.8", PORT);
        final var second = endpoint("1.1.1.1", PORT);
        assertThat(ClprPeerEndpoints.usableOnly(
                        List.of(endpoint("127.0.0.1", PORT), first, endpoint("10.0.0.1", PORT), second), true))
                .containsExactly(first, second);
    }

    @Test
    void boundedManifestDropsUnusableEndpointsBeforeTruncating() {
        final var first = endpoint("203.0.113.1", PORT);
        final var second = endpoint("203.0.113.2", PORT);
        final var manifest = manifest(List.of(
                endpoint("127.0.0.1", PORT),
                first,
                endpoint("169.254.169.254", PORT),
                second,
                endpoint("203.0.113.3", PORT)));

        final var bounded = ClprPeerEndpoints.boundedManifest(manifest, true, 2, Integer.MAX_VALUE);

        assertThat(bounded).isNotNull();
        assertThat(bounded.version()).isEqualTo(manifest.version());
        assertThat(bounded.serviceAddress()).isEqualTo(manifest.serviceAddress());
        assertThat(bounded.endpoints()).containsExactly(first, second);
    }

    @Test
    void boundedManifestKeepsAllUsableEndpointsWhenMaxPeerEndpointsIsZero() {
        final var endpoints = publicEndpoints(12, 0);
        final var bounded = ClprPeerEndpoints.boundedManifest(manifest(endpoints), true, 0, Integer.MAX_VALUE);

        assertThat(bounded).isNotNull();
        assertThat(bounded.endpoints()).containsExactlyElementsOf(endpoints);
    }

    @Test
    void boundedManifestDropsEndpointsPastTheByteBudget() {
        final var endpoints = publicEndpoints(5, 1_000);
        final var fullManifest = manifest(endpoints);
        final int sizeWithThree = ClprEndpointManifest.PROTOBUF.measureRecord(
                fullManifest.copyBuilder().endpoints(endpoints.subList(0, 3)).build());

        final var atBudget = ClprPeerEndpoints.boundedManifest(fullManifest, true, 0, sizeWithThree);
        final var underBudget = ClprPeerEndpoints.boundedManifest(fullManifest, true, 0, sizeWithThree - 1);

        assertThat(atBudget).isNotNull();
        assertThat(atBudget.endpoints()).containsExactlyElementsOf(endpoints.subList(0, 3));
        assertThat(ClprEndpointManifest.PROTOBUF.measureRecord(atBudget)).isEqualTo(sizeWithThree);
        assertThat(underBudget).isNotNull();
        assertThat(underBudget.endpoints()).containsExactlyElementsOf(endpoints.subList(0, 2));
    }

    @Test
    void boundedManifestIsNullWhenTooLargeWithoutEndpoints() {
        final var manifest = manifest(publicEndpoints(1, 0))
                .copyBuilder()
                .serviceAddress(Bytes.wrap(new byte[1_000]))
                .build();

        assertThat(ClprPeerEndpoints.boundedManifest(manifest, true, 10, 512)).isNull();
    }

    private static ClprEndpoint endpoint(final String host, final int port) {
        return ClprEndpoint.newBuilder()
                .serviceEndpoint(ClprServiceEndpoint.newBuilder()
                        .ipAddress(host)
                        .port(port)
                        .build())
                .build();
    }

    private static List<ClprEndpoint> publicEndpoints(final int count, final int certificateBytes) {
        final var endpoints = new ArrayList<ClprEndpoint>();
        for (int i = 0; i < count; i++) {
            endpoints.add(endpoint("203.0.113." + (i + 1), PORT)
                    .copyBuilder()
                    .tlsCertificate(Bytes.wrap(new byte[certificateBytes]))
                    .build());
        }
        return endpoints;
    }

    private static ClprEndpointManifest manifest(final List<ClprEndpoint> endpoints) {
        return ClprEndpointManifest.newBuilder()
                .version(4L)
                .serviceAddress(Bytes.wrap(new byte[20]))
                .endpoints(endpoints)
                .build();
    }
}
