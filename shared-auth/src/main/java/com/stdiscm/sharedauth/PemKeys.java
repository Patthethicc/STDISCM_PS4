package com.stdiscm.sharedauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Loads RSA keys from PEM files: PKCS#8 private key, X.509 (SubjectPublicKeyInfo) public key. */
public final class PemKeys {

    private PemKeys() { }

    public static PublicKey loadPublicKey(Path file) {
        try {
            byte[] der = decode(Files.readString(file));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("cannot load public key from " + file, e);
        }
    }

    public static PrivateKey loadPrivateKey(Path file) {
        try {
            byte[] der = decode(Files.readString(file));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("cannot load private key from " + file, e);
        }
    }

    private static byte[] decode(String pem) {
        String body = pem.replaceAll("-----(BEGIN|END)[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
