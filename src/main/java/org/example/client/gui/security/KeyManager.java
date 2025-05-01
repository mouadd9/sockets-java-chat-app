package org.example.client.gui.security;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

// Gère les clés RSA du client, les clés publiques des contacts et les clés de session
public class KeyManager {

    private static final String KEYS_DIR = System.getProperty("user.dir")
            + File.separator + "keys";
    private String KEY_PAIR_FILE = KEYS_DIR + File.separator + "rsa_keys.properties";
    private static final String PUBLIC_KEYS_FILE = KEYS_DIR + File.separator + "public_keys.properties";
    private static final String SESSION_KEYS_FILE = KEYS_DIR + File.separator + "session_keys.properties";

    private KeyPair userKeyPair;
    private Map<String, PublicKey> contactPublicKeys = new ConcurrentHashMap<>(); // userId -> PublicKey
    private Map<String, SecretKey> sessionKeys = new ConcurrentHashMap<>(); // userId -> SessionKey (RC4/AES)

    public KeyManager() {
        init();
        loadKeys();
    }
    
    // Nouveau constructeur acceptant l'email de l'utilisateur
    public KeyManager(String currentUserEmail) {
        if (currentUserEmail == null || currentUserEmail.trim().isEmpty()) {
            currentUserEmail = "default";
        }
        // Construire le nom de fichier de la paire de clés avec l'identifiant utilisateur
        this.KEY_PAIR_FILE = KEYS_DIR + File.separator + "rsa_keys_" 
                + currentUserEmail.replace("@", "_at_") + ".properties";
        init();
        loadKeys();
    }
    
    private void init() {
        try {
            Files.createDirectories(Paths.get(KEYS_DIR));
        } catch (IOException ioEx) {
            System.err.println("Erreur création dossier clés: " + ioEx.getMessage());
        }
    }
    
    private void loadKeys() {
        try {
            File keyPairFile = new File(KEY_PAIR_FILE);
            if (!keyPairFile.exists()) {
                System.out.println("Aucune paire de clés trouvée, génération...");
                generateAndSaveKeyPair();
            } else {
                loadKeyPair();
            }
            loadContactPublicKeys();
            loadSessionKeys();
        } catch (final Exception e) {
            System.err.println("Erreur lors du chargement des clés: " + e.getMessage());
            if (userKeyPair == null) {
                generateAndSaveKeyPair();
            }
        }
    }

    private void loadKeyPair() throws Exception {
        File f = new File(KEY_PAIR_FILE);
        if (!f.exists())
            return;

        Properties props = new Properties();
        try (InputStream input = new FileInputStream(f)) {
            props.load(input);
            String pubKeyStr = props.getProperty("publicKey");
            String privKeyStr = props.getProperty("privateKey");
            if (pubKeyStr != null && privKeyStr != null) {
                PublicKey publicKey = EncryptionUtils.stringToPublicKey(pubKeyStr);
                PrivateKey privateKey = EncryptionUtils.stringToPrivateKey(privKeyStr);
                userKeyPair = new KeyPair(publicKey, privateKey);
                System.out.println("Paire de clés RSA chargée.");
            }
        }
    }

