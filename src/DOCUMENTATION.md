# Socket Chat Application Documentation

## 1. Project Overview
This is a real-time multi-client chat application built using Java Socket programming. It supports:
- Real-time messaging
- Message status tracking (delivered/queued)
- User authentication
- Offline message handling

## 2. System Architecture
```
+-------------------+       +-------------------+       +-------------------+
|    Client App     | <---> |    Server App     | <---> |   Data Storage    |
| (ClientTCP.java)  |       | (ServeurTCP.java) |       | (JSON files)      |
+-------------------+       +-------------------+       +-------------------+
```

## 3. Key Components
- **ServerTCP**: Main server class that handles client connections
- **ClientHandler**: Manages individual client sessions
- **ClientTCP**: Client-side application
- **Message**: Message entity with status tracking
- **UserService**: Handles user authentication and status
- **MessageService**: Manages message storage and delivery

## 4. Data Flow
1. Client connects to server
2. User authenticates
3. Messages are sent/received through socket streams
4. Server updates message status and stores messages

## 5. Authentication Flow
1. Client sends credentials
2. Server verifies against users.json
3. Server updates user online status
4. Client receives authentication result

## 6. Message Handling
- Messages are stored in messages.json
- Status is tracked (delivered/queued)
- Offline messages are queued and delivered when user comes online

## 7. Error Handling
- Connection errors are logged
- Invalid messages are rejected
- Authentication failures are handled gracefully

## 8. User Manual
### Running the Server
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.ServeurTCP"
```

### Running the Client
```bash
mvn exec:java -Dexec.mainClass="org.example.ClientTCP"
```

### Test Users
- alice@test.com / password123
- bob@test.com / password123
