/**
 * LoginController est le contrôleur pour la vue de connexion de l'application de chat.
 * Il gère l'authentification des utilisateurs et la transition vers la vue de chat principale.
 * Responsabilités principales :
 * - Validation des champs de connexion (email et mot de passe)
 * - Gestion de l'authentification via ChatService
 * - Navigation vers la vue de chat en cas de connexion réussie
 * - Affichage des messages d'erreur appropriés
 */
package org.example.client.gui.controllers;

import java.io.IOException;
import java.net.URL;

import org.example.dto.Credentials;
import org.example.service.ChatService;
import org.example.service.NotificationService;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    // Champs FXML liés aux éléments de l'interface utilisateur
    @FXML
    private TextField emailField;    // Champ de saisie pour l'email
    
    @FXML
    private PasswordField passwordField;  // Champ de saisie pour le mot de passe
    
    @FXML
    private Button loginButton;      // Bouton de connexion
    
    private final ChatService chatService;  // Service de chat pour gérer la communication avec le serveur
    
    /**
     * Constructeur du LoginController.
     * Initialise le service de chat nécessaire pour l'authentification.
     */
    public LoginController() {
        this.chatService = new ChatService();
    }
    
    /**
     * Méthode d'initialisation appelée automatiquement par JavaFX après le chargement du FXML.
     * Configure le comportement du bouton de connexion pour qu'il soit désactivé
     * tant que les champs email et mot de passe ne sont pas remplis.
     */
    @FXML
    public void initialize() {
        // Activer le bouton de connexion seulement si des valeurs sont entrées
        loginButton.disableProperty().bind(
            emailField.textProperty().isEmpty().or(
            passwordField.textProperty().isEmpty())
        );
    }
    
    /**
     * Gère l'action de connexion lorsque l'utilisateur clique sur le bouton de connexion.
     * - Valide les champs de saisie
     * - Tente de se connecter via le ChatService
     * - Gère les différents cas d'erreur
     * - Navigue vers la vue de chat en cas de succès
     */
    @FXML
    private void handleLogin() {
        final String email = emailField.getText().trim();
        final String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Erreur", "Veuillez remplir tous les champs");
            return;
        }

        try {
            final boolean success = chatService.connect(new Credentials(email, password));
            Platform.runLater(() -> {
                if (success) {
                    try {
                        loadChatView();
                    } catch (final IOException e) {
                        showError("Erreur", "Impossible d'ouvrir la fenêtre de chat: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    showError("Erreur", "Email ou mot de passe incorrect");
                }
            });
        } catch (final IOException e) {
            showError("Erreur", "Erreur de connexion: " + e.getMessage());
        }
    }
    
    /**
     * Charge et affiche la vue de chat après une connexion réussie.
     * - Charge le fichier FXML de la vue de chat
     * - Configure le ChatController avec le service de chat et l'email de l'utilisateur
     * - Configure la fenêtre principale
     * - Initialise le service de notification
     * 
     * @throws IOException si le chargement de la vue de chat échoue
     */
    private void loadChatView() throws IOException {
        try {
            // Charger la vue de chat
            final FXMLLoader loader = new FXMLLoader();
            final URL fxmlUrl = LoginController.class.getResource("/fxml/chat.fxml");
            if (fxmlUrl == null) {
                throw new IOException("Impossible de trouver le fichier chat.fxml dans les ressources");
            }
            System.out.println("Chargement du fichier FXML depuis: " + fxmlUrl);
            loader.setLocation(fxmlUrl);
            
            final Parent chatView = loader.load();
            final ChatController chatController = loader.getController();

            // Configurer le contrôleur - IMPORTANT: configurer le service avant l'email
            chatController.setChatService(chatService);
            chatController.setUserEmail(emailField.getText());

            // Afficher la vue de chat
            final Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setTitle("Chat - " + emailField.getText());
            stage.setScene(new Scene(chatView));
            stage.setMaximized(true);
            
            // Initialiser le NotificationService avec le Stage principal
            NotificationService.getInstance().setMainStage(stage);
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la vue de chat: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Affiche une boîte de dialogue d'erreur avec le titre et le message spécifiés.
     * 
     * @param title Le titre de la boîte de dialogue
     * @param message Le message d'erreur à afficher
     */
    private void showError(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
