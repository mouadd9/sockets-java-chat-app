package org.example;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.Entities.Credentials;
import org.example.Entities.Message;

public class ClientTCP {
    private static final String ADRESSE_SERVEUR = "localhost";
    private static final int PORT = 5000;
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    private static Socket socket;
    private static PrintWriter envoyeur;
    private static BufferedReader recepteur;
    private static String currentUserEmail; // Store authenticated user's email
  private static AtomicBoolean isRunning = new AtomicBoolean(true);
    public static void main(String[] args) {
        try {
            // Initialize connection
            socket = new Socket(ADRESSE_SERVEUR, PORT);
            envoyeur = new PrintWriter(socket.getOutputStream(), true);
            recepteur = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Get user credentials
            Scanner scanner = new Scanner(System.in);
            System.out.println("\n🔐 Login");
            System.out.println("─────────────────");
            System.out.print("Email: ");
            String email = scanner.nextLine();
            System.out.print("Password: ");
            String password = scanner.nextLine();

            // Attempt authentication
            if (authenticate(email, password)) {
                System.out.println("\n✅ Authentication successful!");
                currentUserEmail = email; // Store email for later use

                // Start message listener thread
                startMessageListener();

                // Start main session
                handleUserSession(scanner);
            } else {
                System.out.println("\n❌ Authentication failed!");
            }

        } catch (IOException e) {
            System.out.println("❌ Connection error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private static void startMessageListener() {
        Thread messageListener = new Thread(() -> {
            try {
                String incomingMessage;
                while (isRunning.get() && (incomingMessage = recepteur.readLine()) != null) {
                    handleIncomingMessage(incomingMessage);
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    System.out.println("\n❌ Lost connection to server!");
                }
            }
        });
        messageListener.setDaemon(true);
        messageListener.start();
    }

    private static void handleIncomingMessage(String jsonMessage) {
        try {
            Message message = objectMapper.readValue(jsonMessage, Message.class);
            switch (message.getType()) {
                case "CHAT":
                    System.out.println("\n📨 Message from " + message.getSenderEmail() + ":");
                    System.out.println("   " + message.getContent());
                    printPrompt();
                    break;
                case "CONFIRMATION":
                    String status = message.getStatus();
                    if ("delivered".equals(status)) {
                        System.out.println("\n✓ Message delivered");
                    } else if ("queued".equals(status)) {
                        System.out.println("\n⏳ Message queued (recipient offline)");
                    }
                    printPrompt();
                    break;
                case "ERROR":
                    System.out.println("\n❌ Error: " + message.getContent());
                    printPrompt();
                    break;
                case "LOGOUT_CONFIRM":
                    isRunning.set(false);
                    break;
            }
        } catch (IOException e) {
            System.out.println("\n❌ Error processing message: " + e.getMessage());
            printPrompt();
        }
    }


    private static void handleUserSession(Scanner scanner) {
        while (isRunning.get()) {
            printMenu();
            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    sendMessage(scanner);
                    break;
                case "2":
                    logout();
                    return;
                default:
                    System.out.println("\n❌ Invalid choice. Please try again.");
            }
        }
    }

    private static void sendMessage(Scanner scanner) {
        try {
            System.out.println("\n✍️ New Message");
            System.out.println("─────────────────");
            System.out.print("To (email): ");
            String receiverEmail = scanner.nextLine();
            
            if (receiverEmail.equalsIgnoreCase("back")) return;

            System.out.print("Message: ");
            String content = scanner.nextLine();
            
            if (content.equalsIgnoreCase("back")) return;

            Message message = new Message(currentUserEmail, receiverEmail, content);
            message.setType("CHAT");
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            envoyeur.println(jsonMessage);
            System.out.println("\n📤 Sending message...");
            
        } catch (IOException e) {
            System.out.println("\n❌ Error sending message: " + e.getMessage());
        }
    }

   
    private static boolean authenticate(String email, String password) throws IOException {
        // Create credentials object
        Credentials credentials = new Credentials(email, password);
        
        // Convert to JSON and send
        String jsonCredentials = objectMapper.writeValueAsString(credentials);
        envoyeur.println(jsonCredentials);
        
        // Wait for server response
        String response = recepteur.readLine();
        return "AUTH_SUCCESS".equals(response);
    }

    private static void logout() {
        try {
            Message logoutMsg = new Message(currentUserEmail, null, null);
            logoutMsg.setType("LOGOUT");
            envoyeur.println(objectMapper.writeValueAsString(logoutMsg));
            System.out.println("\n👋 Logging out...");
        } catch (IOException e) {
            System.out.println("\n❌ Error during logout: " + e.getMessage());
        }
    }

    private static void cleanup() {
        try {
            isRunning.set(false);
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Error during cleanup: " + e.getMessage());
        }
    }

    private static void printMenu() {
        System.out.println("\n📱 Chat Menu");
        System.out.println("─────────────────");
        System.out.println("1. Send message");
        System.out.println("2. Logout");
        System.out.print("\nChoice (1-2): ");
    }

    private static void printPrompt() {
        System.out.print("\nChoice (1-2): ");
    }
}
