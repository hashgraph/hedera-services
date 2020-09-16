package com.hedera.services.legacy.bip39utils;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */



import com.hedera.services.legacy.bip39utils.bip39.Mnemonic;
import com.hedera.services.legacy.bip39utils.bip39.MnemonicException;
import java.util.List;


public class ServicesSeed {

    public static int bip39WordListSize = 24;
    private byte[] entropy; // 32 Bytes

    public ServicesSeed(byte[] entropy) {
        this.entropy = entropy;
    }

    public ServicesSeed(List<String> mnemonic) throws Exception {
        if (mnemonic.size() == ServicesSeed.bip39WordListSize) {
            this.entropy = new Mnemonic().toEntropy(mnemonic);
        } else  {
            Reference reference = new Reference(String.join(" ", mnemonic));
            this.entropy = reference.toBytes();
        }
    }



    public List<String> toWordsList(){
        try {
            return new Mnemonic().toMnemonic(entropy);
        } catch (MnemonicException.MnemonicLengthException e) {
            e.printStackTrace();
            return null;
        }
    }

    public byte[] getEntropy() {
        return entropy;
    }
}
