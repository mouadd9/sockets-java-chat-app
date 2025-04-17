package org.example.client.gui.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.example.client.gui.repository.JsonLocalMessageRepository;
import org.example.client.gui.service.ChatService;
import org.example.client.gui.service.GroupService;
import org.example.shared.model.Group;
import org.example.shared.model.Message;

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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    private ListView<Group> groupListView; // Utilisation d'un ListView de type Group

    @FXML
    private TextField newContactField;

    @FXML
    private VBox chatHistoryContainer;

    @FXML
    private TextField messageField;

    @FXML
    private Label statusLabel;

    @FXML
    private TextField groupNameField; // zone de saisie pour le nom du groupe

    @FXML
    private TextField memberEmailField; // zone de saisie pour l'email du membre à ajouter

    @FXML
    private ScrollPane chatScrollPane;

    private ChatService chatService;
    private String userEmail;
    private String selectedContact;

    private final ObservableList<String> contacts = FXCollections.observableArrayList();
    private final ObservableList<Group> groups = FXCollections.observableArrayList(); // Déclaration de l'ObservableList
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final JsonLocalMessageRepository localRepo = new JsonLocalMessageRepository();
    private final GroupService groupService = new GroupService();
    private final Object loadLock = new Object(); // verrou pour synchroniser le chargement

    @FXML
    public void initialize() {
        contactListView.setItems(contacts);
        groupListView.setItems(groups);

        // Pour les contacts
        // Dans la méthode initialize(), modifiez le cell factory pour les contacts :
        contactListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(final String email, final boolean empty) {
                super.updateItem(email, empty);
                if (empty || email == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    final HBox hbox = new HBox(10);
                    String imageUrl;
                    try {
                        // Récupération de l'URL via ChatService, en gérant l'exception
                        imageUrl = chatService.getUserProfilePicture(email);
                    } catch (IOException e) {
                        // En cas d'erreur, afficher un message et utiliser l'image par défaut
                        System.err.println("Erreur lors de la récupération de l'image pour "
                                + email + ": " + e.getMessage());
                        imageUrl = null; // ou une URL d'image d'erreur spécifique si vous préférez
                    }
                    // Utiliser l'image par défaut si l'URL est nulle ou vide après la tentative
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        imageUrl = "/images/default_avatar.png";
                    }
                    final ImageView avatar = createCircularAvatar(imageUrl, 30);
                    final Label emailLabel = new Label(email);
                    hbox.getChildren().addAll(avatar, emailLabel);
                    setGraphic(hbox);
                }
            }
        });

        // Pour les groupes
        groupListView.setCellFactory(lv -> new ListCell<Group>() {
            @Override
            protected void updateItem(final Group group, final boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    final HBox hbox = new HBox(10);
                    final String groupImageUrl = (group.getProfilePictureUrl() != null
                            && !group.getProfilePictureUrl().isEmpty())
                                    ? group.getProfilePictureUrl()
                                    : "/images/default_group.png";
                    final ImageView groupAvatar = createCircularAvatar(groupImageUrl, 30);
                    final Label groupLabel = new Label(group.getName());
                    hbox.getChildren().addAll(groupAvatar, groupLabel);
                    setGraphic(hbox);
                }
            }
        });

        // Lors de la sélection d'un contact, vider la sélection du groupe
        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                groupListView.getSelectionModel().clearSelection();
                selectedContact = newVal;
                loadContactConversation(selectedContact);
                setStatus("Conversation chargée avec " + selectedContact);
            }
        });

        // Lors de la sélection d'un groupe, vider la sélection des contacts
        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                contactListView.getSelectionModel().clearSelection();
                loadGroupConversation(newVal);
                setStatus("Conversation de groupe chargée : " + newVal.getName());
            }
        });

        // Permettre d'envoyer un message avec la touche Entrée
        messageField.setOnAction(this::handleSendMessage);
    }

    // Méthode utilitaire pour charger une image à partir d'une URL (ressource)
    private Image loadImage(String imageUrl, final double size) {
        if (!imageUrl.startsWith("/")) {
            imageUrl = "/images/" + imageUrl;
        }
        final InputStream stream = getClass().getResourceAsStream(imageUrl);
        if (stream != null) {
            return new Image(stream, size, size, true, true);
        } else {
            System.err.println("Image introuvable: " + imageUrl);
            return null;
        }
    }

    // Crée un ImageView avec un avatar circulaire
    private ImageView createCircularAvatar(final String imageUrl, final double size) {
        final ImageView imageView = new ImageView();
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        final javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(size / 2, size / 2, size / 2);
        imageView.setClip(clip);
        final Image image = loadImage(imageUrl, size);
        if (image != null) {
            imageView.setImage(image);
        }
        return imageView;
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

        // Charger les groupes
        loadGroups();
    }

    private void loadGroups() {
        try {
            final List<Group> groupList = chatService.getGroupsForUser(userEmail);
            Platform.runLater(() -> {
                groups.clear();
                groups.addAll(groupList);

            });
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement des groupes : " + e.getMessage());
        }
    }

    @FXML
    private void handleSendMessage(final ActionEvent event) {
        final String content = messageField.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        try {
            Message message;
            // Si un contact est sélectionné, envoyer un message direct
            if (contactListView.getSelectionModel().getSelectedItem() != null) {
                selectedContact = contactListView.getSelectionModel().getSelectedItem();
                message = chatService.createDirectMessage(userEmail, selectedContact, content);
                chatService.sendMessage(message);
            }
            // Sinon, si un groupe est sélectionné, envoyer un message de groupe
            else if (groupListView.getSelectionModel().getSelectedItem() != null) {
                final Group selectedGroup = groupListView.getSelectionModel().getSelectedItem();
                message = chatService.createGroupMessage(userEmail, selectedGroup.getId(), content);
                chatService.sendGroupMessage(message);
            } else {
                setStatus("Veuillez sélectionner un groupe ou un contact.");
                return;
            }
            messageField.clear();
            addMessageToChat(message);
            setStatus("Message envoyé");
            localRepo.addLocalMessage(userEmail, message);
        } catch (final IOException e) {
            setStatus("Erreur lors de l'envoi du message : " + e.getMessage());
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
            chatHistoryContainer.getChildren().clear();

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

    @FXML
    private void handleSelectGroup() {
        final Group selectedGroup = groupListView.getSelectionModel().getSelectedItem();
        if (selectedGroup != null) {
            loadGroupConversation(selectedGroup);
            setStatus("Conversation de groupe chargée : " + selectedGroup.getName());
        }
    }

    @FXML
    private void handleCreateGroup(final ActionEvent event) {
        final String groupName = groupNameField.getText().trim();
        if (groupName.isEmpty()) {
            setStatus("Le nom du groupe est obligatoire");
            return;
        }
        final long ownerId = chatService.getCurrentUserId();
        final Group createdGroup = groupService.createGroup(groupName, ownerId);
        if (createdGroup.getId() > 0) {
            setStatus("Groupe créé : " + createdGroup.getName());
            groups.add(createdGroup);
            groupNameField.clear();
        } else {
            setStatus("Erreur lors de la création du groupe");
        }
    }

    @FXML
    private void handleAddMemberToGroup(final ActionEvent event) {
        final String memberEmail = memberEmailField.getText().trim();
        if (memberEmail.isEmpty()) {
            setStatus("Veuillez entrer l'email du membre à ajouter");
            return;
        }
        final Group selectedGroup = groupListView.getSelectionModel().getSelectedItem();
        if (selectedGroup == null) {
            setStatus("Veuillez sélectionner un groupe");
            return;
        }
        try {
            final long memberId = chatService.getUserId(memberEmail);
            final boolean success = groupService.addMemberToGroup(selectedGroup.getId(), memberId);
            if (success) {
                setStatus("Membre ajouté avec succès");
                memberEmailField.clear();
            } else {
                setStatus("Le membre est déjà présent ou l'ajout a échoué");
            }
        } catch (final IOException e) {
            setStatus("Erreur lors de l'ajout du membre : " + e.getMessage());
        }
    }

    private void loadContacts() {
        try {
            final List<String> contactList = chatService.getContacts(userEmail);
            Platform.runLater(() -> {
                contacts.clear();
                contacts.addAll(contactList);

            });
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement des contacts: " + e.getMessage());
        }
    }

    private void handleIncomingMessage(final Message message) {
        Platform.runLater(() -> {
            // Si le message est un message de groupe, le traiter séparément
            if (message.getGroupId() != null) {
                // Vérifier si le groupe est déjà présent dans la liste
                final boolean groupExists = groups.stream().anyMatch(g -> g.getId() == message.getGroupId());
                if (!groupExists) {
                    try {
                        // Récupérer le groupe depuis GroupDAO
                        final Group newGroup = new org.example.shared.dao.GroupDAO()
                                .findGroupById(message.getGroupId());
                        if (newGroup != null) {
                            groups.add(newGroup);
                        }
                    } catch (final Exception e) {
                        setStatus("Erreur lors du chargement du groupe: " + e.getMessage());
                    }
                }
                // Afficher le message uniquement si le groupe actuellement sélectionné
                // correspond
                final Group currentGroup = groupListView.getSelectionModel().getSelectedItem();
                if (currentGroup != null && currentGroup.getId() == message.getGroupId()) {
                    addMessageToChat(message);
                }
                try {
                    localRepo.addLocalMessage(userEmail, message);
                } catch (final IOException e) {
                    System.err.println("Erreur de sauvegarde locale : " + e.getMessage());
                }
                setStatus("Nouveau message de groupe reçu");
                return;
            }

            // Pour les messages directs
            String senderEmail = "";
            String receiverEmail = "";
            try {
                senderEmail = chatService.getUserEmail(message.getSenderUserId());
                if (message.getReceiverUserId() != null) {
                    receiverEmail = chatService.getUserEmail(message.getReceiverUserId());
                }
            } catch (final IOException e) {
                setStatus("Erreur lors de la récupération de l'email: " + e.getMessage());
                return;
            }

            if (selectedContact == null) {
                selectedContact = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
                contactListView.getSelectionModel().select(selectedContact);
                setStatus("Conversation chargée avec " + selectedContact);
            }

            if (selectedContact != null &&
                    (senderEmail.equals(selectedContact) || receiverEmail.equals(selectedContact))) {
                addMessageToChat(message);
            }

            try {
                localRepo.addLocalMessage(userEmail, message);
            } catch (final IOException e) {
                System.err.println("Erreur de sauvegarde locale : " + e.getMessage());
            }

            final String otherUser = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
            if (!contacts.contains(otherUser)) {
                contacts.add(otherUser);
            }

            setStatus("Nouveau message reçu");
        });
    }

    private void addMessageToChat(final Message message) {
        final boolean isMine = message.getSenderUserId() == chatService.getCurrentUserId();

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
            Platform.runLater(this::scrollToBottom);
        });
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            chatScrollPane.setVvalue(1.0);
        });
    }

    private void setStatus(final String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    private void loadContactConversation(final String contactEmail) {
        Platform.runLater(() -> {
            synchronized (loadLock) {
                chatHistoryContainer.getChildren().clear();
                try {
                    final long myId = chatService.getCurrentUserId();
                    final long contactId = chatService.getUserId(contactEmail);
                    final List<Message> contactMessages = localRepo.loadContactMessages(userEmail, myId, contactId);
                    contactMessages.forEach(this::addMessageToChat);
                    scrollToBottom(); // Défilement après chargement des messages
                } catch (final IOException e) {
                    setStatus("Erreur lors du chargement de la conversation avec " + contactEmail + " : "
                            + e.getMessage());
                }
            }
        });
    }

    private void loadGroupConversation(final Group group) {
        Platform.runLater(() -> {
            synchronized (loadLock) {
                chatHistoryContainer.getChildren().clear();
                try {
                    final List<Message> groupMessages = localRepo.loadGroupMessages(userEmail, group.getId());
                    groupMessages.forEach(this::addMessageToChat);
                    scrollToBottom(); // Défilement après chargement des messages
                } catch (final IOException e) {
                    setStatus("Erreur lors du chargement de l'historique de groupe : " + e.getMessage());
                }
            }
        });
    }
}