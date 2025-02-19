package org.example;

import java.io.*;
import java.net.*;

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

public class ServeurTCP {
    private static final int PORT = 5000; // the port where the server will run the socket
    private static int clientID = 0;

    public static void main(String[] args) throws Exception {

        // this is the Socket created by the server and bound to port
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server Socket created and attached to port" + PORT);
        System.out.println("Waiting for clients to contact the Socket...");

        // Boucle infinie pour accepter plusieurs clients
        while (true) {
            // Attendre qu'un client se connecte
            Socket client = serverSocket.accept();
            clientID++;

            // Afficher les informations de connexion
            System.out.println("Nouveau client #" + clientID + " connecté!");

            // Créer un nouveau thread pour gérer ce client
            Thread threadClient = new Thread(() -> gererClient(client));
            threadClient.start();
        }
    }

    // Méthode pour gérer chaque client dans un thread séparé
    private static void gererClient(Socket client) {
        try {
            // Créer les flux d'entrée/sortie pour communiquer avec le client
            BufferedReader recepteur = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter envoyeur = new PrintWriter(client.getOutputStream(), true);

            // Message de bienvenue
            envoyeur.println("Bienvenue! Vous êtes le client #" + clientID);

            // //

            String message;
            // Boucle pour lire les messages du client
            while ((message = recepteur.readLine()) != null) {
                // Afficher le message reçu
                System.out.println("📩 Message reçu du client #" + clientID + ": " + message);
            }
        } catch (IOException e) {
            System.out.println("Client #" + clientID + " s'est déconnecté");
        }
    }
}