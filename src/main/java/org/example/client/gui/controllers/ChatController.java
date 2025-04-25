package org.example.client.gui.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.example.client.gui.repository.JsonLocalMessageRepository;
import org.example.client.gui.service.AudioRecorderService;
import org.example.client.gui.service.ChatService;
import org.example.client.gui.service.ContactService;
import org.example.client.gui.service.FileService;
import org.example.client.gui.service.GroupService;
import org.example.client.gui.service.UserService;
import org.example.shared.model.Group;
import org.example.shared.model.Message;
import org.example.shared.model.User;
import org.example.shared.model.enums.MessageType;

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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
                setStatus("Conversation chargée avec " + selectedContactUser.getDisplayNameOrEmail());
            }
        });

        groupListView.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel != null) {
                contactListView.getSelectionModel().clearSelection();
                selectedGroup = sel;
                selectedContactUser = null;
                loadGroupConversation(sel);
                setStatus("Conversation de groupe chargée : " + sel.getName());
            }
        });

        messageField.setOnAction(this::handleSendMessage);

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
            audioRecordButton.setTooltip(new javafx.scene.control.Tooltip("L'enregistrement audio n'est pas pris en charge sur ce système"));
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
        loadContacts();
        loadGroups();
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
                if (url != null) { // Éviter de passer une URL null à loadImage
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
                contentLabel.setMaxWidth(chatHistoryContainer.getWidth() * 0.6); // Pour laisser de la place à l'horodatage

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
            chatHistoryContainer.getChildren().clear();
            try {
                final long myId = userService.getUserByEmail(userEmail).getId();
                final long contactId = contactUser.getId();
                final List<Message> contactMessages = localRepo.loadContactMessages(userEmail, myId, contactId);
                contactMessages.forEach(this::addMessageToChat);
                scrollToBottom();
            } catch (final IOException e) {
                setStatus("Erreur lors du chargement de la conversation avec " + contactUser.getDisplayNameOrEmail()
                        + " : "
                        + e.getMessage());
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
                        message = chatService.createDirectMediaMessage(userEmail, selectedContactUser.getEmail(), selectedMediaFile);
                        break;
                    case AUDIO:
                        message = chatService.createDirectAudioMessage(userEmail, selectedContactUser.getEmail(), selectedMediaFile);
                        break;
                    default:
                        throw new IllegalStateException("Unsupported media type: " + selectedMediaType);
                }
            } else if (selectedGroup != null) {
                switch (selectedMediaType) {
                    case IMAGE:
                    case VIDEO:
                    case DOCUMENT:
                        message = chatService.createGroupMediaMessage(userEmail, selectedGroup.getId(), selectedMediaFile);
                        break;
                    case AUDIO:
                        message = chatService.createGroupAudioMessage(userEmail, selectedGroup.getId(), selectedMediaFile);
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
            System.out.println("when the user selects a file and a type, and clicks send, this function will be called, it sets the selected media file and type in the chat controller");


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
}