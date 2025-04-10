package org.example.server;

import java.net.ServerSocket;
import java.net.Socket;

import org.example.dao.UserDAO;
import org.example.model.User;

public class ChatServer {
    private static final int PORT = 5000;
    public static void main(final String[] args) throws Exception {

        final ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for clients...");

        while (true) {
            final Socket client = server.accept();
            final ClientHandler clientHandler = new ClientHandler(client);
            new Thread(clientHandler).start();
        }
    }
}