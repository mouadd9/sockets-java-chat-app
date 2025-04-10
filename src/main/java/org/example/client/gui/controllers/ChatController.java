package org.example.client.gui.controllers;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.example.client.repository.JsonLocalMessageRepository;
import org.example.model.Message;
import org.example.service.ChatService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ChatController {
    @FXML
    private Label userEmailLabel;

    @FXML
    private ListView<String> contactListView;

    @FXML
    private TextField newContactField;

    @FXML
    private VBox chatHistoryContainer;

    @FXML
    private TextField messageField;

    @FXML
    private Label statusLabel;

    private ChatService chatService;
    private String userEmail;
    private String selectedContact;

    private final ObservableList<String> contacts = FXCollections.observableArrayList();
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final JsonLocalMessageRepository localRepo = new JsonLocalMessageRepository();

    @FXML
    public void initialize() {
        contactListView.setItems(contacts);

        // Configurer le clic sur un contact
        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedContact = newVal;
                loadConversation(selectedContact);
                setStatus("Conversation chargée avec " + selectedContact);
            }
        });

        // Permettre d'envoyer un message avec la touche Entrée
        messageField.setOnAction(this::handleSendMessage);
    }

    // this function is called when we first create and cnofigure the controller
    // it sets dependencies, and somehow gets incoming messages that were received
    // when authenticating
    public void initData(final ChatService chatService, final String userEmail) {
        this.chatService = chatService;
        this.userEmail = userEmail;
        this.userEmailLabel.setText(userEmail);

        // Configurer le Consumer pour recevoir les messages
        chatService.setMessageConsumer(this::handleIncomingMessage); // !!

        // Charger les contacts
        loadContacts();
    }

    @FXML
    private void handleSendMessage(final ActionEvent event) {
        final String content = messageField.getText().trim();

        if (content.isEmpty() || selectedContact == null) {
            return;
        }

        try {
            final Message message = chatService.createDirectMessage(userEmail, selectedContact, content);
            chatService.sendMessage(message);
            messageField.clear();

            // Ajouter le message à la conversation
            addMessageToChat(message);
            setStatus("Message envoyé");
            localRepo.addLocalMessage(userEmail, message);
        } catch (final IOException e) {
            setStatus("Erreur lors de l'envoi du message: " + e.getMessage());
        }
    }

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
                chatHistoryContainer.getChildren().clear();
                // Conversion des emails en IDs et appel de la méthode mise à jour
                final long myId = chatService.getCurrentUserId();
                final long contactId = chatService.getUserId(selectedContact);
                localRepo.removeConversation(userEmail, myId, contactId);
                setStatus("Contact et conversation supprimés: " + selectedContact);
                selectedContact = null;
            } else {
                setStatus("La suppression du contact a échoué");
            }
        } catch (final IOException e) {
            setStatus("Erreur: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        try {
            chatService.disconnect();

            // Revenir à l'écran de connexion
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            final Parent loginView = loader.load();

            final Stage stage = (Stage) userEmailLabel.getScene().getWindow();
            stage.setTitle("Chat Application");
            stage.setScene(new Scene(loginView, 600, 400));
            stage.centerOnScreen();
        } catch (final IOException e) {
            setStatus("Erreur lors de la déconnexion: " + e.getMessage());
        }
    }

    private void loadContacts() {
        try {
            final List<String> contactList = chatService.getContacts(userEmail);
            Platform.runLater(() -> {
                contacts.clear();
                contacts.addAll(contactList);

                // Sélectionner automatiquement le premier contact
                if (!contacts.isEmpty()) {
                    contactListView.getSelectionModel().select(0);
                }
            });
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement des contacts: " + e.getMessage());
        }
    }

    private void loadConversation(final String contactEmail) {
        chatHistoryContainer.getChildren().clear();

        try {
            final List<Message> localMessages = localRepo.loadLocalMessages(userEmail);
            final long myId = chatService.getCurrentUserId();
            final long contactId = chatService.getUserId(contactEmail);

            // Filtrer uniquement les messages de la conversation entre l'utilisateur et le
            // contact
            localMessages.stream()
                    .filter(m -> (m.getSenderUserId() == myId && m.getReceiverUserId() != null
                            && m.getReceiverUserId().equals(contactId))
                            || (m.getSenderUserId() == contactId && m.getReceiverUserId() != null
                                    && m.getReceiverUserId().equals(myId)))
                    .forEach(this::addMessageToChat);
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement de l'historique local : " + e.getMessage());
        }
    }

    private void handleIncomingMessage(final Message message) {
        Platform.runLater(() -> {
            String senderEmail = "";
            String receiverEmail = "";
            try {
                senderEmail = chatService.getUserEmail(message.getSenderUserId());
                if (message.getReceiverUserId() != null) {
                    receiverEmail = chatService.getUserEmail(message.getReceiverUserId());
                }
            } catch (final IOException e) {
                setStatus("Erreur lors de la récupération de l'email de l'utilisateur: " + e.getMessage());
                return;
            }

            // Si aucun contact n'est sélectionné, déterminer celui-ci selon l'expéditeur et
            // le destinataire.
            if (selectedContact == null) {
                selectedContact = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
                contactListView.getSelectionModel().select(selectedContact);
                setStatus("Conversation chargée avec " + selectedContact);
            }

            // Afficher le message uniquement s'il appartient à la conversation active.
            if (selectedContact != null &&
                    (senderEmail.equals(selectedContact) || receiverEmail.equals(selectedContact))) {
                addMessageToChat(message);
            }

            // Enregistrer le message en local.
            try {
                localRepo.addLocalMessage(userEmail, message);
            } catch (final IOException e) {
                System.err.println("Erreur de sauvegarde locale : " + e.getMessage());
            }

            // Mise à jour des contacts si nécessaire.
            final String otherUser = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
            if (!contacts.contains(otherUser)) {
                contacts.add(otherUser);
            }

            setStatus("Nouveau message reçu");
        });
    }

    private void addMessageToChat(final Message message) {
        final boolean isMine = message.getSenderUserId() == chatService.getCurrentUserId();
        final String senderDisplay = isMine ? userEmail : selectedContact; // pour l'affichage si besoin

        final HBox messageBox = new HBox();
        messageBox.setMaxWidth(chatHistoryContainer.getWidth() * 0.8);
        messageBox.setPadding(new Insets(5));
        messageBox.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        final VBox messageContent = new VBox();
        messageContent.setMaxWidth(chatHistoryContainer.getWidth() * 0.7);
        messageContent.setPadding(new Insets(10));
        messageContent.setStyle("-fx-background-color: " + (isMine ? "#DCF8C6" : "#E1E1E1") + ";" +
                "-fx-background-radius: 10;");

        final Label contentLabel = new Label(message.getContent());
        contentLabel.setWrapText(true);

        final String statusDisplay = message.getTimestamp().format(timeFormatter)
                + " - " + (message.getStatus() != null ? message.getStatus() : "N/A");
        final Label statusLabel = new Label(statusDisplay);
        statusLabel.setTextFill(Color.GRAY);

        messageContent.getChildren().addAll(contentLabel, statusLabel);
        messageBox.getChildren().add(messageContent);

        Platform.runLater(() -> {
            chatHistoryContainer.getChildren().add(messageBox);
            chatHistoryContainer.heightProperty().addListener(observable -> chatHistoryContainer.layout());
        });
    }

    private void setStatus(final String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }
}