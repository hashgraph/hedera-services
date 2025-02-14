// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.yahcli.commands.keys;

import static com.hedera.services.bdd.spec.utilops.inventory.AccessoryUtils.setLogLevels;
import static com.hedera.services.yahcli.config.ConfigUtils.YAHCLI_LOGGING_CLASSES;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

import com.hedera.node.app.hapi.utils.keys.Ed25519Utils;
import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.swirlds.common.utility.CommonUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import picocli.CommandLine;

@CommandLine.Command(
        name = "print-public",
        subcommands = {picocli.CommandLine.HelpCommand.class},
        description = "Prints the public part of a Ed25519 key in PEM or mnemonic form")
public class ExtractPublicCommand implements Callable<Integer> {
    @CommandLine.ParentCommand
    private KeysCommand keysCommand;

    @CommandLine.Option(
            names = {"-p", "--path"},
            paramLabel = "path to PEM or mnemonic to print public part of",
            defaultValue = "keys/ed25519.pem")
    private String loc;

    @CommandLine.Option(
            names = {"-x", "--passphrase"},
            paramLabel = "passphrase for PEM (if needed and not present as matching .pass)")
    private String passphrase;

    @Override
    public Integer call() throws Exception {
        setLogLevels(keysCommand.getYahcli().getLogLevel(), YAHCLI_LOGGING_CLASSES);
        final EdDSAPrivateKey privateKey;
        if (loc.endsWith(".pem")) {
            if (passphrase == null) {
                final var passLoc = loc.replace(".pem", ".pass");
                passphrase = Files.readString(Paths.get(passLoc)).trim();
            }
            privateKey = Ed25519Utils.readKeyFrom(new File(loc), passphrase);
        } else {
            final var mnemonic = Files.readString(Paths.get(loc)).trim();
            final var seed = Bip0032.seedFrom(mnemonic);
            final var curvePoint = Bip0032.privateKeyFrom(seed);
            privateKey = Ed25519Utils.keyFrom(curvePoint);
        }
        final var pubKey = privateKey.getAbyte();
        final var hexedPubKey = CommonUtils.hex(pubKey);
        COMMON_MESSAGES.info("The public key @ " + loc + " is: " + hexedPubKey);

        return 0;
    }
}
