package org.example.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.Entities.Message;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

// this class will provide methods that will do the following : 
  // - a method to save a list of messages objects to a json file (messages.json), by writing to the json file
  // - a method to load all messages from the JSON file (messages.json) and return them as a list of messages objects 
public class JsonMessageRepository {
    // location of the messages.json
    private static final String MESSAGES_FILE = "src/main/data/messages.json";
    private final ObjectMapper objectMapper;
    private final File messagesFile;

    // constructor that creates an instance of ObjectMapper()
    public JsonMessageRepository() {
        // object Mapper is a class provided by the jackson library it serializes and deserialzes JSON strings;
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle LocalDateTime serialization
        this.objectMapper.registerModule(new JavaTimeModule());
        // this provides the path for the messages.json file
        this.messagesFile = new File(MESSAGES_FILE);
    }
    // this method uses a jackson method used to serialize any Java value as JSON output, written to File provided.
    public void saveMessages(List<Message> messages) throws IOException {
        // inputs : a list of messages , a file path 
        // effect : serialization and persistance into the provided file path
        objectMapper.writeValue(messagesFile, messages);
    }
    // this method will retrieve data from the JSON file and return a list of messages 
    // the issue here is, jackson do not know to what type of objects the list will have
    public List<Message> loadMessages() throws IOException {
        if (!messagesFile.exists()) {
            throw new IOException("messages.json file not found in data directory");
        }
        // this is the key line 
        // here we tell jackson that we need an ArrayList containing Message objects !!!!!!!
        // we are telling jackson that the json file is an array (ArrayList) and this array will have objects of type (Message)
        CollectionType listType = objectMapper.getTypeFactory()
            .constructCollectionType(ArrayList.class, Message.class);
        // this readVale() method needs two informations to function , first the path of the JSON file then an information regarding the types 
        // that jackson should use to desirialize
        // its like we are telling jackson to Take this JSON array (messagesFile) and create an ArrayList of Message objects from it.
        ArrayList<Message> messages = objectMapper.readValue(messagesFile, listType);
        // then we return the messages array
        return messages ;
    }

    public void saveMessage(Message message) throws IOException {
        // we will first load all messages from the messages.json using the Jackson desirializer
        List<Message> messages = loadMessages();
        // then we will add the new message at the end of the list
        messages.add(message);
        System.out.println("saving message: " );

        // then we serialize and save the new array of messages
        saveMessages(messages);
        System.out.println("Message saved: " + message.getContent());
    }

    // Get all messages for a specific user (both sent and received)
    public List<Message> getUserMessages(String userEmail) throws IOException {
        return loadMessages().stream()
            .filter(m -> m.getSenderEmail().equals(userEmail) || 
                        m.getReceiverEmail().equals(userEmail))
            .collect(Collectors.toList());
    }

    // Get only messages sent from the user we pass in argument
     public List<Message> getSentMessages(String userEmail) throws IOException {
        return loadMessages()
             .stream() // here we turn this into a stream
             .filter(m -> m.getSenderEmail().equals(userEmail))
             .collect(Collectors.toList()); // this will gather all sent messages and put them into a List
    }

    // this will get only messages received by a user passed in
    public List<Message> getReceivedMessages(String userEmail) throws IOException {
        return loadMessages()
               .stream()
               .filter(m -> m.getReceiverEmail().equals(userEmail))
               .collect(Collectors.toList()); // here we tranform a stream back into a usable list
    }
    // Get only unread messages for a user
    public List<Message> getUnreadMessages(String userEmail) throws IOException {
        return loadMessages()
            .stream()
            .filter(m -> m.getReceiverEmail().equals(userEmail) && !m.isRead())
            .collect(Collectors.toList()); // this will gather all received and unread messages
    }
/*
    // Mark a message as read
    public void markMessageAsRead(String messageId) throws IOException {
        List<Message> messages = loadMessages();
        messages.stream()
            .filter(m -> m.getId().equals(messageId))
            .map(m -> m.setRead(true))
            .findFirst(); // to trigger the execution
        saveMessages(messages);
    }*/

    // Get conversation between two users
    public List<Message> getConversation(String user1Email, String user2Email) throws IOException {
        return loadMessages().stream()
            .filter(m -> (m.getSenderEmail().equals(user1Email) && m.getReceiverEmail().equals(user2Email)) ||
                        (m.getSenderEmail().equals(user2Email) && m.getReceiverEmail().equals(user1Email)))
            .collect(Collectors.toList());
    }

    // Delete a message
    public boolean deleteMessage(String messageId) throws IOException {
        List<Message> messages = loadMessages();
        boolean removed = messages.removeIf(m -> m.getId().equals(messageId));
        if (removed) {
            saveMessages(messages);
            System.out.println("Message " + messageId + " deleted");
        }
        return removed;
    }
}
