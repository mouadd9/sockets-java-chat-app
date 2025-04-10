package org.example.client.gui.controllers;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.nio.file.Files;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.example.client.repository.JsonLocalMessageRepository;
import org.example.repository.JsonMessageRepository;
import org.example.model.Message;
import org.example.model.MessageType;
import org.example.model.User;
import org.example.service.ChatService;
import org.example.service.NotificationService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.scene.input.KeyCode;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import java.util.Base64;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ProgressBar;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import javax.sound.sampled.*;
import java.io.*;

/**
 * ChatController - Contrôleur principal de l'application de chat
 * Gère l'interface utilisateur et la logique de communication
 */
public class ChatController {
    // Composants de l'interface utilisateur injectés par FXML
    @FXML
    private Label userEmailLabel; // Affiche l'email de l'utilisateur connecté
    @FXML
    private Label userStatusLabel; // Affiche le statut de l'utilisateur
    @FXML
    private Label typingIndicatorLabel; // Indique quand un contact est en train d'écrire
    @FXML
    private ListView<String> contactListView; // Liste des contacts
    @FXML
    private TextField newContactField; // Champ pour ajouter un nouveau contact
    @FXML
    private VBox chatHistoryContainer; // Conteneur pour l'historique des messages
    @FXML
    private TextField messageField; // Champ pour écrire un message
    @FXML
    private Label statusLabel; // Affiche les messages de statut
    @FXML
    private VBox quotedMessagePreview; // Aperçu du message cité
    @FXML
    private Label quotedMessageLabel; // Label pour le message cité
    @FXML
    private Button attachButton; // Bouton pour attacher des fichiers
    @FXML
    private Button recordButton; // Boutun pour enregistrer des messages audio
    // Services et données
    private ChatService chatService; // Service de chat pour la communication
    private String userEmail; // Email de l'utilisateur connecté
    private String selectedContact; // Contact actuellement sélectionné
    // Collections pour la gestion des données
    private final ObservableList<String> contacts = FXCollections.observableArrayList(); // Liste observable des contacts
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm"); // Format d'affichage de l'heure
    private final JsonLocalMessageRepository localRepo = new JsonLocalMessageRepository(); // Repository local pour les messages
    // Gestion des événements de frappe
    private final ScheduledExecutorService typingExecutor = Executors.newSingleThreadScheduledExecutor(); // Exécuteur pour les événements de frappe
    private java.util.concurrent.ScheduledFuture<?> stopTypingFuture; // Future pour arrêter l'indicateur de frappe
    private boolean isTyping = false; // État de frappe
    private static final int TYPING_TIMEOUT = 3000; // Timeout de 3 secondes pour l'indicateur de frappe
    // Services et collections pour la gestion des contacts
    private final NotificationService notificationService = NotificationService.getInstance(); // Service de notification
    private final Map<String, Label> contactLabels = new HashMap<>(); // Mappe les contacts à leurs labels
    private final Map<String, Boolean> contactOnlineStatus = new HashMap<>(); // Statut en ligne des contacts
    // Gestion des citations
    private Message quotedMessage; // Message actuellement cité
    // Variables pour l'enregistrement audio
    private boolean isRecording = false; // État d'enregistrement
    private File audioFile; // Fichier audio temporaire
    private TargetDataLine audioLine; // Ligne audio pour l'enregistrement

    /**
     * Initialise le contrôleur et configure les composants de l'interface
     * - Configure la liste des contacts avec leurs photos de profil
     * - Configure les écouteurs d'événements
     * - Initialise les boutons et les champs de texte
     */
    @FXML
    public void initialize() {
        // Configuration de la liste des contacts avec photos de profil
        contactListView.setItems(contacts); // Associe la liste observable des contacts à la ListView
        contactListView.setCellFactory(lv -> new ListCell<String>() { // Configure la fabrique de cellules personnalisée
            private final ImageView photoView = new ImageView(); // Vue pour afficher la photo de profil
            private final Circle clip = new Circle(15); // Cercle pour découper la photo en forme ronde
            
            {
                // Configuration de la photo de profil
                photoView.setFitWidth(30); // Définit la largeur de la photo
                photoView.setFitHeight(30); // Définit la hauteur de la photo
                photoView.setPreserveRatio(true); // Maintient le ratio d'aspect de la photo
                
                // Configuration du clip circulaire pour les photos de profil
                clip.centerXProperty().bind(photoView.fitWidthProperty().divide(2)); // Centre horizontal
                clip.centerYProperty().bind(photoView.fitHeightProperty().divide(2)); // Centre vertical
                clip.radiusProperty().bind(photoView.fitWidthProperty().divide(2)); // Rayon du cercle
                photoView.setClip(clip); // Applique le clip circulaire à la photo
            }
            
            @Override
            protected void updateItem(String contact, boolean empty) {
                super.updateItem(contact, empty); // Appelle la méthode parente
                if (empty || contact == null) { // Vérifie si la cellule est vide
                    setText(null); // Efface le texte
                    setGraphic(null); // Efface le graphique
                } else {
                    // Création du conteneur pour chaque contact
                    HBox container = new HBox(5); // Conteneur horizontal avec espacement de 5 pixels
                    container.setAlignment(Pos.CENTER_LEFT); // Alignement à gauche
                    container.setPadding(new Insets(5)); // Marge de 5 pixels
                    
                    // Chargement de la photo de profil
                    try {
                        User user = chatService.getUser(contact); // Récupère les informations de l'utilisateur
                        if (user != null && user.getProfilePicture() != null) { // Vérifie si l'utilisateur a une photo
                            byte[] imageBytes = Base64.getDecoder().decode(user.getProfilePicture()); // Décode la photo en base64
                            Image image = new Image(new java.io.ByteArrayInputStream(imageBytes)); // Crée l'image
                            photoView.setImage(image); // Affiche l'image
                        } else {
                            // Image par défaut si pas de photo
                            photoView.setImage(null); // Efface l'image
                            Circle defaultPhoto = new Circle(15); // Crée un cercle par défaut
                            defaultPhoto.setFill(javafx.scene.paint.Color.valueOf("#202C33")); // Couleur de fond
                            photoView.setClip(defaultPhoto); // Applique le cercle comme clip
                        }
                    } catch (Exception e) {
                        System.err.println("Erreur lors du chargement de la photo de " + contact + ": " + e.getMessage());
                        photoView.setImage(null); // En cas d'erreur, efface l'image
                    }
                    
                    // Création de l'indicateur de statut (en ligne/hors ligne)
                    Circle statusDot = new Circle(4); // Cercle de 4 pixels de rayon
                    boolean isOnline = contactOnlineStatus.getOrDefault(contact, false); // Récupère le statut
                    statusDot.setFill(javafx.scene.paint.Color.valueOf(isOnline ? "#2ecc71" : "#e74c3c")); // Vert si en ligne, rouge sinon
                    
                    // Affichage de l'email du contact
                    Label emailLabel = new Label(contact); // Crée le label avec l'email
                    emailLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;"); // Style du texte
                    // Assemblage des composants
                    container.getChildren().addAll(photoView, statusDot, emailLabel); // Ajoute les composants au conteneur
                    setGraphic(container); // Définit le conteneur comme graphique de la cellule
                    contactLabels.put(contact, emailLabel); // Stocke le label dans la map
                }
            }
        });
        // Configuration des écouteurs d'événements
        contactListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> { // Écouteur de sélection
                    if (newValue != null) { // Si un nouveau contact est sélectionné
                        selectedContact = newValue; // Met à jour le contact sélectionné
                        loadConversation(selectedContact); // Charge la conversation
                    }
                });

