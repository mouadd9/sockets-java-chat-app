package org.example.client.gui.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.example.client.gui.repository.JsonLocalMessageRepository;
import org.example.client.gui.service.CallManager;
import org.example.client.gui.service.AudioRecorderService;
import org.example.client.gui.service.ChatService;
import org.example.client.gui.service.ContactService;
import org.example.client.gui.service.FileService;
import org.example.client.gui.service.GroupService;
import org.example.client.gui.service.UserService;
import org.example.shared.model.CallSession;
import org.example.shared.model.CallSignal;
import org.example.shared.model.Group;
import org.example.shared.model.Message;
import org.example.shared.model.User;
import org.example.shared.model.enums.MessageType;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.sound.sampled.LineUnavailableException;

public class ChatController {
    @FXML
    private Label userEmailLabel;
    @FXML
    private ListView<User> contactListView;
    @FXML
    private ListView<Group> groupListView;
    @FXML
    private TextField newContactField, messageField, groupNameField, memberEmailField;
    @FXML
    private VBox chatHistoryContainer;
    @FXML
    private ScrollPane chatScrollPane;
    @FXML
    private Label statusLabel;
    @FXML
    private Button mediaButton;
    @FXML
    private Button audioRecordButton;
    @FXML
    private HBox attachmentPreviewContainer;
    @FXML
    private Label attachmentNameLabel;
    @FXML
    private HBox recordingIndicatorContainer;
    @FXML
    private Label recordingTimeLabel;

    @FXML
    private Button mediaGalleryButton;

    @FXML
    private Label groupMembersLabel;

    // Éléments d'interface pour les appels
    @FXML
    private Button callButton;
    @FXML
    private VBox callControlsBox;
    @FXML
    private Label callStatusLabel;
    @FXML
    private Button acceptCallButton;
    @FXML
    private Button rejectCallButton;
    @FXML
    private Button endCallButton;
    @FXML
    private ToggleButton muteButton;

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
    private final FileService fileService = new FileService();
    private final AudioRecorderService audioRecorderService = new AudioRecorderService();

    private final Object loadLock = new Object();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Media attachment state
    private File selectedMediaFile;
    private MessageType selectedMediaType;

    // Audio recording state
    private boolean isRecording = false;
    private Timer recordingTimer;
    private int recordingSeconds = 0;

