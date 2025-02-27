package org.example;

import org.example.Entities.Utilisateur;
import org.example.service.UserService;

import java.util.Optional;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean isRegistered = false;
        boolean isAuthenticated = false;
        UserService userservice = new UserService();

        System.out.println("// creating users ################");
        System.out.println("// creating users ################");
        System.out.println("// creating users ################");
        System.out.println("// creating users ################");

         userservice.registerUser("mouad@gmail.com", "test");
         userservice.registerUser("bencaid@gmail.com", "test");
         userservice.registerUser("bencaid9@gmail.com", "test");


        System.out.println("// creating users ################");
        System.out.println("// creating users ################");
        System.out.println("// creating users ################");
        System.out.println("// creating users ################");
/*

        System.out.println("// authenticating user 1 : mouad@gmail.com  ################");

            isAuthenticated = userservice.authenticateUser("mouad@gmail.com", "test");
            if (isAuthenticated) {
                System.out.println("// authenticating user 1 success)");  }
            else {
                System.out.println("user or email are not correct");
            }


*/
    }
}
