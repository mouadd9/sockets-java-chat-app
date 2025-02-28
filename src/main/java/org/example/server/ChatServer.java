package org.example.server;

import java.net.ServerSocket;
import java.net.Socket;

import org.example.broker.MessageBroker;

public class ChatServer {
    private static final int PORT = 5000;
    public static void main(final String[] args) throws Exception {
        // Initialiser le Message Broker
        MessageBroker.getInstance(); // Précharge les messages non lus
        
        System.out.println("Message Broker initialized");
        
        final ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for clients...");

        while (true) {
            final Socket client = server.accept();
            // Create a new handler for this client
            final ClientHandler clientHandler = new ClientHandler(client);
            // Start the handler in a new thread
            new Thread(clientHandler).start();
        }
    }
}