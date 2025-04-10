package org.example.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.example.broker.MessageBroker;
import org.example.service.UserService;

public class ChatServer {
    private static final int PORT = 5000;

    public static void main(final String[] args) throws Exception {
        // Initialiser le broker de messages
        MessageBroker.getInstance();
        
        // Créer un service pour vérifier périodiquement les statuts
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        final UserService userService = new UserService();
        
        // Vérifier les statuts toutes les 30 secondes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                userService.synchronizeOnlineStatuses();
            } catch (Exception e) {
                System.err.println("Erreur lors de la synchronisation des statuts: " + e.getMessage());
            }
        }, 20, 30, TimeUnit.SECONDS);

        // Démarrer le serveur
        final ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for clients...");

        try {
            while (true) {
                final Socket client = server.accept();
                final ClientHandler clientHandler = new ClientHandler(client);
                new Thread(clientHandler).start();
            }
        } finally {
            scheduler.shutdown();
            server.close();
        }
    }
}