package org.example.client.gui;

/**
 * Classe de lancement pour l'application JavaFX.
 * Cette classe sert de point d'entrée principal pour éviter les problèmes
 * de modules JavaFX lors de l'exécution à partir d'un JAR.
 */
public class Launcher {
    /**
     * Point d'entrée principal de l'application.
     * @param args Arguments de ligne de commande
     */
    public static void main(final String[] args) {
        // Lance l'application JavaFX
        ChatClientApplication.main(args);
    }
}

