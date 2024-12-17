/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.yahcli.config;

import static com.hedera.node.app.hapi.utils.keys.Ed25519Utils.readKeyPairFrom;
import static com.hedera.services.yahcli.config.ConfigUtils.asId;
import static com.hedera.services.yahcli.config.ConfigUtils.isLiteral;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.utilops.inventory.AccessoryUtils;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottlesJsonToGrpcBytes;
import com.hedera.services.yahcli.Yahcli;
import com.hedera.services.yahcli.config.domain.GlobalConfig;
import com.hedera.services.yahcli.config.domain.NetConfig;
import com.hedera.services.yahcli.config.domain.NodeConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import picocli.CommandLine;

public class ConfigManager {
    private static final long MISSING_NODE_ACCOUNT = -1;

    private final Yahcli yahcli;
    private final GlobalConfig global;

    private String defaultPayer;
    private String defaultNodeAccount;
    private String targetName;
    private NetConfig targetNet;

    public ConfigManager(Yahcli yahcli, GlobalConfig global) {
        this.yahcli = yahcli;
        this.global = global;
    }

    static ConfigManager from(Yahcli yahcli) throws IOException {
        var yamlLoc = yahcli.getConfigLoc();
        var yamlIn = new Yaml(new Constructor(GlobalConfig.class, new LoaderOptions()));
        try (InputStream fin = Files.newInputStream(Paths.get(yamlLoc))) {
            GlobalConfig globalConfig = yamlIn.load(fin);
            return new ConfigManager(yahcli, globalConfig);
        }
    }

    public Map<String, String> asSpecConfig() {
        assertNoMissingDefaults();

        ((ThrottlesJsonToGrpcBytes) StandardSerdes.SYS_FILE_SERDES.get(123L))
                .setBelievedNetworkSize(targetNet.getNodes().size());

        var specConfig = targetNet.toSpecProperties();
        if (useFixedFee()) {
            specConfig.put("fees.fixedOffer", String.valueOf(useFixedFee() ? fixedFee() : "0"));
            specConfig.put("fees.useFixedOffer", "true");
        }
        var payerId = asId(defaultPayer);
        if (isLiteral(payerId)) {
            addPayerConfig(specConfig, payerId);
        } else {
            fail("Named accounts not yet supported!");
        }
        specConfig.put("default.node", defaultNodeAccount);
        return specConfig;
    }

    public boolean isAllowListEmptyOrContainsAccount(long account) {
        return targetNet.getAllowedReceiverAccountIds() == null
                || targetNet.getAllowedReceiverAccountIds().isEmpty()
                || targetNet.getAllowedReceiverAccountIds().contains(account);
    }

    private void addPayerConfig(Map<String, String> specConfig, String payerId) {
        specConfig.put("default.payer", payerId);
        var optKeyFile = ConfigUtils.keyFileFor(keysLoc(), "account" + defaultPayer);
        if (optKeyFile.isEmpty()) {
            fail(String.format("No key available for account %s!", payerId));
        }
        var keyFile = optKeyFile.get();
        if (keyFile.getAbsolutePath().endsWith("pem")) {
            Optional<String> finalPassphrase = getFinalPassphrase(keyFile);
            if (!isValid(keyFile, finalPassphrase)) {
                fail(String.format("No valid passphrase could be obtained for PEM %s!", keyFile.getName()));
            }
            specConfig.put("default.payer.pemKeyLoc", keyFile.getPath());
            specConfig.put("default.payer.pemKeyPassphrase", finalPassphrase.get());
        } else if (keyFile.getAbsolutePath().endsWith("words")) {
            specConfig.put("default.payer.mnemonicFile", keyFile.getAbsolutePath());
        } else {
            try {
                var key = Files.readString(keyFile.toPath()).trim();
                specConfig.put("default.payer.key", key);
            } catch (IOException e) {
                fail(String.format("Key file %s is inaccessible!", keyFile.getPath()));
            }
        }
    }

    private Optional<String> getFinalPassphrase(File keyFile) {
        String fromEnv;
        if ((fromEnv = System.getenv("YAHCLI_PASSPHRASE")) != null) {
            return Optional.of(fromEnv);
        }
        Optional<String> finalPassphrase = Optional.empty();
        var optPassFile = AccessoryUtils.passFileFor(keyFile);
        if (optPassFile.isPresent()) {
            try {
                finalPassphrase =
                        Optional.of(Files.readString(optPassFile.get().toPath()).trim());
            } catch (IOException e) {
                System.out.println(String.format(
                        "Password file inaccessible for PEM %s ('%s')!", keyFile.getName(), e.getMessage()));
            }
        }
        if (!isValid(keyFile, finalPassphrase)) {
            var prompt = "Please enter the passphrase for key file " + keyFile;
            finalPassphrase = AccessoryUtils.promptForPassphrase(keyFile.getPath(), prompt, 3);
        }
        return finalPassphrase;
    }

