package org.example.client.gui.controllers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.example.client.gui.service.ChatService;
import org.example.client.gui.service.UserService;
import org.example.shared.model.Message;
import org.example.shared.model.User;
import org.example.shared.model.enums.MessageType;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class MediaGalleryController {

    @FXML
    private Label titleLabel;

    @FXML
    private ComboBox<String> mediaTypeFilter;

    @FXML
    private FlowPane mediaContainer;

    @FXML
    private Label statsLabel;

    private ChatService chatService;
    private UserService userService;
    private List<Message> mediaMessages;
    private String conversationName;
    private boolean isGroup;

    /**
     * Initializes the controller.
     */
    @FXML
    public void initialize() {
        // Initialize media type filter
        mediaTypeFilter.setItems(FXCollections.observableArrayList(
                "Tous", "Images", "Vidéos", "Documents", "Audio"
        ));

        // Add listener to filter media by type
        mediaTypeFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                filterMediaByType(newVal);
            }
        });

        // Set default selection
        mediaTypeFilter.getSelectionModel().select(0);
    }

    /**
     * Sets the data for the gallery.
     *
     * @param chatService The chat service
     * @param userService The user service
     * @param mediaMessages The list of media messages
     * @param conversationName The name of the conversation
     * @param isGroup Whether the conversation is a group
     */
    public void setData(ChatService chatService, UserService userService, List<Message> mediaMessages,
                        String conversationName, boolean isGroup) {
        this.chatService = chatService;
        this.userService = userService;
        this.mediaMessages = new ArrayList<>(mediaMessages);
        this.conversationName = conversationName;
        this.isGroup = isGroup;

        // Update title
        titleLabel.setText("Galerie média - " + conversationName);

        // Load all media initially
        filterMediaByType("Tous");
    }

    /**
     * Filters the media by type.
     *
     * @param filterType The filter type
     */
    private void filterMediaByType(String filterType) {
        List<Message> filteredMessages;

        switch (filterType) {
            case "Images":
                filteredMessages = mediaMessages.stream()
                        .filter(Message::isImageMessage)
                        .collect(Collectors.toList());
                break;
            case "Vidéos":
                filteredMessages = mediaMessages.stream()
                        .filter(Message::isVideoMessage)
                        .collect(Collectors.toList());
                break;
            case "Documents":
                filteredMessages = mediaMessages.stream()
                        .filter(Message::isDocumentMessage)
                        .collect(Collectors.toList());
                break;
            case "Audio":
                filteredMessages = mediaMessages.stream()
                        .filter(Message::isAudioMessage)
                        .collect(Collectors.toList());
                break;
            default:
                filteredMessages = mediaMessages;
                break;
        }

        displayMedia(filteredMessages);
        updateStats(filteredMessages.size());
    }

    /**
     * Displays the media in the gallery.
     *
     * @param messages The messages to display
     */
    private void displayMedia(List<Message> messages) {
        mediaContainer.getChildren().clear();

        for (Message message : messages) {
            try {
                // Get the file
                File mediaFile = chatService.getMediaFile(message);

                // Create the media item based on type
                VBox mediaItem = createMediaItem(message, mediaFile);

                mediaContainer.getChildren().add(mediaItem);
            } catch (Exception e) {
                System.err.println("Error displaying media: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a media item for the gallery.
     *
     * @param message The message
     * @param mediaFile The media file
     * @return The media item
     */
    private VBox createMediaItem(Message message, File mediaFile) throws IOException {
        VBox item = new VBox(5);
        item.setAlignment(Pos.CENTER);
        item.setStyle("-fx-background-color: #f5f5f5; -fx-padding: 10px; -fx-border-radius: 5px; " +
                "-fx-background-radius: 5px; -fx-border-color: #ddd;");
        item.setPrefWidth(150);
        item.setPrefHeight(180);

        // Create the preview based on media type
        switch (message.getType()) {
            case IMAGE:
                createImagePreview(item, mediaFile);
                break;
            case VIDEO:
                createVideoPreview(item);
                break;
            case AUDIO:
                createAudioPreview(item);
                break;
            case DOCUMENT:
                createDocumentPreview(item, message);
                break;
            default:
                item.getChildren().add(new Label("Type non pris en charge"));
                break;
        }

        // Add sender name
        try {
            User sender = userService.getUserById(message.getSenderUserId());
            Label senderLabel = new Label(sender.getDisplayNameOrEmail());
            senderLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");
            item.getChildren().add(senderLabel);
        } catch (IOException e) {
            // Ignore
        }

        // Make the item clickable to open the media
        item.setOnMouseClicked(e -> openMedia(message, mediaFile));

        return item;
    }

    private void createImagePreview(VBox item, File mediaFile) {
        try {
            Image image = new Image(mediaFile.toURI().toString(), 130, 130, true, true);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(130);
            imageView.setFitHeight(130);
            imageView.setPreserveRatio(true);

            item.getChildren().add(imageView);
        } catch (Exception e) {
            Label errorLabel = new Label("Erreur image");
            item.getChildren().add(errorLabel);
        }
    }

    private void createVideoPreview(VBox item) {
        Label videoIcon = new Label("🎬");
        videoIcon.setStyle("-fx-font-size: 48px;");

        Label videoLabel = new Label("Vidéo");

        item.getChildren().addAll(videoIcon, videoLabel);
    }

    private void createAudioPreview(VBox item) {
        Label audioIcon = new Label("🔊");
        audioIcon.setStyle("-fx-font-size: 48px;");

        Label audioLabel = new Label("Audio");

        item.getChildren().addAll(audioIcon, audioLabel);
    }

    private void createDocumentPreview(VBox item, Message message) {
        Label docIcon = new Label("📄");
        docIcon.setStyle("-fx-font-size: 48px;");

        Label docName = new Label(message.getFileName() != null ?
                truncateText(message.getFileName(), 15) : "Document");

        item.getChildren().addAll(docIcon, docName);
    }

    /**
     * Opens the media in an appropriate viewer.
     *
     * @param message The message
     * @param mediaFile The media file
     */
    private void openMedia(Message message, File mediaFile) {
        if (!mediaFile.exists()) {
            return;
        }

        switch (message.getType()) {
            case IMAGE:
                openImageViewer(mediaFile);
                break;
            case VIDEO:
                openVideoPlayer(mediaFile);
                break;
            case AUDIO:
                openAudioPlayer(mediaFile);
                break;
            case DOCUMENT:
                openDocument(mediaFile);
                break;
        }
    }

    private void openImageViewer(File imageFile) {
        try {
            // Create a new stage with an image viewer
            Stage imageStage = new Stage();
            imageStage.setTitle("Visionneuse d'image");

            Image image = new Image(imageFile.toURI().toString());
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);

            // Limit size to fit screen
            imageView.setFitWidth(Math.min(image.getWidth(), 800));
            imageView.setFitHeight(Math.min(image.getHeight(), 600));

            javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(imageView);
            scrollPane.setPannable(true);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane);
            imageStage.setScene(scene);
            imageStage.show();
        } catch (Exception e) {
            System.err.println("Error opening image: " + e.getMessage());
        }
    }

    private void openVideoPlayer(File videoFile) {
        try {
            // Create a new stage with a video player
            Stage videoStage = new Stage();
            videoStage.setTitle("Lecteur vidéo");

            javafx.scene.media.Media media = new javafx.scene.media.Media(videoFile.toURI().toString());
            javafx.scene.media.MediaPlayer mediaPlayer = new javafx.scene.media.MediaPlayer(media);
            javafx.scene.media.MediaView mediaView = new javafx.scene.media.MediaView(mediaPlayer);

            // Set up controls
            javafx.scene.control.Button playButton = new javafx.scene.control.Button("▶ Play");
            playButton.setOnAction(e -> {
                if (mediaPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                    playButton.setText("▶ Play");
                } else {
                    mediaPlayer.play();
                    playButton.setText("⏸ Pause");
                }
            });

            javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
            progressBar.setPrefWidth(300);

            mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                double progress = newVal.toSeconds() / mediaPlayer.getTotalDuration().toSeconds();
                javafx.application.Platform.runLater(() -> progressBar.setProgress(progress));
            });

            // Add seek functionality
            progressBar.setOnMouseClicked(e -> {
                double percent = e.getX() / progressBar.getWidth();
                mediaPlayer.seek(mediaPlayer.getTotalDuration().multiply(percent));
            });

            HBox controls = new HBox(10, playButton, progressBar);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new javafx.geometry.Insets(10));

            VBox root = new VBox(10, mediaView, controls);
            root.setAlignment(Pos.CENTER);

            javafx.scene.Scene scene = new javafx.scene.Scene(root, 640, 480);
            videoStage.setScene(scene);

            videoStage.setOnCloseRequest(e -> mediaPlayer.stop());

            videoStage.show();
            mediaPlayer.play();
        } catch (Exception e) {
            System.err.println("Error opening video: " + e.getMessage());
        }
    }

    private void openAudioPlayer(File audioFile) {
        try {
            // Create a new stage with an audio player
            Stage audioStage = new Stage();
            audioStage.setTitle("Lecteur audio");

            javafx.scene.media.Media media = new javafx.scene.media.Media(audioFile.toURI().toString());
            javafx.scene.media.MediaPlayer mediaPlayer = new javafx.scene.media.MediaPlayer(media);

            // Create controls
            javafx.scene.control.Button playButton = new javafx.scene.control.Button("▶ Play");
            javafx.scene.control.Slider timeSlider = new javafx.scene.control.Slider();
            javafx.scene.control.Label timeLabel = new javafx.scene.control.Label("00:00 / 00:00");

            // Configure play button
            playButton.setOnAction(e -> {
                if (mediaPlayer.getStatus() == javafx.scene.media.MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                    playButton.setText("▶ Play");
                } else {
                    mediaPlayer.play();
                    playButton.setText("⏸ Pause");
                }
            });

            // Configure time slider
            timeSlider.setMin(0);
            timeSlider.setMax(1);
            timeSlider.setValue(0);
            timeSlider.setPrefWidth(300);

            timeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (timeSlider.isValueChanging()) {
                    mediaPlayer.seek(mediaPlayer.getTotalDuration().multiply(newVal.doubleValue()));
                }
            });

            // Update time label and slider
            mediaPlayer.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
                if (!timeSlider.isValueChanging()) {
                    timeSlider.setValue(newVal.toSeconds() / mediaPlayer.getTotalDuration().toSeconds());

                    int currentMinutes = (int) newVal.toMinutes();
                    int currentSeconds = (int) newVal.toSeconds() % 60;

                    int totalMinutes = (int) mediaPlayer.getTotalDuration().toMinutes();
                    int totalSeconds = (int) mediaPlayer.getTotalDuration().toSeconds() % 60;

                    timeLabel.setText(String.format("%02d:%02d / %02d:%02d",
                            currentMinutes, currentSeconds, totalMinutes, totalSeconds));
                }
            });

            // Layout
            HBox controls = new HBox(10, playButton, timeSlider, timeLabel);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new javafx.geometry.Insets(20));

            audioStage.setScene(new javafx.scene.Scene(controls, 500, 100));
            audioStage.setOnCloseRequest(e -> mediaPlayer.stop());
            audioStage.show();
        } catch (Exception e) {
            System.err.println("Error opening audio: " + e.getMessage());
        }
    }

    private void openDocument(File documentFile) {
        try {
            java.awt.Desktop.getDesktop().open(documentFile);
        } catch (Exception e) {
            System.err.println("Error opening document: " + e.getMessage());
        }
    }

    /**
     * Updates the statistics label.
     *
     * @param count The number of media items
     */
    private void updateStats(int count) {
        statsLabel.setText("Total: " + count + " média" + (count > 1 ? "s" : ""));
    }

    /**
     * Truncates a text to a maximum length.
     *
     * @param text The text to truncate
     * @param maxLength The maximum length
     * @return The truncated text
     */
    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Handles the close button action.
     *
     * @param event The action event
     */
    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) mediaContainer.getScene().getWindow();
        stage.close();
    }
}