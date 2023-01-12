/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.utilops.inventory;

import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.keys.Ed25519Utils;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyGenerator;
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.keys.deterministic.Bip0032;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Key;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NewSpecKey extends UtilOp {
    static final Logger log = LogManager.getLogger(NewSpecKey.class);

    private boolean yahcliLogger = false;
    private boolean verboseLoggingOn = false;
    private boolean exportEd25519Mnemonic = false;
    private final String name;
    private Optional<String> immediateExportLoc = Optional.empty();
    private Optional<String> immediateExportPass = Optional.empty();
    private Optional<KeyType> type = Optional.empty();
    private Optional<SigControl> shape = Optional.empty();
    private Optional<KeyLabel> labels = Optional.empty();
    private Optional<KeyGenerator> generator = Optional.empty();

    public NewSpecKey(String name) {
        this.name = name;
    }

    public NewSpecKey exportingTo(String loc, String pass) {
        immediateExportLoc = Optional.of(loc);
        immediateExportPass = Optional.of(pass);
        return this;
    }

    public NewSpecKey includingEd25519Mnemonic() {
        exportEd25519Mnemonic = true;
        return this;
    }

    public NewSpecKey logged() {
        verboseLoggingOn = true;
        return this;
    }

    public NewSpecKey yahcliLogged() {
        verboseLoggingOn = true;
        yahcliLogger = true;
        return this;
    }

    public NewSpecKey type(KeyType toGen) {
        type = Optional.of(toGen);
        return this;
    }

    public NewSpecKey shape(SigControl control) {
        shape = Optional.of(control);
        return this;
    }

    public NewSpecKey labels(KeyLabel kl) {
        labels = Optional.of(kl);
        return this;
    }

    public NewSpecKey generator(KeyGenerator gen) {
        generator = Optional.of(gen);
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        if (exportEd25519Mnemonic) {
            if (!immediateExportLoc.isPresent() || !immediateExportPass.isPresent()) {
                throw new IllegalStateException("Must have an export location for the key info");
            }

            final var mnemonic = SpecKey.randomMnemonic();
            final var seed = Bip0032.seedFrom(mnemonic);
            final var curvePoint = Bip0032.privateKeyFrom(seed);
            final EdDSAPrivateKey privateKey = Ed25519Utils.keyFrom(curvePoint);

            final var pubKey = privateKey.getAbyte();
            final var key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();

            spec.registry().saveKey(name, key);
            spec.keys().incorporate(name, privateKey);

            final var exportLoc = immediateExportLoc.get();
            final var exportPass = immediateExportPass.get();
            exportWithPass(spec, name, exportLoc, exportPass);
            final var wordsLoc = exportLoc.replace(".pem", ".words");
            Files.writeString(Paths.get(wordsLoc), mnemonic);
            return false;
        }

        final var keyGen = generator.orElse(DEFAULT_KEY_GEN);
        Key key;
        if (shape.isPresent()) {
            if (labels.isPresent()) {
                key = spec.keys().generateSubjectTo(spec, shape.get(), keyGen, labels.get());
            } else {
                key = spec.keys().generateSubjectTo(spec, shape.get(), keyGen);
            }
        } else {
            key = spec.keys().generate(spec, type.orElse(KeyType.SIMPLE), keyGen);
        }
        spec.registry().saveKey(name, key);
        if (immediateExportLoc.isPresent() && immediateExportPass.isPresent()) {
            final var exportLoc = immediateExportLoc.get();
            final var exportPass = immediateExportPass.get();
            exportWithPass(spec, name, exportLoc, exportPass);
            if (verboseLoggingOn && yahcliLogger) {
                COMMON_MESSAGES.info(
                        "Exported a newly generated key in PEM format to " + exportLoc);
            }
        }
        if (verboseLoggingOn && !yahcliLogger) {
            if (type.orElse(KeyType.SIMPLE) == KeyType.SIMPLE) {
                log.info(
                        "Created simple '{}' w/ Ed25519 public key {}",
                        name,
                        hex(key.getEd25519().toByteArray()));
            } else {
                log.info("Created a complex key...");
            }
        }
        return false;
    }

    static void exportWithPass(HapiSpec spec, String name, String exportLoc, String exportPass)
            throws IOException {
        spec.keys().exportSimpleKey(exportLoc, name, exportPass);
        final var passLoc = exportLoc.replace(".pem", ".pass");
        Files.writeString(Paths.get(passLoc), exportPass);
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        return super.toStringHelper().add("name", name);
    }
}
