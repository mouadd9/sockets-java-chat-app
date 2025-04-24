package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.example.server.broker.MessageBroker;
import org.example.shared.dao.UserDAO;
import org.example.shared.dto.Credentials;
import org.example.shared.model.Message;
import org.example.shared.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final MessageBroker broker;
    private final UserDAO userDAO;
    private final ObjectMapper mapper;
    private final ServerFileService fileService;

    private String clientEmail;
    private long clientId;
    private PrintWriter output;
    private BufferedReader input;
    private volatile boolean isConnected;

    public ClientHandler(final Socket socket) {
        this.clientSocket = socket;
        this.broker = MessageBroker.getInstance();
        this.userDAO = new UserDAO();
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.fileService = new ServerFileService();
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            this.input = in;
            this.output = out;

            if (!authenticateUser()) {
                sendResponse("AUTH_FAILED");
                return;
            }
            sendResponse("AUTH_SUCCESS");
            
            initializeSubscription();
            processMessages();
        } catch (final IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private boolean authenticateUser() throws IOException {
        final Credentials credentials = mapper.readValue(input.readLine(), Credentials.class);
        final User user = userDAO.findUserByEmail(credentials.getEmail());
        if (user != null && user.getPasswordHash().equals(credentials.getPassword())) {
            this.clientEmail = user.getEmail();
            this.clientId = user.getId();
            return true;
        }
        return false;
    }

    private void initializeSubscription() throws IOException {
        broker.registerListener(clientId, this);
        isConnected = true;
    }

    private void processMessages() throws IOException {
        String messageJson;
        while (isConnected && (messageJson = input.readLine()) != null) {
            try {
                final Message message = mapper.readValue(messageJson, Message.class);
                if ("LOGOUT".equalsIgnoreCase(message.getContent())) {
                    terminateSession();
                } else {
                    // Process the message
                    if (message.isMediaMessage()) {
                        processMediaMessage(message);
                    }
                    broker.sendMessage(message);
                }
            } catch (final IOException e) {
                System.out.println("Invalid message format: " + messageJson);
            }
        }
    }

    /**
     * Processes a media message by ensuring the file is available on the server
     * for all clients to access.
     *
     * @param message The media message
     */
    private void processMediaMessage(Message message) {
        try {
            // For most cases, the client will have already saved the file locally
            // and the server will use the same path. However, we need to make sure
            // the directory structure exists on the server.

            // Ensure the corresponding media directory exists based on type
            fileService.ensureMediaDirectoriesExist();

            System.out.println("Processing media message: " + message.getType() +
                    ", File: " + (message.getFileName() != null ? message.getFileName() : "Unknown"));

        } catch (Exception e) {
            System.err.println("Error processing media message: " + e.getMessage());
        }
    }

    public void onMessageReceived(final Message message) throws IOException {
        output.println(mapper.writeValueAsString(message));
    }

    private void sendResponse(final String response) {
        output.println(response);
    }

    private void terminateSession() {
        isConnected = false;
    }

    private void cleanup() {
        if (clientEmail != null) {
            broker.unregisterListener(clientId);
        }
    }
}