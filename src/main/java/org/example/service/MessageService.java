package org.example.service;

import org.example.Entities.Message;
import org.example.repository.JsonMessageRepository;
import org.example.repository.JsonUserRepository;

import java.io.IOException;
import java.util.List;

public class MessageService {
    private final JsonMessageRepository messageRepository;
    private final JsonUserRepository userRepository;

    public MessageService() {
        this.messageRepository = new JsonMessageRepository();
        this.userRepository = new JsonUserRepository();
    }

    // this 
    public boolean sendMessage(String senderEmail, String receiverEmail, String content) throws IOException {
        // if sender email or receiver email do not exist then we return false
        if (userRepository.findByEmail(senderEmail).isEmpty() || 
            userRepository.findByEmail(receiverEmail).isEmpty()) {
            return false;
        }
        // if both exist we create a new message entity
        Message message = new Message(senderEmail, receiverEmail, content);
        // then we save it
        messageRepository.saveMessage(message);
        return true;
    }

    public List<Message> getUnreadMessages(String userEmail) throws IOException {
        return messageRepository.getUnreadMessagesForUser(userEmail);
    }

    public void markMessageAsRead(String messageId) throws IOException {
        messageRepository.markMessageAsRead(messageId);
    }
}
