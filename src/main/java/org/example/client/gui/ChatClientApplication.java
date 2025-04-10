package org.example.client.gui;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

/**
 * Classe principale de l'application de chat.
 * Gère le démarrage, l'initialisation et la fermeture de l'application.
 * Configure la fenêtre principale et charge l'interface utilisateur initiale.
 */
public class ChatClientApplication extends Application {

    /**
     * Point d'entrée principal de l'application JavaFX.
     * Initialise l'interface utilisateur et configure la fenêtre principale.
     * 
     * @param primaryStage La fenêtre principale de l'application
     */
    @Override
    public void start(final Stage primaryStage) {
        try {
            // Chargement de l'interface utilisateur initiale (écran de connexion)
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            final Parent root = loader.load();
            
            // Configuration de la fenêtre principale
            primaryStage.setTitle("Chat Application");
            primaryStage.setScene(new Scene(root, 600, 400));
            primaryStage.setMinWidth(600);    // Largeur minimale de la fenêtre
            primaryStage.setMinHeight(400);   // Hauteur minimale de la fenêtre
            primaryStage.show();
        } catch (final IOException e) {
            // Gestion des erreurs de chargement FXML
            showErrorAndExit("Erreur de chargement FXML", 
                    "Impossible de charger l'interface utilisateur: " + e.getMessage());
        } catch (final Exception e) {
            // Gestion des autres erreurs d'initialisation
            showErrorAndExit("Erreur d'initialisation", 
                    "L'application n'a pas pu démarrer correctement: " + e.getMessage());
        }
    }

    /**
     * Affiche une boîte de dialogue d'erreur et quitte l'application.
     * Utilisé en cas d'erreur critique empêchant le démarrage de l'application.
     * 
     * @param title Le titre de la boîte de dialogue
     * @param message Le message d'erreur à afficher
     */
    private void showErrorAndExit(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Erreur critique");
        alert.setContentText(message);
        alert.showAndWait();
        Platform.exit();  // Fermeture propre de l'application
    }

    /**
     * Point d'entrée de l'application.
     * Lance l'application JavaFX et gère les erreurs de configuration.
     * 
     * @param args Les arguments de la ligne de commande
     */
    public static void main(final String[] args) {
        try {
            launch(args);  // Démarre l'application JavaFX
        } catch (final Exception e) {
            // Gestion des erreurs liées à la configuration JavaFX
            System.err.println("Erreur lors du lancement de l'application JavaFX:");
            System.err.println("Cette erreur peut être due à l'absence de modules JavaFX.");
            System.err.println("Veuillez lancer l'application avec les arguments VM suivants:");
            System.err.println("--module-path <chemin/vers/javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml");
            e.printStackTrace();
        }
    }
    
    /**
     * Méthode appelée lors de la fermeture de l'application.
     * Permet de nettoyer les ressources avant la fermeture.
     */
    @Override
    public void stop() {
        // Nettoyage des ressources lors de la fermeture de l'application
        System.out.println("Application fermée");
    }
}
