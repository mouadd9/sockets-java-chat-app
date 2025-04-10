package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.example.broker.MessageBroker;
import org.example.dao.UserDAO;
import org.example.dto.Credentials;
import org.example.model.Message;
import org.example.model.User;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final MessageBroker broker;
    private final UserDAO userDAO;
    private final ObjectMapper mapper;

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
    }

    @Override
    public void run() {
        try (
                Socket socket = clientSocket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
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
                    broker.sendMessage(message);
                }
            } catch (final IOException e) {
                System.out.println("Invalid message format: " + messageJson);
            }
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