package org.example.client.gui.service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.example.shared.model.CallSession;
import org.example.shared.model.User;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Gère les appels audio via UDP pour l'application de chat.
 */
public class CallManager {
    // Paramètres audio
    private static final float SAMPLE_RATE = 44100.0f;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;
    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(
            SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, SIGNED, BIG_ENDIAN);
    private static final int BUFFER_SIZE = 4096;

    // État de l'appel
    private final AtomicBoolean isCallActive = new AtomicBoolean(false);
    private final AtomicBoolean isMicrophoneMuted = new AtomicBoolean(false);
    private final StringProperty callStatus = new SimpleStringProperty("Aucun appel en cours");
    
    // Informations sur l'appel en cours
    private CallSession currentSession;
    private User remoteUser;
    private InetAddress remoteAddress;
    private int remotePort;
    
    // Sockets et threads pour la communication UDP
    private DatagramSocket audioSocket;
    private Thread audioSenderThread;
    private Thread audioReceiverThread;
    private int localPort;
    
    // Lignes audio pour la capture et la lecture
    private TargetDataLine microphoneLine;
    private SourceDataLine speakerLine;
    
    // Singleton
    private static CallManager instance;
    
    private CallManager() {
        // Constructeur privé pour le singleton
    }
    
    public static synchronized CallManager getInstance() {
        if (instance == null) {
            instance = new CallManager();
        }
        return instance;
    }
    
