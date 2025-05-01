package org.example.client.gui.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.example.shared.dao.ContactDAO;
import org.example.shared.dao.UserDAO;
import org.example.shared.model.Contact;
import org.example.shared.model.User;

/**
 * Service dédié à la gestion des contacts.
 */
public class ContactService {

    private final UserDAO userDAO;
    private final ContactDAO contactDAO;
    private final UserService userService = new UserService();

    public ContactService() {
        this.userDAO = new UserDAO();
        this.contactDAO = new ContactDAO();
    }

    /**
     * Récupère la liste des emails des contacts
     */
    public List<String> getContacts(final String userEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + userEmail);
        }
        return contactDAO.getContactsByUserId(user.getId());
    }

    /**
     * Récupère la liste des objets User complets pour les contacts
     */
    public List<User> getContactUsers(final String userEmail) throws IOException {
        final List<String> contactEmails = getContacts(userEmail);
        final List<User> users = new ArrayList<>();
        
        for (final String email : contactEmails) {
            final User user = userService.getUserByEmail(email);
            if (user != null) {
                users.add(user);
            }
        }
        
        return users;
    }

    /**
     * Ajoute un contact et retourne l'objet User correspondant
     */
    public User addContactUser(final String userEmail, final String contactEmail) throws IOException {
        final boolean added = addContact(userEmail, contactEmail);
        if (added) {
            return userService.getUserByEmail(contactEmail);
        }
        return null;
    }

    /**
     * Ajoute un contact par email
     */
    public boolean addContact(final String userEmail, final String contactEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        final User contactUser = userDAO.findUserByEmail(contactEmail);

        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + userEmail);
        }
        if (contactUser == null) {
            throw new IOException("Contact non trouvé: " + contactEmail);
        }
        if (user.getId() == contactUser.getId()) {
            throw new IllegalArgumentException("Vous ne pouvez pas vous ajouter vous-même comme contact.");
        }

        final Contact first_Contact = new Contact(user.getId(), contactUser.getId());
        final Contact second_Contact = new Contact(contactUser.getId(), user.getId());


        contactDAO.createContact(first_Contact);
        contactDAO.createContact(second_Contact);
        return true;
    }

    /**
     * Supprime un contact
     */
    public boolean removeContact(final String userEmail, final String contactEmail) throws IOException {
        final User user = userDAO.findUserByEmail(userEmail);
        final User contactUser = userDAO.findUserByEmail(contactEmail);

        if (user == null) {
            throw new IOException("Utilisateur non trouvé: " + userEmail);
        }
        if (contactUser == null) {
            return false;
        }

        return contactDAO.deleteContact(user.getId(), contactUser.getId());
    }

    /**
     * Vérifie si un utilisateur est en ligne.
     */
    public boolean isUserOnline(final long userId) throws IOException {
        final User user = userService.getUserById(userId);
        if (user == null) {
            throw new IOException("Utilisateur non trouvé avec l'ID: " + userId);
        }
        return user.isOnline();
    }
}
