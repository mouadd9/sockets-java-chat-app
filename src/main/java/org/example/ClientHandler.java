package org.example;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.example.Entities.Credentials;
import org.example.Entities.Message;
import org.example.service.MessageService;
import org.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private String clientEmail;
    private PrintWriter out;
    private BufferedReader in;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final UserService userService;
    
    // Map to store all online clients (shared between all handlers)
    private static Map<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();
    
    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.messageService = new MessageService();
        this.userService = new UserService();
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            // Step 1: Handle Authentication
            String jsonCredentials = in.readLine();
            Credentials credentials = objectMapper.readValue(jsonCredentials, Credentials.class);
            
            if (userService.authenticateUser(credentials.getEmail(), credentials.getPassword())) {
                // Authentication successful
                setClientEmail(credentials.getEmail());
                out.println("AUTH_SUCCESS");
                
                // Send any offline messages
                sendOfflineMessages();
                
                // Start handling messages
                handleMessages();
            } else {
                out.println("AUTH_FAILED");
                clientSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Client disconnected during authentication: " + e.getMessage());
        } finally {
            handleDisconnection();
        }
    }

    private void sendOfflineMessages() {
        try {
            List<Message> offlineMessages = messageService.getUnreadMessages(clientEmail);
            for (Message message : offlineMessages) {
                String jsonMessage = objectMapper.writeValueAsString(message);
                sendMessage(jsonMessage);
            }
        } catch (IOException e) {
            System.out.println("Error sending offline messages: " + e.getMessage());
        }
    }

    private void handleMessages() {
        try {
            String messageData;
            while ((messageData = in.readLine()) != null) {
                try {
                    Message message = objectMapper.readValue(messageData, Message.class);
                    
                    switch (message.getType()) {
                        case "CHAT":
                            handleChatMessage(message);
                            break;
                        case "LOGOUT":
                            handleLogout();
                            return;
                        default:
                            System.out.println("Unknown message type: " + message.getType());
                    }
                } catch (IOException e) {
                    System.out.println("Error processing message: " + e.getMessage());
                    sendMessage("{\"type\":\"ERROR\",\"content\":\"Invalid message format\"}");
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + clientEmail);
        }
    }

    private void handleChatMessage(Message message) throws IOException {
        // Save to database
        messageService.sendMessage(message);
        
        // Check if receiver is online
        ClientHandler receiverHandler = onlineClients.get(message.getReceiverEmail());
        if (receiverHandler != null) {
            // Forward message
            String jsonMessage = objectMapper.writeValueAsString(message);
            receiverHandler.sendMessage(jsonMessage);
            
            // Create confirmation message
            Message confirmation = new Message(null, message.getSenderEmail(), null);
            confirmation.setType("CONFIRMATION");
            confirmation.setStatus("delivered");
            confirmation.setId(message.getId());
            sendMessage(objectMapper.writeValueAsString(confirmation));
        } else {
            // Create queued confirmation
            Message confirmation = new Message(null, message.getSenderEmail(), null);
            confirmation.setType("CONFIRMATION");
            confirmation.setStatus("queued");
            confirmation.setId(message.getId());
            sendMessage(objectMapper.writeValueAsString(confirmation));
        }
    }

    private void handleLogout() {
        try {
            userService.setUserOnlineStatus(clientEmail, false);
            sendMessage("{\"type\":\"LOGOUT_CONFIRM\"}");
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("Error during logout: " + e.getMessage());
        }
    }

    private void handleDisconnection() {
        try {
            if (clientEmail != null) {
                userService.setUserOnlineStatus(clientEmail, false);
                onlineClients.remove(clientEmail);
                System.out.println("Client disconnected and removed: " + clientEmail);
            }
            if (!clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error during disconnection: " + e.getMessage());
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public void setClientEmail(String email) throws IOException {
        this.clientEmail = email;
        onlineClients.put(email, this);
        userService.setUserOnlineStatus(email, true);
        System.out.println("Client registered: " + email);
    }
}

/*
ClientHandler Flow Schema:

1. Client Connection:
   Client Socket ----connects----> Server Socket
                                      │
                                      ▼
                                 Create ClientHandler
                                      │
                                      ▼
                                 Start Thread

2. Authentication Flow:
   Client ----sends credentials----> ClientHandler
                                      │
                                      ▼
                                 Validate User
                                      │
                           ┌─────────┴──────────┐
                           ▼                     ▼
                     AUTH_SUCCESS           AUTH_FAILED
                           │                     │
                           ▼                     ▼
                    Add to onlineClients    Close Connection
                           │
                           ▼
                    Send Offline Messages

3. Message Handling Flow:
   a) Sending Message:
      Client A ----sends message----> ClientHandler A
                                          │
                                     Save to DB
                                          │
                                    Check Receiver
                                          │
                            ┌─────────────┴────────────┐
                            ▼                          ▼
                     Receiver Online             Receiver Offline
                            │                          │
                    Forward Message              Queue Message
                            │                          │
                    Send "delivered"            Send "queued"
                    to sender                   to sender

   b) Receiving Message:
      ClientHandler A ----forwards----> ClientHandler B
                                           │
                                           ▼
                                     Send to Client B

4. Disconnection Flow:
   Client ----closes/crashes----> ClientHandler
                                      │
                                      ▼
                                Set User Offline
                                      │
                                      ▼
                            Remove from onlineClients
                                      │
                                      ▼
                                Close Socket

Static Data Structure:
onlineClients = {
    "user1@email.com": ClientHandler1,
    "user2@email.com": ClientHandler2,
    ...
}

Message Types:
1. CHAT: Regular chat message
2. LOGOUT: Client logout request
3. CONFIRMATION: Message delivery status
4. ERROR: Error notifications
*/