package com.hedera.services.bdd.spec.infrastructure;

import org.bouncycastle.crypto.ec.ECNewPublicKeyTransform;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.*;

public class ECKeyUtil {
    private static String byteArrayToHexString(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02X", b));
        return sb.toString();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static byte[] getPublicKeyFromPrivateKey2(ECPrivateKey key) {
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        org.bouncycastle.math.ec.ECPoint pointQ = spec.getG().multiply(new BigInteger(1, key.getEncoded()));
        byte[] publickKeyByte = pointQ.getEncoded(false);
        return publickKeyByte;
    }

    public static PublicKey getPublicKeyFromPrivateKey(ECPrivateKey privateKey) throws Exception {
return null;
//        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
//        org.bouncycastle.math.ec.ECPoint pointQ = spec.getG().multiply(new BigInteger(1, privateKey.getEncoded()));
//        byte[] publickKeyByte = pointQ.getEncoded(false);
//        String publicKeyBc = byteArrayToHexString(publickKeyByte);
//
//
//        PublicKey
//
//
//
//        // Get the EC parameters from the private key
////        ECParameterSpec ecSpec = privateKey.getParams();
//
////        ecSpec.getGenerator().getAffineX().multiply(privateKey.getS());
////        privateKey.getS().multiply(ecSpec.getGenerator());
////        EllipticCurve ellipticCurve = new EllipticCurve(ecSpec.getCurve(), ecSpec.getGenerator(), ecSpec.getOrder(), ecSpec.getCofactor());
////
////        // Create the public key spec
////        ECPublicKeySpec pubSpec = new ECPublicKeySpec(ecSpec.getGenerator(), ecSpec);
//
//
//
//        // Generate the public key
//        KeyFactory keyFactory = KeyFactory.getInstance("EC");
//        return keyFactory.generatePublic(pubSpec);
    }

    public static void main(String[] args) throws Exception {
        // Example usage
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256k1"));
        KeyPair keyPair = keyGen.generateKeyPair();
        ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
        PublicKey publicKey = getPublicKeyFromPrivateKey(privateKey);

        System.out.println("Private Key: " + privateKey);
        System.out.println("Public Key: " + publicKey);
    }
}