package org.example.client.gui;

import java.io.IOException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class ChatClientApplication extends Application {

    @Override
    public void start(final Stage primaryStage) {
        try {
            // Vérification que les ressources FXML peuvent être chargées
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            final Parent root = loader.load();
            
            primaryStage.setTitle("Chat Application");
            primaryStage.setScene(new Scene(root, 600, 400));
            primaryStage.setMinWidth(600);
            primaryStage.setMinHeight(400);
            primaryStage.show();
        } catch (final IOException e) {
            showErrorAndExit("Erreur de chargement FXML", 
                    "Impossible de charger l'interface utilisateur: " + e.getMessage());
        } catch (final Exception e) {
            showErrorAndExit("Erreur d'initialisation", 
                    "L'application n'a pas pu démarrer correctement: " + e.getMessage());
        }
    }

    /**
     * Affiche une erreur et quitte l'application
     */
    private void showErrorAndExit(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText("Erreur critique");
        alert.setContentText(message);
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(final String[] args) {
        try {
            launch(args);
        } catch (final Exception e) {
            System.err.println("Erreur lors du lancement de l'application JavaFX:");
            System.err.println("Cette erreur peut être due à l'absence de modules JavaFX.");
            System.err.println("Veuillez lancer l'application avec les arguments VM suivants:");
            System.err.println("--module-path <chemin/vers/javafx-sdk>/lib --add-modules javafx.controls,javafx.fxml");
            e.printStackTrace();
        }
    }
    
    @Override
    public void stop() {
        // Nettoyage des ressources lors de la fermeture de l'application
        System.out.println("Application fermée");
    }
}