    @FXML
    public void initialize() {
        mediaGalleryButton.setOnAction(this::handleOpenMediaGallery);
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
                        if (m.isTextMessage()) {
                            return prefix + truncate(m.getContent(), 30);
                        } else {
                            String mediaTypeIcon = getMediaTypeIcon(m.getType());
                            return prefix + mediaTypeIcon + " " + (m.getFileName() != null ? m.getFileName() : "Média");
                        }
                    } catch (final Exception e) {
                        return "Erreur chargement";
                    }
                }).orElse("")));

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
                    } catch (final IOException e) {
                        /* ignore */ }
                    final String prefix = (m.getSenderUserId() == chatService.getCurrentUserId() ? "Vous: "
                            : name + ": ");
                    if (m.isTextMessage()) {
                        return prefix + truncate(m.getContent(), 30);
                    } else {
                        String mediaTypeIcon = getMediaTypeIcon(m.getType());
                        return prefix + mediaTypeIcon + " " + (m.getFileName() != null ? m.getFileName() : "Média");
                    }
                }).orElse("")));

        contactListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                groupListView.getSelectionModel().clearSelection();
                selectedContactUser = sel;
                selectedGroup = null;
                loadContactConversation(selectedContactUser);

                // Effacer l'affichage des membres pour une conversation individuelle
                groupMembersLabel.setText("");

                setStatus("Conversation chargée avec " + selectedContactUser.getDisplayNameOrEmail());

                // Activer le bouton d'appel uniquement pour les conversations de contact (pas
                // de groupe)
                callButton.setDisable(false);
            }
        });

        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                contactListView.getSelectionModel().clearSelection();
                selectedGroup = sel;
                selectedContactUser = null;
                loadGroupConversation(sel);

                // Les membres seront affichés par la méthode loadGroupConversation
                // qui appelle displayGroupMembers

                setStatus("Conversation de groupe chargée : " + sel.getName());

                // Désactiver le bouton d'appel pour les conversations de groupe
                callButton.setDisable(true);
            }
        });

        messageField.setOnAction(this::handleSendMessage);

        // Initialiser l'interface d'appel
        initCallUI();

        // Initialize media button context menu
        final ContextMenu mediaMenu = new ContextMenu();

        MenuItem imageItem = new MenuItem("Image");
        imageItem.setOnAction(e -> openMediaFileChooser("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        MenuItem videoItem = new MenuItem("Vidéo");
        videoItem.setOnAction(e -> openMediaFileChooser("Vidéos", "*.mp4", "*.avi", "*.mov", "*.wmv"));

        MenuItem documentItem = new MenuItem("Document");
        documentItem.setOnAction(e -> openMediaFileChooser("Documents", "*.*"));

        mediaMenu.getItems().addAll(imageItem, videoItem, documentItem);

        mediaButton.setOnMouseClicked(e -> {
            mediaMenu.show(mediaButton, e.getScreenX(), e.getScreenY());
        });

        // Check if audio recording is supported
        if (!audioRecorderService.isAudioRecordingSupported()) {
            audioRecordButton.setDisable(true);
            audioRecordButton.setTooltip(
                    new javafx.scene.control.Tooltip("L'enregistrement audio n'est pas pris en charge sur ce système"));
        }
    }

    private String getMediaTypeIcon(MessageType type) {
        switch (type) {
            case IMAGE:
                return "🖼️";
            case VIDEO:
                return "🎬";
            case AUDIO:
                return "🔊";
            case DOCUMENT:
                return "📄";
            default:
                return "";
        }
    }

    public void initData(final ChatService service, final String userEmail) {
        this.chatService = service;
        this.userEmail = userEmail;
        userEmailLabel.setText(userEmail);

        chatService.setMessageConsumer(this::handleIncomingMessage);
        chatService.setCallSignalConsumer(this::handleCallSignal);
        loadContacts();
        loadGroups();

        // Démarrer le rafraîchissement périodique des statuts
        startContactStatusUpdater();
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
    private void handleCallRequest(final ActionEvent event) {
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
    private void handleAcceptCall(final ActionEvent event) {
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
    private void handleRejectCall(final ActionEvent event) {
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
    private void handleEndCall(final ActionEvent event) {
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
    private void handleToggleMute(final ActionEvent event) {
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
                    0, // Le port sera défini lors de l'acceptation
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
     * @param incoming true si c'est un appel entrant, false si c'est un appel
     *                 sortant
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
        return new ListCell<T>() {
            private User currentUser = null;
            private final ChangeListener<Boolean> onlineListener = (obs, oldVal, newVal) -> {
                updateOnlineIndicator(newVal);
            };

            private final HBox hbox = new HBox(10);
            private final ImageView avatar = new ImageView();
            private final VBox contentBox = new VBox(2);
            private final Label nameLabel = new Label();
            private final Label lastMsgLabel = new Label();
            private final Circle onlineIndicator = new Circle(5);

            {
                hbox.setPadding(new Insets(5));
                hbox.setAlignment(Pos.CENTER_LEFT);
                avatar.setFitHeight(30);
                avatar.setFitWidth(30);
                avatar.setPreserveRatio(true);
                nameLabel.getStyleClass().add("contact-name");
                lastMsgLabel.getStyleClass().add("last-message");
                contentBox.getChildren().addAll(nameLabel, lastMsgLabel);

                onlineIndicator.getStyleClass().add("offline-indicator");

                final StackPane avatarContainer = new StackPane();
                avatarContainer.getChildren().add(avatar);
                StackPane.setAlignment(onlineIndicator, Pos.BOTTOM_RIGHT);
                avatarContainer.getChildren().add(onlineIndicator);

                hbox.getChildren().addAll(avatarContainer, contentBox);
            }

            @Override
            protected void updateItem(final T item, final boolean empty) {
                super.updateItem(item, empty);

                // Remove previous listener
                if (currentUser != null) {
                    currentUser.onlineProperty().removeListener(onlineListener);
                    currentUser = null;
                }

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    onlineIndicator.setVisible(false);
                    return;
                }

                nameLabel.setText(nameFn.apply(item));
                final String url = avatarUrlFn.apply(item);
                if (url != null) {
                    final Image img = loadImage(url, 30);
                    if (img != null) {
                        avatar.setImage(img);
                    }
                }
                final Optional<Message> msg = lastMsgFn.apply(item);
                lastMsgLabel.setText(msgTextFn.apply(msg));

                if (item instanceof User) {
                    currentUser = (User) item;
                    currentUser.onlineProperty().addListener(onlineListener);
                    updateOnlineIndicator(currentUser.isOnline());
                } else {
                    onlineIndicator.setVisible(false);
                }

                setGraphic(hbox);
            }

            private void updateOnlineIndicator(final boolean isOnline) {
                Platform.runLater(() -> {
                    onlineIndicator.getStyleClass().removeAll("online-indicator", "offline-indicator");
                    onlineIndicator.getStyleClass().add(isOnline ? "online-indicator" : "offline-indicator");
                    onlineIndicator.setVisible(true);
                });
            }
        };
    }

    private Image loadImage(final String imageUrl, final double size) {

        try (InputStream stream = getClass().getResourceAsStream(imageUrl)) {
            if (stream != null) {
                return new Image(stream, size, size, true, true);
            } else {
                final String fallback = imageUrl.contains("group") ? "/images/default_group.png"
                        : "/images/default_avatar.png";
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
            if (!isMine)
                messageContainer.getChildren().add(avatar);

            // Contenu du message
            final VBox contentBox = new VBox(5);
            contentBox.getStyleClass().add("message-content");
            contentBox.getStyleClass().add(isMine ? "my-message" : "other-message");

            if (isGroup && !isMine) {
                final Label nameLabel = new Label(sender.getDisplayNameOrEmail());
                nameLabel.getStyleClass().add("sender-name");
                contentBox.getChildren().add(nameLabel);
            }

            // For text messages, use the existing logic
            if (message.isTextMessage()) {
                // Créer un conteneur horizontal pour le texte et l'horodatage
                final HBox contentTimeContainer = new HBox();
                contentTimeContainer.getStyleClass().add("content-time-container");

                // Texte du message
                final Label contentLabel = new Label(message.getContent());
                contentLabel.setWrapText(true);
                contentLabel.getStyleClass().add("message-text");
                contentLabel.setMaxWidth(chatHistoryContainer.getWidth() * 0.6); // Pour laisser de la place à
                                                                                 // l'horodatage

                // Horodatage
                final Label timeLabel = new Label(message.getTimestamp().format(TIME_FMT));
                timeLabel.getStyleClass().add("message-time");

                // Assembler le conteneur de message
                contentTimeContainer.getChildren().addAll(contentLabel, timeLabel);
                contentBox.getChildren().add(contentTimeContainer);
            }
            // For media messages, create appropriate media preview
            else {
                // Add media content based on the type
                switch (message.getType()) {
                    case IMAGE:
                        addImageContent(contentBox, message);
                        break;
                    case VIDEO:
                        addVideoContent(contentBox, message);
                        break;
                    case AUDIO:
                        addAudioContent(contentBox, message);
                        break;
                    case DOCUMENT:
                        addDocumentContent(contentBox, message);
                        break;
                    default:
                        // Fallback to text representation
                        final Label fallbackLabel = new Label("Type de média non pris en charge");
                        contentBox.getChildren().add(fallbackLabel);
                }

                // Add timestamp below the media
                final HBox timeContainer = new HBox();
                timeContainer.setAlignment(Pos.CENTER_RIGHT);
                final Label timeLabel = new Label(message.getTimestamp().format(TIME_FMT));
                timeLabel.getStyleClass().add("message-time");
                timeContainer.getChildren().add(timeLabel);
                contentBox.getChildren().add(timeContainer);
            }

            messageContainer.getChildren().add(contentBox);

            if (isMine)
                messageContainer.getChildren().add(avatar);

            Platform.runLater(() -> {
                chatHistoryContainer.getChildren().add(messageContainer);
                scrollToBottom();
            });
        } catch (final IOException e) {
            setStatus("Erreur d'affichage du message : " + e.getMessage());
        }
    }

    private void addImageContent(VBox contentBox, Message message) {
        try {
            System.out.println();
            File imageFile = chatService.getMediaFile(message);
            if (imageFile.exists()) {
                Image image = new Image(imageFile.toURI().toString());
                ImageView imageView = new ImageView(image);

                // Limit image size
                double maxWidth = 250;
                double maxHeight = 250;

                if (image.getWidth() > maxWidth || image.getHeight() > maxHeight) {
                    double widthRatio = maxWidth / image.getWidth();
                    double heightRatio = maxHeight / image.getHeight();
                    double ratio = Math.min(widthRatio, heightRatio);

                    imageView.setFitWidth(image.getWidth() * ratio);
                    imageView.setFitHeight(image.getHeight() * ratio);
                } else {
                    imageView.setFitWidth(image.getWidth());
                    imageView.setFitHeight(image.getHeight());
                }

                imageView.getStyleClass().add("image-preview");

                // Add click handler to open the image in a new window
                imageView.setOnMouseClicked(e -> openImageViewer(image));

                contentBox.getChildren().add(imageView);

                // Add filename if available
                if (message.getFileName() != null) {
                    Label filenameLabel = new Label(message.getFileName());
                    filenameLabel.setTextFill(Color.GRAY);
                    filenameLabel.setFont(Font.font("System", FontWeight.NORMAL, 10));
                    contentBox.getChildren().add(filenameLabel);
                }
            } else {
                Label errorLabel = new Label("Image non disponible");
                contentBox.getChildren().add(errorLabel);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur de chargement: " + e.getMessage());
            contentBox.getChildren().add(errorLabel);
        }
    }

    private void addVideoContent(VBox contentBox, Message message) {
        try {
            File videoFile = chatService.getMediaFile(message);
            if (videoFile.exists()) {
                HBox videoContainer = new HBox();
                videoContainer.setAlignment(Pos.CENTER);
                videoContainer.getStyleClass().add("video-preview");

                // Create a thumbnail or play button
                Button playButton = new Button("▶");
                playButton.getStyleClass().add("audio-play-button");
                playButton.setOnAction(e -> openVideoPlayer(videoFile));

                Label videoLabel = new Label(message.getFileName() != null ? message.getFileName() : "Vidéo");

                videoContainer.getChildren().addAll(playButton, videoLabel);
                contentBox.getChildren().add(videoContainer);
            } else {
                Label errorLabel = new Label("Vidéo non disponible");
                contentBox.getChildren().add(errorLabel);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur de chargement: " + e.getMessage());
            contentBox.getChildren().add(errorLabel);
        }
    }

    private void addAudioContent(VBox contentBox, Message message) {
        try {
            File audioFile = chatService.getMediaFile(message);
            if (audioFile.exists()) {
                HBox audioPlayer = new HBox(10);
                audioPlayer.setAlignment(Pos.CENTER_LEFT);
                audioPlayer.getStyleClass().add("audio-player");

                Button playButton = new Button("▶");
                playButton.getStyleClass().add("audio-play-button");

                ProgressBar progressBar = new ProgressBar(0);
                progressBar.getStyleClass().add("audio-progress");
                progressBar.setPrefWidth(150);

                Label durationLabel = new Label("00:00");

                // Create the media player
                Media media = new Media(audioFile.toURI().toString());
                MediaPlayer mediaPlayer = new MediaPlayer(media);

                // Configure the progress bar and duration label
                mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                    double progress = newVal.toSeconds() / mediaPlayer.getTotalDuration().toSeconds();
                    Platform.runLater(() -> {
                        progressBar.setProgress(progress);
                        durationLabel.setText(formatDuration(newVal));
                    });
                });

                mediaPlayer.setOnEndOfMedia(() -> {
                    mediaPlayer.stop();
                    mediaPlayer.seek(javafx.util.Duration.ZERO);
                    playButton.setText("▶");
                });

                // Configure the play button
                playButton.setOnAction(e -> {
                    if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                        mediaPlayer.pause();
                        playButton.setText("▶");
                    } else {
                        mediaPlayer.play();
                        playButton.setText("⏸");
                    }
                });

                audioPlayer.getChildren().addAll(playButton, progressBar, durationLabel);
                contentBox.getChildren().add(audioPlayer);
            } else {
                Label errorLabel = new Label("Audio non disponible");
                contentBox.getChildren().add(errorLabel);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur de chargement: " + e.getMessage());
            contentBox.getChildren().add(errorLabel);
        }
    }

    private void addDocumentContent(VBox contentBox, Message message) {
        try {
            File documentFile = chatService.getMediaFile(message);
            if (documentFile.exists()) {
                HBox documentContainer = new HBox(10);
                documentContainer.getStyleClass().add("document-preview");

                Label iconLabel = new Label("📄");
                iconLabel.getStyleClass().add("document-icon");

                VBox documentInfo = new VBox(5);

                Label nameLabel = new Label(message.getFileName() != null ? message.getFileName() : "Document");
                nameLabel.getStyleClass().add("document-name");

                Label sizeLabel = new Label(formatFileSize(message.getFileSize()));
                sizeLabel.getStyleClass().add("document-size");

                documentInfo.getChildren().addAll(nameLabel, sizeLabel);
                documentContainer.getChildren().addAll(iconLabel, documentInfo);

                // Add click handler to open the document
                documentContainer.setOnMouseClicked(e -> openDocument(documentFile));

                contentBox.getChildren().add(documentContainer);
            } else {
                Label errorLabel = new Label("Document non disponible");
                contentBox.getChildren().add(errorLabel);
            }
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur de chargement: " + e.getMessage());
            contentBox.getChildren().add(errorLabel);
        }
    }

    // Helper method to format file size
    private String formatFileSize(Long size) {
        if (size == null) {
            return "Taille inconnue";
        }

        if (size < 1024) {
            return size + " octets";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    // Helper method to format duration
    private String formatDuration(javafx.util.Duration duration) {
        int seconds = (int) Math.floor(duration.toSeconds());
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Open image in a larger viewer
    private void openImageViewer(Image image) {
        Stage imageStage = new Stage();
        imageStage.initModality(Modality.APPLICATION_MODAL);
        imageStage.setTitle("Visionneuse d'image");

        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);

        // Limit size to fit screen
        imageView.setFitWidth(Math.min(image.getWidth(), 800));
        imageView.setFitHeight(Math.min(image.getHeight(), 600));

        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        Scene scene = new Scene(scrollPane);
        imageStage.setScene(scene);
        imageStage.show();
    }

    // Open video player
    private void openVideoPlayer(File videoFile) {
        Stage videoStage = new Stage();
        videoStage.initModality(Modality.APPLICATION_MODAL);
        videoStage.setTitle("Lecteur vidéo");

        Media media = new Media(videoFile.toURI().toString());
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        MediaView mediaView = new MediaView(mediaPlayer);

        // Set up controls
        Button playButton = new Button("⏸");
        playButton.setOnAction(e -> {
            if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                playButton.setText("▶");
            } else {
                mediaPlayer.play();
                playButton.setText("⏸");
            }
        });

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);

        mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
            double progress = newVal.toSeconds() / mediaPlayer.getTotalDuration().toSeconds();
            Platform.runLater(() -> progressBar.setProgress(progress));
        });

        // Add seek functionality
        progressBar.setOnMouseClicked(e -> {
            double percent = e.getX() / progressBar.getWidth();
            mediaPlayer.seek(mediaPlayer.getTotalDuration().multiply(percent));
        });

        HBox controls = new HBox(10, playButton, progressBar);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(10));

        VBox root = new VBox(10, mediaView, controls);
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 640, 480);
        videoStage.setScene(scene);

        videoStage.setOnCloseRequest(e -> mediaPlayer.stop());

        videoStage.show();
        mediaPlayer.play();
    }

    // Open document with system default application
    private void openDocument(File documentFile) {
        try {
            java.awt.Desktop.getDesktop().open(documentFile);
        } catch (Exception e) {
            setStatus("Erreur lors de l'ouverture du document: " + e.getMessage());
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

    private String truncate(final String text, final int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
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
                    // Effacer l'affichage des membres car on est dans une conversation individuelle
                    groupMembersLabel.setText("");

                    final long myId = userService.getUserByEmail(userEmail).getId();
                    final long contactId = contactUser.getId();
                    final List<Message> contactMessages = localRepo.loadContactMessages(userEmail, myId, contactId);
                    contactMessages.forEach(this::addMessageToChat);
                    scrollToBottom();

                    // Activer le bouton d'appel pour les conversations individuelles
                    callButton.setDisable(false);
                } catch (final IOException e) {
                    setStatus("Erreur lors du chargement de la conversation avec " + contactUser.getDisplayNameOrEmail()
                            + " : "
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
                    // Afficher les membres du groupe dans l'en-tête
                    displayGroupMembers(group);
                    final List<Message> groupMessages = localRepo.loadGroupMessages(userEmail, group.getId());
                    groupMessages.forEach(this::addMessageToChat);
                    scrollToBottom();
                } catch (final IOException e) {
                    setStatus("Erreur lors du chargement de l'historique de groupe : " + e.getMessage());
                }
            }
        });
    }

    /**
     * Affiche les membres du groupe dans l'en-tête de la conversation
     */
    private void displayGroupMembers(final Group group) {
        if (group == null) {
            groupMembersLabel.setText("");
            return;
        }
        try {
            // Récupérer les IDs des membres du groupe
            final List<Long> memberIds = groupService.getMembersForGroup(group.getId());
            // Construire la liste des noms des membres
            final List<String> memberNames = new ArrayList<>();
            for (final Long memberId : memberIds) {
                try {
                    final User user = userService.getUserById(memberId);
                    final String displayName = user.getDisplayNameOrEmail().split("@")[0];
                    memberNames.add(displayName);
                } catch (final IOException e) {
                    memberNames.add("Inconnu");
                }
            }
            // Joindre les noms avec des virgules
            final String membersText = "Membres : " + String.join(", ", memberNames);
            groupMembersLabel.setText(membersText);
        } catch (final Exception e) {
            groupMembersLabel.setText("Erreur lors du chargement des membres");
            setStatus("Erreur lors du chargement des membres : " + e.getMessage());
        }
    }

    @FXML
    private void handleSendMessage(final ActionEvent event) {
        System.out.println("////////::selected Media File ");
        if (selectedMediaFile != null) {
            sendMediaMessage();
            return;
        }

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
                chatService.sendMessage(message);
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

    /**
     * Sends the currently selected media file as a message.
     */
    // Java
    private void sendMediaMessage() {
        try {
            if (selectedMediaFile == null) {
                setStatus("No media file selected.");
                return;
            }
            // Auto-detect the media type if it is null
            if (selectedMediaType == null) {
                selectedMediaType = fileService.detectMessageType(selectedMediaFile.getName());
                System.out.println("Auto-detected media type: " + selectedMediaType);
            }
            // If the type is still null after detection, abort the sending process
            if (selectedMediaType == null) {
                setStatus("Unsupported media type for file: " + selectedMediaFile.getName());
                clearMediaSelection();
                return;
            }

            Message message;
            if (selectedContactUser != null) {
                switch (selectedMediaType) {
                    case IMAGE:
                    case VIDEO:
                    case DOCUMENT:
                        message = chatService.createDirectMediaMessage(userEmail, selectedContactUser.getEmail(),
                                selectedMediaFile);
                        break;
                    case AUDIO:
                        message = chatService.createDirectAudioMessage(userEmail, selectedContactUser.getEmail(),
                                selectedMediaFile);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported media type: " + selectedMediaType);
                }
            } else if (selectedGroup != null) {
                switch (selectedMediaType) {
                    case IMAGE:
                    case VIDEO:
                    case DOCUMENT:
                        message = chatService.createGroupMediaMessage(userEmail, selectedGroup.getId(),
                                selectedMediaFile);
                        break;
                    case AUDIO:
                        message = chatService.createGroupAudioMessage(userEmail, selectedGroup.getId(),
                                selectedMediaFile);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported media type: " + selectedMediaType);
                }
            } else {
                setStatus("Please select a contact or group.");
                return;
            }

            chatService.sendMessage(message);

            clearMediaSelection();
            addMessageToChat(message);
            localRepo.addLocalMessage(userEmail, message);

            if (message.getGroupId() != null) {
                groupListView.refresh();
            } else {
                contactListView.refresh();
            }

            String mediaTypeStr = "";
            switch (selectedMediaType) {
                case IMAGE:
                    mediaTypeStr = "Image";
                    break;
                case VIDEO:
                    mediaTypeStr = "Video";
                    break;
                case AUDIO:
                    mediaTypeStr = "Audio";
                    break;
                case DOCUMENT:
                    mediaTypeStr = "Document";
                    break;
            }
            setStatus(mediaTypeStr + " sent");
        } catch (IOException e) {
            setStatus("Error sending media: " + e.getMessage());
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

                    // Ajouter le contact s'il n'existe pas
                    if (sender != null && !contacts.contains(sender)) {
                        contacts.add(sender);
                    }
                    contactListView.refresh();

                    // Afficher le message si la conversation est actuellement sélectionnée
                    if (selectedContactUser != null &&
                            sender.getId() == selectedContactUser.getId()) {
                        addMessageToChat(message);
                    }

                    if (message.isTextMessage()) {
                        setStatus("Nouveau message reçu");
                    } else {
                        setStatus("Nouveau média reçu");
                    }
                }

                scrollToBottom();
            } catch (final IOException e) {
                setStatus("Erreur lors du traitement du message : " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleAddContact() {
        final String email = newContactField.getText().trim();

        if (email.isEmpty()) {
            setStatus("Veuillez saisir un email");
            return;
        }

        // Vérifier si le contact existe déjà dans la liste locale
        final boolean contactExists = contacts.stream()
                .anyMatch(user -> user.getEmail().equalsIgnoreCase(email));

        if (contactExists) {
            setStatus("Ce contact existe déjà.");
            newContactField.clear(); // Optionnel: vider le champ
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

    @FXML
    private void handleRemoveMemberFromGroup(final ActionEvent event) {
        final Group selectedGroup = groupListView.getSelectionModel().getSelectedItem();
        if (selectedGroup == null) {
            setStatus("Veuillez sélectionner un groupe");
            return;
        }

        final String memberEmail = memberEmailField.getText().trim();
        if (memberEmail.isEmpty()) {
            setStatus("Veuillez entrer l'email du membre à supprimer");
            return;
        }

        try {
            final User memberUser = userService.getUserByEmail(memberEmail);
            if (memberUser == null) {
                setStatus("Utilisateur non trouvé: " + memberEmail);
                return;
            }

            // Vérifier que le membre n'est pas le propriétaire du groupe
            if (memberUser.getId() == selectedGroup.getOwnerUserId()) {
                setStatus("Impossible de supprimer le propriétaire du groupe");
                return;
            }

            final boolean removed = groupService.removeMemberFromGroup(selectedGroup.getId(), memberUser.getId());
            if (removed) {
                setStatus("Membre supprimé avec succès");
                memberEmailField.clear();

                // Rafraîchir l'affichage des membres si le groupe est sélectionné
                if (selectedGroup.equals(this.selectedGroup)) {
                    displayGroupMembers(selectedGroup);
                }
            } else {
                setStatus("La suppression du membre a échoué ou l'utilisateur n'était pas membre du groupe");
            }
        } catch (final IOException e) {
            setStatus("Erreur lors de la suppression du membre: " + e.getMessage());
        }
    }

    @FXML
    private void handleLogout() {
        try {
            chatService.disconnect();
            chatHistoryContainer.getChildren().clear();

            // final Stage stage = (Stage) userEmailLabel.getScene().getWindow();
            // stage.close();

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

    private void openMediaFileChooser(final String description, final String... extensions) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Sélectionner un fichier");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(description, extensions));

        File selectedFile = fileChooser.showOpenDialog(mediaButton.getScene().getWindow());
        if (selectedFile != null) {
            selectedMediaFile = selectedFile;
            selectedMediaType = fileService.detectMessageType(selectedFile.getName());

            // Show the attachment preview
            attachmentNameLabel.setText(selectedFile.getName());
            attachmentPreviewContainer.setManaged(true);
            attachmentPreviewContainer.setVisible(true);

            // Clear the message field
            messageField.clear();
            messageField.setPromptText("Appuyez sur Envoyer pour envoyer le fichier");
        }
    }

    @FXML
    private void handleRemoveAttachment() {
        clearMediaSelection();
    }

    private void clearMediaSelection() {
        selectedMediaFile = null;
        selectedMediaType = null;
        attachmentPreviewContainer.setManaged(false);
        attachmentPreviewContainer.setVisible(false);
        messageField.setPromptText("Écrire un message...");
    }

    @FXML
    private void handleAudioRecordButtonClick() {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            // Start recording
            audioRecorderService.startRecording();
            isRecording = true;

            // Update UI
            audioRecordButton.setText("■");
            audioRecordButton.getStyleClass().add("recording");
            recordingIndicatorContainer.setManaged(true);
            recordingIndicatorContainer.setVisible(true);

            // Disable other inputs
            messageField.setDisable(true);
            mediaButton.setDisable(true);

            // Start timer
            recordingSeconds = 0;
            recordingTimeLabel.setText("00:00");
            recordingTimer = new Timer();
            recordingTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    recordingSeconds++;
                    Platform.runLater(() -> {
                        int minutes = recordingSeconds / 60;
                        int seconds = recordingSeconds % 60;
                        recordingTimeLabel.setText(String.format("%02d:%02d", minutes, seconds));
                    });
                }
            }, 1000, 1000);

            setStatus("Enregistrement audio démarré");
        } catch (LineUnavailableException e) {
            setStatus("Erreur lors du démarrage de l'enregistrement: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            // Stop the timer
            if (recordingTimer != null) {
                recordingTimer.cancel();
                recordingTimer = null;
            }

            // Stop recording and get the recorded file
            File audioFile = audioRecorderService.stopRecording();
            isRecording = false;

            // Update UI
            audioRecordButton.setText("🎤");
            audioRecordButton.getStyleClass().remove("recording");
            recordingIndicatorContainer.setManaged(false);
            recordingIndicatorContainer.setVisible(false);

            // Re-enable inputs
            messageField.setDisable(false);
            mediaButton.setDisable(false);

            // If we have a valid audio file, set it as the selected media
            if (audioFile != null && audioFile.exists()) {
                selectedMediaFile = audioFile;
                selectedMediaType = MessageType.AUDIO;

                // Show the attachment preview
                attachmentNameLabel.setText("Enregistrement audio (" + formatDuration(recordingSeconds) + ")");
                attachmentPreviewContainer.setManaged(true);
                attachmentPreviewContainer.setVisible(true);

                setStatus("Enregistrement audio terminé");
            } else {
                setStatus("L'enregistrement audio a échoué");
            }
        } catch (IOException e) {
            setStatus("Erreur lors de l'arrêt de l'enregistrement: " + e.getMessage());
        }
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @FXML
    private void handleOpenMediaGallery(ActionEvent event) {
        try {
            // First, check if we have a selected conversation
            if (selectedContactUser == null && selectedGroup == null) {
                setStatus("Veuillez sélectionner un contact ou un groupe pour voir la galerie média");
                return;
            }

            // Load all media messages for the current conversation
            List<Message> mediaMessages;
            String conversationName;
            boolean isGroup;

            if (selectedContactUser != null) {
                // Direct conversation
                final long myId = userService.getUserByEmail(userEmail).getId();
                final long contactId = selectedContactUser.getId();
                mediaMessages = localRepo.loadContactMessages(userEmail, myId, contactId)
                        .stream()
                        .filter(Message::isMediaMessage)
                        .collect(Collectors.toList());
                conversationName = selectedContactUser.getDisplayNameOrEmail();
                isGroup = false;
            } else {
                // Group conversation
                mediaMessages = localRepo.loadGroupMessages(userEmail, selectedGroup.getId())
                        .stream()
                        .filter(Message::isMediaMessage)
                        .collect(Collectors.toList());
                conversationName = selectedGroup.getName();
                isGroup = true;
            }

            // Check if there are any media messages
            if (mediaMessages.isEmpty()) {
                setStatus("Aucun média trouvé dans cette conversation");
                return;
            }

            // Load the media gallery
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/media_gallery.fxml"));
            Parent root = loader.load();

            // Get the controller and set up the gallery
            MediaGalleryController galleryController = loader.getController();
            galleryController.setData(chatService, userService, mediaMessages, conversationName, isGroup);

            // Create and show the gallery
            Stage galleryStage = new Stage();
            galleryStage.setTitle("Galerie média - " + conversationName);
            galleryStage.initModality(Modality.WINDOW_MODAL);
            galleryStage.initOwner(mediaGalleryButton.getScene().getWindow());
            galleryStage.setScene(new Scene(root));
            galleryStage.show();

        } catch (IOException e) {
            setStatus("Erreur lors de l'ouverture de la galerie média: " + e.getMessage());
        }
    }

    @FXML
    private void handleMediaButtonClick(final ActionEvent event) {
        try {

            System.out.println("loading Media Dialog View ....");
            // Load the media dialog
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/media_dialog.fxml"));
            Parent root = loader.load();

            System.out.println("loading Media Dialog Controller ....");
            // Get the controller and set up the send handler
            MediaDialogController dialogController = loader.getController();
            System.out.println("setting up send handler ....");
            System.out.println("we pass a function to send handler, this function takes in a file and a type");
            System.out.println(
                    "when the user selects a file and a type, and clicks send, this function will be called, it sets the selected media file and type in the chat controller");

            dialogController.setSendHandler((file, type) -> {
                // When media is selected in the dialog, handle it here
                selectedMediaFile = file;
                selectedMediaType = type;

                // Show the attachment preview
                attachmentNameLabel.setText(file.getName());
                attachmentPreviewContainer.setManaged(true);
                attachmentPreviewContainer.setVisible(true);

                // Clear the message field
                messageField.clear();
                messageField.setPromptText("Appuyez sur Envoyer pour envoyer le fichier");
                this.sendMediaMessage();
            });

            // Create and show the dialog
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Envoyer un média");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(mediaButton.getScene().getWindow());
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

        } catch (IOException e) {
            setStatus("Erreur lors de l'ouverture du dialogue média: " + e.getMessage());
        }
    }

    private void startContactStatusUpdater() {
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                for (final User user : contacts) {
                    final boolean isOnline = contactService.isUserOnline(user.getId());
                    Platform.runLater(() -> user.setOnline(isOnline));
                }
            } catch (final IOException e) {
                Platform.runLater(() -> setStatus("Erreur mise à jour des statuts: " + e.getMessage()));
            }
        }, 0, 5, TimeUnit.SECONDS); // Vérifie toutes les 5 secondes

        // Arrêter le scheduler à la fermeture de la fenêtre
        Platform.runLater(() -> {
            final Stage stage = (Stage) userEmailLabel.getScene().getWindow();
            stage.setOnCloseRequest(event -> scheduler.shutdown());
        });
    }
}
