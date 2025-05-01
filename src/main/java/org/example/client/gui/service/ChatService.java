// filepath: src/main/java/org/example/client/gui/service/ChatService.java
package org.example.client.gui.service;

// ... autres imports ...
import org.example.client.gui.security.EncryptionUtils;
import org.example.client.gui.security.KeyManager;
import org.example.shared.model.Message;
import org.example.shared.model.User;
import org.example.shared.dto.Credentials;
import org.example.shared.model.CallSession;
import org.example.shared.model.CallSignal;
import org.example.shared.model.enums.MessageType;
import org.example.shared.dao.UserDAO; // Importer UserDAO
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature; // Pour ignorer props inconnues
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.function.Consumer;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class ChatService {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String userEmail;
    private long currentUserId = -1; // ID de l'utilisateur connecté
    private final ObjectMapper objectMapper;
    private Consumer<Message> messageConsumer;
    private Consumer<CallSignal> callSignalConsumer;
    private Thread listenerThread;
    private volatile boolean isRunning = false; // Remplacer 'running' par 'isRunning'

    // Instances DAO pour la persistance locale (si nécessaire côté client)
    // private final MessageDAO messageDAO;
    // private final GroupDAO groupDAO;
    private final UserDAO userDAO; // Pour obtenir l'ID utilisateur

    // New file service for handling multimedia
    private final FileService fileService;

    // --- AJOUTS POUR E2EE ---
    private final KeyManager keyManager;
    // --- FIN AJOUTS POUR E2EE ---

    // Constructeur modifié
    public ChatService(KeyManager keyManager) { // Accepter KeyManager
        this.keyManager = keyManager; // Stocker KeyManager
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Important
        // this.messageDAO = new MessageDAO(); // Si besoin côté client
        // this.groupDAO = new GroupDAO(); // Si besoin côté client
        this.userDAO = new UserDAO(); // Initialiser UserDAO
        this.fileService = new FileService();
    }

    public void setMessageConsumer(Consumer<Message> consumer) {
        this.messageConsumer = consumer;
    }

    public void setCallSignalConsumer(Consumer<CallSignal> consumer) {
        this.callSignalConsumer = consumer;
    }

    public long getCurrentUserId() {
        // Assurer que l'ID est chargé après la connexion
        if (currentUserId == -1 && userEmail != null) {
            try {
                User user = userDAO.findUserByEmail(userEmail);
                if (user != null) {
                    currentUserId = user.getId();
                }
            } catch (Exception e) {
                System.err.println("Erreur récupération ID utilisateur: " + e.getMessage());
            }
        }
        return currentUserId;
    }

    // resp : initie la connexion avec le serveur / l'authentification
    public boolean connect(final Credentials credentials) throws IOException {
        try {
            // resp 1 : etablissement de la connexion avec le serveur (creation de la socket coté serveur)
            System.out.println("Connexion au serveur " + SERVER_ADDRESS + ":" + SERVER_PORT);
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // Envoyer la commande de connexion pour différencier des requêtes d'inscription
            out.println("LOGIN"); // Commande texte simple

            // Envoyer les identifiants en JSON
            final String jsonCredentials = objectMapper.writeValueAsString(credentials);
            out.println(jsonCredentials);

            // Attendre la réponse du serveur
            final String response = in.readLine();
            final boolean success = "AUTH_SUCCESS".equals(response);

            if (success) {
                this.userEmail = credentials.getEmail();
                this.currentUserId = getCurrentUserId(); // Charger l'ID utilisateur
                if (this.currentUserId == -1) {
                     throw new IOException("Impossible de récupérer l'ID utilisateur pour " + this.userEmail);
                }
                isRunning = true; // Mettre à jour isRunning
                startMessageListener();
                System.out.println("Authentification réussie pour " + userEmail + " (ID: " + currentUserId + ")");
                // Envoyer la clé publique APRÈS succès de l'authentification
                sendPublicKeyToServer();
            } else {
                System.out.println("Échec de l'authentification: " + (response != null ? response : "Réponse nulle"));
                disconnect(); // Utiliser la méthode disconnect existante
            }

            return success;
        } catch (final ConnectException e) {
            throw new IOException(
                    "Impossible de se connecter au serveur. Assurez-vous que le serveur est démarré et accessible sur "
                            + SERVER_ADDRESS + ":" + SERVER_PORT,
                    e);
        } catch (final Exception e) {
             System.err.println("Erreur détaillée connexion: " + e);
             e.printStackTrace();
            throw new IOException("Erreur lors de la connexion ou authentification: " + e.getMessage(), e);
        }
    }

    // --- GESTION E2EE ---

    /** Envoie la clé publique de l'utilisateur actuel au serveur. */
    public void sendPublicKeyToServer() {
        if (!isConnected() || getCurrentUserId() == -1 || keyManager.getUserPublicKeyString() == null) {
            System.err.println("Impossible d'envoyer la clé publique: non connecté, ID utilisateur ou clé manquante.");
            return;
        }
        try {
            System.out.println("Envoi de la clé publique au serveur...");
            // Utiliser un objet Message pour la clé publique pour une meilleure structure
            Message pubKeyMsg = new Message();
            pubKeyMsg.setSenderUserId(getCurrentUserId());
            pubKeyMsg.setType(MessageType.PUBLIC_KEY_RESPONSE); // On utilise ce type pour envoyer notre clé
            pubKeyMsg.setContent(keyManager.getUserPublicKeyString());

            sendMessage(pubKeyMsg); // Utiliser la méthode existante pour envoyer l'objet Message

            // Alternative avec commande texte simple (si ClientHandler est adapté)
            // String command = "PUB_KEY " + getCurrentUserId() + " " + keyManager.getUserPublicKeyString();
            // out.println(command);

        } catch (Exception e) {
            System.err.println("Erreur lors de l'envoi de la clé publique: " + e.getMessage());
        }
    }

    /** Demande la clé publique d'un autre utilisateur au serveur. */
    public void requestPublicKey(long targetUserId) {
         if (!isConnected()) return;
         try {
             System.out.println("Demande de la clé publique pour l'utilisateur ID: " + targetUserId);
             // Utiliser la factory pour créer le message de requête
             Message request = Message.newPublicKeyRequestMessage(getCurrentUserId(), targetUserId);
             sendMessage(request); // Envoyer l'objet Message
         } catch (IOException e) {
             System.err.println("Erreur demande clé publique pour ID " + targetUserId + ": " + e.getMessage());
         }
     }

    /**
     * Assure qu'une session E2EE est établie avec le destinataire.
     * Demande la clé publique si nécessaire et envoie la clé de session RC4/AES.
     * @param recipientUserId L'ID du destinataire.
     * @throws IllegalStateException si la clé publique n'est pas disponible immédiatement.
     * @throws Exception pour toute autre erreur de chiffrement ou réseau.
     */
    private void ensureSession(long recipientUserId) throws Exception {
        // Si on n'a pas de clé de session pour ce contact, il faut l'établir
        if (keyManager.getSessionKey(String.valueOf(recipientUserId)) == null) { // Utiliser l'ID comme clé de map
            System.out.println("Pas de clé de session pour ID " + recipientUserId + ". Tentative d'établissement...");

            // 1. A-t-on la clé publique du destinataire ?
            PublicKey recipientPublicKey = keyManager.getContactPublicKey(String.valueOf(recipientUserId));
            if (recipientPublicKey == null) {
                System.out.println("Clé publique pour ID " + recipientUserId + " non trouvée localement. Demande au serveur...");
                requestPublicKey(recipientUserId);
                // Il faut attendre la réponse. Pour l'instant, on lève une exception.
                // Une meilleure gestion impliquerait une attente asynchrone ou une mise en file d'attente du message.
                throw new IllegalStateException("Clé publique pour l'utilisateur ID " + recipientUserId + " non disponible. Demande envoyée. Réessayez d'envoyer le message.");
            }

            // 2. On a la clé publique, générer et envoyer la clé de session
            System.out.println("Clé publique trouvée pour ID " + recipientUserId + ". Génération et envoi de la clé de session...");
            // Générer une nouvelle clé de session RC4 (ou AES)
            SecretKey sessionKey = EncryptionUtils.generateRc4SessionKey(); // !! Changer pour AES !!
            String sessionKeyId = String.valueOf(System.currentTimeMillis()); // ID simple basé sur le temps

            // Chiffrer la clé de session avec la clé publique RSA du destinataire
            byte[] encryptedSessionKeyBytes = EncryptionUtils.encryptWithRsaPublicKey(sessionKey.getEncoded(), recipientPublicKey);

            // Créer le message d'initialisation de session
            Message initMessage = Message.newSessionInitMessage(
                    getCurrentUserId(),
                    recipientUserId,
                    encryptedSessionKeyBytes
            );
            // initMessage.setSessionKeyId(sessionKeyId); // Optionnel: envoyer l'ID de clé

            // Envoyer le message d'init
            sendMessage(initMessage);

            // Stocker la clé de session localement associée à l'ID utilisateur
            keyManager.storeSessionKey(String.valueOf(recipientUserId), sessionKey);
            System.out.println("Message d'initialisation de session envoyé à ID " + recipientUserId);

        } else {
             System.out.println("Clé de session existante pour ID " + recipientUserId);
        }
    }

    /**
     * Envoie un message texte chiffré E2EE.
     * Gère l'établissement de session si nécessaire.
     * @param recipientUserId L'ID du destinataire.
     * @param textContent Le contenu texte à chiffrer et envoyer.
     * @throws Exception Si l'envoi ou le chiffrement échoue.
     */
    public void sendEncryptedTextMessage(long recipientUserId, String textContent) throws Exception {
        if (!isConnected() || recipientUserId <= 0 || textContent == null || textContent.isEmpty()) {
            throw new IllegalArgumentException("Paramètres invalides pour sendEncryptedTextMessage");
        }

        // 1. Assurer que la session E2EE est prête (peut lever une exception si clé publique manque)
        ensureSession(recipientUserId);

        // 2. Récupérer la clé de session (devrait exister après ensureSession)
        SecretKey sessionKey = keyManager.getSessionKey(String.valueOf(recipientUserId));
        if (sessionKey == null) {
            // Ne devrait pas arriver si ensureSession a réussi, mais sécurité
            throw new IllegalStateException("Échec critique: Clé de session non trouvée après ensureSession pour ID " + recipientUserId);
        }
        String sessionKeyId = null; // Récupérer l'ID si on l'a stocké/généré

        // 3. Chiffrer le contenu avec la clé de session RC4 (ou AES)
        byte[] encryptedBytes = EncryptionUtils.encryptWithRc4(textContent.getBytes(StandardCharsets.UTF_8), sessionKey); // !! Changer pour AES !!

        // 4. Créer l'objet Message chiffré
        Message message = Message.newEncryptedTextMessage(
                getCurrentUserId(),
                recipientUserId,
                encryptedBytes,
                sessionKeyId // Passer l'ID de session si utilisé
        );

        // 5. Envoyer l'objet Message via la méthode existante
        sendMessage(message);
        System.out.println("Message texte chiffré envoyé à ID " + recipientUserId);
    }

    // --- FIN GESTION E2EE ---


    // Méthode existante pour envoyer un objet Message (adaptée pour JSON)
    public void sendMessage(final Message message) throws IOException {
        if (out == null || socket == null || socket.isClosed()) {
            throw new IOException("Non connecté au serveur.");
        }
        // Assigner l'expéditeur si ce n'est pas déjà fait
        if (message.getSenderUserId() <= 0) {
             message.setSenderUserId(getCurrentUserId());
        }
        try {
            final String jsonMessage = objectMapper.writeValueAsString(message);
            out.println(jsonMessage);
        } catch (JsonProcessingException e) {
            throw new IOException("Erreur lors de la sérialisation du message en JSON", e);
        }
    }

    // Méthode pour envoyer un objet CallSignal (adaptée pour JSON)
    public void sendCallSignal(final CallSignal signal) throws IOException {
         if (out == null || socket == null || socket.isClosed()) {
            throw new IOException("Non connecté au serveur.");
        }
         // Assigner l'expéditeur si ce n'est pas déjà fait
         if (signal.getSenderUserId() <= 0) { // Assumant un champ senderUserId dans CallSignal
             signal.setSenderUserId(getCurrentUserId());
         }
         try {
            final String jsonSignal = objectMapper.writeValueAsString(signal);
            out.println(jsonSignal);
        } catch (JsonProcessingException e) {
            throw new IOException("Erreur lors de la sérialisation du signal d'appel en JSON", e);
        }
    }


    private void startMessageListener() {
        if (listenerThread != null && listenerThread.isAlive()) {
            return; // Déjà en cours d'écoute
        }
        isRunning = true;
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (isRunning && (line = in.readLine()) != null) {
                    handleReceivedJson(line); // Traiter la ligne JSON reçue
                }
            } catch (SocketException e) {
                if (isRunning) {
                    System.err.println("Connexion perdue avec le serveur (SocketException): " + e.getMessage());
                } else {
                    System.out.println("Socket fermé normalement.");
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("Erreur de lecture du serveur: " + e.getMessage());
                    e.printStackTrace();
                }
            } catch (Exception e) { // Capturer autres exceptions (ex: JSON parsing)
                 if (isRunning) {
                    System.err.println("Erreur inattendue dans le listener: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                isRunning = false;
                // Ne pas appeler disconnect() ici pour éviter boucle infinie si disconnect cause l'erreur
                System.out.println("Thread d'écoute arrêté.");
                // Notifier l'UI de la déconnexion ?
            }
        });
        listenerThread.start();
    }

    // Traite une ligne JSON reçue du serveur
    private void handleReceivedJson(String jsonLine) {
        try {
            // Essayer de déterminer si c'est un Message ou un CallSignal (ou autre)
            // On peut regarder des champs clés ou utiliser une approche plus robuste
            if (jsonLine.contains("\"type\"") && (jsonLine.contains("\"senderUserId\"") || jsonLine.contains("\"encryptedContentBase64\""))) { // Heuristique simple pour Message
                Message message = objectMapper.readValue(jsonLine, Message.class);
                handleReceivedMessage(message);
            } else if (jsonLine.contains("\"type\"") && jsonLine.contains("\"sessionId\"")) { // Heuristique simple pour CallSignal
                CallSignal signal = objectMapper.readValue(jsonLine, CallSignal.class);
                if (callSignalConsumer != null) {
                    Platform.runLater(() -> callSignalConsumer.accept(signal));
                }
            }
             // Gérer d'autres types d'objets JSON si nécessaire (ex: UserStatusUpdate)
             else if (jsonLine.contains("\"online\"") && jsonLine.contains("\"userId\"")) { // Heuristique pour UserStatusUpdate
                 // Supposons une classe UserStatusUpdate comme dans l'exemple précédent
                 // UserStatusUpdate update = objectMapper.readValue(jsonLine, UserStatusUpdate.class);
                 // if (userStatusListener != null) {
                 //     Platform.runLater(() -> userStatusListener.updateUserStatus(update.getUserId(), update.isOnline()));
                 // }
                 System.out.println("Mise à jour de statut reçue (non traitée) : " + jsonLine);
             }
            else {
                System.out.println("JSON reçu non reconnu: " + jsonLine);
            }
        } catch (JsonProcessingException e) {
            System.err.println("Erreur de parsing JSON: " + e.getMessage() + " | JSON: " + jsonLine);
        } catch (Exception e) {
             System.err.println("Erreur traitement JSON reçu: " + e.getMessage() + " | JSON: " + jsonLine);
             e.printStackTrace();
        }
    }

    // Traite un objet Message désérialisé
    private void handleReceivedMessage(Message message) {
        System.out.println("Message reçu: " + message); // Log brut

        switch (message.getType()) {
            case PUBLIC_KEY_RESPONSE:
                handlePublicKeyResponse(message);
                break;
            case E2E_SESSION_INIT:
                handleSessionInit(message);
                break;
            case TEXT:
                // Vérifier si le message TEXT est chiffré ou non
                if (message.getEncryptedContent() != null) {
                    handleEncryptedTextMessage(message);
                } else if (message.getContent() != null && messageConsumer != null) {
                    // Message texte simple non chiffré (ou système)
                    Platform.runLater(() -> messageConsumer.accept(message));
                }
                break;
            case SYSTEM:
                // Afficher directement les messages système
                if (messageConsumer != null && message.getContent() != null) {
                     Platform.runLater(() -> messageConsumer.accept(message));
                }
                break;
            // Gérer les types MEDIA (pour l'instant, on les passe au consumer)
            case IMAGE:
            case VIDEO:
            case AUDIO:
            case DOCUMENT:
                 if (messageConsumer != null) {
                     // TODO: Gérer le téléchargement/déchiffrement E2EE des médias ici si nécessaire
                     Platform.runLater(() -> messageConsumer.accept(message));
                 }
                 break;
            default:
                System.out.println("Type de message non géré: " + message.getType());
        }
    }

    // --- Méthodes de traitement E2EE spécifiques ---

    private void handlePublicKeyResponse(Message message) {
        // Le contenu contient la clé publique en Base64
        String publicKeyString = message.getContent();
        long keyOwnerUserId = message.getSenderUserId(); // L'ID de l'utilisateur dont c'est la clé

        if (publicKeyString != null && !publicKeyString.isEmpty() && keyOwnerUserId > 0) {
            try {
                PublicKey publicKey = EncryptionUtils.stringToPublicKey(publicKeyString);
                keyManager.storeContactPublicKey(String.valueOf(keyOwnerUserId), publicKey); // Utiliser ID comme clé
                System.out.println("Clé publique pour ID " + keyOwnerUserId + " stockée.");
                // Informer l'UI ou déclencher une action (ex: réessayer d'envoyer un message en attente) ?
            } catch (Exception e) {
                System.err.println("Erreur conversion/stockage clé publique reçue pour ID " + keyOwnerUserId + ": " + e.getMessage());
            }
        } else {
             System.err.println("Réponse de clé publique invalide reçue: " + message);
        }
    }

    private void handleSessionInit(Message message) {
        // Ce message contient la clé de session RC4/AES, chiffrée avec NOTRE clé publique RSA
        byte[] encryptedSessionKey = message.getEncryptedContent(); // Vient de getEncryptedContentBase64 via Jackson
        long senderId = message.getSenderUserId();
        PrivateKey myPrivateKey = keyManager.getUserPrivateKey();

        if (encryptedSessionKey != null && senderId > 0 && myPrivateKey != null) {
            try {
                // Déchiffrer la clé de session avec notre clé privée RSA
                byte[] sessionKeyBytes = EncryptionUtils.decryptWithRsaPrivateKey(encryptedSessionKey, myPrivateKey);
                // Recréer la clé secrète (utiliser l'algo approprié)
                SecretKey sessionKey = new SecretKeySpec(sessionKeyBytes, "RC4"); // !! Changer pour AES !!

                // Stocker la clé de session associée à l'expéditeur (utiliser ID)
                keyManager.storeSessionKey(String.valueOf(senderId), sessionKey);
                System.out.println("Clé de session E2EE établie avec ID " + senderId);

                // Optionnel: Confirmer l'établissement de la session à l'autre client ?
                // Optionnel: Déclencher l'envoi de messages en attente ?

            } catch (Exception e) {
                System.err.println("Échec déchiffrement/stockage clé de session de ID " + senderId + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
             System.err.println("Message d'initialisation de session invalide reçu: " + message);
        }
    }

    private void handleEncryptedTextMessage(Message message) {
        // Message texte chiffré avec la clé de session RC4/AES
        byte[] encryptedContent = message.getEncryptedContent();
        long senderId = message.getSenderUserId();
        SecretKey sessionKey = keyManager.getSessionKey(String.valueOf(senderId)); // Utiliser ID

        if (encryptedContent != null && senderId > 0 && sessionKey != null) {
            try {
                // Déchiffrer le contenu avec la clé de session
                byte[] decryptedBytes = EncryptionUtils.decryptWithRc4(encryptedContent, sessionKey); // !! Changer pour AES !!
                String decryptedContent = new String(decryptedBytes, StandardCharsets.UTF_8);

                // Créer un nouveau message (ou modifier l'existant) avec le contenu déchiffré
                // pour le passer au consumer standard de l'UI.
                Message decryptedMessage = new Message();
                decryptedMessage.setId(message.getId()); // Garder les métadonnées
                decryptedMessage.setSenderUserId(senderId);
                decryptedMessage.setReceiverUserId(message.getReceiverUserId());
                decryptedMessage.setGroupId(message.getGroupId());
                decryptedMessage.setTimestamp(message.getTimestamp());
                decryptedMessage.setStatus(message.getStatus());
                decryptedMessage.setType(MessageType.TEXT); // C'est redevenu un message texte simple
                decryptedMessage.setContent(decryptedContent); // Mettre le contenu déchiffré

                // Notifier l'UI avec le message déchiffré
                if (messageConsumer != null) {
                    Platform.runLater(() -> messageConsumer.accept(decryptedMessage));
                }

            } catch (Exception e) {
                System.err.println("Échec du déchiffrement du message de ID " + senderId + ": " + e.getMessage());
                // Afficher un message d'erreur dans l'UI ?
                Message errorMsg = createErrorMessage(message, "[Erreur de déchiffrement]");
                 if (messageConsumer != null) {
                     Platform.runLater(() -> messageConsumer.accept(errorMsg));
                 }
            }
        } else {
             System.err.println("Message texte chiffré invalide ou clé de session manquante pour ID " + senderId + ": " + message);
             Message errorMsg = createErrorMessage(message, "[Message illisible - Session E2EE non établie?]");
              if (messageConsumer != null) {
                  Platform.runLater(() -> messageConsumer.accept(errorMsg));
              }
        }
    }

    // Helper pour créer un message d'erreur à afficher dans l'UI
    private Message createErrorMessage(Message original, String errorText) {
         Message errorMsg = new Message();
         errorMsg.setId(original.getId());
         errorMsg.setSenderUserId(original.getSenderUserId());
         errorMsg.setReceiverUserId(original.getReceiverUserId());
         errorMsg.setGroupId(original.getGroupId());
         errorMsg.setTimestamp(original.getTimestamp());
         errorMsg.setStatus(original.getStatus());
         errorMsg.setType(MessageType.SYSTEM); // Marquer comme système/erreur
         errorMsg.setContent(errorText);
         return errorMsg;
    }


    // --- Méthodes utilitaires et existantes ---

    public void disconnect() { // Surcharge pour correspondre à l'appel dans ChatController
         // Pas besoin de envoyer LOGOUT ici si closeResources le fait via le finally du thread
         closeResources();
    }

    private void closeResources() {
         isRunning = false; // Arrêter la boucle d'écoute
         if (listenerThread != null) {
             listenerThread.interrupt(); // Interrompre le thread s'il est bloqué sur readLine
         }
         try {
             if (out != null) out.close();
         } catch (Exception e) { /* Ignorer */ }
         try {
             if (in != null) in.close();
         } catch (Exception e) { /* Ignorer */ }
         try {
             if (socket != null && !socket.isClosed()) socket.close();
         } catch (IOException e) { /* Ignorer */ }
         out = null;
         in = null;
         socket = null;
         listenerThread = null; // Permettre la recréation
         // Ne pas effacer userEmail/currentUserId ici, utile pour savoir qui était connecté
         System.out.println("Ressources réseau fermées.");
         // Effacer les clés de session ? Bonne pratique pour la sécurité.
         // keyManager.clearAllSessionKeys();
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed() && isRunning;
    }

    // --- Méthodes pour la gestion des médias ---
    public File getMediaFile(final Message message) {
        if (!message.isMediaMessage()) {
            throw new IllegalArgumentException("Not a media message");
        }
        File file = fileService.getFile(message.getContent());
        System.out.println("Looking for media file at: " + file.getAbsolutePath());
        System.out.println("File exists: " + file.exists());
        return file;
    }

    // Adapter ces méthodes pour utiliser l'ID utilisateur (long)
    public Message createDirectMediaMessage(final String senderEmail, final String receiverEmail, final File mediaFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        final User receiver = userDAO.findUserByEmail(receiverEmail);

        // Detect file type and save the file
        final MessageType type = fileService.detectMessageType(mediaFile.getName());
        // relative path of the media
        final String filePath = fileService.saveFile(mediaFile, type, mediaFile.getName());
        final String mimeType = fileService.getMimeType(mediaFile);

        return Message.newDirectMediaMessage(
                sender.getId(),
                receiver.getId(),
                filePath,
                type,
                mediaFile.getName(),
                mediaFile.length(),
                mimeType);
    }

    public Message createGroupMediaMessage(final String senderEmail, final long groupId, final File mediaFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);

        // Detect file type and save the file
        final MessageType type = fileService.detectMessageType(mediaFile.getName());
        final String filePath = fileService.saveFile(mediaFile, type, mediaFile.getName());
        final String mimeType = fileService.getMimeType(mediaFile);

        return Message.newGroupMediaMessage(
                sender.getId(),
                groupId,
                filePath,
                type,
                mediaFile.getName(),
                mediaFile.length(),
                mimeType);
    }

    public Message createDirectAudioMessage(final String senderEmail, final String receiverEmail, final File audioFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);
        final User receiver = userDAO.findUserByEmail(receiverEmail);

        // Save the audio file
        final String filePath = fileService.saveFile(audioFile, MessageType.AUDIO, audioFile.getName());
        final String mimeType = fileService.getMimeType(audioFile);

        return Message.newDirectMediaMessage(
                sender.getId(),
                receiver.getId(),
                filePath,
                MessageType.AUDIO,
                audioFile.getName(),
                audioFile.length(),
                mimeType);
    }

    public Message createGroupAudioMessage(final String senderEmail, final long groupId, final File audioFile)
            throws IOException {
        final User sender = userDAO.findUserByEmail(senderEmail);

        // Save the audio file
        final String filePath = fileService.saveFile(audioFile, MessageType.AUDIO, audioFile.getName());
        final String mimeType = fileService.getMimeType(audioFile);

        return Message.newGroupMediaMessage(
                sender.getId(),
                groupId,
                filePath,
                MessageType.AUDIO,
                audioFile.getName(),
                audioFile.length(),
                mimeType);
    }

     // --- Méthodes pour les appels (existantes, adaptées pour JSON/ID) ---
     public CallSignal createCallRequest(CallSession session, String targetEmail) throws IOException {
         long targetId = userDAO.findUserByEmail(targetEmail).getId();
         return CallSignal.createCallRequest(session.getSessionId(), getCurrentUserId(), targetId);
     }
     public CallSignal createCallAccept(String sessionId, long targetUserId, int localPort) throws IOException {
         // L'IP locale peut être ajoutée ici ou côté serveur
         String localIp = socket.getLocalAddress().getHostAddress(); // Utiliser l'IP de la socket connectée
         return CallSignal.createCallAccept(sessionId, getCurrentUserId(), targetUserId, localIp, localPort);
     }
     public CallSignal createCallReject(String sessionId, long targetUserId) throws IOException {
         return CallSignal.createCallReject(sessionId, getCurrentUserId(), targetUserId);
     }
     public CallSignal createCallEnd(String sessionId, long targetUserId) throws IOException {
         return CallSignal.createCallEnd(sessionId, getCurrentUserId(), targetUserId);
     }

}