    public static boolean isValid(File keyFile, Optional<String> passphrase) {
        return passphrase.isPresent() && unlocks(keyFile, passphrase.get());
    }

    static boolean unlocks(File keyFile, String passphrase) {
        try {
            readKeyPairFrom(keyFile, passphrase);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    public String keysLoc() {
        return targetName + "/keys";
    }

    public boolean useFixedFee() {
        return yahcli.getFixedFee() != Yahcli.NO_FIXED_FEE;
    }

    public long fixedFee() {
        return yahcli.getFixedFee();
    }

    public int numNodesInTargetNet() {
        assertTargetNetIsKnown();
        final ConfigManager freshConfig;
        try {
            freshConfig = ConfigManager.from(yahcli);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final var baseNetwork =
                Objects.requireNonNull(freshConfig.global.getNetworks().get(targetName));
        return baseNetwork.getNodes().size();
    }

    public Set<Long> nodeIdsInTargetNet() {
        assertTargetNetIsKnown();
        final ConfigManager freshConfig;
        try {
            freshConfig = ConfigManager.from(yahcli);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final var baseNetwork =
                Objects.requireNonNull(freshConfig.global.getNetworks().get(targetName));
        return baseNetwork.getNodes().stream()
                .map(NodeConfig::getId)
                .map(i -> (long) i)
                .collect(Collectors.toSet());
    }

    public void assertNoMissingDefaults() {
        assertTargetNetIsKnown();
        assertDefaultPayerIsKnown();
        assertDefaultNodeAccountIsKnown();
    }

    private void assertDefaultNodeAccountIsKnown() {
        final var configDefault = "0.0." + targetNet.getDefaultNodeAccount();
        defaultNodeAccount = Optional.ofNullable(yahcli.getNodeAccount()).orElse(configDefault);
    }

    private void assertDefaultPayerIsKnown() {
        if (yahcli.getPayer() == null && targetNet.getDefaultPayer() == null) {
            fail(String.format(
                    "No payer was specified, and no default is available in %s for network" + " '%s'",
                    yahcli.getConfigLoc(), targetName));
        }
        defaultPayer = Optional.ofNullable(yahcli.getPayer()).orElse(targetNet.getDefaultPayer());
    }

    private void assertTargetNetIsKnown() {
        if (yahcli.getNet() == null
                && global.getDefaultNetwork() == null
                && global.getNetworks().size() != 1) {
            fail(String.format(
                    "No target network was specified, and no default from %d networks is" + " given in %s",
                    global.getNetworks().size(), yahcli.getConfigLoc()));
        }
        targetName = Optional.ofNullable(yahcli.getNet())
                .orElse(Optional.ofNullable(global.getDefaultNetwork())
                        .orElse(global.getNetworks().keySet().iterator().next()));
        if (!global.getNetworks().containsKey(targetName)) {
            fail(String.format(
                    "Target network '%s' not configured in %s, only %s are known",
                    targetName,
                    yahcli.getConfigLoc(),
                    global.getNetworks().keySet().stream()
                            .map(s -> "'" + s + "'")
                            .collect(Collectors.toList())));
        }
        targetNet = global.getNetworks().get(targetName);
        if (yahcli.getNodeIpv4Addr() != null) {
            final var ip = yahcli.getNodeIpv4Addr();
            var nodeAccount = (yahcli.getNodeAccount() == null)
                    ? MISSING_NODE_ACCOUNT
                    : HapiPropertySource.asDotDelimitedLongArray(yahcli.getNodeAccount())[2];
            final var nodes = targetNet.getNodes();
            if (nodeAccount == MISSING_NODE_ACCOUNT) {
                for (final var node : nodes) {
                    if (ip.equals(node.getIpv4Addr())) {
                        nodeAccount = node.getAccount();
                        break;
                    }
                }
            }

            if (nodeAccount == MISSING_NODE_ACCOUNT) {
                fail(String.format("Account of node with ip '%s' was not specified, and not in" + " config.yml", ip));
            }

            final var overrideConfig = new NodeConfig();
            overrideConfig.setIpv4Addr(ip);
            overrideConfig.setAccount(nodeAccount);
            targetNet.setNodes(List.of(overrideConfig));
        }
    }

    private void fail(String msg) {
        throw new CommandLine.ParameterException(yahcli.getSpec().commandLine(), msg);
    }

    public String getDefaultPayer() {
        return defaultPayer;
    }

    public String getTargetName() {
        return targetName;
    }
}
