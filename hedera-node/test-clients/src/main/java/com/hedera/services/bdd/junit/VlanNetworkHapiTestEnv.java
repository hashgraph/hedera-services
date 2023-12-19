package com.hedera.services.bdd.junit;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class VlanNetworkHapiTestEnv extends HapiTestEnvBase {

    private static final IPAllocator VLAN_IP_ALLOC = nodeId -> String.format("10.191.%d.1", nodeId);

    private static final PortAllocator VLAN_GRPC_PORT_ALLOC = nodeId -> FIRST_GRPC_PORT + (nodeId * 2);

    private static final PortAllocator VLAN_GOSSIP_PORT_ALLOC = nodeId -> 50111;

    public VlanNetworkHapiTestEnv(@NonNull final String testName, final boolean cluster, final boolean useInProcessAlice) {
        super(VLAN_IP_ALLOC, VLAN_GOSSIP_PORT_ALLOC, VLAN_GRPC_PORT_ALLOC);
        initialize(testName, cluster, useInProcessAlice);
    }

    @Override
    protected void setupNetwork(final int nodeId, @NonNull final String nodeAddress, final int gossipPort, final int grpcPort) {
        createVlanAdapter(nodeId);
        assignNetworkAddress(nodeId, nodeAddress);
    }

    @Override
    protected void teardownNetwork(final int nodeId, @NonNull final String nodeAddress) {
        destroyVlanAdapter(nodeId);
    }

    private void createVlanAdapter(final int nodeId) {
        manipulateVlanAdapter(nodeId, "create");
    }

    private void destroyVlanAdapter(final int nodeId) {
        manipulateVlanAdapter(nodeId, "destroy");
    }

    private void manipulateVlanAdapter(final int nodeId, @NonNull final String op) {
        try {
            final ProcessBuilder pb = new ProcessBuilder();
            pb.command("/usr/bin/env", "sudo", "ifconfig", String.format("vlan10%d", nodeId), op);
            pb.inheritIO();
            final Process process = pb.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Command execution timed out while waiting for completion.");
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException(String.format("Command execution failed with an error (Exit Code: %d).", process.exitValue()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void assignNetworkAddress(final int nodeId, @NonNull String nodeAddress) {
        try {
            final ProcessBuilder pb = new ProcessBuilder();
            pb.command("/usr/bin/env", "sudo", "ifconfig", String.format("vlan10%d", nodeId), "inet", nodeAddress, "netmask", "255.255.255.0");
            pb.inheritIO();
            final Process process = pb.start();
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Command execution timed out while waiting for completion.");
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException(String.format("Command execution failed with an error (Exit Code: %d).", process.exitValue()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