    /**
     * Initialise un appel sortant vers un utilisateur.
     * 
     * @param targetUser L'utilisateur à appeler
     * @param session Les informations de session d'appel
     * @return true si l'initialisation a réussi
     */
    public boolean initiateCall(User targetUser, CallSession session) {
        if (isCallActive.get()) {
            return false; // Un appel est déjà en cours
        }
        
        try {
            this.remoteUser = targetUser;
            this.currentSession = session;
            
            // Initialiser le socket UDP pour être prêt à envoyer/recevoir
            initializeUdpSocket();
            
            // Le port et l'adresse seront définis lors de l'acceptation de l'appel
            updateCallStatus("Appel en cours vers " + targetUser.getDisplayNameOrEmail());
            return true;
        } catch (Exception e) {
            updateCallStatus("Erreur lors de l'initialisation de l'appel: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Initialise le socket UDP et récupère le port local.
     */
    private void initializeUdpSocket() {
        try {
            // Fermer l'ancien socket s'il existe
            if (audioSocket != null && !audioSocket.isClosed()) {
                audioSocket.close();
            }
            
            // Créer un nouveau socket avec un port spécifique
            audioSocket = new DatagramSocket(0); // 0 = port aléatoire attribué par le système
            localPort = audioSocket.getLocalPort();
            
            System.out.println("Socket UDP initialisé sur le port local: " + localPort);
        } catch (SocketException e) {
            System.err.println("Erreur lors de l'initialisation du socket UDP: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Obtient le port local utilisé par le socket UDP.
     * 
     * @return le port local
     */
    public int getLocalPort() {
        return localPort;
    }
    
    /**
     * Accepte un appel entrant et démarre la communication audio.
     * 
     * @param session Les informations de session d'appel
     * @param callerIp L'adresse IP de l'appelant
     * @param callerPort Le port UDP de l'appelant
     * @return true si l'acceptation a réussi
     */
    public boolean acceptCall(CallSession session, String callerIp, int callerPort) {
        if (isCallActive.get()) {
            return false; // Un appel est déjà en cours
        }
        
        try {
            this.currentSession = session;
            
            // Initialiser le socket UDP
            initializeUdpSocket();
            
            // Configurer l'adresse et le port distant
            this.remoteAddress = InetAddress.getByName(callerIp);
            this.remotePort = callerPort;
            
            System.out.println("Acceptation d'appel de " + callerIp + ":" + callerPort + 
                               " avec socket local sur port " + localPort);
            
            // Démarrer la communication audio
            startAudioCommunication();
            isCallActive.set(true);
            updateCallStatus("En appel avec " + remoteUser.getDisplayNameOrEmail());
            return true;
        } catch (Exception e) {
            updateCallStatus("Erreur lors de l'acceptation de l'appel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Configure la connexion après que l'autre partie a accepté l'appel.
     * 
     * @param remoteIp L'adresse IP distante
     * @param remotePort Le port UDP distant
     * @return true si la configuration a réussi
     */
    public boolean setupCallConnection(String remoteIp, int remotePort) {
        try {
            if (remotePort <= 0) {
                updateCallStatus("Erreur: Port distant invalide (" + remotePort + ")");
                return false;
            }
            
            this.remoteAddress = InetAddress.getByName(remoteIp);
            this.remotePort = remotePort;
            
            System.out.println("Configuration de la connexion d'appel avec " + remoteIp + ":" + remotePort + 
                               " depuis le port local " + localPort);
            
            // Démarrer la communication audio
            startAudioCommunication();
            isCallActive.set(true);
            updateCallStatus("En appel avec " + remoteUser.getDisplayNameOrEmail());
            return true;
        } catch (UnknownHostException e) {
            updateCallStatus("Adresse IP invalide: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Termine l'appel en cours.
     */
    public void endCall() {
        if (!isCallActive.get()) {
            return;
        }
        
        isCallActive.set(false);
        
        // Arrêter les threads audio
        if (audioSenderThread != null) {
            audioSenderThread.interrupt();
        }
        if (audioReceiverThread != null) {
            audioReceiverThread.interrupt();
        }
        
        // Fermer les lignes audio
        if (microphoneLine != null && microphoneLine.isOpen()) {
            microphoneLine.stop();
            microphoneLine.close();
        }
        if (speakerLine != null && speakerLine.isOpen()) {
            speakerLine.stop();
            speakerLine.close();
        }
        
        // Fermer le socket
        if (audioSocket != null && !audioSocket.isClosed()) {
            audioSocket.close();
        }
        
        // Réinitialiser les variables
        currentSession = null;
        remoteUser = null;
        remoteAddress = null;
        remotePort = 0;
        
        updateCallStatus("Appel terminé");
    }
    
    /**
     * Active/désactive le microphone pendant l'appel.
     * 
     * @param muted true pour couper le micro, false pour l'activer
     */
    public void setMicrophoneMuted(boolean muted) {
        isMicrophoneMuted.set(muted);
        if (microphoneLine != null && microphoneLine.isOpen()) {
            if (muted) {
                microphoneLine.stop();
            } else {
                microphoneLine.start();
            }
        }
    }
    
    /**
     * Démarre la communication audio (envoi et réception).
     */
    private void startAudioCommunication() {
        try {
            // Vérifier que le socket est initialisé
            if (audioSocket == null || audioSocket.isClosed()) {
                initializeUdpSocket();
            }
            
            // Initialiser les lignes audio
            initAudioLines();
            
            // Démarrer les threads d'envoi et de réception
            startAudioSender();
            startAudioReceiver();
        } catch (LineUnavailableException e) {
            updateCallStatus("Erreur lors de l'initialisation des lignes audio: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initialise les lignes audio pour la capture et la lecture.
     */
    private void initAudioLines() throws LineUnavailableException {
        // Ligne pour capturer l'audio du microphone
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
        if (!AudioSystem.isLineSupported(micInfo)) {
            throw new LineUnavailableException("Le format audio n'est pas supporté pour la capture");
        }
        microphoneLine = (TargetDataLine) AudioSystem.getLine(micInfo);
        microphoneLine.open(AUDIO_FORMAT, BUFFER_SIZE);
        microphoneLine.start();
        
        // Ligne pour jouer l'audio reçu
        DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
        if (!AudioSystem.isLineSupported(speakerInfo)) {
            throw new LineUnavailableException("Le format audio n'est pas supporté pour la lecture");
        }
        speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);
        speakerLine.open(AUDIO_FORMAT, BUFFER_SIZE);
        speakerLine.start();
    }
    
    /**
     * Démarre le thread d'envoi audio.
     */
    private void startAudioSender() {
        audioSenderThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[BUFFER_SIZE];
                
                while (isCallActive.get()) {
                    if (microphoneLine != null && microphoneLine.isOpen()) {
                        int bytesRead = microphoneLine.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            // Envoyer les données audio
                            sendAudioPacket(buffer);
                        }
                    }
                    Thread.sleep(5); // Petite pause pour éviter de surcharger le CPU
                }
            } catch (InterruptedException e) {
                // L'interruption est normale lors de la fin de l'appel
                if (isCallActive.get()) {
                    updateCallStatus("Erreur d'envoi audio: " + e.getMessage());
                }
            }
        });
        audioSenderThread.setDaemon(true);
        audioSenderThread.start();
    }
    
    /**
     * Démarre le thread de réception audio.
     */
    private void startAudioReceiver() {
        audioReceiverThread = new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            try {
                while (isCallActive.get() && !Thread.currentThread().isInterrupted()) {
                    // Recevoir les données audio
                    audioSocket.receive(packet);
                    
                    // Jouer les données audio reçues
                    speakerLine.write(packet.getData(), 0, packet.getLength());
                }
            } catch (IOException e) {
                // L'interruption est normale lors de la fin de l'appel
                if (isCallActive.get()) {
                    updateCallStatus("Erreur de réception audio: " + e.getMessage());
                }
            }
        });
        audioReceiverThread.setDaemon(true);
        audioReceiverThread.start();
    }
    
    /**
     * Envoie un paquet audio à l'adresse distante.
     * 
     * @param audioData Les données audio à envoyer
     */
    private void sendAudioPacket(byte[] audioData) {
        if (audioSocket == null || audioSocket.isClosed()) {
            updateCallStatus("Erreur: Socket UDP non initialisé");
            return;
        }
        
        if (remoteAddress == null) {
            updateCallStatus("Erreur: Adresse distante non définie");
            return;
        }
        
        if (remotePort <= 0) {
            updateCallStatus("Erreur d'envoi audio: Can't send to port " + remotePort);
            return;
        }
        
        try {
            DatagramPacket packet = new DatagramPacket(
                    audioData, audioData.length, remoteAddress, remotePort);
            
            audioSocket.send(packet);
            
            // Débogage - Afficher périodiquement des informations sur l'envoi
            if (Math.random() < 0.01) { // ~1% des paquets pour éviter de spammer la console
                System.out.println("Envoi d'un paquet audio de " + audioData.length + 
                                  " octets vers " + remoteAddress + ":" + remotePort + 
                                  " depuis le port local " + localPort);
            }
        } catch (IOException e) {
            // Afficher l'erreur complète pour le débogage
            System.err.println("Erreur lors de l'envoi du paquet audio: " + e.getMessage());
            e.printStackTrace();
            
            // Mettre à jour le statut de l'appel avec un message d'erreur plus précis
            updateCallStatus("Erreur d'envoi audio: " + e.getMessage());
        }
    }
    
    /**
     * Met à jour le statut de l'appel.
     */
    private void updateCallStatus(String status) {
        Platform.runLater(() -> callStatus.set(status));
    }
    
    /**
     * Vérifie si un appel est en cours.
     */
    public boolean isCallActive() {
        return isCallActive.get();
    }
    
    /**
     * Obtient la propriété de statut d'appel pour la liaison UI.
     */
    public StringProperty callStatusProperty() {
        return callStatus;
    }
    
    /**
     * Obtient la session d'appel en cours.
     */
    public CallSession getCurrentSession() {
        return currentSession;
    }
    
    /**
     * Obtient l'utilisateur distant de l'appel en cours.
     */
    public User getRemoteUser() {
        return remoteUser;
    }
    
    /**
     * Définit l'utilisateur distant pour l'appel en cours.
     */
    public void setRemoteUser(User remoteUser) {
        this.remoteUser = remoteUser;
    }
}
