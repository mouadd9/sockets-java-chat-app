package org.example;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientTCP {
    // Configuration de la connexion
    private static final String ADRESSE_SERVEUR = "localhost";
    private static final int PORT = 5000;

    public static void main(String[] args) {
        try {
            // Connexion au serveur
            System.out.println("Tentative de connexion au serveur...");
            Socket socket = new Socket(ADRESSE_SERVEUR, PORT);
            System.out.println("Connecté au serveur!");

            // Création des flux d'entrée/sortie
            PrintWriter envoyeur = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader recepteur = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in);
           
            Thread receptionMessages = new Thread(() -> {
                try {
                    String message;
                    while ((message = recepteur.readLine()) != null) {
                        System.out.println("\n📩 Message reçu du serveur: " + message);
                    }
                } catch (IOException e) {
                    System.out.println("Déconnecté du serveur");
                }
            });
            receptionMessages.start(); 

            // Boucle principale pour envoyer des messages
            String message;
            while (true) {
                message = scanner.nextLine();

                if (message.equalsIgnoreCase("quit")) {
                    System.out.println("Au revoir!");
                    break;
                }

                envoyeur.println(message);
            }

            // Fermeture des ressources
            scanner.close();
            socket.close();

        } catch (IOException e) {
            System.out.println("Erreur de connexion: " + e.getMessage());
        }
    }
}