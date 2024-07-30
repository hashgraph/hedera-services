package com.hedera.services.yahcli.commands.nodes;


import static com.hedera.hapi.node.base.ResponseCodeEnum.FQDN_SIZE_TOO_LARGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.GOSSIP_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_IPV4_ADDRESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_DESCRIPTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SERVICE_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SERVICE_ENDPOINTS_EXCEEDED_LIMIT;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;
import static com.hedera.services.yahcli.suites.CreateSuite.NOVELTY;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.UpdateNodeSuite;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import picocli.CommandLine;

@CommandLine.Command(
        name = "update",
        subcommands = {CommandLine.HelpCommand.class},
        description = "Update an existing node")
public class UpdateCommand  implements Callable<Integer> {
    private static final int DEFAULT_NUM_RETRIES = 5;
    private static final Pattern IPV4_ADDRESS_PATTERN =
            Pattern.compile("^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");

    @CommandLine.ParentCommand
    NodesCommand nodesCommand;

    @CommandLine.Option(
            names = {"-n", "--nodeId"},
            paramLabel = "node Id for update")
    String nodeId;

    @CommandLine.Option(
            names = {"-a", "--accountId"},
            paramLabel = "account id")
    String accountId;

    @CommandLine.Option(
            names = {"-aa", "--accountAlias"},
            paramLabel = "account alias")
    String accountAlias;

    @CommandLine.Option(
            names = {"-d", "--description"},
            paramLabel = "description")
    String description;

    @CommandLine.Option(
            names = {"-g", "--gossipEndPoints"},
            paramLabel = "gossip end points id ; delimited")
    String gossipEndPoint;

    @CommandLine.Option(
            names = {"-s", "--serviceEndPoints"},
            paramLabel = "service end points id ; delimited")
    String serviceEndPoints;

    @CommandLine.Option(
            names = {"-gc", "--gossipCaCertificatePath"},
            paramLabel = "path to the gossip CA certificate path")
    String gossipCaCertificatePath;

    @CommandLine.Option(
            names = {"-gr", "--grpcCertificateHash"},
            paramLabel = "grpc certificate hash path")
    String grpcCertificateHashPath;

    @CommandLine.Option(
            names = {"-k", "--adminKeyPath"},
            paramLabel = "adminKey path")
    String adminKeyPath;

    @CommandLine.Option(
            names = {"-r", "--retries"},
            paramLabel = "Number of times to retry on BUSY")
    Integer boxedRetries;

    @Override
    public Integer call() throws Exception {
        final var yahcli = nodesCommand.getYahcli();
        var config = ConfigUtils.configFrom(yahcli);

        final var noveltyLoc = config.keysLoc() + File.separator + NOVELTY + ".pem";
        final var effectiveNodeId = nodeId != null ? nodeId : "";
        final var effectiveAccountId = accountId != null ? accountId : "";
        final var effectiveAccountAlias = accountAlias != null ? accountAlias : "";
        var effectiveDescription = "";
        if(description != null) {
            validateDescription(description);
            effectiveDescription = description;
        }
        final var effectiveGossipEndPoint = gossipEndPoint != null ? gossipEndPoint : "";
        final List<ServiceEndpoint> gossipEndPoints = convertStringDelimitedToServiceEndPoints(effectiveGossipEndPoint, ";");
        validateGossipEndpoint(gossipEndPoints);
        final var effectiveServiceEndPoints = serviceEndPoints != null ? serviceEndPoints : "";
        final List<ServiceEndpoint> serviceEndpoints = convertStringDelimitedToServiceEndPoints(effectiveServiceEndPoints, ";");
        validateServiceEndpoint(serviceEndpoints);

        final var effectiveGossipCaCertificatePath = gossipCaCertificatePath != null ? gossipCaCertificatePath : "";
        final byte[] finalGossipCaCertificate = readCertificate(effectiveGossipCaCertificatePath);
        final var effectiveGrpcCertificateHashPath = grpcCertificateHashPath != null ? grpcCertificateHashPath : "";
        final byte[] finalGrpcCertificateHash = readCertificate(effectiveGrpcCertificateHashPath);

        final var effectiveAdminKeyPath = adminKeyPath != null ? adminKeyPath : "";
        final var effectivePublicKeys = unHexListOfKeys(readPublicKeyFromFile(effectiveAdminKeyPath));

        final var retries = boxedRetries != null ? boxedRetries.intValue() : DEFAULT_NUM_RETRIES;

        final var delegate = new UpdateNodeSuite(
                config.asSpecConfig(), nodeId, effectiveAccountId, effectiveDescription, gossipEndPoints, serviceEndpoints, finalGossipCaCertificate, finalGrpcCertificateHash, effectivePublicKeys, noveltyLoc, retries);
        delegate.runSuiteSync();

        if (delegate.getFinalSpecs().get(0).getStatus() == HapiSpec.SpecStatus.PASSED) {
            COMMON_MESSAGES.info("SUCCESS - node Id "
                    + effectiveNodeId
                    + " has been updated");
        } else {
            COMMON_MESSAGES.warn("FAILED to update node Id "
                    + effectiveNodeId);
            return 1;
        }

        return 0;
    }

