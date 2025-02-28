package org.example.client.gui.controllers;

import java.io.IOException;

import org.example.dto.Credentials;
import org.example.service.ChatService;

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
    
    @FXML
    private void handleLogin(final ActionEvent event) {
        final String email = emailField.getText().trim();
        final String password = passwordField.getText();
        
        // Débinder la propriété disable avant de la modifier
        loginButton.disableProperty().unbind();
        loginButton.setDisable(true); // Désactiver le bouton

        new Thread(() -> {
            try {
                final Credentials credentials = new Credentials(email, password);
                final boolean success = chatService.connect(credentials);
                
                Platform.runLater(() -> {
                    if (success) {
                        try {
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