    private void loadContactPublicKeys() {
        File f = new File(PUBLIC_KEYS_FILE);
        if (!f.exists())
            return;
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(f)) {
            props.load(input);
            for (String userId : props.stringPropertyNames()) {
                try {
                    PublicKey pubKey = EncryptionUtils.stringToPublicKey(props.getProperty(userId));
                    contactPublicKeys.put(userId, pubKey);
                } catch (Exception e) {
                    System.err.println("Erreur chargement clé publique pour " + userId + ": " + e.getMessage());
                }
            }
            System.out.println("Clés publiques des contacts chargées.");
        } catch (IOException e) {
            System.err.println("Erreur lecture fichier clés publiques: " + e.getMessage());
        }
    }

    private void loadSessionKeys() {
        File f = new File(SESSION_KEYS_FILE);
        if (!f.exists())
            return;
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(f)) {
            props.load(input);
            for (String userId : props.stringPropertyNames()) {
                try {
                    // !! Attention: Stocker les clés de session n'est pas idéal pour la Perfect
                    // Forward Secrecy
                    // Il vaut mieux les regénérer à chaque session. Ceci est un exemple simple.
                    SecretKey sessionKey = EncryptionUtils.stringToRc4SecretKey(props.getProperty(userId)); // Adapter
                                                                                                            // pour AES
                                                                                                            // si besoin
                    sessionKeys.put(userId, sessionKey);
                } catch (Exception e) {
                    System.err.println("Erreur chargement clé session pour " + userId + ": " + e.getMessage());
                }
            }
            System.out.println("Clés de session (persistées) chargées.");
        } catch (IOException e) {
            System.err.println("Erreur lecture fichier clés session: " + e.getMessage());
        }
    }

    private void generateAndSaveKeyPair() {
        try {
            userKeyPair = EncryptionUtils.generateRsaKeyPair();
            saveKeyPair();
            System.out.println("Nouvelle paire de clés RSA générée et sauvegardée.");
        } catch (Exception e) {
            System.err.println("Impossible de générer/sauvegarder la paire de clés RSA: " + e.getMessage());
            // Gérer l'erreur critique - l'application ne peut pas fonctionner sans clés
        }
    }

    private void saveKeyPair() throws IOException {
        if (userKeyPair == null)
            return;
        Properties props = new Properties();
        props.setProperty("publicKey", EncryptionUtils.keyToString(userKeyPair.getPublic()));
        props.setProperty("privateKey", EncryptionUtils.keyToString(userKeyPair.getPrivate())); // !! Sécurité: Stockage
                                                                                                // non chiffré !!

        try (OutputStream output = new FileOutputStream(KEY_PAIR_FILE)) {
            props.store(output, "RSA Key Pair - NE PAS MODIFIER");
        }
    }

    private void saveContactPublicKeys() {
        Properties props = new Properties();
        contactPublicKeys.forEach((userId, key) -> {
            props.setProperty(userId, EncryptionUtils.keyToString(key));
        });
        try (OutputStream output = new FileOutputStream(PUBLIC_KEYS_FILE)) {
            props.store(output, "Contact Public Keys");
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde clés publiques contacts: " + e.getMessage());
        }
    }

    private void saveSessionKeys() {
        Properties props = new Properties();
        sessionKeys.forEach((userId, key) -> {
            props.setProperty(userId, EncryptionUtils.keyToString(key));
        });
        try (OutputStream output = new FileOutputStream(SESSION_KEYS_FILE)) {
            props.store(output, "Session Keys (RC4/AES) - Pour débogage/persistance simple");
        } catch (IOException e) {
            System.err.println("Erreur sauvegarde clés session: " + e.getMessage());
        }
    }

    public PublicKey getUserPublicKey() {
        return (userKeyPair != null) ? userKeyPair.getPublic() : null;
    }

    public PrivateKey getUserPrivateKey() {
        return (userKeyPair != null) ? userKeyPair.getPrivate() : null;
    }

    public String getUserPublicKeyString() {
        PublicKey key = getUserPublicKey();
        return (key != null) ? EncryptionUtils.keyToString(key) : null;
    }

    public void storeContactPublicKey(String userId, PublicKey key) {
        contactPublicKeys.put(userId, key);
        saveContactPublicKeys(); // Sauvegarder immédiatement
    }

    public PublicKey getContactPublicKey(String userId) {
        return contactPublicKeys.get(userId);
    }

    public void storeSessionKey(String userId, SecretKey key) {
        sessionKeys.put(userId, key);
        // saveSessionKeys(); // Décider si on persiste les clés de session
    }

    public SecretKey getSessionKey(String userId) {
        return sessionKeys.get(userId);
    }

    public void clearSessionKey(String userId) {
        sessionKeys.remove(userId);
        // saveSessionKeys();
    }

    public void clearAllSessionKeys() {
        sessionKeys.clear();
        // saveSessionKeys();
    }
}