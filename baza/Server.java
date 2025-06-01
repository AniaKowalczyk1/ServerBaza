package baza;


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;

public class Server {
    private static final int MAX_CLIENTS = 250;
    private static List<Question> questions = new ArrayList<>();
    private static int port;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC";
    private static final String DB_NAME = "Quiz";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    public static void main(String[] args) {
    	initDatabase();
    	loadQuestionsFromDatabase();
        loadConfig();
     

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serwer uruchomiony na porcie " + port);
            ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTS);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                pool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Błąd serwera: " + e.getMessage());
        }
    }
    
    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // 1. Utwórz bazę, jeśli nie istnieje
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME);

            // 2. Połącz się z bazą Quiz
            try (Connection quizConn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/" + DB_NAME + "?useSSL=false&serverTimezone=UTC",
                    DB_USER, DB_PASS)) {

                // 3a. Utwórz tabelę questions, jeśli nie istnieje
                if (!tableExists(quizConn, "questions")) {
                    try (Statement quizStmt = quizConn.createStatement()) {
                        String createTableSQL = """
                            CREATE TABLE questions (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                question TEXT,
                                option_a VARCHAR(255),
                                option_b VARCHAR(255),
                                option_c VARCHAR(255),
                                option_d VARCHAR(255),
                                correct_option VARCHAR(1)
                            )
                        """;
                        quizStmt.executeUpdate(createTableSQL);
                        System.out.println("Utworzono tabelę 'questions'.");
                    }
                } else {
                    System.out.println("Tabela 'questions' już istnieje.");
                }

                // 4. Sprawdź, czy w tabeli questions są już dane
                if (isTableEmpty(quizConn, "questions")) {
                    loadQuestionsIntoDatabase(quizConn, "bazaPytan.txt");
                    System.out.println("Załadowano pytania do bazy danych.");
                } else {
                    System.out.println("Tabela 'questions' nie jest pusta – pomijam ładowanie danych.");
                }
                
                // 3c. Utwórz tabelę answers, jeśli nie istnieje
                if (!tableExists(quizConn, "answers")) {
                    try (Statement quizStmt = quizConn.createStatement()) {
                        String createAnswersTable = """
                            CREATE TABLE IF NOT EXISTS answers (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                client_id VARCHAR(36),
                                question_text TEXT,
                                client_answer VARCHAR(255),
                                correct_answer VARCHAR(10),
                                is_correct BOOLEAN DEFAULT FALSE,
                                answer_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            )
                        """;
                        quizStmt.executeUpdate(createAnswersTable);
                        System.out.println("Utworzono tabelę 'answers'.");
                    }
                } else {
                    System.out.println("Tabela 'answers' już istnieje.");
                }

                // 3c. Utwórz tabelę results, jeśli nie istnieje
                if (!tableExists(quizConn, "results")) {
                    try (Statement quizStmt = quizConn.createStatement()) {
                        String createResultsTable = """
                            CREATE TABLE IF NOT EXISTS results (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                client_id VARCHAR(36),
                                score INT,
                                total_questions INT,
                                completion_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                            )
                        """;
                        quizStmt.executeUpdate(createResultsTable);
                        System.out.println("Utworzono tabelę 'results'.");
                    }
                } else {
                    System.out.println("Tabela 'results' już istnieje.");
                }

            }

        } catch (SQLException e) {
            System.err.println("Błąd inicjalizacji bazy danych: " + e.getMessage());
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (var rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next(); // Jeśli jest wynik, to tabela istnieje
        }
    }

    private static boolean isTableEmpty(Connection conn, String tableName) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        }
        return true;
    }


    private static void loadConfig() {
        Properties props = new Properties();
        try (InputStream input = Server.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                port = Integer.parseInt(props.getProperty("port", "1234"));
            } else {
                System.err.println("Nie znaleziono pliku config.properties. Używam domyślnych wartości.");
                port = 1234;
            }
        } catch (IOException e) {
            System.err.println("Błąd ładowania config.properties. Używam domyślnych wartości.");
            port = 1234;
        }
    }
    
    private static void loadQuestionsFromDatabase() {
        String query = "SELECT question, option_a, option_b, option_c, option_d, correct_option FROM questions";

        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/" + DB_NAME + "?useSSL=false&serverTimezone=UTC",
                DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String text = rs.getString("question");
                String[] options = {
                     rs.getString("option_a"),
                     rs.getString("option_b"),
                     rs.getString("option_c"),
                     rs.getString("option_d")
                };
                String correct = rs.getString("correct_option");

                questions.add(new Question(text, options, correct));
            }

            System.out.println("Załadowano pytania z bazy danych: " + questions.size());

        } catch (SQLException e) {
            System.err.println("Błąd ładowania pytań z bazy danych: " + e.getMessage());
        }
    }


    private static void loadQuestionsIntoDatabase(Connection conn, String fileName) {
        String insertSQL = """
            INSERT INTO questions (question, option_a, option_b, option_c, option_d, correct_option)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (BufferedReader reader = new BufferedReader(new FileReader(fileName));
             PreparedStatement ps = conn.prepareStatement(insertSQL)) {

            while (true) {
                String question = reader.readLine();
                if (question == null) break;

                String[] opts = new String[4];
                for (int i = 0; i < 4; i++) {
                    opts[i] = reader.readLine();
                    if (opts[i] == null) return;
                }

                String correct = reader.readLine();
                if (correct == null) return;

                reader.readLine(); // pusta linia

                ps.setString(1, question);
                ps.setString(2, opts[0]);
                ps.setString(3, opts[1]);
                ps.setString(4, opts[2]);
                ps.setString(5, opts[3]);
                ps.setString(6, correct.trim().toUpperCase());
                ps.addBatch();
            }

            ps.executeBatch();

        } catch (IOException | SQLException e) {
            System.err.println("Błąd ładowania pytań do bazy: " + e.getMessage());
        }
    }



    private static void handleClient(Socket socket) {
        String clientId = UUID.randomUUID().toString();

        try (
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/" + DB_NAME + "?useSSL=false&serverTimezone=UTC",
                DB_USER, DB_PASS);
            PreparedStatement psAnswers = conn.prepareStatement("""
                INSERT INTO answers (client_id, question_text, client_answer, correct_answer, is_correct)
                VALUES (?, ?, ?, ?, ?)
            """);
            PreparedStatement psResults = conn.prepareStatement("""
                INSERT INTO results (client_id, score, total_questions)
                VALUES (?, ?, ?)
            """);
            socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            List<String> userAnswers = new ArrayList<>();
            int score = 0;
            boolean quitEarly = false;

            out.println("Twój unikatowy identyfikator: " + clientId);

            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                out.println(q.formatForSending());

                String response = in.readLine();

                if (response == null || response.equals("Timeout") || response.trim().isEmpty()) {
                    response = "Brak odpowiedzi (timeout)";
                }

                if (response.equalsIgnoreCase("Q")) {
                    response = "Zrezygnowano z testu.";
                    quitEarly = true;

                    // Zapis dla bieżącego pytania
                    psAnswers.setString(1, clientId);
                    psAnswers.setString(2, q.getQuestionText());
                    psAnswers.setString(3, response);
                    psAnswers.setString(4, q.getCorrectAnswer());
                    psAnswers.setBoolean(5, false);
                    psAnswers.addBatch();

                    // Dla pozostałych pytań zapisz rezygnację
                    for (int j = i + 1; j < questions.size(); j++) {
                        Question qRest = questions.get(j);
                        psAnswers.setString(1, clientId);
                        psAnswers.setString(2, qRest.getQuestionText());
                        psAnswers.setString(3, "Zrezygnowano z testu.");
                        psAnswers.setString(4, qRest.getCorrectAnswer());
                        psAnswers.setBoolean(5, false);
                        psAnswers.addBatch();
                    }
                    break; // kończymy pętlę pytań po rezygnacji
                }

                boolean isCorrect = q.isCorrect(response);
                if (isCorrect) score++;
                userAnswers.add(response);

                psAnswers.setString(1, clientId);
                psAnswers.setString(2, q.getQuestionText());
                psAnswers.setString(3, response);
                psAnswers.setString(4, q.getCorrectAnswer());
                psAnswers.setBoolean(5, isCorrect);
                psAnswers.addBatch();
            }

            psAnswers.executeBatch();

            // Zapis wyniku do tabeli results
            psResults.setString(1, clientId);
            psResults.setInt(2, quitEarly ? score : score);  // score jest tylko do poprawnych odpowiedzi przed rezygnacją
            psResults.setInt(3, questions.size());
            psResults.executeUpdate();

            out.println("Twój wynik: " + score + "/" + questions.size());

        } catch (IOException | SQLException e) {
            System.err.println("Błąd klienta lub bazy: " + e.getMessage());
        }
    }


}
