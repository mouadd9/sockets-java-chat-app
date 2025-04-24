package org.example.client.gui.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.example.client.gui.repository.JsonLocalMessageRepository;
import org.example.client.gui.service.CallManager;
import org.example.client.gui.service.ChatService;
import org.example.client.gui.service.ContactService;
import org.example.client.gui.service.GroupService;
import org.example.client.gui.service.UserService;
import org.example.shared.model.CallSession;
import org.example.shared.model.CallSignal;
import org.example.shared.model.Group;
import org.example.shared.model.Message;
import org.example.shared.model.User;

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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ChatController {
    @FXML private Label userEmailLabel;
    @FXML private ListView<User> contactListView;
    @FXML private ListView<Group> groupListView;
    @FXML private TextField newContactField, messageField, groupNameField, memberEmailField;
    @FXML private VBox chatHistoryContainer;
    @FXML private ScrollPane chatScrollPane;
    @FXML private Label statusLabel;
    
    // Éléments d'interface pour les appels
    @FXML private Button callButton;
    @FXML private VBox callControlsBox;
    @FXML private Label callStatusLabel;
    @FXML private Button acceptCallButton;
    @FXML private Button rejectCallButton;
    @FXML private Button endCallButton;
    @FXML private ToggleButton muteButton;

    private ChatService chatService;
    private String userEmail;
    private User selectedContactUser;
    private Group selectedGroup;

    private final ObservableList<User> contacts = FXCollections.observableArrayList();
    private final ObservableList<Group> groups = FXCollections.observableArrayList();
    
    private final JsonLocalMessageRepository localRepo = new JsonLocalMessageRepository();
    private final ContactService contactService = new ContactService();
    private final GroupService groupService = new GroupService();
    private final UserService userService = new UserService();
    private final CallManager callManager = CallManager.getInstance();

    private final Object loadLock = new Object();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        contactListView.setItems(contacts);
        groupListView.setItems(groups);

        contactListView.setCellFactory(list -> createCell(
            user -> user.getDisplayNameOrEmail(),
            user -> {
                try {
                    return localRepo.getLastContactMessage(userEmail, 
                            chatService.getCurrentUserId(), 
                            user.getId());
                } catch (final IOException e) {
                    return Optional.empty();
                }
            },
            User::getAvatarUrl,
            msg -> msg.map(m -> {
                try {
                    final String prefix = (m.getSenderUserId() == chatService.getCurrentUserId()) ? "Vous: " : "";
                    return prefix + truncate(m.getContent(), 30);
                } catch (final Exception e) {
                    return "Erreur chargement";
                }
            }).orElse("")
        ));

        groupListView.setCellFactory(list -> createCell(
            group -> group.getName(),
            group -> {
                try {
                    return localRepo.getLastGroupMessage(userEmail, group.getId());
                } catch (final IOException e) {
                    return Optional.empty();
                }
            },
            group -> Optional.ofNullable(group.getProfilePictureUrl())
                    .filter(u -> !u.isEmpty())
                    .orElse("/images/default_group.png"),
            msg -> msg.map(m -> {
                String name = "Inconnu";
                try { 
                    name = userService.getUserById(m.getSenderUserId()).getEmail().split("@")[0];
                } catch (final IOException e) { /* ignore */ }
                final String prefix = (m.getSenderUserId() == chatService.getCurrentUserId()? "Vous: ": name+": ");
                return prefix + truncate(m.getContent(), 30);
            }).orElse("")
        ));

        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                groupListView.getSelectionModel().clearSelection();
                selectedContactUser = sel;
                selectedGroup = null;
                loadContactConversation(selectedContactUser);
                setStatus("Conversation chargée avec " + selectedContactUser.getDisplayNameOrEmail());
                
                // Activer le bouton d'appel uniquement pour les conversations de contact (pas de groupe)
                callButton.setDisable(false);
            }
        });
        
        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                contactListView.getSelectionModel().clearSelection();
                selectedGroup = sel;
                selectedContactUser = null;
                loadGroupConversation(sel);
                setStatus("Conversation de groupe chargée : " + sel.getName());
                
                // Désactiver le bouton d'appel pour les conversations de groupe
                callButton.setDisable(true);
            }
        });

        messageField.setOnAction(this::handleSendMessage);
        
        // Initialiser l'interface d'appel
        initCallUI();
    }

    public void initData(final ChatService service, final String userEmail) {
        this.chatService = service;
        this.userEmail = userEmail;
        userEmailLabel.setText(userEmail);

        chatService.setMessageConsumer(this::handleIncomingMessage);
        chatService.setCallSignalConsumer(this::handleCallSignal);
        loadContacts();
        loadGroups();
    }

    /**
     * Initialise l'interface utilisateur pour les appels.
     */
    private void initCallUI() {
        // Lier le label de statut d'appel au CallManager
        callStatusLabel.textProperty().bind(callManager.callStatusProperty());
        
        // Initialiser les boutons d'appel comme invisibles
        callControlsBox.setVisible(false);
        callControlsBox.setManaged(false);
        acceptCallButton.setVisible(false);
        acceptCallButton.setManaged(false);
        rejectCallButton.setVisible(false);
        rejectCallButton.setManaged(false);
        endCallButton.setVisible(false);
        endCallButton.setManaged(false);
        muteButton.setVisible(false);
        muteButton.setManaged(false);
    }
    
    /**
     * Gère une demande d'appel (bouton Appeler).
     */
    @FXML
    private void handleCallRequest(ActionEvent event) {
        if (selectedContactUser == null) {
            setStatus("Veuillez sélectionner un contact pour appeler");
            return;
        }
        
        try {
            // Créer une nouvelle session d'appel
            final User currentUser = userService.getUserByEmail(userEmail);
            final CallSession callSession = new CallSession(currentUser.getId(), selectedContactUser.getId());
            
            // Initialiser l'appel dans le CallManager
            if (!callManager.initiateCall(selectedContactUser, callSession)) {
                setStatus("Impossible d'initialiser l'appel. Un appel est peut-être déjà en cours.");
                return;
            }
            
            // Envoyer le signal d'appel
            final CallSignal callRequest = chatService.createCallRequest(callSession, selectedContactUser.getEmail());
            chatService.sendCallSignal(callRequest);
            
            // Afficher l'interface d'appel
            showCallUI(false);
            
            setStatus("Appel en cours vers " + selectedContactUser.getDisplayNameOrEmail());
        } catch (final IOException e) {
            setStatus("Erreur lors de l'appel : " + e.getMessage());
        }
    }
    
    /**
     * Gère l'acceptation d'un appel.
     */
    @FXML
    private void handleAcceptCall(ActionEvent event) {
        try {
            final CallSession currentSession = callManager.getCurrentSession();
            if (currentSession == null) {
                setStatus("Aucun appel à accepter");
                return;
            }
            
            // Initialiser le CallManager qui va créer le socket UDP et obtenir un port
            // Le port local sera maintenant géré par le CallManager
            
            // Récupérer le port local attribué par le CallManager
            final int localPort = callManager.getLocalPort();
            System.out.println("Acceptation d'appel avec port local: " + localPort);
            
            // Envoyer le signal d'acceptation avec l'adresse IP et le port local
            final CallSignal acceptSignal = chatService.createCallAccept(
                    currentSession.getSessionId(), 
                    currentSession.getCallerUserId(), 
                    localPort);
            chatService.sendCallSignal(acceptSignal);
            
            // Mettre à jour l'interface d'appel
            updateCallUIForActiveCall();
            
            setStatus("Appel accepté");
        } catch (final IOException e) {
            setStatus("Erreur lors de l'acceptation de l'appel : " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gère le rejet d'un appel.
     */
    @FXML
    private void handleRejectCall(ActionEvent event) {
        try {
            final CallSession currentSession = callManager.getCurrentSession();
            if (currentSession == null) {
                setStatus("Aucun appel à rejeter");
                return;
            }
            
            // Envoyer le signal de rejet
            final CallSignal rejectSignal = chatService.createCallReject(
                    currentSession.getSessionId(), 
                    currentSession.getCallerUserId());
            chatService.sendCallSignal(rejectSignal);
            
            // Terminer l'appel localement
            callManager.endCall();
            
            // Masquer l'interface d'appel
            hideCallUI();
            
            setStatus("Appel rejeté");
        } catch (final IOException e) {
            setStatus("Erreur lors du rejet de l'appel : " + e.getMessage());
        }
    }
    
    /**
     * Gère la fin d'un appel en cours.
     */
    @FXML
    private void handleEndCall(ActionEvent event) {
        try {
            final CallSession currentSession = callManager.getCurrentSession();
            if (currentSession == null) {
                setStatus("Aucun appel à terminer");
                return;
            }
            
            // Déterminer l'autre utilisateur dans l'appel
            final long otherUserId = (currentSession.getCallerUserId() == chatService.getCurrentUserId())
                    ? currentSession.getReceiverUserId()
                    : currentSession.getCallerUserId();
            
            // Envoyer le signal de fin d'appel
            final CallSignal endSignal = chatService.createCallEnd(
                    currentSession.getSessionId(), 
                    otherUserId);
            chatService.sendCallSignal(endSignal);
            
            // Terminer l'appel localement
            callManager.endCall();
            
            // Masquer l'interface d'appel
            hideCallUI();
            
            setStatus("Appel terminé");
        } catch (final IOException e) {
            setStatus("Erreur lors de la fin de l'appel : " + e.getMessage());
        }
    }
    
    /**
     * Gère l'activation/désactivation du microphone.
     */
    @FXML
    private void handleToggleMute(ActionEvent event) {
        final boolean muted = muteButton.isSelected();
        callManager.setMicrophoneMuted(muted);
        muteButton.setText(muted ? "Activer micro" : "Muet");
    }
    
    /**
     * Gère les signaux d'appel reçus.
     */
    private void handleCallSignal(final CallSignal signal) {
        Platform.runLater(() -> {
            try {
                switch (signal.getType()) {
                    case CALL_REQUEST:
                        handleIncomingCallRequest(signal);
                        break;
                        
                    case CALL_ACCEPT:
                        handleCallAccepted(signal);
                        break;
                        
                    case CALL_REJECT:
                        handleCallRejected(signal);
                        break;
                        
                    case CALL_END:
                        handleCallEnded(signal);
                        break;
                        
                    case CALL_BUSY:
                        handleCallBusy(signal);
                        break;
                }
            } catch (final Exception e) {
                setStatus("Erreur lors du traitement du signal d'appel : " + e.getMessage());
            }
        });
    }
    
    /**
     * Gère une demande d'appel entrante.
     */
    private void handleIncomingCallRequest(final CallSignal signal) throws IOException {
        // Vérifier si un appel est déjà en cours
        if (callManager.isCallActive()) {
            // Envoyer un signal d'occupation
            final CallSignal busySignal = CallSignal.createCallBusy(
                    signal.getSessionId(), 
                    signal.getReceiverUserId(), 
                    signal.getSenderUserId());
            chatService.sendCallSignal(busySignal);
            return;
        }
        
        // Récupérer l'utilisateur appelant
        final User caller = userService.getUserById(signal.getSenderUserId());
        
        // Créer une session d'appel
        final CallSession callSession = new CallSession(signal.getSenderUserId(), signal.getReceiverUserId());
        callSession.setSessionId(signal.getSessionId());
        callSession.setStatus(CallSession.CallStatus.RINGING);
        
        // Définir l'utilisateur distant dans le CallManager
        callManager.setRemoteUser(caller);
        
        // Afficher la fenêtre de dialogue d'appel entrant
        showIncomingCallDialog(callSession, caller);
    }
    
    /**
     * Gère l'acceptation d'un appel par le destinataire.
     */
    private void handleCallAccepted(final CallSignal signal) {
        // Configurer la connexion d'appel avec les informations du destinataire
        callManager.setupCallConnection(signal.getIpAddress(), signal.getPort());
        
        // Mettre à jour l'interface d'appel
        updateCallUIForActiveCall();
    }
    
    /**
     * Gère le rejet d'un appel par le destinataire.
     */
    private void handleCallRejected(final CallSignal signal) {
        // Terminer l'appel localement
        callManager.endCall();
        
        // Masquer l'interface d'appel
        hideCallUI();
        
        setStatus("Appel rejeté par le destinataire");
    }
    
    /**
     * Gère la fin d'un appel par l'autre partie.
     */
    private void handleCallEnded(final CallSignal signal) {
        // Terminer l'appel localement
        callManager.endCall();
        
        // Masquer l'interface d'appel
        hideCallUI();
        
        setStatus("Appel terminé par l'autre partie");
    }
    
    /**
     * Gère le cas où le destinataire est occupé.
     */
    private void handleCallBusy(final CallSignal signal) {
        // Terminer l'appel localement
        callManager.endCall();
        
        // Masquer l'interface d'appel
        hideCallUI();
        
        setStatus("Le destinataire est occupé");
    }
    
    /**
     * Affiche la fenêtre de dialogue pour un appel entrant.
     */
    private void showIncomingCallDialog(final CallSession callSession, final User caller) {
        try {
            // Charger le FXML de la fenêtre de dialogue
            final FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/call-dialog.fxml"));
            final Parent dialogRoot = loader.load();
            
            // Récupérer le contrôleur
            final CallDialogController controller = loader.getController();
            
            // Initialiser le contrôleur avec les informations d'appel
            controller.initData(
                    callSession, 
                    caller, 
                    null, // L'adresse IP sera définie lors de l'acceptation
                    0,    // Le port sera défini lors de l'acceptation
                    () -> {
                        // Callback pour l'acceptation
                        showCallUI(true);
                    },
                    () -> {
                        // Callback pour le rejet
                        // Rien à faire ici, le contrôleur envoie déjà le signal de rejet
                    });
            
            // Créer et afficher la fenêtre
            final Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.UNDECORATED);
            dialogStage.setScene(new Scene(dialogRoot));
            dialogStage.setTitle("Appel entrant");
            dialogStage.show();
            
        } catch (final IOException e) {
            setStatus("Erreur lors de l'affichage de la fenêtre d'appel : " + e.getMessage());
        }
    }
    
    /**
     * Affiche l'interface d'appel.
     * 
     * @param incoming true si c'est un appel entrant, false si c'est un appel sortant
     */
    private void showCallUI(final boolean incoming) {
        callControlsBox.setVisible(true);
        callControlsBox.setManaged(true);
        
        if (incoming) {
            // Pour un appel entrant, on affiche les boutons Accepter et Refuser
            acceptCallButton.setVisible(true);
            acceptCallButton.setManaged(true);
            rejectCallButton.setVisible(true);
            rejectCallButton.setManaged(true);
            endCallButton.setVisible(false);
            endCallButton.setManaged(false);
            muteButton.setVisible(false);
            muteButton.setManaged(false);
        } else {
            // Pour un appel sortant, on affiche uniquement le bouton Terminer
            acceptCallButton.setVisible(false);
            acceptCallButton.setManaged(false);
            rejectCallButton.setVisible(false);
            rejectCallButton.setManaged(false);
            endCallButton.setVisible(true);
            endCallButton.setManaged(true);
            muteButton.setVisible(false);
            muteButton.setManaged(false);
        }
    }
    
    /**
     * Met à jour l'interface d'appel pour un appel actif.
     */
    private void updateCallUIForActiveCall() {
        callControlsBox.setVisible(true);
        callControlsBox.setManaged(true);
        
        acceptCallButton.setVisible(false);
        acceptCallButton.setManaged(false);
        rejectCallButton.setVisible(false);
        rejectCallButton.setManaged(false);
        endCallButton.setVisible(true);
        endCallButton.setManaged(true);
        muteButton.setVisible(true);
        muteButton.setManaged(true);
    }
    
    /**
     * Masque l'interface d'appel.
     */
    private void hideCallUI() {
        callControlsBox.setVisible(false);
        callControlsBox.setManaged(false);
    }

    private <T> ListCell<T> createCell(
            final java.util.function.Function<T, String> nameFn,
            final java.util.function.Function<T, Optional<Message>> lastMsgFn,
            final java.util.function.Function<T, String> avatarUrlFn,
            final java.util.function.Function<Optional<Message>, String> msgTextFn) {
        return new ListCell<>() {
            private final ImageView avatar = createCircularAvatar(null, 30);
            private final Label nameLabel = new Label();
            private final Label lastMsgLabel = new Label();
            private final VBox textVBox = new VBox(nameLabel, lastMsgLabel);
            private final HBox hbox = new HBox(10, avatar, textVBox);
            {
                lastMsgLabel.setTextFill(Color.GRAY);
                lastMsgLabel.setFont(Font.font("System", FontWeight.NORMAL, 12));
                hbox.setAlignment(Pos.CENTER_LEFT);
                
                // Définir une image par défaut pour éviter les problèmes d'initialisation
                try (InputStream defaultImageStream = getClass().getResourceAsStream("/images/default_avatar.png")) {
                    if (defaultImageStream != null) {
                        avatar.setImage(new Image(defaultImageStream, 30, 30, true, true));
                    }
                } catch (final IOException e) {
                    System.err.println("Impossible de charger l'image par défaut: " + e.getMessage());
                }
            }
            
            @Override 
            protected void updateItem(final T item, final boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { 
                    setText(null);
                    setGraphic(null); 
                    return; 
                }
                
                nameLabel.setText(nameFn.apply(item));
                final String url = avatarUrlFn.apply(item);
                if (url != null) {  // Éviter de passer une URL null à loadImage
                    final Image img = loadImage(url, 30);
                    if (img != null) {
                        avatar.setImage(img);
                    }
                }
                final Optional<Message> msg = lastMsgFn.apply(item);
                lastMsgLabel.setText(msgTextFn.apply(msg));
                setGraphic(hbox);
            }
        };
    }

    private Image loadImage(final String imageUrl, final double size) {
        try (InputStream stream = getClass().getResourceAsStream(imageUrl)) {
            if (stream != null) {
                return new Image(stream, size, size, true, true);
            } else {
                final String fallback = imageUrl.contains("group") ? 
                    "/images/default_group.png" : "/images/default_avatar.png";
                try (InputStream fallbackStream = getClass().getResourceAsStream(fallback)) {
                    if (fallbackStream != null) {
                        return new Image(fallbackStream, size, size, true, true);
                    }
                }
            }
        } catch (final IOException e) {
            System.err.println("Erreur lors du chargement de l'image " + imageUrl + ": " + e.getMessage());
        }
        return null;
    }

    private ImageView createCircularAvatar(final String imageUrl, final double size) {
        final ImageView imageView = new ImageView();
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        final javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(size / 2, size / 2, size / 2);
        imageView.setClip(clip);
        
        return imageView;
    }

    private void loadContacts() {
        try {
            final List<User> contactList = contactService.getContactUsers(userEmail);
            Platform.runLater(() -> {
                contacts.clear();
                contacts.addAll(contactList);
            });
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement des contacts : " + e.getMessage());
        }
    }

    private void loadGroups() {
        try {
            final long userId = userService.getUserByEmail(userEmail).getId();
            final List<Group> groupList = groupService.getGroupsForUser(userId);
            Platform.runLater(() -> {
                groups.clear();
                groups.addAll(groupList);
            });
        } catch (final IOException e) {
            setStatus("Erreur lors du chargement des groupes : " + e.getMessage());
        }
    }

    private void loadContactConversation(final User contactUser) {
        Platform.runLater(() -> {
            synchronized (loadLock) {
                chatHistoryContainer.getChildren().clear();
                try {
                    final long myId = userService.getUserByEmail(userEmail).getId();
                    final long contactId = contactUser.getId();
                    final List<Message> contactMessages = localRepo.loadContactMessages(userEmail, myId, contactId);
                    contactMessages.forEach(this::addMessageToChat);
                    scrollToBottom();
                } catch (final IOException e) {
                    setStatus("Erreur lors du chargement de la conversation avec " + contactUser.getDisplayNameOrEmail() + " : "
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
                    scrollToBottom();
                } catch (final IOException e) {
                    setStatus("Erreur lors du chargement de l'historique de groupe : " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleSendMessage(final ActionEvent event) {
        final String content = messageField.getText().trim();
        if (content.isEmpty()) {
            return;
        }

        try {
            Message message;
            if (selectedContactUser != null) {
                final User sender = userService.getUserByEmail(userEmail);
                message = Message.newDirectMessage(sender.getId(), selectedContactUser.getId(), content);
                chatService.sendMessage(message);
            } else if (selectedGroup != null) {
                final User sender = userService.getUserByEmail(userEmail);
                message = Message.newGroupMessage(sender.getId(), selectedGroup.getId(), content);
                chatService.sendGroupMessage(message);
            } else {
                setStatus("Veuillez sélectionner un groupe ou un contact.");
                return;
            }
            messageField.clear();
            addMessageToChat(message);
            localRepo.addLocalMessage(userEmail, message);
            if (message.getGroupId() != null) {
                groupListView.refresh();
            } else {
                contactListView.refresh();
            }
            setStatus("Message envoyé");
        } catch (final IOException e) {
            setStatus("Erreur lors de l'envoi du message : " + e.getMessage());
        }
    }

    private void handleIncomingMessage(final Message message) {
        Platform.runLater(() -> {
            try {
                localRepo.addLocalMessage(userEmail, message);
                
                // Message de groupe
                if (message.getGroupId() != null) {
                    // Vérifier si le groupe est déjà dans la liste, sinon recharger les groupes
                    final boolean groupExists = groups.stream()
                            .anyMatch(g -> g.getId() == message.getGroupId());
                    if (!groupExists) {
                        loadGroups();
                    } else {
                        groupListView.refresh();
                    }
                    
                    // Afficher le message si le groupe est actuellement sélectionné
                    if (selectedGroup != null && selectedGroup.getId() == message.getGroupId()) {
                        addMessageToChat(message);
                    }
                    setStatus("Nouveau message de groupe reçu");
                } 
                // Message direct
                else {
                    final User sender = userService.getUserById(message.getSenderUserId());
                    final User receiver = message.getReceiverUserId() != null ? 
                            userService.getUserById(message.getReceiverUserId()) : null;
                    
                    final User otherUser = sender.getEmail().equals(userEmail) ? receiver : sender;
                    
                    // Ajouter le contact s'il n'existe pas
                    if (otherUser != null && !contacts.contains(otherUser)) {
                        contacts.add(otherUser);
                    }
                    contactListView.refresh();
                    
                    // Si aucune conversation n'est sélectionnée, sélectionner celle-ci
                    if (selectedContactUser == null && selectedGroup == null && otherUser != null) {
                        contactListView.getSelectionModel().select(otherUser);
                    }
                    
                    // Afficher le message si la conversation est actuellement sélectionnée
                    if (selectedContactUser != null && 
                            (sender.getId() == selectedContactUser.getId() || 
                             (receiver != null && receiver.getId() == selectedContactUser.getId()))) {
                        addMessageToChat(message);
                    }
                    
                    setStatus("Nouveau message reçu");
                }
                
                scrollToBottom();
            } catch (final IOException e) {
                setStatus("Erreur lors du traitement du message : " + e.getMessage());
            }
        });
    }

    private void addMessageToChat(final Message message) {
        try {
            final User sender = userService.getUserById(message.getSenderUserId());
            final User currentUser = userService.getUserByEmail(userEmail);
            final boolean isMine = message.getSenderUserId() == currentUser.getId();
            final boolean isGroup = message.getGroupId() != null;

            final HBox messageContainer = new HBox(10);
            messageContainer.getStyleClass().add("message-container");
            messageContainer.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageContainer.setPadding(new Insets(5));
            messageContainer.setMaxWidth(chatHistoryContainer.getWidth() * 0.8);

            // Avatar
            final ImageView avatar = createCircularAvatar(sender.getAvatarUrl(), 35);
            avatar.getStyleClass().add("message-avatar");
            if (!isMine) messageContainer.getChildren().add(avatar);

            // Contenu du message
            final VBox contentBox = new VBox(5);
            contentBox.getStyleClass().add("message-content");
            contentBox.getStyleClass().add(isMine ? "my-message" : "other-message");
            
            if (isGroup && !isMine) {
                final Label nameLabel = new Label(sender.getDisplayNameOrEmail());
                nameLabel.getStyleClass().add("sender-name");
                contentBox.getChildren().add(nameLabel);
            }

            // Créer un conteneur horizontal pour le texte et l'horodatage
            final HBox contentTimeContainer = new HBox();
            contentTimeContainer.getStyleClass().add("content-time-container");
            
            // Texte du message
            final Label contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            contentLabel.getStyleClass().add("message-text");
            contentLabel.setMaxWidth(chatHistoryContainer.getWidth() * 0.6);  // Pour laisser de la place à l'horodatage

            // Horodatage
            final Label timeLabel = new Label(message.getTimestamp().format(TIME_FMT));
            timeLabel.getStyleClass().add("message-time");
            
            // Assembler le conteneur de message
            contentTimeContainer.getChildren().addAll(contentLabel, timeLabel);
            contentBox.getChildren().add(contentTimeContainer);
            messageContainer.getChildren().add(contentBox);
            
            if (isMine) messageContainer.getChildren().add(avatar);

            Platform.runLater(() -> {
                chatHistoryContainer.getChildren().add(messageContainer);
                scrollToBottom();
            });
        } catch (final IOException e) {
            setStatus("Erreur d'affichage du message : " + e.getMessage());
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            chatScrollPane.setVvalue(1.0);
        });
    }

    private void setStatus(final String status) {
        Platform.runLater(() -> {
            statusLabel.setText(status);
            statusLabel.getStyleClass().add("status-label");
        });
    }

    @FXML
    private void handleAddContact() {
        final String email = newContactField.getText().trim();

        if (email.isEmpty()) {
            setStatus("Veuillez saisir un email");
            return;
        }

        try {
            final User addedUser = contactService.addContactUser(userEmail, email);
            if (addedUser != null) {
                contacts.add(addedUser);
                newContactField.clear();
                setStatus("Contact ajouté: " + addedUser.getDisplayNameOrEmail());
            }
        } catch (final IllegalArgumentException e) {
            setStatus("Erreur: " + e.getMessage());
        } catch (final IOException e) {
            setStatus("Erreur de connexion: " + e.getMessage());
        }
    }

    @FXML
    private void handleRemoveContact() {
        if (selectedContactUser == null) {
            setStatus("Aucun contact sélectionné pour la suppression");
            return;
        }
        try {
            final boolean removed = contactService.removeContact(userEmail, selectedContactUser.getEmail());
            if (removed) {
                contacts.remove(selectedContactUser);
                chatHistoryContainer.getChildren().clear();
                final long myId = chatService.getCurrentUserId();
                localRepo.removeConversation(userEmail, myId, selectedContactUser.getId());
                setStatus("Contact et conversation supprimés: " + selectedContactUser.getDisplayNameOrEmail());
                selectedContactUser = null;
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
            final long memberId = userService.getUserByEmail(memberEmail).getId();
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

    private String truncate(final String text, final int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}