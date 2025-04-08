package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.example.broker.MessageBroker;
import org.example.dto.Credentials;
import org.example.model.Message;
import org.example.service.UserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {
    private static final String AUTH_SUCCESS = "AUTH_SUCCESS";
    private static final String AUTH_FAILED = "AUTH_FAILED";

    private final Socket clientSocket; // socket used to communicate with the client

    private final MessageBroker broker;
    private final UserService userService;
    private final ObjectMapper mapper;

    private String clientEmail;

    // input and output streams, to receive and send data over the socket
    private PrintWriter output;
    private BufferedReader input;

    private volatile boolean isConnected;

    public ClientHandler(final Socket socket) {
        this.clientSocket = socket; // this is the socket used to communicate with the client
        this.broker = MessageBroker.getInstance();
        this.userService = new UserService(); // this is used to authenticate clients
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule()); // this is used to serialize and deserialize messages
    }

    // this runs when the client handler is created, its purpose is to set up the input and outpur streams 
    // authenticate the client that initiated the connection
    @Override
    public void run() {
        try (
            Socket socket = clientSocket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
            ) {
            this.input = in;
            this.output = out;
            if (!authenticateUser()) {
                sendResponse("AUTH_FAILED");
                return;
            }

            sendResponse("AUTH_SUCCESS");
            // here we register this client handler as a listener (that listens for incomming events or messages)
            // now these messages are routed using the broker
            initializeSubscription();
            processMessages();

        } catch (final IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // this function when called, expects the client to send JSON-encoded credentials, which it verifies using the UserService
    private boolean authenticateUser() throws IOException {
        // here we deserealize credentials into a Credentials Object 
        final Credentials credentials = mapper.readValue(input.readLine(), Credentials.class);
        // we use the service to authenticate the user
        if (userService.authenticate(credentials.getEmail(), credentials.getPassword())) {
            this.clientEmail = credentials.getEmail();
            return true;
        }
        return false;
    }

    private void initializeSubscription() throws IOException {
        userService.setUserOnlineStatus(clientEmail, true);
        // broker.unregisterListener(clientEmail);
        broker.registerListener(clientEmail, this);
        isConnected = true;
    }

    private void processMessages() throws IOException {
        String messageJson;
        while (isConnected && (messageJson = input.readLine()) != null) {
            try {
                final Message message = mapper.readValue(messageJson, Message.class);
                
                if ("CHAT".equals(message.getType())) {
                    broker.sendMessage(message);
                } else if ("LOGOUT".equals(message.getType())) {
                    terminateSession();
                }
            } catch (final IOException e) {
                System.out.println("Invalid message format: " + messageJson);
            }
        }
    }

    public void onMessageReceived(final Message message) throws IOException {
        output.println(mapper.writeValueAsString(message));
    }

    // this sends a response to the client associated to this client handler
    private void sendResponse(final String response) {
        output.println(response);
    }

    private void terminateSession() {
        isConnected = false;
    }

    private void cleanup() {
        if (clientEmail != null) {
            try {
                broker.unregisterListener(clientEmail);
                userService.setUserOnlineStatus(clientEmail, false);
            } catch (final IOException e) {
                System.err.println("Error setting user offline status: " + e.getMessage());
            }
        }
    }
}