package org.example.client.gui.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.example.client.gui.repository.JsonLocalMessageRepository;
import org.example.client.gui.service.ChatService;
import org.example.client.gui.service.ContactService;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
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
    private Group selectedGroup; // Nouvelle variable pour suivre le groupe sélectionné

    private final ObservableList<String> contacts = FXCollections.observableArrayList();
    private final ObservableList<Group> groups = FXCollections.observableArrayList(); // Déclaration de l'ObservableList
    private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    private final JsonLocalMessageRepository localRepo = new JsonLocalMessageRepository();
    private final GroupService groupService = new GroupService();
    private final ContactService contactService = new ContactService();
    private final Object loadLock = new Object(); // verrou pour synchroniser le chargement

    @FXML
    public void initialize() {
        contactListView.setItems(contacts);
        groupListView.setItems(groups);

        // Pour les contacts
        contactListView.setCellFactory(lv -> new ListCell<String>() {
            private final ImageView avatar = createCircularAvatar(null, 30);
            private final Label emailLabel = new Label();
            private final Label lastMessageLabel = new Label();
            private final VBox textVBox = new VBox(emailLabel, lastMessageLabel);
            private final HBox hbox = new HBox(10, avatar, textVBox);

            {
                lastMessageLabel.setTextFill(Color.GRAY);
                lastMessageLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                hbox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(final String email, final boolean empty) {
                super.updateItem(email, empty);
                if (empty || email == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    emailLabel.setText(email);
                    String imageUrl = "/images/default_avatar.png";
                    String lastMsgText = "";

                    try {
                        if (chatService != null) {
                            imageUrl = chatService.getUserProfilePicture(email);
                            final long myId = chatService.getCurrentUserId();
                            final long contactId = chatService.getUserId(email);
                            final Optional<Message> lastMsgOpt = localRepo.getLastContactMessage(userEmail, myId,
                                    contactId);
                            if (lastMsgOpt.isPresent()) {
                                final Message lastMsg = lastMsgOpt.get();
                                final String prefix = lastMsg.getSenderUserId() == myId ? "Vous: " : "";
                                lastMsgText = prefix + truncate(lastMsg.getContent(), 30);
                            }
                        }
                    } catch (final IOException e) {
                        lastMsgText = "Erreur chargement";
                    }

                    final Image img = loadImage(imageUrl, 30);
                    avatar.setImage(img != null ? img : loadImage("/images/default_avatar.png", 30));
                    lastMessageLabel.setText(lastMsgText);
                    setGraphic(hbox);
                }
            }
        });

        // CellFactory pour les groupes
        groupListView.setCellFactory(lv -> new ListCell<Group>() {
            private final ImageView avatar = createCircularAvatar(null, 30);
            private final Label nameLabel = new Label();
            private final Label lastMessageLabel = new Label();
            private final VBox textVBox = new VBox(nameLabel, lastMessageLabel);
            private final HBox hbox = new HBox(10, avatar, textVBox);

            {
                lastMessageLabel.setTextFill(Color.GRAY);
                lastMessageLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                hbox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(final Group group, final boolean empty) {
                super.updateItem(group, empty);
                if (empty || group == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    nameLabel.setText(group.getName());
                    String imageUrl = group.getProfilePictureUrl();
                    if (imageUrl == null || imageUrl.isEmpty()) {
                        imageUrl = "/images/default_group.png";
                    }
                    String lastMsgText = "";

                    try {
                        // Récupérer le dernier message du groupe
                        final Optional<Message> lastMsgOpt = localRepo.getLastGroupMessage(userEmail, group.getId());
                        if (lastMsgOpt.isPresent()) {
                            final Message lastMsg = lastMsgOpt.get();
                            String senderName = "Quelqu'un"; // Nom par défaut
                            long myId = -1; // ID par défaut

                            if (chatService != null) { // Vérifier si chatService est initialisé
                                myId = chatService.getCurrentUserId();
                                try {
                                    // Essayer de récupérer l'email de l'expéditeur pour l'affichage
                                    // Gérer l'IOException ici
                                    senderName = chatService.getUserEmail(lastMsg.getSenderUserId()); // Peut lancer
                                                                                                      // IOException
                                    senderName = senderName.split("@")[0]; // Juste la partie avant @
                                } catch (final IOException e) {
                                    System.err.println("Erreur cellFactory groupe (getUserEmail): " + e.getMessage());
                                    senderName = "Inconnu"; // Utiliser un nom de fallback en cas d'erreur
                                }
                            }

                            final String prefix = (chatService != null && lastMsg.getSenderUserId() == myId) ? "Vous: "
                                    : senderName + ": ";
                            lastMsgText = prefix + truncate(lastMsg.getContent(), 30);
                        }
                    } catch (final IOException e) {
                        System.err.println("Erreur cellFactory groupe (getLastGroupMessage) (" + group.getName() + "): "
                                + e.getMessage());
                        lastMsgText = "Erreur chargement";
                    } catch (final NullPointerException npe) {
                        System.err.println(
                                "Erreur cellFactory groupe (" + group.getName() + "): chatService non initialisé?");
                    }

                    final Image img = loadImage(imageUrl, 30);
                    if (img != null) {
                        avatar.setImage(img);
                    } else {
                        avatar.setImage(loadImage("/images/default_group.png", 30)); // Fallback
                    }
                    lastMessageLabel.setText(lastMsgText);
                    setGraphic(hbox);
                }
            }
        });

        // Lors de la sélection d'un contact, vider la sélection du groupe
        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                groupListView.getSelectionModel().clearSelection();
                selectedContact = newVal;
                selectedGroup = null; // Reset group selection
                loadContactConversation(selectedContact);
                setStatus("Conversation chargée avec " + selectedContact);
            }
        });

        // Lors de la sélection d'un groupe, vider la sélection des contacts
        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                contactListView.getSelectionModel().clearSelection();
                selectedGroup = newVal; // Stocker la sélection
                selectedContact = null; // Reset contact selection
                loadGroupConversation(newVal);
                setStatus("Conversation de groupe chargée : " + newVal.getName());
            }
        });

        // Permettre d'envoyer un message avec la touche Entrée
        messageField.setOnAction(this::handleSendMessage);
    }

    // Méthode utilitaire pour charger une image à partir d'une URL (ressource)
    private Image loadImage(String imageUrl, final double size) {
        if (imageUrl == null) {
            System.err.println("L'URL de l'image est null, utilisation d'un fallback");
            return null;
        }
        // Si l'URL ne commence pas par '/' on ajoute par défaut "/images/"
        if (!imageUrl.startsWith("/")) {
            imageUrl = "/images/" + imageUrl;
        }
        try (InputStream stream = getClass().getResourceAsStream(imageUrl)) {
            if (stream != null) {
                return new Image(stream, size, size, true, true);
            } else {
                System.err.println("Image introuvable: " + imageUrl);
                return null;
            }
        } catch (final IOException e) {
            System.err.println("Erreur lors du chargement de l'image " + imageUrl + ": " + e.getMessage());
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
            if (message.getGroupId() != null) {
                groupListView.refresh(); // Rafraîchir la liste des groupes
            } else {
                contactListView.refresh(); // Rafraîchir la liste des contacts
            }
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
            final boolean added = contactService.addContact(userEmail, email);
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
            final boolean removed = contactService.removeContact(userEmail, selectedContact);
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
            final List<String> contactList = contactService.getContacts(userEmail);
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
                groupListView.refresh();
                return;
            } else {
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

                if (selectedGroup == null) { // Uniquement si aucun groupe n'est sélectionné
                    if (selectedContact == null) {
                        selectedContact = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
                        contactListView.getSelectionModel().select(selectedContact);
                        setStatus("Conversation chargée avec " + selectedContact);
                    }
                }

                if (selectedGroup == null && selectedContact != null &&
                        (senderEmail.equals(selectedContact) || receiverEmail.equals(selectedContact))) {
                    addMessageToChat(message);
                }

                try {
                    localRepo.addLocalMessage(userEmail, message);
                } catch (final IOException e) {
                    System.err.println("Erreur de sauvegarde locale : " + e.getMessage());
                }
                contactListView.refresh();

                final String otherUser = senderEmail.equals(userEmail) ? receiverEmail : senderEmail;
                if (!contacts.contains(otherUser)) {
                    contacts.add(otherUser);
                }

                setStatus("Nouveau message reçu");
            }
        });
    }

    private void addMessageToChat(final Message message) {
        if (message.getGroupId() != null && selectedGroup == null)
            return;
        if (message.getGroupId() == null && selectedContact == null)
            return;

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

    private String truncate(final String text, final int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}