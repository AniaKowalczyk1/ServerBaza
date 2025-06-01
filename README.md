Ten projekt to wielowątkowa aplikacja serwerowa napisana w języku Java, która obsługuje testy/quizy dla wielu klientów równolegle (do 250 jednocześnie). Serwer komunikuje się z klientami przez TCP, a dane quizowe są przechowywane i zarządzane za pomocą bazy danych MySQL.

Funkcje
Obsługa do 250 klientów równocześnie (pula wątków ExecutorService).
Przechowywanie pytań w bazie danych MySQL.
Automatyczne tworzenie bazy danych i tabel (questions, answers, results).
Import pytań z pliku bazaPytan.txt, jeśli tabela questions jest pusta.
Obsługa rezygnacji klienta z testu (klient może wysłać Q, by przerwać test).
Zapis odpowiedzi klienta i wyniku końcowego do bazy danych.
Możliwość konfiguracji portu przez plik config.properties.
