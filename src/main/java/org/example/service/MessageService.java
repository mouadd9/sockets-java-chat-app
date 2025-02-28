package org.example.service;

import java.io.IOException;
import java.util.List;

import org.example.broker.MessageBroker;
import org.example.model.Message;
import org.example.repository.JsonMessageRepository;
import org.example.repository.JsonUserRepository;

public class MessageService {
    private final JsonMessageRepository messageRepository;
    private final JsonUserRepository userRepository;
    private final MessageBroker messageBroker;

    public MessageService() {
        this.messageRepository = new JsonMessageRepository();
        this.userRepository = new JsonUserRepository();
        this.messageBroker = MessageBroker.getInstance();
    }

    public boolean sendMessage(final Message message) throws IOException {
        // Vérifier que l'expéditeur et le destinataire existent
        if (userRepository.findByEmail(message.getSenderEmail()).isEmpty() || 
            userRepository.findByEmail(message.getReceiverEmail()).isEmpty()) {
            return false;
        }
        
        // Le Message Broker gère la persistance et la mise en file d'attente
        // Nous passons null ici car la décision d'envoyer le message directement
        // est déjà prise au niveau du ClientHandler
        messageBroker.sendMessage(message, null);
        return true;
    }

    public List<Message> getUnreadMessages(final String userEmail) throws IOException {
        return messageRepository.getUnreadMessages(userEmail);
    }
}
