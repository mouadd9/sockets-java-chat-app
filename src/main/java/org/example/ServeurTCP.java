package org.example;

import java.net.*;

public class ServeurTCP {
    private static final int PORT = 5000;
    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for clients...");

        while (true) {
            Socket client = server.accept();
            // Create a new handler for this client
            ClientHandler clientHandler = new ClientHandler(client);
            // Start the handler in a new thread
            new Thread(clientHandler).start();
        }
    }
}

/*
 Prerequisits: 
  - What is a Port ? 
  a port is a numbered entry point to your computer, each port can be used to access your machine
  - What is a Socket and a ServerSocket ?
  a socket is an endpoint for sending and receiving data across the network.
  a socket is created only during the lifetime of a process of an application.
  the process uses an API to create a handle for each socket created.
  when created with the API a socket is bound to the combination of a type of network protocol (TCP/IP by default) and a port number.

 How does the communication happen ? 
 an application can communicate with a remote process by exchanging data with TCP/IP 
 by knowing the combination of protocol type, IP address, and port number.
 this conbination is often known as a socket address. 

 ServerSocket:
- Only listens for connections
- Can't send/receive data
- Creates new Sockets for communication

Socket:
- Actually handles the data transfer
- Can both send and receive
- One Socket per connection

  The server creates a socket Socket(), attaches it to a network port addresse Bind()
  then waits for the client to contact it Listen().


*/

/*public class ServeurTCP {
    private static final int PORT = 5000; // the port where the server will run the socket
    private static int clientID = 0;
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {

        // this is the Socket created by the server and bound to port
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);
        System.out.println("Waiting for clients...");

        // Boucle infinie pour accepter plusieurs clients
        while (true) {
            // Attendre qu'un client se connecte
            Socket client = server.accept();
            clientID++;

            // Afficher les informations de connexion
            System.out.println("Client #" + clientID + " connected!");
            
            // Créer un nouveau thread pour gérer ce client
            Thread threadClient = new Thread(() -> gererClient(client));
            threadClient.start();
        }
    }

    // Méthode pour gérer chaque client dans un thread séparé
    private static void gererClient(Socket client) {
        try {
            // Setup streams
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true);
            UserService userService = new UserService();
            MessageService messageService = new MessageService();

            // Handle authentication
            String jsonCredentials = in.readLine();
            System.out.println("DEBUG: Received credentials from client #" + clientID);

            try {
                // Parse JSON to Credentials object
                Credentials credentials = objectMapper.readValue(jsonCredentials, Credentials.class);
                
                // Attempt authentication
                boolean authenticated = userService.authenticateUser(
                    credentials.getEmail(), 
                    credentials.getPassword()
                );

                if (authenticated) {
                    System.out.println("DEBUG: Client #" + clientID + " (" + credentials.getEmail() + ") authenticated successfully");
                    out.println("AUTH_SUCCESS");

                    // Handle post-authentication communication
                    System.out.println("DEBUG: Starting message handling loop for client #" + clientID);
                    String messageData;
                    while ((messageData = in.readLine()) != null) {
                        System.out.println("DEBUG: Received message data from client #" + clientID + ": " + messageData);
                        try {
                            System.out.println("data received from client : " + messageData );
                            // Parse JSON to Message object
                            Message message = objectMapper.readValue(messageData, Message.class);
                            System.out.println("DEBUG: Parsed message object: " + message.getContent());
                            
                            switch (message.getType()) {
                                case "CHAT":
                                    System.out.println("DEBUG: Processing CHAT message");
                                    // Save message to database
                                    boolean sent = messageService.sendMessage(
                                        message
                                    );

                                    if (sent) {
                                        System.out.println("DEBUG: Message saved successfully");
                                        System.out.println(" Message saved: " + message.getSenderEmail() + " -> " + message.getReceiverEmail());
                                        System.out.println(" Message sent successfully!");
                                    } else {
                                        System.out.println("DEBUG: Failed to save message");
                                        System.out.println(" Failed to send message. Invalid sender or receiver.");
                                    }
                                    break;
                                    
                                case "LOGOUT":
                                    System.out.println(" Client #" + clientID + " (" + credentials.getEmail() + ") logged out");
                                    userService.setUserOnlineStatus(credentials.getEmail(), false);
                                    client.close();
                                    return;
                                    
                                default:
                                    System.out.println(" Unknown message type: " + message.getType());
                                    out.println(" Unknown message type");
                            }
                            
                        } catch (Exception e) {
                            System.out.println("Error processing message: " + e.getMessage());
                            e.printStackTrace(); // Add stack trace
                            System.out.println("Received message data: " + messageData); // Print received data
                            out.println(" Error processing message: " + e.getMessage());
                        }
                    }
                    client.close();
                } else {
                    System.out.println("Client #" + clientID + " authentication failed");
                    out.println("AUTH_FAILED");
                    client.close();
                }

            } catch (Exception e) {
                System.out.println("Error processing credentials: " + e.getMessage());
                out.println("AUTH_FAILED");
                client.close();
            }

        } catch (IOException e) {
            System.out.println("Client #" + clientID + " disconnected");
        }
    }
}*/
