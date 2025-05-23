package org.example.server;

import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {
    private static final int PORT = 5000;
    public static void main(final String[] args) throws Exception {
        // Démarrer le serveur UDP pour les appels audio
        UdpCallServer.getInstance().start();
        
        final ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server TCP started on port " + PORT);
        System.out.println("Waiting for clients...");

        while (true) {
            final Socket client = server.accept();
            final ClientHandler clientHandler = new ClientHandler(client);
            new Thread(clientHandler).start();
        }
    }
}