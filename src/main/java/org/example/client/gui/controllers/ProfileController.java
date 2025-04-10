package org.example.client.gui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.layout.StackPane;
import javafx.geometry.Bounds;

import org.example.model.User;
import org.example.service.ChatService;
import org.example.repository.JsonUserRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Contrôleur pour la gestion du profil utilisateur.
 * Gère l'affichage et la modification des informations du profil,
 * y compris le nom, le statut et la photo de profil.
 */
public class ProfileController {
    // Composants de l'interface utilisateur
    @FXML private Button backButton;        // Bouton pour revenir en arrière
    @FXML private Button editButton;        // Bouton pour basculer en mode édition
    @FXML private Circle profilePhotoCircle; // Cercle pour le clip de la photo
    @FXML private ImageView profilePhotoView; // Vue pour afficher la photo
    @FXML private Button changePhotoButton; // Bouton pour changer la photo
    @FXML private TextField nameField;      // Champ pour le nom d'affichage
    @FXML private TextArea statusField;     // Zone de texte pour le statut
    @FXML private Label nameCharCount;      // Compteur de caractères pour le nom
    @FXML private Label statusCharCount;    // Compteur de caractères pour le statut

    // Services et données
    private ChatService chatService;        // Service pour la communication avec le serveur
    private String userEmail;               // Email de l'utilisateur connecté
    private boolean isEditMode = false;     // Indique si on est en mode édition
    private JsonUserRepository userRepository; // Repository pour la persistance des données

    // Constantes de configuration
    private static final int MAX_NAME_LENGTH = 25;    // Longueur maximale du nom
    private static final int MAX_STATUS_LENGTH = 140; // Longueur maximale du statut
    private static final long MAX_PHOTO_SIZE = 5 * 1024 * 1024; // Taille maximale de la photo (5MB)

