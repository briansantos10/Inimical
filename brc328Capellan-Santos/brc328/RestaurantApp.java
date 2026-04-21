import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Scanner;

public class RestaurantApp {
    static final String DBURL = "jdbc:oracle:thin:@//rocordb01.cse.lehigh.edu:1522/cse241pdb";
    static Scanner scanner = new Scanner(System.in);
    static Console console = System.console();

    public static void main(String[] args) {
        String uid = null;
        String pass = null;
        Connection conn = null;

        uid = readLine("Enter Oracle user id: ");
        pass = readPassword("Enter Oracle password for " + uid + ": ");

        while (conn == null) {
            try {
                conn = DriverManager.getConnection(DBURL, uid, pass);
            } catch (SQLException se) {
                System.err.println("Login failed: " + se.getMessage());
                uid = readLine("Enter Oracle user id: ");
                pass = readPassword("Enter Oracle password for " + uid + ": ");
            }
        }

        try (Connection c = conn) {
            runApp(c);
        } catch (SQLException se) {
            System.err.println(se.getMessage());
            se.printStackTrace();
        }
    }

    static void runApp(Connection conn) throws SQLException {
        while (true) {
            System.out.println("\n=============================");
            System.out.println("  Inimical's Restaurant");
            System.out.println("=============================");
            System.out.println("  1. Customer");
            System.out.println("  2. Management");
            System.out.println("  3. Location Manager");
            System.out.println("  4. Exit");
            System.out.println("=============================");

            String choice = readLine("Select: ");

            switch (choice.trim()) {
                case "1":
                    CustomerUI.run(conn);
                    break;
                case "2":
                    // ManagementUI.run(conn);
                    break;
                case "3":
                    // LocationManagerUI.run(conn);
                    break;
                case "4":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option. Please enter 1, 2, 3, or 4.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Shared I/O helpers � used by all UI classes
    // ----------------------------------------------------------------

    static String readLine(String prompt) {
        if (console != null) {
            return console.readLine(prompt);
        } else {
            System.out.print(prompt);
            return scanner.nextLine();
        }
    }

    static String readPassword(String prompt) {
        if (console != null) {
            return new String(console.readPassword(prompt));
        } else {
            System.out.print(prompt);
            return scanner.nextLine();
        }
    }

    // Reads an integer from the user, re-prompting on bad input.
    // Returns -1 if the user types "back" or "0" to cancel.
    static int readInt(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (input == null) return -1;
            input = input.trim();
            if (input.equalsIgnoreCase("back") || input.equals("0")) return -1;
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    // Reads a positive double, re-prompting on bad input.
    static double readDouble(String prompt) {
        while (true) {
            String input = readLine(prompt);
            if (input == null) continue;
            try {
                double val = Double.parseDouble(input.trim());
                if (val >= 0) return val;
                System.out.println("Please enter a positive number.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    // Prints a simple divider line
    static void divider() {
        System.out.println("-----------------------------");
    }
}
