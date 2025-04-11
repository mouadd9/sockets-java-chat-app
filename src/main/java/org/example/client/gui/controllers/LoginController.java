package org.example.client.gui.controllers;

import java.io.IOException;

import org.example.client.gui.service.ChatService;
import org.example.dto.Credentials;

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

    @FXML
    private TextField emailField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginButton;
    
    private final ChatService chatService;
    
    public LoginController() {
        this.chatService = new ChatService();
    }
    
    @FXML
    public void initialize() {
        // Activer le bouton de connexion seulement si des valeurs sont entrées
        loginButton.disableProperty().bind(
            emailField.textProperty().isEmpty().or(
            passwordField.textProperty().isEmpty())
        );
    }

    // this function takes in the login event from the view extracts credentials and calls connect from chat service
    @FXML
    private void handleLogin(final ActionEvent event) {
        // here we get credentials
        final String email = emailField.getText().trim();
        final String password = passwordField.getText();

        // Débinder la propriété disable avant de la modifier
        loginButton.disableProperty().unbind();
        loginButton.setDisable(true); // Désactiver le bouton

        new Thread(() -> {
            try {
                final Credentials credentials = new Credentials(email, password);
                // here we call connect() in chat service
                // connect does the following :
                    // - establishes connection with server socket (a client socket is created in server side for further communication)
                    // - it send credentials using the output stream and waits for response the server creates a client handler and checks authenticates client
                    // - if authenticated we run code in a thread (to load messages sent to the client offline using loadMessages() ), and we return "true".
                final boolean success = chatService.connect(credentials);
                
                Platform.runLater(() -> {
                    if (success) {
                        try {
                            // if authentication is successful we open the chat view
                            openChatWindow(email);
                        } catch (final IOException e) {
                            showError("Erreur d'interface", "Impossible d'ouvrir la fenêtre de chat: " + e.getMessage());
                            rebindLoginButton();
                        }
                    } else {
                        showError("Échec de connexion", "Email ou mot de passe incorrect");
                        rebindLoginButton();
                    }
                });
            } catch (final IOException e) {
                Platform.runLater(() -> {
                    showError("Erreur de connexion", "Impossible de se connecter au serveur: " + e.getMessage());
                    rebindLoginButton();
                });
            }
        }).start();
    }
    
    /**
     * Rétablit le binding du bouton de login
     */
    private void rebindLoginButton() {
        loginButton.disableProperty().unbind(); // S'assurer qu'il n'y a pas de binding actif
        loginButton.setDisable(false); // Réactiver le bouton
        // Rebinder le bouton avec la condition initiale
        loginButton.disableProperty().bind(
            emailField.textProperty().isEmpty().or(
            passwordField.textProperty().isEmpty())
        );
    }

    // this function loads chat.fxml and configures it with a controller chatController
    private void openChatWindow(final String userEmail) throws IOException {
        // Charger la vue de chat
        final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/chat.fxml"));
        final Parent chatView = loader.load();
        
        // Configurer le contrôleur de chat
        final ChatController chatController = loader.getController();
        chatController.initData(chatService, userEmail);
        
        // Créer et afficher la nouvelle scène
        final Scene chatScene = new Scene(chatView, 800, 600);
        final Stage currentStage = (Stage) loginButton.getScene().getWindow();
        
        currentStage.setTitle("Chat - " + userEmail);
        currentStage.setScene(chatScene);
        currentStage.setMinWidth(800);
        currentStage.setMinHeight(600);
        currentStage.centerOnScreen();
    }
    
    private void showError(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
