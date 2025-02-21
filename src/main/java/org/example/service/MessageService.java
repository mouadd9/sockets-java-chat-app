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

    // this method will send create a new entry in the message.json with the new message
    public boolean sendMessage(Message message) throws IOException {
        // if sender email or receiver email do not exist then we return false
        if (userRepository.findByEmail(message.getSenderEmail()).isEmpty() || 
            userRepository.findByEmail(message.getReceiverEmail()).isEmpty()) {
            return false;
        }
        // then we save it
        System.out.println("this is the object we created in the client side and sent here : " + message.getContent() + " sender : " + message.getSenderEmail());
        messageRepository.saveMessage(message);
        return true;
    }

    

    public List<Message> getUnreadMessages(String userEmail) throws IOException {
        return messageRepository.getUnreadMessages(userEmail);
    }
/*
    public void markMessageAsRead(String messageId) throws IOException {
        messageRepository.markMessageAsRead(messageId);
    }*/
}
