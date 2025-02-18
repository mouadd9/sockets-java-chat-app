package org.example;

import java.io.*;
import java.net.*;

public class ServeurTCP {
    // Définition du port sur lequel le serveur va écouter
    private static final int PORT = 5000;

    // Liste pour garder trace des clients connectés (optionnel)
    private static int clientID = 0;

    public static void main(String[] args) {
        try {
            // Créer un serveur qui écoute sur le port spécifié
            ServerSocket serveur = new ServerSocket(PORT);
            System.out.println("Serveur démarré sur le port " + PORT);
            System.out.println("En attente de connexions des clients...");

            // Boucle infinie pour accepter plusieurs clients
            while (true) {
                // Attendre qu'un client se connecte
                Socket client = serveur.accept();
                clientID++;

                // Afficher les informations de connexion
                System.out.println("Nouveau client #" + clientID + " connecté!");

                // Créer un nouveau thread pour gérer ce client
                Thread threadClient = new Thread(() -> gererClient(client));
                threadClient.start();
            }

        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage du serveur: " + e.getMessage());
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