    public void validateGossipEndpoint(
            @Nullable final List<ServiceEndpoint> endpointList) {
        validateFalse(endpointList == null || endpointList.isEmpty(), INVALID_GOSSIP_ENDPOINT);
        validateFalse(endpointList.size() > 10, GOSSIP_ENDPOINTS_EXCEEDED_LIMIT);
        // for phase 2: The first in the list is used as the Internal IP address in config.txt,
        // the second in the list is used as the External IP address in config.txt
        validateFalse(endpointList.size() < 2, INVALID_GOSSIP_ENDPOINT);

        for (final var endpoint : endpointList) {
            validateFalse(true && !endpoint.domainName().isEmpty(),
                    GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN);
            validateEndpoint(endpoint);
        }
    }

    public void validateServiceEndpoint(
            @Nullable final List<ServiceEndpoint> endpointList) {

        validateFalse(endpointList == null || endpointList.isEmpty(), INVALID_SERVICE_ENDPOINT);
        validateFalse(endpointList.size() > 8, SERVICE_ENDPOINTS_EXCEEDED_LIMIT);
        for (final var endpoint : endpointList) {
            validateEndpoint(endpoint);
        }
    }

    private void validateEndpoint(@NonNull final ServiceEndpoint endpoint) {
        requireNonNull(endpoint);

        validateFalse(endpoint.port() == 0, INVALID_ENDPOINT);
        final var addressLen = endpoint.ipAddressV4().length();
        validateFalse(addressLen == 0 && endpoint.domainName().trim().isEmpty(), INVALID_ENDPOINT);
        validateFalse(
                addressLen != 0 && !endpoint.domainName().trim().isEmpty(), IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT);
        validateFalse(endpoint.domainName().trim().length() > 253, FQDN_SIZE_TOO_LARGE);
        validateFalse(addressLen != 0 && addressLen != 4, INVALID_IPV4_ADDRESS);
    }

    private List<ServiceEndpoint>  convertStringDelimitedToServiceEndPoints(String endPoints, String delimiter){
        List<ServiceEndpoint> serviceEndPoints = new ArrayList<>();

        if (endPoints != null && !endPoints.isEmpty()) {
            for (String endPoint : endPoints.split(delimiter)) {
                String[] parts = endPoint.split(":");
                ServiceEndpoint service = endpointFor(parts[0], Integer.parseInt(parts[1]));
                serviceEndPoints.add(service);
            }
        }
        return serviceEndPoints;
    }

    private  ServiceEndpoint endpointFor(@NonNull final String host, final int port) {
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

    private byte[] readCertificate(final String filePath) throws IOException {
        File file = new File(filePath);
        return Files.toByteArray(file);
    }

    private void validateDescription(@Nullable final String description) {

        if (description == null || description.isEmpty()) {
            return;
        }
        final var raw = description.getBytes(StandardCharsets.UTF_8);
        validateFalse(raw.length > 100, INVALID_NODE_DESCRIPTION);
        validateFalse(containsZeroByte(raw), INVALID_NODE_DESCRIPTION);
    }

    private List<String> readPublicKeyFromFile(final String filePath) throws IOException {
        File file = new File(filePath);
        return Files.readLines(file, StandardCharsets.UTF_8);
    }

    private List<Key> unHexListOfKeys(final List<String> hexedKeys) {
        List<Key> unHexedKeys = Lists.newArrayList();
        for (String hexedKey : hexedKeys) {
            ByteString byteString = ByteString.copyFrom(CommonUtils.unhex(hexedKey));
            Key key = Key.newBuilder().setEd25519(byteString).build();
            unHexedKeys.add(key);
        }
        return unHexedKeys;
    }

    private boolean containsZeroByte(@NonNull final byte[] bytes) {
        requireNonNull(bytes);

        boolean ret = false;
        for (final byte b : bytes) {
            if (b == 0) {
                ret = true;
                break;
            }
        }
        return ret;
    }

}
