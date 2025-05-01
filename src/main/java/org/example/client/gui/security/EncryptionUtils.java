package org.example.client.gui.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EncryptionUtils {

    private static final String RSA_ALGORITHM = "RSA";
    private static final String RC4_ALGORITHM = "RC4"; // !! Utiliser "AES" en production !!
    private static final int RSA_KEY_SIZE = 2048;
    private static final int RC4_KEY_SIZE = 128; // Pour AES, utiliser 128, 192 ou 256

    // Génère une paire de clés RSA
    public static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }

    // Génère une clé secrète RC4 (ou AES)
    public static SecretKey generateRc4SessionKey() throws NoSuchAlgorithmException {
         // Pour RC4, on peut créer directement depuis des bytes, mais utilisons KeyGenerator pour la forme
         // KeyGenerator keyGen = KeyGenerator.getInstance(RC4_ALGORITHM);
         // keyGen.init(RC4_KEY_SIZE);
         // return keyGen.generateKey();

         // Alternative simple pour RC4 (taille variable, ici 128 bits / 16 bytes)
         SecureRandom random = new SecureRandom();
         byte[] keyBytes = new byte[RC4_KEY_SIZE / 8];
         random.nextBytes(keyBytes);
         return new SecretKeySpec(keyBytes, RC4_ALGORITHM);

        /* Pour AES:
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256); // Ou 128, 192
        return keyGen.generateKey();
        */
    }

    // Chiffre des données avec une clé publique RSA
    public static byte[] encryptWithRsaPublicKey(byte[] data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM); // Utiliser "RSA/ECB/PKCS1Padding" est souvent plus explicite
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    // Déchiffre des données avec une clé privée RSA
    public static byte[] decryptWithRsaPrivateKey(byte[] data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_ALGORITHM); // Utiliser "RSA/ECB/PKCS1Padding"
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

     // Chiffre des données avec une clé RC4 (ou AES)
    public static byte[] encryptWithRc4(byte[] data, SecretKey sessionKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RC4_ALGORITHM); // Pour AES: "AES/GCM/NoPadding" (nécessite IvParameterSpec) ou "AES/ECB/PKCS5Padding" (plus simple mais moins sûr)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey);
        return cipher.doFinal(data);
        // Pour AES/GCM, il faudrait gérer l'IV (Initialization Vector)
    }

    // Déchiffre des données avec une clé RC4 (ou AES)
    public static byte[] decryptWithRc4(byte[] encryptedData, SecretKey sessionKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RC4_ALGORITHM); // Pour AES: "AES/GCM/NoPadding" ou "AES/ECB/PKCS5Padding"
        cipher.init(Cipher.DECRYPT_MODE, sessionKey);
        return cipher.doFinal(encryptedData);
         // Pour AES/GCM, il faudrait fournir l'IV utilisé lors du chiffrement
    }

    // --- Méthodes utilitaires pour convertir les clés en String (Base64) pour stockage/transmission ---

    public static String keyToString(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static PublicKey stringToPublicKey(String keyString) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyString);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePublic(spec);
    }

     public static PrivateKey stringToPrivateKey(String keyString) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(keyString);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        return keyFactory.generatePrivate(spec);
    }

     public static SecretKey stringToRc4SecretKey(String keyString) {
         byte[] keyBytes = Base64.getDecoder().decode(keyString);
         return new SecretKeySpec(keyBytes, RC4_ALGORITHM);
     }

      /* Pour AES:
     public static SecretKey stringToAesSecretKey(String keyString) {
         byte[] keyBytes = Base64.getDecoder().decode(keyString);
         return new SecretKeySpec(keyBytes, "AES");
     }
     */
}