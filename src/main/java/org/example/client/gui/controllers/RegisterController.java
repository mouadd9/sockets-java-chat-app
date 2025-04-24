package org.example.client.gui.controllers;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import org.example.shared.dto.RegistrationDTO;
import org.example.shared.util.ValidationUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class RegisterController {

    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 5000;

    @FXML
    private TextField emailField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private PasswordField confirmPasswordField;
    
    @FXML
    private Button registerButton;
    
    @FXML
    private Label emailErrorLabel;
    
    @FXML
    private Label passwordErrorLabel;
    
    @FXML
    private Label confirmPasswordErrorLabel;
    
    private final ObjectMapper mapper;
    
    public RegisterController() {
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }
    
    @FXML
    public void initialize() {
        // Activer le bouton d'inscription seulement si tous les champs sont remplis
        registerButton.disableProperty().bind(
            emailField.textProperty().isEmpty().or(
            passwordField.textProperty().isEmpty().or(
            confirmPasswordField.textProperty().isEmpty()))
        );
        
        // Réinitialiser les messages d'erreur lorsque l'utilisateur modifie les champs
        emailField.textProperty().addListener((observable, oldValue, newValue) -> {
            emailErrorLabel.setVisible(false);
        });
        
        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            passwordErrorLabel.setVisible(false);
        });
        
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            confirmPasswordErrorLabel.setVisible(false);
        });
    }
    
    @FXML
    private void handleRegister(final ActionEvent event) {
        // Réinitialiser les messages d'erreur
        emailErrorLabel.setVisible(false);
        passwordErrorLabel.setVisible(false);
        confirmPasswordErrorLabel.setVisible(false);
        
        // Récupérer les valeurs des champs
        final String email = emailField.getText().trim();
        final String password = passwordField.getText();
        final String confirmPassword = confirmPasswordField.getText();
        
        // Valider l'email
        if (!ValidationUtils.isValidEmail(email)) {
            emailErrorLabel.setText("Format d'email invalide");
            emailErrorLabel.setVisible(true);
            return;
        }
        
        // Valider le mot de passe
        if (!ValidationUtils.isStrongPassword(password)) {
            passwordErrorLabel.setText("Le mot de passe ne respecte pas les critères de sécurité");
            passwordErrorLabel.setVisible(true);
            return;
        }
        
        // Vérifier que les mots de passe correspondent
        if (!ValidationUtils.doPasswordsMatch(password, confirmPassword)) {
            confirmPasswordErrorLabel.setText("Les mots de passe ne correspondent pas");
            confirmPasswordErrorLabel.setVisible(true);
            return;
        }
        
        // Débinder la propriété disable avant de la modifier
        registerButton.disableProperty().unbind();
        registerButton.setDisable(true);
        
        // Créer le DTO d'inscription
        final RegistrationDTO registrationDTO = new RegistrationDTO(email, password, confirmPassword);
        
        // Envoyer la requête d'inscription au serveur
        CompletableFuture.runAsync(() -> {
            Socket socket = null;
            PrintWriter out = null;
            Scanner scanner = null;
            
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                scanner = new Scanner(socket.getInputStream());
                
                // Envoyer une commande spécifique pour indiquer qu'il s'agit d'une inscription
                out.println("REGISTER");
                
                // Envoyer les données d'inscription
                out.println(mapper.writeValueAsString(registrationDTO));
                
                // Lire la réponse du serveur
                final String response = scanner.nextLine();
                
                Platform.runLater(() -> {
                    if ("REGISTER_SUCCESS".equals(response)) {
                        showSuccess("Inscription réussie", "Votre compte a été créé avec succès. Vous pouvez maintenant vous connecter.");
                        try {
                            openLoginWindow();
                        } catch (final IOException e) {
                            showError("Erreur d'interface", "Impossible d'ouvrir la fenêtre de connexion: " + e.getMessage());
                            rebindRegisterButton();
                        }
                    } else {
                        showError("Échec de l'inscription", response);
                        rebindRegisterButton();
                    }
                });
            } catch (final IOException e) {
                Platform.runLater(() -> {
                    showError("Erreur de connexion", "Impossible de se connecter au serveur: " + e.getMessage());
                    rebindRegisterButton();
                });
            } finally {
                if (scanner != null) scanner.close();
                if (out != null) out.close();
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // Ignorer l'exception lors de la fermeture
                    }
                }
            }
        });
    }
    
    @FXML
    private void handleLoginLink(final ActionEvent event) {
        try {
            openLoginWindow();
        } catch (final IOException e) {
            showError("Erreur d'interface", "Impossible d'ouvrir la fenêtre de connexion: " + e.getMessage());
        }
    }
    
    private void openLoginWindow() throws IOException {
        // Charger la vue de connexion
        final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        final Parent loginView = loader.load();
        
        // Créer et afficher la nouvelle scène
        final Scene loginScene = new Scene(loginView, 600, 400);
        final Stage currentStage = (Stage) registerButton.getScene().getWindow();
        
        currentStage.setTitle("Connexion");
        currentStage.setScene(loginScene);
        currentStage.centerOnScreen();
    }
    
    private void rebindRegisterButton() {
        registerButton.disableProperty().unbind(); // S'assurer qu'il n'y a pas de binding actif
        registerButton.setDisable(false); // Réactiver le bouton
        // Rebinder le bouton avec la condition initiale
        registerButton.disableProperty().bind(
            emailField.textProperty().isEmpty().or(
            passwordField.textProperty().isEmpty().or(
            confirmPasswordField.textProperty().isEmpty()))
        );
    }
    
    private void showError(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showSuccess(final String title, final String message) {
        final Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
