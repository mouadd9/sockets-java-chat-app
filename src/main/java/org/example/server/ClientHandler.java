package org.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

import org.example.server.broker.MessageBroker;
import org.example.shared.dao.UserDAO;
import org.example.shared.dto.Credentials;
import org.example.shared.dto.RegistrationDTO;
import org.example.shared.model.CallSignal;
import org.example.shared.model.Message;
import org.example.shared.model.User;
import org.example.shared.model.enums.MessageType;
import org.example.shared.util.PasswordUtils;
import org.example.shared.util.ValidationUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
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
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            this.input = in;
            this.output = out;

            final String requestType = input.readLine();
            if (requestType == null) return;

            if ("REGISTER".equals(requestType)) {
                handleRegistration();
                return;
            } else if ("LOGIN".equals(requestType)) {
                if (!authenticateUser()) {
                    sendResponse("AUTH_FAILED");
                    return;
                }
                sendResponse("AUTH_SUCCESS");

                handleUserLogin(clientEmail);
                initializeSubscription();
                processMessages();
            } else {
                System.out.println("Unknown request type: " + requestType);
                sendResponse("UNKNOWN_REQUEST_TYPE");
                return;
            }
        } catch (final IOException e) {
            System.out.println("Client connection error for " + (clientEmail != null ? clientEmail : clientSocket.getInetAddress()) + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleRegistration() throws IOException {
        try {
            final RegistrationDTO registrationDTO = mapper.readValue(input.readLine(), RegistrationDTO.class);

            if (!ValidationUtils.isValidEmail(registrationDTO.getEmail())) {
                sendResponse("Invalid email format");
                return;
            }

            if (!ValidationUtils.isStrongPassword(registrationDTO.getPassword())) {
                sendResponse("Password does not meet security criteria");
                return;
            }

            if (!ValidationUtils.doPasswordsMatch(registrationDTO.getPassword(), registrationDTO.getPasswordConfirmation())) {
                sendResponse("Passwords do not match");
                return;
            }

            final User existingUser = userDAO.findUserByEmail(registrationDTO.getEmail());
            if (existingUser != null) {
                sendResponse("REGISTER_FAILURE_EMAIL_EXISTS");
                return;
            }

            final User newUser = new User();
            newUser.setEmail(registrationDTO.getEmail());
            newUser.setDisplayName(registrationDTO.getEmail().split("@")[0]);
            newUser.setPasswordHash(PasswordUtils.hashPassword(registrationDTO.getPassword()));
            newUser.setCreatedAt(LocalDateTime.now());
            newUser.setOnline(false);

            userDAO.createUser(newUser);

            sendResponse("REGISTER_SUCCESS");
        } catch (final Exception e) {
            System.out.println("Registration error: " + e.getMessage());
            e.printStackTrace();
            sendResponse("Registration error: " + e.getMessage());
        }
    }

    private boolean authenticateUser() throws IOException {
        final String jsonCredentials = input.readLine();
        if (jsonCredentials == null) return false;

        try {
            final Credentials credentials = mapper.readValue(jsonCredentials, Credentials.class);
            final User user = userDAO.findUserByEmail(credentials.getEmail());
            if (user != null && PasswordUtils.verifyPassword(credentials.getPassword(), user.getPasswordHash())) {
                this.clientEmail = user.getEmail();
                this.clientId = user.getId();
                System.out.println("Authentication successful for " + clientEmail + " (ID: " + clientId + ")");
                return true;
            } else {
                System.out.println("Authentication failed for " + credentials.getEmail());
                return false;
            }
        } catch (final JsonProcessingException e) {
            System.err.println("Error parsing JSON credentials: " + e.getMessage());
            sendResponse("AUTH_ERROR_FORMAT");
            return false;
        } catch (final Exception e) {
            System.err.println("Error during authentication: " + e.getMessage());
            sendResponse("AUTH_ERROR_SERVER");
            e.printStackTrace();
            return false;
        }
    }

    private void initializeSubscription() throws IOException {
        broker.registerListener(clientId, this);
        isConnected = true;
    }

    private void processMessages() throws IOException {
        String jsonData;
        while (isConnected && (jsonData = input.readLine()) != null) {
            try {
                // Vérifier d'abord si c'est un signal d'appel basé sur le JSON brut
                if (jsonData.contains("\"type\":\"CALL_")) {
                    final CallSignal signal = mapper.readValue(jsonData, CallSignal.class);
                    broker.routeCallSignal(signal);
                    continue; // Passer au prochain message après avoir traité le signal d'appel
                }

                // Si ce n'est pas un signal d'appel, traiter comme un Message normal
                final Message message = mapper.readValue(jsonData, Message.class);
                message.setSenderUserId(this.clientId);

                // Vérifier si c'est un message de déconnexion par son contenu
                if ("LOGOUT".equalsIgnoreCase(message.getContent()) && message.getType() == MessageType.SYSTEM) {
                    handleUserLogout(clientEmail);
                    terminateSession();
                    sendResponse("LOGOUT_SUCCESS");
                    return;
                }

                // Gérer les autres types de messages via le switch
                switch (message.getType()) {
                    case PUBLIC_KEY_REQUEST:
                        handlePublicKeyRequest(message);
                        break;
                    case PUBLIC_KEY_RESPONSE:
                        handleClientPublicKeyUpdate(message);
                        break;
                    case E2E_SESSION_INIT:
                    case TEXT:
                    case IMAGE:
                    case VIDEO:
                    case AUDIO:
                    case DOCUMENT:
                        if (message.isMediaMessage()) {
                            processMediaMessage(message);
                        }
                        broker.sendMessage(message);
                        break;
                    case SYSTEM:
                        System.out.println("SYSTEM message received from client ID " + message.getSenderUserId() + " (content: " + message.getContent() + ") - Ignored unless LOGOUT.");
                        break;
                    default:
                        System.out.println("Unknown or unhandled message type received: " + message.getType());
                }
            } catch (final JsonProcessingException e) {
                System.err.println("Error parsing JSON from " + clientEmail + ": " + e.getMessage() + " | JSON: " + jsonData);
            } catch (final IOException e) {
                System.err.println("IO error processing message from " + clientEmail + ": " + e.getMessage());
                throw e;
            } catch (final Exception e) {
                System.err.println("Unexpected error processing message from " + clientEmail + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void processMediaMessage(final Message message) {
        try {
            fileService.ensureMediaDirectoriesExist();
            System.out.println("Processing media message: " + message.getType() +
                    ", File: " + (message.getFileName() != null ? message.getFileName() : "Unknown"));
        } catch (final Exception e) {
            System.err.println("Error processing media message: " + e.getMessage());
        }
    }

    private void handlePublicKeyRequest(final Message requestMessage) {
        try {
            final long targetUserId = Long.parseLong(requestMessage.getContent());
            final long requesterUserId = requestMessage.getSenderUserId();

            System.out.println("Public key request for ID " + targetUserId + " by ID " + requesterUserId);

            final Optional<String> publicKeyOpt = userDAO.getPublicKey(targetUserId);

            if (publicKeyOpt.isPresent() && publicKeyOpt.get() != null && !publicKeyOpt.get().isEmpty()) {
                final Message response = new Message();
                response.setType(MessageType.PUBLIC_KEY_RESPONSE);
                response.setSenderUserId(targetUserId);
                response.setReceiverUserId(requesterUserId);
                response.setContent(publicKeyOpt.get());
                response.setTimestamp(LocalDateTime.now());

                sendMessageToClient(response);
                System.out.println("Public key for ID " + targetUserId + " sent to ID " + requesterUserId);
            } else {
                System.out.println("Public key not found or empty for ID " + targetUserId);
            }
        } catch (final NumberFormatException e) {
            System.err.println("Invalid target ID in PUBLIC_KEY_REQUEST from ID " + requestMessage.getSenderUserId() + ": " + requestMessage.getContent());
        } catch (final Exception e) {
            System.err.println("Error processing PUBLIC_KEY_REQUEST from ID " + requestMessage.getSenderUserId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleClientPublicKeyUpdate(final Message message) {
        final String publicKeyString = message.getContent();
        final long senderId = message.getSenderUserId();

        if (senderId != this.clientId) {
            System.err.println("Attempt to update public key by ID " + senderId + " for connected client ID " + this.clientId + ". Ignored.");
            return;
        }

        if (publicKeyString != null && !publicKeyString.isEmpty()) {
            final boolean success = userDAO.updatePublicKey(this.clientId, publicKeyString);
            if (success) {
                System.out.println("Public key stored/updated for ID " + this.clientId);
            } else {
                System.err.println("Failed to store public key for ID " + this.clientId);
            }
        } else {
            System.err.println("Empty public key received from ID " + this.clientId);
        }
    }

    public void sendMessageToClient(final Message message) {
        if (output != null && !clientSocket.isClosed() && isConnected) {
            try {
                final String jsonMessage = mapper.writeValueAsString(message);
                output.println(jsonMessage);
            } catch (final JsonProcessingException e) {
                System.err.println("Error serializing JSON for direct send to ID " + clientId + ": " + e.getMessage());
            } catch (final Exception e) {
                System.err.println("Unknown error during direct send to ID " + clientId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Attempt to send direct message to disconnected or uninitialized client (ID: " + clientId + ")");
        }
    }

    public void onMessageReceived(final Message message) throws IOException {
        sendMessageToClient(message);
    }

    public void onCallSignalReceived(final CallSignal signal) throws IOException {
        if (output != null && !clientSocket.isClosed() && isConnected) {
            try {
                final String jsonSignal = mapper.writeValueAsString(signal);
                output.println(jsonSignal);
            } catch (final JsonProcessingException e) {
                System.err.println("Error serializing JSON for call signal to ID " + clientId + ": " + e.getMessage());
            } catch (final Exception e) {
                System.err.println("Unknown error during call signal send to ID " + clientId + ": " + e.getMessage());
            }
        } else {
            System.err.println("Attempt to send call signal to disconnected or uninitialized client (ID: " + clientId + ")");
        }
    }

    private void sendResponse(final String response) {
        if (output != null) {
            output.println(response);
        }
    }

    private void terminateSession() {
        isConnected = false;
    }

    private void cleanup() {
        if (!isConnected) {
            return;
        }
        isConnected = false;
        System.out.println("Cleanup for " + (clientEmail != null ? clientEmail : clientSocket.getInetAddress()));

        if (clientId > 0) {
            broker.unregisterListener(clientId);
            handleUserLogout(clientEmail);
        }

        try {
            if (input != null) input.close();
        } catch (final IOException e) { }
        if (output != null) output.close();
        try {
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (final IOException e) { }
        System.out.println("Resources closed for " + (clientEmail != null ? clientEmail : "unauthenticated client"));
    }

    public void handleUserLogin(final String email) {
        try {
            final User user = userDAO.findUserByEmail(email);
            if (user != null) {
                user.setOnline(true);
                final boolean success = userDAO.updateUser(user);
                if (!success) {
                    System.err.println("Failed to update online status for: " + email);
                }
            }
        } catch (final Exception e) {
            System.err.println("Error updating online status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void handleUserLogout(final String email) {
        try {
            final User user = userDAO.findUserByEmail(email);
            if (user != null) {
                user.setOnline(false);
                final boolean success = userDAO.updateUser(user);
                if (!success) {
                    System.err.println("Failed to update offline status for: " + email);
                }
            }
        } catch (final Exception e) {
            System.err.println("Error updating offline status: " + e.getMessage());
            e.printStackTrace();
        }
    }
}