    /**
     * Initialise le contrôleur et configure les composants de l'interface.
     * Configure les limites de caractères, les écouteurs d'événements et le clip circulaire pour la photo.
     */
    @FXML
    public void initialize() {
        userRepository = new JsonUserRepository();
        
        // Configuration des limites de caractères pour le nom
        nameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > MAX_NAME_LENGTH) {
                nameField.setText(oldValue);
            }
            updateNameCharCount();
        });

        // Configuration des limites de caractères pour le statut
        statusField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() > MAX_STATUS_LENGTH) {
                statusField.setText(oldValue);
            }
            updateStatusCharCount();
        });

        // Configuration des boutons
        backButton.setOnAction(event -> handleBack());
        editButton.setOnAction(event -> toggleEditMode());
        changePhotoButton.setOnAction(event -> handleChangePhoto());

        // Configuration du clip circulaire pour la photo de profil
        Circle clip = new Circle(60);
        clip.centerXProperty().bind(profilePhotoView.fitWidthProperty().divide(2));
        clip.centerYProperty().bind(profilePhotoView.fitHeightProperty().divide(2));
        clip.radiusProperty().bind(profilePhotoView.fitWidthProperty().divide(2));
        profilePhotoView.setClip(clip);
    }

    /**
     * Définit l'email de l'utilisateur et charge son profil.
     * @param email L'email de l'utilisateur
     */
    public void setUserEmail(String email) {
        this.userEmail = email;
        loadUserProfile();
    }

    /**
     * Définit le service de chat utilisé pour la communication.
     * @param service Le service de chat
     */
    public void setChatService(ChatService service) {
        this.chatService = service;
    }

    /**
     * Charge les informations du profil utilisateur depuis le serveur.
     * Affiche le nom, le statut et la photo de profil.
     */
    private void loadUserProfile() {
        try {
            User user = chatService.getUser(userEmail);
            if (user != null) {
                nameField.setText(user.getDisplayName() != null ? user.getDisplayName() : userEmail);
                statusField.setText(user.getStatus() != null ? user.getStatus() : "Disponible");
                loadProfilePhoto(user.getProfilePicture());
                updateCharCounts();
            }
        } catch (IOException e) {
            showError("Erreur lors du chargement du profil", e.getMessage());
        }
    }

    /**
     * Bascule entre le mode lecture et le mode édition.
     * Active/désactive les champs éditables et change l'apparence du bouton d'édition.
     * Sauvegarde automatiquement les modifications en quittant le mode édition.
     */
    private void toggleEditMode() {
        isEditMode = !isEditMode;
        nameField.setEditable(isEditMode);
        statusField.setEditable(isEditMode);
        changePhotoButton.setVisible(isEditMode);
        editButton.setText(isEditMode ? "✔️" : "✏️");

        if (!isEditMode) {
            saveProfile();
        }
    }

    /**
     * Gère le changement de photo de profil.
     * Ouvre un sélecteur de fichier, vérifie la taille de l'image,
     * et sauvegarde la nouvelle photo.
     */
    private void handleChangePhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choisir une photo de profil");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg")
        );

        File file = fileChooser.showOpenDialog(changePhotoButton.getScene().getWindow());
        if (file != null) {
            try {
                if (file.length() > MAX_PHOTO_SIZE) {
                    showError("Fichier trop volumineux", 
                             "La taille de l'image ne doit pas dépasser 5MB");
                    return;
                }

                // Chargement et affichage de l'image
                Image image = new Image(file.toURI().toString());
                if (!image.isError()) {
                    profilePhotoView.setImage(image);
                    
                    // Conversion en Base64 pour le stockage
                    byte[] imageBytes = Files.readAllBytes(file.toPath());
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    
                    // Sauvegarde de la photo
                    saveProfilePhoto(base64Image);
                } else {
                    showError("Erreur de format", "Le fichier sélectionné n'est pas une image valide");
                }
            } catch (IOException e) {
                showError("Erreur lors du chargement de l'image", e.getMessage());
            }
        }
    }

    /**
     * Sauvegarde les modifications du profil (nom et statut).
     */
    private void saveProfile() {
        try {
            User user = chatService.getUser(userEmail);
            if (user != null) {
                user.setDisplayName(nameField.getText());
                user.setStatus(statusField.getText());
                userRepository.updateUser(user);
                showSuccess("Profil mis à jour avec succès !");
            }
        } catch (IOException e) {
            showError("Erreur lors de la sauvegarde", e.getMessage());
        }
    }

    /**
     * Sauvegarde la photo de profil en format Base64.
     * @param base64Image La photo encodée en Base64
     */
    private void saveProfilePhoto(String base64Image) {
        try {
            User user = chatService.getUser(userEmail);
            if (user != null) {
                user.setProfilePicture(base64Image);
                userRepository.updateUser(user);
                showSuccess("Photo de profil mise à jour !");
            }
        } catch (IOException e) {
            showError("Erreur lors de la sauvegarde de la photo", e.getMessage());
        }
    }

    /**
     * Charge et affiche la photo de profil depuis une chaîne Base64.
     * @param base64Image La photo encodée en Base64
     */
    private void loadProfilePhoto(String base64Image) {
        if (base64Image != null && !base64Image.isEmpty()) {
            try {
                byte[] imageBytes = Base64.getDecoder().decode(base64Image);
                Image image = new Image(new java.io.ByteArrayInputStream(imageBytes));
                profilePhotoView.setImage(image);
            } catch (IllegalArgumentException e) {
                System.err.println("Erreur lors du décodage de l'image: " + e.getMessage());
            }
        }
    }

    /**
     * Gère l'action de retour en arrière.
     * Demande confirmation si des modifications non sauvegardées existent.
     */
    private void handleBack() {
        if (isEditMode) {
            if (showConfirmation("Quitter sans sauvegarder ?", 
                "Les modifications non sauvegardées seront perdues.")) {
                toggleEditMode();
            }
        }
        ((Stage) backButton.getScene().getWindow()).close();
    }

    /**
     * Met à jour les compteurs de caractères pour le nom et le statut.
     */
    private void updateCharCounts() {
        updateNameCharCount();
        updateStatusCharCount();
    }

    /**
     * Met à jour le compteur de caractères pour le nom.
     */
    private void updateNameCharCount() {
        nameCharCount.setText(nameField.getText().length() + "/" + MAX_NAME_LENGTH);
    }

    /**
     * Met à jour le compteur de caractères pour le statut.
     */
    private void updateStatusCharCount() {
        statusCharCount.setText(statusField.getText().length() + "/" + MAX_STATUS_LENGTH);
    }

    /**
     * Affiche une boîte de dialogue d'erreur.
     * @param title Le titre de la boîte de dialogue
     * @param content Le message d'erreur
     */
    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Affiche une boîte de dialogue de succès.
     * @param message Le message de succès
     */
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Succès");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Affiche une boîte de dialogue de confirmation.
     * @param title Le titre de la boîte de dialogue
     * @param content Le message de confirmation
     * @return true si l'utilisateur confirme, false sinon
     */
    private boolean showConfirmation(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setContentText(content);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
} 