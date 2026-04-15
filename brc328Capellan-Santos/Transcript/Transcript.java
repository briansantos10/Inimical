import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Scanner;

public class Transcript {
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
      runTranscript(c);
    } catch (SQLException se) {
      System.err.println(se.getMessage());
      se.printStackTrace();
    }
  }

  static void runTranscript(Connection conn) throws SQLException {
    // Search loop
    while (true) {
      String substring = readLine("Input name search substring: ");
      if (substring.contains("'")) {
        System.out.println("The single quote is not allowed in a search string.");
        continue;
      }
      if (searchStudents(conn, substring))
        break;
    }

    // ID input and validation
    int studentId = -1;
    System.out.println("Enter the student ID for the student whose transcript you seek.");
    while (true) {
      String input = readLine("Please enter an integer between 0 and 99999: ");
      try {
        studentId = Integer.parseInt(input.trim());
      } catch (NumberFormatException e) {
        continue;
      }
      if (studentId < 0 || studentId > 99999) {
        continue;
      }
      int result = validateStudent(conn, studentId);
      if (result == -1)
        return;
      if (result == 0)
        break;
    }
    printTranscript(conn, studentId);
  }

  // Returns true if matches found, false if empty
  static boolean searchStudents(Connection conn, String substring) throws SQLException {
    String sql = "SELECT id, name FROM student WHERE name LIKE ?";
    try (PreparedStatement pStmt = conn.prepareStatement(sql)) {
      pStmt.setString(1, "%" + substring + "%");
      ResultSet rs = pStmt.executeQuery();

      if (!rs.next()) {
        System.out.println("No matches. Try again.");
        return false;
      }

      System.out.println("Here is a list of all matching IDs");
      do {
        System.out.println(rs.getInt("id") + " " + rs.getString("name"));
      } while (rs.next());
      return true;
    }
  }

  // Returns 0 if valid, -1 if not found
  static int validateStudent(Connection conn, int studentId) throws SQLException {
    String sql = "SELECT id, name FROM student WHERE id = ?";
    try (PreparedStatement pStmt = conn.prepareStatement(sql)) {
      pStmt.setInt(1, studentId);
      ResultSet rs = pStmt.executeQuery();

      if (!rs.next()) {
        System.out.println("No student with ID " + studentId + " exists. Exiting.");
        return -1;
      }

      System.out.println("\nTranscript for student " + rs.getInt("id") + " " + rs.getString("name"));
      return 0;
    }
  }

  // Phase 4 coming next
  static void printTranscript(Connection conn, int studentId) throws SQLException {
    String sql = "SELECT YEAR, SEMESTER, DEPT_NAME, COURSE_ID, TITLE, GRADE " +
        "FROM COURSE " +
        "NATURAL JOIN SECTION " +
        "NATURAL JOIN TAKES " +
        "WHERE ID = ? " +
        "ORDER BY YEAR ASC, SEMESTER DESC, COURSE_ID ASC";

    try (PreparedStatement pStmt = conn.prepareStatement(sql)) {
      pStmt.setInt(1, studentId);
      ResultSet rs = pStmt.executeQuery();

      if (!rs.next()) {
        System.out.println("This student has taken no courses.");
        return;
      }

      do {
        System.out.println(String.format("%-6d %-8s %-20s %-6s %-35s %s",
            rs.getInt("YEAR"),
            rs.getString("SEMESTER"),
            rs.getString("DEPT_NAME"),
            rs.getString("COURSE_ID"),
            rs.getString("TITLE"),
            rs.getString("GRADE")));
      } while (rs.next());
    }
  }

  // System.console() returns null in some IDEs like vscode.
  // We fall back to Scanner in those cases so the program still runs, at the cost of password being visible on screen.
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
}