        // Configuration du champ de message
        messageField.setOnKeyPressed(event -> { // Écouteur de touches
            if (event.getCode() == KeyCode.ENTER) { // Si la touche Entrée est pressée
                handleSendMessage(); // Envoie le message
            }
        });

        // Configuration de l'indicateur de frappe
        messageField.textProperty().addListener((observable, oldValue, newValue) -> { // Écouteur de texte
            handleTyping(); // Gère l'indicateur de frappe
        });

        // Configuration du double-clic pour citer un message
        chatHistoryContainer.setOnMouseClicked(event -> { // Écouteur de clic
            if (event.getClickCount() == 2) { // Si double-clic
                Node clickedNode = event.getPickResult().getIntersectedNode(); // Récupère le nœud cliqué
                if (clickedNode instanceof Label) { // Si c'est un label
                    Label messageLabel = (Label) clickedNode; // Cast en Label
                    Message msg = (Message) messageLabel.getUserData(); // Récupère le message associé
                    if (msg != null) { // Si un message est associé
                        handleQuoteMessage(msg); // Gère la citation
                    }
                }
            }
        });

        // Configuration du menu contextuel pour les messages
        quotedMessagePreview.setVisible(false); // Cache l'aperçu de citation
        quotedMessagePreview.setManaged(false); // Ne gère pas l'espace

