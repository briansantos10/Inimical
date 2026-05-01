import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class RestaurantApp {
    static final String DBURL = "jdbc:oracle:thin:@//rocordb01.cse.lehigh.edu:1522/cse241pdb";
    static Scanner scanner = new Scanner(System.in);
    static Console console = System.console();

    public static void main(String[] args) {
        String uid  = null;
        String pass = null;
        Connection conn = null;

        uid  = readLine("Enter Oracle user id: ");
        pass = readPassword("Enter Oracle password for " + uid + ": ");

        while (conn == null) {
            try {
                conn = DriverManager.getConnection(DBURL, uid, pass);
            } catch (SQLException se) {
                System.err.println("Login failed: " + se.getMessage());
                uid  = readLine("Enter Oracle user id: ");
                pass = readPassword("Enter Oracle password for " + uid + ": ");
            }
        }

        try (Connection c = conn) {
            c.setAutoCommit(false);
            runApp(c);
        } catch (SQLException se) {
            logError("top-level", se);
            System.err.println("An unexpected error occurred. Please restart the application.");
        }
    }

    static void runApp(Connection conn) throws SQLException {
        while (true) {
            clearScreen();
            System.out.println("\n=============================");
            System.out.println("  Inimical's Restaurant");
            System.out.println("=============================");
            System.out.println("  1. Customer");
            System.out.println("  2. Management");
            System.out.println("  3. Location Manager");
            System.out.println("  4. Exit");
            System.out.println("=============================");

            String choice = readLine("Select: ");

            switch (choice == null ? "" : choice.trim()) {
                case "1": CustomerUI.run(conn);         break;
                case "2": ManagementUI.run(conn);       break;
                case "3": LocationManagerUI.run(conn);  break;
                case "4":
                    System.out.println("Goodbye!");
                    return;
                default:
                    System.out.println("Invalid option. Please enter 1, 2, 3, or 4.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Shared I/O helpers — used by all UI classes
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

    // Clears the terminal screen using ANSI escape codes
    static void clearScreen() {
        // Maybe remove all "   Enter to continue..." to instead replace it with a timeout?
        try {
            // Pause for 1500 milliseconds (1.5 seconds)
            Thread.sleep(1500); 
        } catch (InterruptedException e) {
            // Restore interrupted state if the sleep is interrupted
            Thread.currentThread().interrupt();
        }
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // Logs an error to error.log without showing it to the user
    static void logError(String context, Exception e) {
        try (java.io.FileWriter fw = new java.io.FileWriter("error.log", true);
             java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
            pw.println("[" + new java.util.Date() + "] " + context);
            e.printStackTrace(pw);
            pw.println();
        } catch (java.io.IOException ignored) {}
    }

    // ----------------------------------------------------------------
    // Pagination helpers — used by all UI classes
    // ----------------------------------------------------------------

    // Returns a sublist for the given page (0-indexed)
    static <T> List<T> getPage(List<T> items, int page, int pageSize) {
        int from = page * pageSize;
        int to   = Math.min(from + pageSize, items.size());
        if (from >= items.size()) return new ArrayList<>();
        return items.subList(from, to);
    }

    // True if there is a next page
    static boolean hasNextPage(int page, int totalItems, int pageSize) {
        return (page + 1) * pageSize < totalItems;
    }

    // Prints the standard paging control footer
    static void printPageControls(int page, int totalItems, int pageSize) {
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        System.out.println();
        divider();
        System.out.printf("  Page %d of %d  (%d items total)%n",
            page + 1, Math.max(1, totalPages), totalItems);
        StringBuilder controls = new StringBuilder("  ");
        if (page > 0)                                controls.append("[P] Prev  ");
        if (hasNextPage(page, totalItems, pageSize)) controls.append("[N] Next  ");
        controls.append("[B] Back");
        System.out.println(controls);
        divider();
    }
}
