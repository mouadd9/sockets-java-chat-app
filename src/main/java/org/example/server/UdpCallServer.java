package org.example.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serveur UDP pour relayer les paquets audio entre clients.
 * Utilisé comme solution de secours quand la connexion directe entre clients échoue.
 */
public class UdpCallServer {
    private static final int UDP_PORT = 5001;
    private static final int BUFFER_SIZE = 4096;
    
    private DatagramSocket socket;
    private final ExecutorService threadPool;
    private final Map<String, CallSession> activeSessions;
    private volatile boolean running;
    
    // Singleton
    private static UdpCallServer instance;
    
    /**
     * Représente une session d'appel active avec les informations des deux clients.
     */
    private static class CallSession {
        private final String sessionId;
        private InetAddress caller;
        private int callerPort;
        private InetAddress receiver;
        private int receiverPort;
        
        public CallSession(String sessionId) {
            this.sessionId = sessionId;
        }
        
        public void setCallerEndpoint(InetAddress address, int port) {
            this.caller = address;
            this.callerPort = port;
        }
        
        public void setReceiverEndpoint(InetAddress address, int port) {
            this.receiver = address;
            this.receiverPort = port;
        }
        
        public boolean isComplete() {
            return caller != null && receiver != null;
        }
        
        public boolean isFromCaller(InetAddress address, int port) {
            return address.equals(caller) && port == callerPort;
        }
        
        public boolean isFromReceiver(InetAddress address, int port) {
            return address.equals(receiver) && port == receiverPort;
        }
        
        public String getSessionId() {
            return sessionId;
        }
    }
    
    private UdpCallServer() {
        this.threadPool = Executors.newCachedThreadPool();
        this.activeSessions = new ConcurrentHashMap<>();
    }
    
    public static synchronized UdpCallServer getInstance() {
        if (instance == null) {
            instance = new UdpCallServer();
        }
        return instance;
    }
    
    /**
     * Démarre le serveur UDP.
     */
    public void start() {
        if (running) {
            return;
        }
        
        try {
            socket = new DatagramSocket(UDP_PORT);
            running = true;
            
            System.out.println("Serveur UDP démarré sur le port " + UDP_PORT);
            
            // Démarrer le thread principal pour recevoir les paquets
            threadPool.execute(this::receivePackets);
        } catch (SocketException e) {
            System.err.println("Erreur lors du démarrage du serveur UDP: " + e.getMessage());
        }
    }
    
    /**
     * Arrête le serveur UDP.
     */
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        threadPool.shutdown();
        activeSessions.clear();
    }
    
    /**
     * Enregistre une nouvelle session d'appel.
     * 
     * @param sessionId ID unique de la session
     * @return true si la session a été créée avec succès
     */
    public boolean registerSession(String sessionId) {
        if (activeSessions.containsKey(sessionId)) {
            return false;
        }
        activeSessions.put(sessionId, new CallSession(sessionId));
        return true;
    }
    
    /**
     * Enregistre un point de terminaison pour une session d'appel.
     * 
     * @param sessionId ID de la session
     * @param isCaller true si c'est l'appelant, false si c'est le destinataire
     * @param address adresse IP du client
     * @param port port UDP du client
     * @return true si l'enregistrement a réussi
     */
    public boolean registerEndpoint(String sessionId, boolean isCaller, InetAddress address, int port) {
        CallSession session = activeSessions.get(sessionId);
        if (session == null) {
            return false;
        }
        
        if (isCaller) {
            session.setCallerEndpoint(address, port);
        } else {
            session.setReceiverEndpoint(address, port);
        }
        
        return true;
    }
    
    /**
     * Supprime une session d'appel.
     * 
     * @param sessionId ID de la session à supprimer
     */
    public void removeSession(String sessionId) {
        activeSessions.remove(sessionId);
    }
    
    /**
     * Boucle principale pour recevoir et relayer les paquets.
     */
    private void receivePackets() {
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        
        while (running) {
            try {
                // Réinitialiser le paquet pour la prochaine réception
                packet.setLength(buffer.length);
                
                // Recevoir un paquet
                socket.receive(packet);
                
                // Traiter le paquet dans un thread séparé
                final DatagramPacket receivedPacket = new DatagramPacket(
                        packet.getData().clone(),
                        packet.getLength(),
                        packet.getAddress(),
                        packet.getPort());
                
                threadPool.execute(() -> processPacket(receivedPacket));
                
            } catch (IOException e) {
                if (running) {
                    System.err.println("Erreur lors de la réception d'un paquet UDP: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Traite un paquet reçu et le relaie au destinataire approprié.
     * 
     * @param packet Le paquet reçu
     */
    private void processPacket(DatagramPacket packet) {
        // Extraire les informations du paquet
        InetAddress sourceAddress = packet.getAddress();
        int sourcePort = packet.getPort();
        
        // Trouver la session correspondante
        for (CallSession session : activeSessions.values()) {
            if (!session.isComplete()) {
                continue;
            }
            
            try {
                // Déterminer la destination en fonction de la source
                if (session.isFromCaller(sourceAddress, sourcePort)) {
                    // Relayer du caller vers le receiver
                    DatagramPacket forwardPacket = new DatagramPacket(
                            packet.getData(),
                            packet.getLength(),
                            session.receiver,
                            session.receiverPort);
                    socket.send(forwardPacket);
                    return;
                } else if (session.isFromReceiver(sourceAddress, sourcePort)) {
                    // Relayer du receiver vers le caller
                    DatagramPacket forwardPacket = new DatagramPacket(
                            packet.getData(),
                            packet.getLength(),
                            session.caller,
                            session.callerPort);
                    socket.send(forwardPacket);
                    return;
                }
            } catch (IOException e) {
                System.err.println("Erreur lors du relais d'un paquet UDP: " + e.getMessage());
            }
        }
    }
}