        // Configuration des boutons
        attachButton.setOnAction(event -> handleAttachFile()); // Gestionnaire pour le bouton d'attachement
        recordButton.setOnAction(event -> handleRecordAudio()); // Gestionnaire pour le bouton d'enregistrement
    }

    /**
     * Définit l'email de l'utilisateur connecté
     * - Met à jour l'interface avec l'email
     * - Charge le statut de l'utilisateur
     * - Charge la liste des contacts
     */
    public void setUserEmail(String email) {
        this.userEmail = email;
        userEmailLabel.setText(email);
        loadUserStatus();
        loadContacts();
    }

    /**
     * Définit le service de chat
     * - Configure le consommateur de messages
     * - Lance le rafraîchissement périodique des statuts
     */
    public void setChatService(ChatService service) {
        this.chatService = service;
        chatService.setMessageConsumer(this::processMessage);
        
        // Rafraîchir les statuts toutes les 30 secondes
        ScheduledExecutorService statusRefreshExecutor = Executors.newSingleThreadScheduledExecutor();
        statusRefreshExecutor.scheduleAtFixedRate(() -> {
            if (chatService != null) {
                chatService.refreshOnlineStatuses();
            }
        }, 15, 30, TimeUnit.SECONDS);

        // Recharger le statut si l'email est déjà configuré
        if (userEmail != null) {
            loadUserStatus();
        }
    }

    /**
     * Traite les messages entrants
     * - Gère les mises à jour de statut
     * - Gère les messages de chat
     * - Gère les indicateurs de frappe
     */
    private void processMessage(Message message) {
        if (message.getType() == MessageType.STATUS_UPDATE) {
            handleStatusUpdate(message);
        } else if (message.getType() == MessageType.CHAT || message.getType() == MessageType.FILE) {
            handleChatMessage(message);
        } else if (message.getType() == MessageType.TYPING) {
            handleTypingIndicator(message, true);
        } else if (message.getType() == MessageType.STOP_TYPING) {
            handleTypingIndicator(message, false);
        }
    }

    /**
     * Gère les mises à jour de statut des contacts
     * - Met à jour l'état en ligne/hors ligne
     * - Rafraîchit l'interface utilisateur
     */
    private void handleStatusUpdate(Message message) {
        String contactEmail = message.getSenderEmail();
        boolean isOnline = Boolean.parseBoolean(message.getContent());
        
        Platform.runLater(() -> {
            // Mettre à jour le statut dans la map
            boolean oldStatus = contactOnlineStatus.getOrDefault(contactEmail, false);
            contactOnlineStatus.put(contactEmail, isOnline);
            
            // Ne rafraîchir que si le statut a réellement changé
            if (oldStatus != isOnline) {
                // Rafraîchir la vue pour mettre à jour les indicateurs visuels
                contactListView.refresh();
                System.out.println("Mise à jour du statut pour " + contactEmail + ": " + 
                    (isOnline ? "en ligne" : "hors ligne") + " (changement détecté)");
                
                // Si c'est le contact actuellement sélectionné, mettre à jour son statut dans l'interface
                if (contactEmail.equals(selectedContact)) {
                    updateSelectedContactStatus(isOnline);
                }
            }
        });
    }

    /**
     * Met à jour le statut du contact sélectionné
     * - Affiche le statut dans l'interface
     */
    private void updateSelectedContactStatus(boolean isOnline) {
        if (selectedContact != null) {
            // Mettre à jour un indicateur visuel dans l'interface pour le contact sélectionné
            String statusText = isOnline ? "en ligne" : "hors ligne";
            Platform.runLater(() -> {
                setStatus("Contact " + selectedContact + " est " + statusText);
            });
        }
    }

    /**
     * Gère l'indicateur de frappe
     * - Envoie un message de frappe au contact
     * - Gère le timeout de frappe
     */
    private void handleTyping() {
        if (selectedContact == null) return;
        
        if (!isTyping) {
            isTyping = true;
            try {
                Message typingMsg = new Message();
                typingMsg.setType(MessageType.TYPING);
                typingMsg.setSenderEmail(userEmail);
                typingMsg.setReceiverEmail(selectedContact);
                chatService.sendMessage(typingMsg);
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi de l'indicateur de frappe: " + e.getMessage());
            }
        }
        
        // Réinitialiser le timer d'arrêt de frappe
        if (stopTypingFuture != null) {
            stopTypingFuture.cancel(false);
        }
        
        // Programmer l'arrêt de l'indicateur après 2 secondes d'inactivité
        stopTypingFuture = typingExecutor.schedule(() -> {
            stopTypingIndicator();
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * Gère l'affichage de l'indicateur de frappe
     * - Affiche/masque l'indicateur selon l'état
     */
    private void handleTypingIndicator(Message message, boolean isTyping) {
        Platform.runLater(() -> {
            // Afficher l'indicateur uniquement si le message vient du contact sélectionné
            if (selectedContact != null && message.getSenderEmail().equals(selectedContact)) {
                typingIndicatorLabel.setText(isTyping ? selectedContact + " est en train d'écrire..." : "");
            }
        });
    }

    /**
     * Gère l'envoi d'un message
     * - Crée et envoie le message
     * - Gère les citations
     * - Met à jour l'interface
     */
    @FXML
    private void handleSendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty() || selectedContact == null) {
            return;
        }

        try {
            // Création du message
            Message message = new Message();
            message.setSenderEmail(userEmail);
            message.setReceiverEmail(selectedContact);
            message.setContent(content);
            message.setTimestamp(LocalDateTime.now());
            message.setType(MessageType.CHAT);

            // Gestion de la citation
            if (quotedMessage != null) {
                message.quoteMessage(quotedMessage);
            }

            // Envoi du message
            chatService.sendMessage(message);
            
            // Mise à jour de l'interface
            addMessageToChat(message);
            
            // Sauvegarde locale
            localRepo.addLocalMessage(userEmail, message);
            
            // Nettoyage
            messageField.clear();
            handleCancelQuote();
        } catch (IOException e) {
            setStatus("Erreur lors de l'envoi du message: " + e.getMessage());
        }
    }

    /**
     * Ajoute un nouveau contact
     * - Vérifie la validité de l'email
     * - Envoie la demande au serveur
     * - Met à jour l'interface
     */
    @FXML
    private void handleAddContact() {
        final String email = newContactField.getText().trim();

        if (email.isEmpty()) {
            setStatus("Veuillez saisir un email");
            return;
        }

        try {
            final boolean added = chatService.addContact(userEmail, email);
            if (added) {
                contacts.add(email);
                newContactField.clear();
                setStatus("Contact ajouté: " + email);
            }
        } catch (final IllegalArgumentException e) {
            // Afficher le message d'erreur spécifique
            setStatus("Erreur: " + e.getMessage());
        } catch (final IOException e) {
            setStatus("Erreur de connexion: " + e.getMessage());
        }
    }

    /**
     * Supprime un contact
     * - Envoie la demande au serveur
     * - Nettoie l'interface
     * - Supprime la conversation locale
     */
    @FXML
    private void handleRemoveContact() {
        if (selectedContact == null) {
            setStatus("Aucun contact sélectionné pour la suppression");
            return;
        }
        try {
            final boolean removed = chatService.removeContact(userEmail, selectedContact);
            if (removed) {
                contacts.remove(selectedContact);
                // Nettoyer la conversation affichée
                chatHistoryContainer.getChildren().clear();
                // Supprimer la conversation persistée localement
                localRepo.removeConversation(userEmail, selectedContact);
                setStatus("Contact et conversation supprimés: " + selectedContact);
                selectedContact = null;
            } else {
                setStatus("La suppression du contact a échoué");
            }
        } catch (final IOException e) {
            setStatus("Erreur: " + e.getMessage());
        }
    }

    /**
     * Gère la déconnexion
     * - Arrête les services
     * - Retourne à l'écran de connexion
     */
    @FXML
    private void handleLogout() {
        try {
            // Arrêter l'indicateur de frappe avant la déconnexion
            stopTypingIndicator();
            typingExecutor.shutdown();
            
            System.out.println("Déconnexion de l'utilisateur: " + userEmail);
            // Le ChatService va notifier le PresenceManager de la déconnexion
            chatService.disconnect();

            // Revenir à l'écran de connexion
            final FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getClassLoader().getResource("fxml/login.fxml"));
            if (loader.getLocation() == null) {
                throw new IOException("Impossible de trouver le fichier login.fxml");
            }
            System.out.println("Chargement du fichier FXML depuis: " + loader.getLocation());
            final Parent loginView = loader.load();

            final Stage stage = (Stage) userEmailLabel.getScene().getWindow();
            stage.setTitle("Chat Application");
            stage.setScene(new Scene(loginView, 600, 400));
            stage.centerOnScreen();
        } catch (final IOException e) {
            setStatus("Erreur lors de la déconnexion: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Modifie le statut de l'utilisateur
     * - Affiche une boîte de dialogue
     * - Envoie la mise à jour au serveur
     */
    @FXML
    private void handleEditStatus() {
        TextInputDialog dialog = new TextInputDialog(userStatusLabel.getText());
        dialog.setTitle("Modifier le statut");
        dialog.setHeaderText("Entrez votre nouveau statut");
        dialog.setContentText("Statut:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newStatus -> {
            try {
                chatService.updateUserStatus(userEmail, newStatus);
                userStatusLabel.setText(newStatus.isEmpty() ? "👍 Disponible" : newStatus);
                setStatus("Statut mis à jour");
            } catch (IOException e) {
                setStatus("Erreur lors de la mise à jour du statut: " + e.getMessage());
            }
        });
    }

    /**
     * Charge la liste des contacts
     * - Récupère les contacts du serveur
     * - Initialise les statuts
     * - Met à jour l'interface
     */
    private void loadContacts() {
        try {
            final List<String> contactList = chatService.getContacts(userEmail);
            Platform.runLater(() -> {
                contacts.clear();
                contacts.addAll(contactList);

                // Initialiser les statuts des contacts
                contactList.forEach(contact -> {
                    try {
                        User user = chatService.getUser(contact);
                        if (user != null) {
                            // Par défaut en ligne sauf si explicitement marqué hors ligne
                            contactOnlineStatus.put(contact, user.isOnline());
                            System.out.println("Statut initial de " + contact + ": " + (user.isOnline() ? "en ligne" : "hors ligne"));
                        }
                    } catch (IOException e) {
                        // En cas d'erreur, considérer l'utilisateur comme en ligne
                        contactOnlineStatus.put(contact, true);
                        System.err.println("Erreur lors du chargement du statut pour " + contact + ": " + e.getMessage());
                    }
                });

                // Sélectionner automatiquement le premier contact
                if (!contacts.isEmpty()) {
                    contactListView.getSelectionModel().select(0);
                }
                
                // Rafraîchir la vue
                contactListView.refresh();
            });
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement des contacts: " + e.getMessage());
        }
    }

    /**
     * Charge une conversation
     * - Récupère les messages du serveur
     * - Affiche l'historique
     */
    private void loadConversation(final String contactEmail) {
        if (contactEmail == null) return;
        
        Platform.runLater(() -> {
            chatHistoryContainer.getChildren().clear();
            try {
                System.out.println("Chargement de la conversation avec: " + contactEmail);
                // Charger les messages depuis le serveur
                List<Message> messages = chatService.getConversation(userEmail, contactEmail);
                System.out.println("Nombre de messages trouvés: " + messages.size());
                
                for (Message message : messages) {
                    addMessageToChat(message);
                }
                setStatus("Conversation chargée avec " + contactEmail);
                
                // Réinitialiser l'indicateur de messages non lus
                updateContactStyle(contactEmail, false);
                
            } catch (final IOException e) {
                setStatus("Erreur lors du chargement de la conversation : " + e.getMessage());
                System.err.println("Erreur de chargement: " + e.getMessage());
            }
        });
    }

    /**
     * Gère les messages de chat
     * - Affiche les messages
     * - Gère les notifications
     * - Met à jour l'interface
     */
    private void handleChatMessage(final Message message) {
        Platform.runLater(() -> {
            String senderEmail = message.getSenderEmail();
            String receiverEmail = message.getReceiverEmail();
            
            // Vérifier si le message appartient à la conversation actuelle
            boolean isRelevantMessage = (selectedContact != null) && 
                ((senderEmail.equals(selectedContact) && receiverEmail.equals(userEmail)) ||
                 (senderEmail.equals(userEmail) && receiverEmail.equals(selectedContact)));

            // Afficher une notification si ce n'est pas le contact actif
            if (!senderEmail.equals(userEmail) && !senderEmail.equals(selectedContact)) {
                notificationService.showNotification(senderEmail, message.getContent());
                updateContactStyle(senderEmail, true);
            }

            // Afficher le message s'il appartient à la conversation active
            if (isRelevantMessage) {
                addMessageToChat(message);
                // Réinitialiser l'indicateur de frappe
                if (senderEmail.equals(selectedContact)) {
                    typingIndicatorLabel.setText("");
                }
            }

            // Enregistrer localement le message
            try {
                localRepo.addLocalMessage(userEmail, message);
                System.out.println("Message enregistré localement - De: " + senderEmail + " À: " + receiverEmail);
            } catch (final IOException e) {
                System.err.println("Erreur de sauvegarde locale : " + e.getMessage());
            }

            // Mise à jour des contacts si nécessaire
            final String otherUser = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
            if (!contacts.contains(otherUser)) {
                contacts.add(otherUser);
                System.out.println("Nouveau contact ajouté: " + otherUser);
            }

            // Envoyer l'accusé de réception
            try {
                chatService.acknowledgeMessage(message.getId());
                System.out.println("Accusé de réception envoyé pour le message: " + message.getId());
            } catch (final IOException e) {
                System.err.println("Erreur d'accusé de réception: " + e.getMessage());
            }
        });
    }

    /**
     * Gère l'attachement de fichiers
     * - Affiche le sélecteur de fichiers
     * - Envoie le fichier
     * - Met à jour l'interface
     */
    @FXML
    private void handleAttachFile() {
        if (selectedContact == null) {
            setStatus("Veuillez sélectionner un contact avant d'envoyer un fichier");
            return;
        }

        // Configuration du sélecteur de fichiers
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un fichier");
        
        // Filtres pour les types de fichiers acceptés
        FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter(
            "Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp");
        FileChooser.ExtensionFilter videoFilter = new FileChooser.ExtensionFilter(
            "Vidéos", "*.mp4", "*.avi", "*.mov", "*.wmv", "*.flv", "*.mkv", "*.webm");
        FileChooser.ExtensionFilter audioFilter = new FileChooser.ExtensionFilter(
            "Audio", "*.mp3", "*.wav", "*.ogg", "*.m4a", "*.aac");
        
        fileChooser.getExtensionFilters().addAll(videoFilter, imageFilter, audioFilter);

        // Sélection du fichier
        File selectedFile = fileChooser.showOpenDialog(messageField.getScene().getWindow());
        if (selectedFile != null) {
            try {
                // Vérification de la taille du fichier
                long maxFileSize = 100 * 1024 * 1024; // 100 MB
                if (selectedFile.length() > maxFileSize) {
                    setStatus("Le fichier est trop volumineux. La taille maximale est de 100 MB.");
                    return;
                }

                setStatus("Chargement du fichier en cours...");

                // Chargement du fichier en arrière-plan
                Thread fileLoadThread = new Thread(() -> {
                    try {
                        // Encodage du fichier en base64
                        byte[] fileBytes = Files.readAllBytes(selectedFile.toPath());
                        String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                        // Création du message de fichier
                        Message fileMessage = Message.createFileMessage(
                            userEmail,
                            selectedContact,
                            selectedFile.getName(),
                            getFileType(selectedFile.getName()),
                            base64Data,
                            selectedFile.length()
                        );

                        // Envoi du message
                        Platform.runLater(() -> {
                            try {
                                chatService.sendMessage(fileMessage);
                                addMessageToChat(fileMessage);
                                localRepo.addLocalMessage(userEmail, fileMessage);
                                setStatus("Fichier envoyé: " + selectedFile.getName());
                            } catch (IOException e) {
                                setStatus("Erreur lors de l'envoi du fichier: " + e.getMessage());
                            }
                        });
                    } catch (IOException e) {
                        Platform.runLater(() -> 
                            setStatus("Erreur lors du chargement du fichier: " + e.getMessage())
                        );
                    }
                });

                fileLoadThread.start();

            } catch (Exception e) {
                setStatus("Erreur lors de l'envoi du fichier: " + e.getMessage());
            }
        }
    }

    /**
     * Détermine le type de fichier
     * - Identifie le type selon l'extension
     */
    private String getFileType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                return "image";
            case "mp4":
            case "avi":
            case "mov":
            case "wmv":
            case "flv":
            case "mkv":
            case "webm":
                return "video";
            case "mp3":
            case "wav":
            case "ogg":
            case "m4a":
            case "aac":
                return "audio";
            default:
                return "file";
        }
    }

    /**
     * Ajoute un message à l'interface
     * - Crée les composants visuels
     * - Gère les différents types de messages
     * - Met à jour l'historique
     */
    @FXML
    private void addMessageToChat(final Message message) {
        if (message == null) {
            System.err.println("Tentative d'ajout d'un message null");
            return;
        }

        final boolean isMine = message.getSenderEmail().equals(userEmail);

        // Créer une boîte pour le message
        final VBox messageContent = new VBox(4);
        messageContent.setMaxWidth(chatHistoryContainer.getWidth() * 0.6);
        messageContent.setPadding(new Insets(8));
        messageContent.setStyle("-fx-background-color: " + (isMine ? "#005C4B" : "#1F2C34") + ";" +
                              "-fx-background-radius: 8;");

        if (message.hasFile()) {
            // Afficher le fichier
            if (message.getFileType().equals("image")) {
                try {
                    byte[] imageData = Base64.getDecoder().decode(message.getFileData());
                    ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                    Image image = new Image(bis);
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(200);
                    imageView.setPreserveRatio(true);
                    
                    // Ajouter un événement de clic pour agrandir l'image
                    imageView.setOnMouseClicked(event -> {
                        Stage imageStage = new Stage();
                        imageStage.setTitle("Image");
                        
                        // Créer une nouvelle ImageView pour la fenêtre popup
                        ImageView fullImageView = new ImageView(image);
                        
                        // Calculer la taille maximale de l'image (80% de la taille de l'écran)
                        Screen screen = Screen.getPrimary();
                        Rectangle2D bounds = screen.getVisualBounds();
                        double maxWidth = bounds.getWidth() * 0.8;
                        double maxHeight = bounds.getHeight() * 0.8;
                        
                        // Ajuster la taille de l'image tout en préservant le ratio
                        if (image.getWidth() > maxWidth || image.getHeight() > maxHeight) {
                            double scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
                            fullImageView.setFitWidth(image.getWidth() * scale);
                            fullImageView.setFitHeight(image.getHeight() * scale);
                        } else {
                            fullImageView.setFitWidth(image.getWidth());
                            fullImageView.setFitHeight(image.getHeight());
                        }
                        
                        // Créer un ScrollPane pour permettre le défilement si l'image est grande
                        ScrollPane scrollPane = new ScrollPane(fullImageView);
                        scrollPane.setFitToWidth(true);
                        scrollPane.setFitToHeight(true);
                        
                        // Ajouter un gestionnaire de clic pour fermer la fenêtre
                        scrollPane.setOnMouseClicked(e -> imageStage.close());
                        
                        Scene scene = new Scene(scrollPane);
                        imageStage.setScene(scene);
                        
                        // Centrer la fenêtre sur l'écran
                        imageStage.centerOnScreen();
                        
                        // Afficher la fenêtre
                        imageStage.show();
                    });
                    
                    // Changer le curseur en main au survol
                    imageView.setStyle("-fx-cursor: hand;");
                    
                    messageContent.getChildren().add(imageView);
                } catch (Exception e) {
                    Label errorLabel = new Label("Erreur lors du chargement de l'image");
                    errorLabel.setTextFill(Color.RED);
                    messageContent.getChildren().add(errorLabel);
                }
            } else if (message.getFileType().equals("audio")) {
                try {
                    // Créer un conteneur pour le lecteur audio
                    HBox audioContainer = new HBox(10);
                    audioContainer.setAlignment(Pos.CENTER_LEFT);

                    // Créer un bouton de lecture/pause
                    Button playButton = new Button("▶");
                    playButton.setStyle("-fx-background-color: #00A884; -fx-text-fill: white; -fx-min-width: 30px;");

                    // Créer une barre de progression
                    ProgressBar progressBar = new ProgressBar(0);
                    progressBar.setPrefWidth(150);
                    HBox.setHgrow(progressBar, Priority.ALWAYS);

                    // Créer un label pour la durée
                    Label durationLabel = new Label("0:00");
                    durationLabel.setTextFill(Color.WHITE);

                    // Ajouter les composants au conteneur
                    audioContainer.getChildren().addAll(playButton, progressBar, durationLabel);

                    // Créer le lecteur audio
                    byte[] audioData = Base64.getDecoder().decode(message.getFileData());
                    File tempFile = File.createTempFile("audio", ".temp");
                    Files.write(tempFile.toPath(), audioData);
                    Media media = new Media(tempFile.toURI().toString());
                    MediaPlayer mediaPlayer = new MediaPlayer(media);
                    tempFile.deleteOnExit();

                    // Gérer le bouton de lecture/pause
                    playButton.setOnAction(e -> {
                        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                            mediaPlayer.pause();
                            playButton.setText("▶");
                        } else {
                            mediaPlayer.play();
                            playButton.setText("⏸");
                        }
                    });

                    // Mettre à jour la barre de progression
                    mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                        if (newTime != null && media.getDuration().greaterThan(Duration.ZERO)) {
                            double progress = newTime.toMillis() / media.getDuration().toMillis();
                            Platform.runLater(() -> {
                                progressBar.setProgress(progress);
                                durationLabel.setText(formatDuration(newTime) + "/" + formatDuration(media.getDuration()));
                            });
                        }
                    });

                    // Réinitialiser le bouton à la fin de la lecture
                    mediaPlayer.setOnEndOfMedia(() -> {
                        Platform.runLater(() -> {
                            playButton.setText("▶");
                            mediaPlayer.stop();
                            mediaPlayer.seek(Duration.ZERO);
                        });
                    });

                    // Permettre de cliquer sur la barre de progression pour se déplacer
                    progressBar.setOnMouseClicked(e -> {
                        double mouseX = e.getX();
                        double width = progressBar.getWidth();
                        double percent = mouseX / width;
                        Duration duration = media.getDuration();
                        double seekTime = duration.toMillis() * percent;
                        mediaPlayer.seek(new Duration(seekTime));
                    });

                    messageContent.getChildren().add(audioContainer);
                } catch (Exception e) {
                    Label errorLabel = new Label("Erreur lors du chargement de l'audio");
                    errorLabel.setTextFill(Color.RED);
                    messageContent.getChildren().add(errorLabel);
                }
            } else if (message.getFileType().equals("video")) {
                try {
                    // Créer un conteneur pour le lecteur vidéo
                    VBox videoContainer = new VBox(10);
                    videoContainer.setAlignment(Pos.CENTER);

                    // Créer un MediaView pour afficher la vidéo
                    MediaView mediaView = new MediaView();
                    mediaView.setFitWidth(300);
                    mediaView.setPreserveRatio(true);

                    // Créer les contrôles
                    HBox controls = new HBox(10);
                    controls.setAlignment(Pos.CENTER);

                    Button playButton = new Button("▶");
                    playButton.setStyle("-fx-background-color: #00A884; -fx-text-fill: white; -fx-min-width: 30px;");

                    ProgressBar progressBar = new ProgressBar(0);
                    progressBar.setPrefWidth(200);
                    HBox.setHgrow(progressBar, Priority.ALWAYS);

                    Label timeLabel = new Label("0:00/0:00");
                    timeLabel.setTextFill(Color.WHITE);

                    controls.getChildren().addAll(playButton, progressBar, timeLabel);

                    // Créer un fichier temporaire pour la vidéo
                    byte[] videoData = Base64.getDecoder().decode(message.getFileData());
                    File tempFile = File.createTempFile("video", ".temp");
                    Files.write(tempFile.toPath(), videoData);
                    Media media = new Media(tempFile.toURI().toString());
                    MediaPlayer mediaPlayer = new MediaPlayer(media);
                    tempFile.deleteOnExit();

                    // Configurer le MediaView avec le MediaPlayer
                    mediaView.setMediaPlayer(mediaPlayer);

                    // Gérer le bouton play/pause
                    playButton.setOnAction(e -> {
                        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                            mediaPlayer.pause();
                            playButton.setText("▶");
                        } else {
                            mediaPlayer.play();
                            playButton.setText("⏸");
                        }
                    });

                    // Mettre à jour la barre de progression et le temps
                    mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                        if (newTime != null && media.getDuration().greaterThan(Duration.ZERO)) {
                            double progress = newTime.toMillis() / media.getDuration().toMillis();
                            Platform.runLater(() -> {
                                progressBar.setProgress(progress);
                                timeLabel.setText(formatDuration(newTime) + "/" + formatDuration(media.getDuration()));
                            });
                        }
                    });

                    // Réinitialiser à la fin de la lecture
                    mediaPlayer.setOnEndOfMedia(() -> {
                        Platform.runLater(() -> {
                            playButton.setText("▶");
                            mediaPlayer.stop();
                            mediaPlayer.seek(Duration.ZERO);
                        });
                    });

                    // Navigation dans la vidéo via la barre de progression
                    progressBar.setOnMouseClicked(e -> {
                        double mouseX = e.getX();
                        double width = progressBar.getWidth();
                        double percent = mouseX / width;
                        Duration duration = media.getDuration();
                        double seekTime = duration.toMillis() * percent;
                        mediaPlayer.seek(new Duration(seekTime));
                    });

                    // Ajouter les composants au conteneur
                    videoContainer.getChildren().addAll(mediaView, controls);
                    messageContent.getChildren().add(videoContainer);

                    // Nettoyer les ressources quand la fenêtre est fermée
                    messageContent.sceneProperty().addListener((obs, oldScene, newScene) -> {
                        if (oldScene != null && newScene == null) {
                            mediaPlayer.dispose();
                        }
                    });

                } catch (Exception e) {
                    Label errorLabel = new Label("Erreur lors du chargement de la vidéo");
                    errorLabel.setTextFill(Color.RED);
                    messageContent.getChildren().add(errorLabel);
                }
            } else {
                // Pour les autres types de fichiers, afficher un bouton de téléchargement
                Button downloadButton = new Button("Télécharger " + message.getFileName());
                downloadButton.setOnAction(e -> {
                    try {
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Enregistrer le fichier");
                        fileChooser.setInitialFileName(message.getFileName());
                        File file = fileChooser.showSaveDialog(messageField.getScene().getWindow());
                        if (file != null) {
                            byte[] fileData = Base64.getDecoder().decode(message.getFileData());
                            Files.write(file.toPath(), fileData);
                            setStatus("Fichier enregistré: " + file.getAbsolutePath());
                        }
                    } catch (IOException ex) {
                        setStatus("Erreur lors de l'enregistrement du fichier: " + ex.getMessage());
                    }
                });
                messageContent.getChildren().add(downloadButton);
            }
        } else {
            // Afficher le texte du message
            final Label contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setTextFill(Color.WHITE);
            messageContent.getChildren().add(contentLabel);
        }

        // Afficher l'heure et le statut du message
        final String statusDisplay = message.getTimestamp().format(timeFormatter)
                + " - " + (message.getStatus() != null ? message.getStatus() : "N/A");
        final Label statusLabel = new Label(statusDisplay);
        statusLabel.setTextFill(Color.GRAY);
        messageContent.getChildren().add(statusLabel);

        // Créer le conteneur final avec l'alignement approprié
        HBox messageBox = new HBox(messageContent);
        messageBox.setMaxWidth(chatHistoryContainer.getWidth() * 0.8);
        messageBox.setPadding(new Insets(2));
        messageBox.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        // Ajouter le message à l'historique
        Platform.runLater(() -> {
            chatHistoryContainer.getChildren().add(messageBox);
            // Faire défiler vers le bas pour voir le nouveau message
            chatHistoryContainer.heightProperty().addListener(observable -> chatHistoryContainer.layout());
        });
    }

    /**
     * Formate la durée pour l'affichage
     * - Convertit la durée en format MM:SS
     */
    private String formatDuration(Duration duration) {
        int seconds = (int) Math.floor(duration.toSeconds());
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * Met à jour le message de statut
     * - Affiche le message dans l'interface
     */
    private void setStatus(final String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    /**
     * Charge le statut de l'utilisateur
     * - Récupère le statut du serveur
     * - Met à jour l'interface
     */
    private void loadUserStatus() {
        if (chatService == null || userEmail == null) {
            return; // Ne rien faire si le service ou l'email n'est pas encore configuré
        }
        
        try {
            User user = chatService.getUser(userEmail);
            Platform.runLater(() -> {
                userStatusLabel.setText(user.getStatus());
            });
        } catch (IOException e) {
            setStatus("Erreur lors du chargement du statut: " + e.getMessage());
        }
    }

    /**
     * Arrête l'indicateur de frappe
     * - Envoie le message d'arrêt
     * - Réinitialise l'interface
     */
    private void stopTypingIndicator() {
        if (selectedContact != null && isTyping) {
            isTyping = false;
            try {
                Message stopTypingMessage = new Message();
                stopTypingMessage.setSenderEmail(userEmail);
                stopTypingMessage.setReceiverEmail(selectedContact);
                stopTypingMessage.setType(MessageType.STOP_TYPING);
                chatService.sendMessage(stopTypingMessage);
            } catch (IOException e) {
                System.err.println("Erreur lors de l'envoi de l'événement STOP_TYPING: " + e.getMessage());
            }
        }
        Platform.runLater(() -> typingIndicatorLabel.setText(""));
    }

    /**
     * Met à jour le style d'un contact
     * - Gère l'affichage des messages non lus
     */
    private void updateContactStyle(String contact, boolean hasUnreadMessages) {
        Platform.runLater(() -> {
            Label label = contactLabels.get(contact);
            if (label != null) {
                if (hasUnreadMessages) {
                    label.setStyle("-fx-font-weight: bold;");
                } else {
                    label.setStyle("");
                }
            }
        });
    }

    /**
     * Configure le menu contextuel des messages
     * - Ajoute les options de réponse
     */
    private void setupMessageContextMenu(VBox messageContent, Message message) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem replyMenuItem = new MenuItem("Répondre");
        replyMenuItem.setOnAction(event -> handleQuoteMessage(message));
        contextMenu.getItems().add(replyMenuItem);

        messageContent.setOnContextMenuRequested(event -> {
            contextMenu.show(messageContent, event.getScreenX(), event.getScreenY());
        });
    }

    /**
     * Gère la citation d'un message
     * - Stocke le message à citer
     * - Affiche l'aperçu
     */
    private void handleQuoteMessage(Message messageToQuote) {
        this.quotedMessage = messageToQuote;
        showQuotePreview(messageToQuote);
        messageField.requestFocus();
    }

    /**
     * Affiche l'aperçu du message cité
     * - Crée les composants visuels
     * - Affiche le contenu
     */
    private void showQuotePreview(Message quotedMessage) {
        Platform.runLater(() -> {
            quotedMessagePreview.getChildren().clear();
            
            HBox quoteContainer = new HBox(8);
            quoteContainer.setPadding(new Insets(8));
            
            Region verticalBar = new Region();
            verticalBar.setMinWidth(4);
            verticalBar.setStyle("-fx-background-color: #00A884;");
            
            VBox quoteContent = new VBox(4);
            
            HBox header = new HBox();
            header.setAlignment(Pos.CENTER_LEFT);
            header.setSpacing(8);
            
            Label senderLabel = new Label(quotedMessage.getSenderEmail());
            senderLabel.setStyle("-fx-text-fill: #00A884; -fx-font-weight: bold;");
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            
            Button closeButton = new Button("✕");
            closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #8696A0;");
            closeButton.setOnAction(e -> handleCancelQuote());
            
            header.getChildren().addAll(senderLabel, spacer, closeButton);
            
            // Contenu du message cité
            String content = quotedMessage.getContent();
            if (content.length() > 50) {
                content = content.substring(0, 47) + "...";
            }
            Label contentLabel = new Label(content);
            contentLabel.setWrapText(true);
            contentLabel.setStyle("-fx-text-fill: #8696A0;");
            
            quoteContent.getChildren().addAll(header, contentLabel);
            quoteContainer.getChildren().addAll(verticalBar, quoteContent);
            
            // Fond gris pour la citation
            VBox quoteBackground = new VBox(quoteContainer);
            quoteBackground.setStyle("-fx-background-color: rgba(0, 0, 0, 0.2); -fx-background-radius: 4;");
            
            quotedMessagePreview.getChildren().add(quoteBackground);
            quotedMessagePreview.setVisible(true);
            quotedMessagePreview.setManaged(true);
        });
    }

    /**
     * Annule la citation
     * - Réinitialise l'interface
     */
    @FXML
    private void handleCancelQuote() {
        this.quotedMessage = null;
        quotedMessagePreview.getChildren().clear();
        quotedMessagePreview.setVisible(false);
        quotedMessagePreview.setManaged(false);
        messageField.requestFocus();
    }

    /**
     * Ouvre la fenêtre de profil
     * - Charge et affiche l'interface
     */
    @FXML
    private void handleOpenProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getClassLoader().getResource("fxml/profile.fxml"));
            Parent profileView = loader.load();
            
            ProfileController profileController = loader.getController();
            profileController.setChatService(chatService);
            profileController.setUserEmail(userEmail);
            
            Stage profileStage = new Stage();
            profileStage.setTitle("Profil");
            profileStage.setScene(new Scene(profileView));
            profileStage.initOwner(userEmailLabel.getScene().getWindow());
            profileStage.show();
        } catch (IOException e) {
            setStatus("Erreur lors de l'ouverture du profil: " + e.getMessage());
        }
    }

    /**
     * Gère l'enregistrement audio
     * - Démarre/arrête l'enregistrement
     * - Envoie le message audio
     */
    @FXML
    private void handleRecordAudio() {
        if (selectedContact == null) {
            setStatus("Veuillez sélectionner un contact avant d'enregistrer un audio");
            return;
        }

        if (!isRecording) {
            // Démarrage de l'enregistrement
            try {
                // Création du fichier temporaire
                audioFile = File.createTempFile("recording", ".wav");
                audioFile.deleteOnExit();

                // Configuration de l'enregistreur audio
                AudioFormat format = new AudioFormat(44100, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    setStatus("L'enregistrement audio n'est pas supporté sur cet appareil");
                    return;
                }

                // Initialisation de la ligne audio
                audioLine = (TargetDataLine) AudioSystem.getLine(info);
                audioLine.open(format);
                audioLine.start();

                // Thread d'enregistrement
                Thread recordingThread = new Thread(() -> {
                    try (AudioInputStream ais = new AudioInputStream(audioLine)) {
                        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audioFile);
                    } catch (IOException e) {
                        Platform.runLater(() -> setStatus("Erreur lors de l'enregistrement: " + e.getMessage()));
                    }
                });

                // Démarrage de l'enregistrement
                recordingThread.start();
                isRecording = true;
                recordButton.setText("⏹");
                recordButton.setStyle("-fx-background-color: #FF0000; -fx-text-fill: white;");
                setStatus("Enregistrement en cours...");

            } catch (Exception e) {
                setStatus("Erreur lors du démarrage de l'enregistrement: " + e.getMessage());
            }
        } else {
            // Arrêt de l'enregistrement
            try {
                if (audioLine != null) {
                    audioLine.stop();
                    audioLine.close();
                    audioLine = null;
                }

                // Envoi du fichier audio
                if (audioFile != null && audioFile.exists()) {
                    byte[] audioData = Files.readAllBytes(audioFile.toPath());
                    String base64Data = Base64.getEncoder().encodeToString(audioData);

                    // Création et envoi du message audio
                    Message audioMessage = Message.createFileMessage(
                        userEmail,
                        selectedContact,
                        "audio_" + System.currentTimeMillis() + ".wav",
                        "audio",
                        base64Data,
                        audioFile.length()
                    );

                    chatService.sendMessage(audioMessage);
                    addMessageToChat(audioMessage);
                    localRepo.addLocalMessage(userEmail, audioMessage);
                    setStatus("Message vocal envoyé");

                    // Nettoyage
                    audioFile.delete();
                    audioFile = null;
                }

                // Réinitialisation de l'interface
                isRecording = false;
                recordButton.setText("🎤");
                recordButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #8696A0;");

            } catch (Exception e) {
                setStatus("Erreur lors de l'envoi de l'audio: " + e.getMessage());
            }
        }
    }
}