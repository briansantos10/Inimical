import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CustomerUI {

    // ----------------------------------------------------------------
    // Entry point called from RestaurantApp
    // ----------------------------------------------------------------
    public static void run(Connection conn) throws SQLException {
        Session session = new Session();

        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n=============================");
            System.out.println("  Welcome to Inimical's");
            System.out.println("=============================");
            System.out.println("  1. Guest");
            System.out.println("  2. Login");
            System.out.println("  3. Create Account");
            System.out.println("  4. Back");
            System.out.println("=============================");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim()) {
                case "1":
                    handleGuest(conn, session);
                    return;
                case "2":
                    if (handleLogin(conn, session)) {
                        customerHome(conn, session);
                    }
                    return;
                case "3":
                    if (handleCreateAccount(conn, session)) {
                        customerHome(conn, session);
                    }
                    return;
                case "4":
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Guest — no account, limited access
    // ----------------------------------------------------------------
    static void handleGuest(Connection conn, Session session) throws SQLException {
        System.out.println("\nContinuing as guest.");
        placeOrder(conn, session);
    }

    // ----------------------------------------------------------------
    // Login via email
    // ----------------------------------------------------------------
    static boolean handleLogin(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Login ---");

        String email = RestaurantApp.readLine("Enter email: ");
        if (email == null || email.trim().isEmpty()) {
            System.out.println("Login cancelled.");
            return false;
        }

        String sql = "SELECT acc_id, f_name, l_name FROM Account WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim());
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                System.out.println("No account found with that email.");
                return false;
            }

            session.accountId = rs.getInt("acc_id");
            session.firstName = rs.getString("f_name");
            session.lastName  = rs.getString("l_name");

            System.out.println("Welcome back, " + session.firstName + "!");
            return true;
        }
    }

    // ----------------------------------------------------------------
    // Create new account
    // ----------------------------------------------------------------
    static boolean handleCreateAccount(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Create Account ---");

        String firstName = RestaurantApp.readLine("First name: ");
        if (firstName == null || firstName.trim().isEmpty()) {
            System.out.println("Account creation cancelled.");
            return false;
        }

        String middleName = RestaurantApp.readLine("Middle name (press Enter to skip): ");

        String lastName = RestaurantApp.readLine("Last name: ");
        if (lastName == null || lastName.trim().isEmpty()) {
            System.out.println("Account creation cancelled.");
            return false;
        }

        String email = RestaurantApp.readLine("Email: ");
        if (email == null || email.trim().isEmpty()) {
            System.out.println("Account creation cancelled.");
            return false;
        }

        String checkSql = "SELECT acc_id FROM Account WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, email.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("An account with that email already exists.");
                return false;
            }
        }

        String phone = RestaurantApp.readLine("Phone number: ");
        if (phone == null || phone.trim().isEmpty()) {
            System.out.println("Account creation cancelled.");
            return false;
        }

        String insertSql = "INSERT INTO Account (acc_id, f_name, m_name, l_name, email, phone, points) " +
                           "VALUES (acct_seq.NEXTVAL, ?, ?, ?, ?, ?, 0)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql, new String[]{"acc_id"})) {
            ps.setString(1, firstName.trim());
            ps.setString(2, (middleName == null || middleName.trim().isEmpty()) ? null : middleName.trim());
            ps.setString(3, lastName.trim());
            ps.setString(4, email.trim());
            ps.setString(5, phone.trim());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                session.accountId = keys.getInt(1);
            } else {
                String fetchSql = "SELECT acc_id FROM Account WHERE email = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(fetchSql)) {
                    ps2.setString(1, email.trim());
                    ResultSet rs2 = ps2.executeQuery();
                    if (rs2.next()) session.accountId = rs2.getInt("acc_id");
                }
            }

            session.firstName = firstName.trim();
            session.lastName  = lastName.trim();

            conn.commit();
            System.out.println("\nAccount created! Welcome, " + session.firstName + "!");
            return true;
        }
    }

    // ----------------------------------------------------------------
    // Customer Home — main hub after login
    // ----------------------------------------------------------------
    static void customerHome(Connection conn, Session session) throws SQLException {
        while (true) {
            RestaurantApp.clearScreen();

            int points = 0;
            String ptsSql = "SELECT points FROM Account WHERE acc_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(ptsSql)) {
                ps.setInt(1, session.accountId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) points = rs.getInt("points");
            }

            System.out.println("\n=============================");
            System.out.println("  Hello, " + session.firstName + "!");
            System.out.println("  Points: " + points);
            System.out.println("=============================");
            System.out.println("  1. Place Order");
            System.out.println("  2. Order History");
            System.out.println("  3. Manage Payment Methods");
            System.out.println("  4. Build Custom Item");
            System.out.println("  5. Logout");
            System.out.println("=============================");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim()) {
                case "1":
                    placeOrder(conn, session);
                    break;
                case "2":
                    orderHistory(conn, session);
                    break;
                case "3":
                    managePayments(conn, session);
                    break;
                case "4":
                    buildCustomItem(conn, session);
                    break;
                case "5":
                    session.logout();
                    System.out.println("Logged out.");
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Place Order -- order type -> location -> browse -> cart -> checkout
    // ----------------------------------------------------------------
    static void placeOrder(Connection conn, Session session) throws SQLException {
        // Guests are always in-person; logged-in users can choose
        char ordTyp = 'I';
        if (session.isLoggedIn()) {
            System.out.println("\n--- Order Type ---");
            System.out.println("  1. In-person");
            System.out.println("  2. Online");
            System.out.println("  0. Cancel");
            String typeChoice = RestaurantApp.readLine("Select: ");
            if (typeChoice == null || typeChoice.trim().equals("0") || typeChoice.trim().isEmpty()) return;
            switch (typeChoice.trim()) {
                case "1": ordTyp = 'I'; break;
                case "2": ordTyp = 'O'; break;
                default:
                    System.out.println("Invalid option.");
                    return;
            }
        }
        session.ordTyp = ordTyp;

        if (!selectLocation(conn, session)) return;
        browseItems(conn, session);
        // checkout is now triggered from within browseItems via the Checkout option
        // cart is cleared by checkout on success, or by user choosing Cancel Order
    }

    // ----------------------------------------------------------------
    // Select Location
    // ----------------------------------------------------------------
    static boolean selectLocation(Connection conn, Session session) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Select a Location ---");

        String sql = "SELECT loc_id, city, state, stname, st_num FROM Location ORDER BY loc_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %-25s %s%n", "ID", "City", "Address");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("  %-6d %-25s %s %s%n",
                    rs.getInt("loc_id"),
                    rs.getString("city") + ", " + rs.getString("state"),
                    rs.getString("st_num"),
                    rs.getString("stname"));
            }

            if (!any) {
                System.out.println("No locations available.");
                return false;
            }
        }

        while (true) {
            int locId = RestaurantApp.readInt("Enter location ID (0 to cancel): ");
            if (locId == -1) return false;

            String checkSql = "SELECT loc_id, city FROM Location WHERE loc_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, locId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    session.locId   = rs.getInt("loc_id");
                    session.locCity = rs.getString("city");
                    System.out.println("Location set to: " + session.locCity);
                    return true;
                } else {
                    System.out.println("Invalid location ID. Try again.");
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Browse Items
    // ----------------------------------------------------------------
    static void browseItems(Connection conn, Session session) throws SQLException {
        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Menu ---");
            System.out.println("  1. View All Items");
            System.out.println("  2. Filter by Meal Type");
            if (session.isLoggedIn()) {
                System.out.println("  3. View Custom Items");
            }
            System.out.println("  V. View / Manage Cart");
            System.out.println("  C. Checkout");
            System.out.println("  X. Cancel Order");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim().toUpperCase()) {
                case "1":
                    RestaurantApp.clearScreen();
                    displayItems(conn, session, null, false);
                    promptAddToCart(conn, session);
                    break;
                case "2":
                    filterByMealType(conn, session);
                    break;
                case "3":
                    if (session.isLoggedIn()) {
                        RestaurantApp.clearScreen();
                        displayItems(conn, session, null, true);
                        promptAddToCart(conn, session);
                    } else {
                        System.out.println("Invalid option.");
                    }
                    break;
                case "V":
                    manageCart(conn, session);
                    break;
                case "C":
                    if (session.cartIsEmpty()) {
                        System.out.println("\nNo items in cart. Add something first.");
                        RestaurantApp.readLine("Press Enter to continue...");
                        break;
                    }
                    checkout(conn, session);
                    if (session.cartIsEmpty()) return; // order committed -- go home
                    break; // cancelled at confirm/payment -- stay in browse
                case "X":
                    if (session.cartIsEmpty()) {
                        System.out.println("\nNo active order to cancel. Heading back.");
                        RestaurantApp.readLine("Press Enter to continue...");
                        return;
                    }
                    String xConfirm = RestaurantApp.readLine("Cancel order and clear cart? (y/n): ");
                    if (xConfirm != null && xConfirm.trim().equalsIgnoreCase("y")) {
                        session.clearCart();
                        System.out.println("Order cancelled. Cart cleared.");
                        RestaurantApp.readLine("Press Enter to continue...");
                        return;
                    }
                    break; // user said n -- stay in browse
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Manage Cart — view, remove items, or clear
    // ----------------------------------------------------------------
    static void manageCart(Connection conn, Session session) throws SQLException {
        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Your Cart ---");

            if (session.cartIsEmpty()) {
                System.out.println("  (cart is empty)");
                System.out.println("\n  Press Enter to go back.");
                RestaurantApp.readLine("");
                return;
            }

            // Print numbered cart with per-line totals
            System.out.printf("  %-4s %-6s %-35s %-5s %10s%n", "#", "ID", "Item", "Qty", "Total");
            RestaurantApp.divider();
            List<Session.CartItem> items = session.cart;
            double cartTotal = 0;
            for (int i = 0; i < items.size(); i++) {
                Session.CartItem ci = items.get(i);
                double unitPrice = getItemPrice(conn, ci.itemId, session.locId);
                double lineTotal = unitPrice * ci.qty;
                cartTotal += lineTotal;
                String lineTotalStr = unitPrice > 0 ? String.format("$%.2f", lineTotal) : "varies";
                System.out.printf("  %-4d %-6d %-35s %-5d %10s%n",
                    i + 1, ci.itemId, ci.itemName, ci.qty, lineTotalStr);
            }
            RestaurantApp.divider();
            System.out.printf("  %-52s %10s%n", "Estimated subtotal (before tax):", String.format("$%.2f", cartTotal));

            System.out.println("\n  R <#>  - Remove item (e.g. R 2)");
            System.out.println("  C      - Clear entire cart");
            System.out.println("  B      - Back to menu");

            String input = RestaurantApp.readLine("> ");
            if (input == null || input.trim().isEmpty()) continue;

            String trimmed = input.trim().toUpperCase();

            if (trimmed.equals("B")) {
                return;
            } else if (trimmed.equals("C")) {
                String confirm = RestaurantApp.readLine("Clear all items from cart? (y/n): ");
                if (confirm != null && confirm.trim().equalsIgnoreCase("y")) {
                    session.clearCart();
                    System.out.println("Cart cleared.");
                    return;
                }
            } else if (trimmed.startsWith("R ")) {
                String numPart = trimmed.substring(2).trim();
                try {
                    int idx = Integer.parseInt(numPart) - 1;
                    if (idx < 0 || idx >= items.size()) {
                        System.out.println("Invalid item number.");
                    } else {
                        String removed = items.get(idx).itemName;
                        items.remove(idx);
                        System.out.println("Removed: " + removed);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Format: R <number>  e.g. R 2");
                }
            } else {
                System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Display items — optionally filtered by meal type or custom only
    // ----------------------------------------------------------------
    static void displayItems(Connection conn, Session session, String mealType, boolean customOnly) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT m.itmid, m.name, m.itmtyp, " +
            "COALESCE(ml.loc_pr, m.nat_pr) AS price " +
            "FROM MenuItem m " +
            "LEFT JOIN MILoc ml ON m.itmid = ml.itmid AND ml.loc_id = ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(session.locId);

        if (mealType != null) {
            sql.append("JOIN MIMeal mm ON m.itmid = mm.itmid AND mm.mltype = ? ");
            params.add(mealType);
        }

        if (customOnly) {
            sql.append("WHERE m.itmtyp = 'C' ");
        }

        sql.append("ORDER BY m.itmtyp ASC, m.itmid ASC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                else ps.setString(i + 1, (String) p);
            }

            ResultSet rs = ps.executeQuery();

            System.out.printf("%n  %-6s %-35s %-6s %s%n", "ID", "Name", "Type", "Price");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                String type  = rs.getString("itmtyp").equals("S") ? "Std" : "Custom";
                double price = rs.getDouble("price");
                String priceStr = (rs.wasNull() || price == 0) ? "varies" : String.format("$%.2f", price);

                System.out.printf("  %-6d %-35s %-6s %s%n",
                    rs.getInt("itmid"),
                    rs.getString("name"),
                    type,
                    priceStr);
            }

            if (!any) System.out.println("  No items found.");
        }
    }

    // ----------------------------------------------------------------
    // Filter by meal type submenu
    // ----------------------------------------------------------------
    static void filterByMealType(Connection conn, Session session) throws SQLException {
        System.out.println("\n  Meal type: 1. Lunch  2. Dinner  3. Dessert");
        String choice = RestaurantApp.readLine("Select: ");
        if (choice == null) return;

        String mealType;
        switch (choice.trim()) {
            case "1": mealType = "lunch";   break;
            case "2": mealType = "dinner";  break;
            case "3": mealType = "dessert"; break;
            default:
                System.out.println("Invalid option.");
                return;
        }

        displayItems(conn, session, mealType, false);
        promptAddToCart(conn, session);
    }

    // ----------------------------------------------------------------
    // Prompt to add item to cart
    // ----------------------------------------------------------------
    static void promptAddToCart(Connection conn, Session session) throws SQLException {
        System.out.println("\nEnter item ID and quantity to add (e.g. 1001 2), or press Enter to go back:");

        while (true) {
            String input = RestaurantApp.readLine("> ");
            if (input == null || input.trim().isEmpty()) return;

            String[] parts = input.trim().split("\\s+");
            if (parts.length != 2) {
                System.out.println("Format: <item_id> <quantity>");
                continue;
            }

            int itemId, qty;
            try {
                itemId = Integer.parseInt(parts[0]);
                qty    = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                System.out.println("Please enter valid numbers.");
                continue;
            }

            if (qty <= 0) {
                System.out.println("Quantity must be at least 1.");
                continue;
            }

            String checkSql = "SELECT name FROM MenuItem WHERE itmid = ?";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setInt(1, itemId);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) {
                    System.out.println("Item ID not found.");
                    continue;
                }
                String itemName = rs.getString("name");
                session.addToCart(itemId, itemName, qty);
                System.out.println("Added " + qty + "x " + itemName + " to cart.");
                System.out.println("Continue adding items or press Enter to go back.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Checkout
    // ----------------------------------------------------------------
    static void checkout(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Checkout ---");

        System.out.println("\nYour order at " + session.locCity + ":");
        System.out.printf("  %-6s %-35s %-5s %10s%n", "ID", "Item", "Qty", "Price");
        RestaurantApp.divider();

        double subtotal = 0;
        for (Session.CartItem ci : session.cart) {
            double unitPrice = getItemPrice(conn, ci.itemId, session.locId);
            double lineTotal = unitPrice * ci.qty;
            subtotal += lineTotal;

            String priceStr = unitPrice > 0
                ? String.format("$%.2f", lineTotal)
                : "n/a";

            System.out.printf("  %-6d %-35s %-5d %10s%n",
                ci.itemId, ci.itemName, ci.qty, priceStr);
        }

        double taxRate = getLocationTaxRate(conn, session.locId);
        double tax     = subtotal * taxRate;
        double total   = subtotal + tax;

        RestaurantApp.divider();
        System.out.printf("  %-42s %10s%n", "Subtotal:", String.format("$%.2f", subtotal));
        System.out.printf("  %-42s %10s%n", String.format("Tax (%.2f%%):", taxRate * 100), String.format("$%.2f", tax));
        System.out.printf("  %-42s %10s%n", "Total:", String.format("$%.2f", total));

        String confirm = RestaurantApp.readLine("\nConfirm order? (y/n): ");
        if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("Order cancelled.");
            return;
        }

        String ccNum = handlePayment(conn, session);
        if (ccNum == null) {
            System.out.println("Order cancelled.");
            return;
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());

        String insertOrder =
            "INSERT INTO Orders (ord_id, placed, pickup, ordtyp, status, acc_id, loc_id, cc_num) " +
            "VALUES (ord_seq.NEXTVAL, ?, NULL, ?, 'pending', ?, ?, ?)";

        int newOrdId = -1;
        try (PreparedStatement ps = conn.prepareStatement(insertOrder, new String[]{"ord_id"})) {
            ps.setTimestamp(1, now);
            ps.setString(2, String.valueOf(session.ordTyp));
            if (session.isLoggedIn()) ps.setInt(3, session.accountId);
            else                      ps.setNull(3, Types.INTEGER);
            ps.setInt(4, session.locId);
            ps.setString(5, ccNum);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) newOrdId = keys.getInt(1);

            if (newOrdId == -1) {
                String fetchOrd = "SELECT MAX(ord_id) FROM Orders";
                try (PreparedStatement ps2 = conn.prepareStatement(fetchOrd)) {
                    ResultSet rs2 = ps2.executeQuery();
                    if (rs2.next()) newOrdId = rs2.getInt(1);
                }
            }
        }

        String insertItem = "INSERT INTO OrdMItm (ord_id, itmid, qty) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertItem)) {
            for (Session.CartItem ci : session.cart) {
                ps.setInt(1, newOrdId);
                ps.setInt(2, ci.itemId);
                ps.setInt(3, ci.qty);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        String complete = "UPDATE Orders SET status = 'completed' WHERE ord_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(complete)) {
            ps.setInt(1, newOrdId);
            ps.executeUpdate();
        }

        conn.commit();

        printReceipt(newOrdId, session, subtotal, tax, total, ccNum);
        session.clearCart();

        RestaurantApp.readLine("\nPress Enter to continue...");
    }

    // ----------------------------------------------------------------
    // Handle payment — returns cc_num to use
    // ----------------------------------------------------------------
    static String handlePayment(Connection conn, Session session) throws SQLException {
        if (session.isLoggedIn()) {
            String sql = "SELECT cc_num, type, expiry FROM CCard WHERE acc_id = ?";
            List<String[]> cards = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, session.accountId);
                ResultSet rs = ps.executeQuery();

                System.out.println("\n--- Payment ---");
                int idx = 1;
                while (rs.next()) {
                    String num    = rs.getString("cc_num");
                    String type   = rs.getString("type");
                    String expiry = rs.getString("expiry");
                    System.out.printf("  %d. %s ending in %s  exp %s%n",
                        idx, type, num.substring(num.length() - 4), expiry);
                    cards.add(new String[]{num});
                    idx++;
                }
            }

            if (!cards.isEmpty()) {
                System.out.println("  " + (cards.size() + 1) + ". Enter a new card");
                System.out.println("  0. Cancel");

                while (true) {
                    int pick = RestaurantApp.readInt("Select payment: ");
                    if (pick == -1) return null;
                    if (pick >= 1 && pick <= cards.size()) {
                        return cards.get(pick - 1)[0];
                    } else if (pick == cards.size() + 1) {
                        return enterNewCard(conn, session);
                    } else {
                        System.out.println("Invalid selection.");
                    }
                }
            } else {
                System.out.println("No stored cards. Please enter a card.");
                return enterNewCard(conn, session);
            }
        } else {
            return enterNewCard(conn, session);
        }
    }

    // ----------------------------------------------------------------
    // Enter a new credit card
    // ----------------------------------------------------------------
    static String enterNewCard(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Enter Card Details ---");
        System.out.println("  Format: xxxx-xxxx-xxxx-xxxx  or  xxxxxxxxxxxxxxxx");

        String ccNum = null;
        while (ccNum == null) {
            String input = RestaurantApp.readLine("Card number: ");
            if (input == null || input.trim().isEmpty()) return null;
            String stripped = input.trim().replaceAll("[\\s\\-]", "");
            if (stripped.length() != 16 || !stripped.matches("\\d+")) {
                System.out.println("Invalid card number. Must be 16 digits.");
                continue;
            }
            ccNum = stripped;
        }

        String checkSql = "SELECT cc_num FROM CCard WHERE cc_num = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, ccNum);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return ccNum;
        }

        String expiry = null;
        while (expiry == null) {
            String input = RestaurantApp.readLine("Expiry (MM/YY): ");
            if (input == null || input.trim().isEmpty()) return null;
            input = input.trim();
            if (!input.matches("(0[1-9]|1[0-2])/\\d{2}")) {
                System.out.println("Invalid format. Use MM/YY (e.g. 05/28).");
                continue;
            }
            expiry = input;
        }

        Integer accId = null;
        if (session.isLoggedIn()) {
            String save = RestaurantApp.readLine("Save this card to your account? (y/n): ");
            if (save != null && save.trim().equalsIgnoreCase("y")) {
                accId = session.accountId;
            }
        }

        String insertSql = "INSERT INTO CCard (cc_num, type, cvc, expiry, acc_id) VALUES (?, 'one-time', NULL, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, ccNum);
            ps.setString(2, expiry);
            if (accId != null) ps.setInt(3, accId);
            else               ps.setNull(3, Types.INTEGER);
            ps.executeUpdate();
        }

        return ccNum;
    }

    // ----------------------------------------------------------------
    // Get effective item price (local override or national, recursive for custom)
    // ----------------------------------------------------------------
    static double getItemPrice(Connection conn, int itemId, int locId) throws SQLException {
        String typeSql = "SELECT itmtyp, nat_pr FROM MenuItem WHERE itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return 0;

            String type = rs.getString("itmtyp");

            if (type.equals("S")) {
                String locSql = "SELECT loc_pr FROM MILoc WHERE itmid = ? AND loc_id = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(locSql)) {
                    ps2.setInt(1, itemId);
                    ps2.setInt(2, locId);
                    ResultSet rs2 = ps2.executeQuery();
                    if (rs2.next()) return rs2.getDouble("loc_pr");
                }
                return rs.getDouble("nat_pr");
            } else {
                return computeCustomPrice(conn, itemId, locId);
            }
        }
    }

    // ----------------------------------------------------------------
    // Recursively compute custom item price
    // ----------------------------------------------------------------
    static double computeCustomPrice(Connection conn, int itemId, int locId) throws SQLException {
        double total = 0;

        String invSql = "SELECT i.basect, c.qty FROM MIComp c JOIN InvItm i ON c.inv_id = i.inv_id WHERE c.itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                total += rs.getDouble("basect") * rs.getDouble("qty");
            }
        }

        String contSql = "SELECT compid, qty FROM MICont WHERE contid = ?";
        try (PreparedStatement ps = conn.prepareStatement(contSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int childId  = rs.getInt("compid");
                int childQty = rs.getInt("qty");
                total += getItemPrice(conn, childId, locId) * childQty;
            }
        }

        return total;
    }

    // ----------------------------------------------------------------
    // Get location tax rate
    // ----------------------------------------------------------------
    static double getLocationTaxRate(Connection conn, int locId) throws SQLException {
        String sql = "SELECT tax_rt FROM Location WHERE loc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("tax_rt");
        }
        return 0;
    }

    // ----------------------------------------------------------------
    // Print receipt
    // ----------------------------------------------------------------
    static void printReceipt(int ordId, Session session,
                              double subtotal, double tax, double total, String ccNum) {
        System.out.println("\n=============================");
        System.out.println("       ORDER CONFIRMED");
        System.out.println("=============================");
        System.out.println("  Order #:   " + ordId);
        System.out.println("  Type:      " + (session.ordTyp == 'O' ? "Online" : "In-person"));
        System.out.println("  Location:  " + session.locCity);
        System.out.println("  Card:      **** **** **** " + ccNum.substring(Math.max(0, ccNum.length() - 4)));
        RestaurantApp.divider();
        System.out.printf("  %-30s $%.2f%n", "Subtotal:", subtotal);
        System.out.printf("  %-30s $%.2f%n", "Tax:", tax);
        System.out.printf("  %-30s $%.2f%n", "Total:", total);
        RestaurantApp.divider();
        if (session.isLoggedIn()) {
            System.out.println("  Loyalty points earned: 10");
        }
        System.out.println("  Thank you for your order!");
        System.out.println("=============================");
    }

    // ----------------------------------------------------------------
    // Order History
    // ----------------------------------------------------------------
    static void orderHistory(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Order History ---");

        String sql =
            "SELECT o.ord_id, o.placed, o.ordtyp, l.city " +
            "FROM Orders o JOIN Location l ON o.loc_id = l.loc_id " +
            "WHERE o.acc_id = ? ORDER BY o.placed DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, session.accountId);
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-10s %-22s %-12s %s%n",
                "Order #", "Placed", "Type", "Location");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                String type = rs.getString("ordtyp").equals("O") ? "Online" : "In-person";
                System.out.printf("  %-10d %-22s %-12s %s%n",
                    rs.getInt("ord_id"),
                    rs.getTimestamp("placed").toString().substring(0, 16),
                    type,
                    rs.getString("city"));
            }

            if (!any) System.out.println("  No orders found.");
        }

        System.out.println("\nEnter an order number to view its items, or press Enter to go back:");
        String input = RestaurantApp.readLine("> ");
        if (input == null || input.trim().isEmpty()) return;

        try {
            int ordId = Integer.parseInt(input.trim());
            showOrderItems(conn, ordId, session.accountId);
        } catch (NumberFormatException e) {
            System.out.println("Invalid order number.");
        }
    }

    // ----------------------------------------------------------------
    // Show items for a specific order
    // ----------------------------------------------------------------
    static void showOrderItems(Connection conn, int ordId, int accId) throws SQLException {
        String checkSql = "SELECT ord_id FROM Orders WHERE ord_id = ? AND acc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setInt(1, ordId);
            ps.setInt(2, accId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                System.out.println("Order not found.");
                return;
            }
        }

        String sql =
            "SELECT m.name, oi.qty " +
            "FROM OrdMItm oi JOIN MenuItem m ON oi.itmid = m.itmid " +
            "WHERE oi.ord_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ordId);
            ResultSet rs = ps.executeQuery();

            System.out.println("\nItems in Order #" + ordId + ":");
            System.out.printf("  %-35s %s%n", "Item", "Qty");
            RestaurantApp.divider();

            while (rs.next()) {
                System.out.printf("  %-35s %d%n",
                    rs.getString("name"), rs.getInt("qty"));
            }
        }
    }

    // ----------------------------------------------------------------
    // Manage Payment Methods
    // ----------------------------------------------------------------
    static void managePayments(Connection conn, Session session) throws SQLException {
        while (true) {
            System.out.println("\n--- Payment Methods ---");

            String sql = "SELECT cc_num, type, expiry FROM CCard WHERE acc_id = ?";
            List<String> cardNums = new ArrayList<>();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, session.accountId);
                ResultSet rs = ps.executeQuery();

                int idx = 1;
                while (rs.next()) {
                    String num    = rs.getString("cc_num");
                    String type   = rs.getString("type");
                    String expiry = rs.getString("expiry");
                    System.out.printf("  %d. %s ending in %s  exp %s%n",
                        idx, type, num.substring(num.length() - 4), expiry);
                    cardNums.add(num);
                    idx++;
                }

                if (cardNums.isEmpty()) System.out.println("  No stored cards.");
            }

            System.out.println("\n  A. Add a card");
            System.out.println("  R. Remove a card");
            System.out.println("  B. Back");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim().toUpperCase()) {
                case "A":
                    enterNewCard(conn, session);
                    conn.commit();
                    break;
                case "R":
                    if (cardNums.isEmpty()) {
                        System.out.println("No cards to remove.");
                        break;
                    }
                    int pick = RestaurantApp.readInt("Enter card number to remove (1-" + cardNums.size() + "): ");
                    if (pick >= 1 && pick <= cardNums.size()) {
                        removeCard(conn, cardNums.get(pick - 1));
                        conn.commit();
                    } else {
                        System.out.println("Invalid selection.");
                    }
                    break;
                case "B":
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Remove a stored card
    // ----------------------------------------------------------------
    static void removeCard(Connection conn, String ccNum) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM Orders WHERE cc_num = ? AND status = 'pending'";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, ccNum);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("Cannot remove card — it is used in a pending order.");
                return;
            }
        }

        String deleteSql = "DELETE FROM CCard WHERE cc_num = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setString(1, ccNum);
            ps.executeUpdate();
            System.out.println("Card removed.");
        }
    }

    // ================================================================
    // BUILD CUSTOM ITEM
    // ================================================================

    static void buildCustomItem(Connection conn, Session session) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Build Custom Item ---");

        // Show user's own custom items
        String myItemsSql =
            "SELECT itmid, name, crdate FROM MenuItem WHERE cr_acc = ? ORDER BY crdate DESC";
        try (PreparedStatement ps = conn.prepareStatement(myItemsSql)) {
            ps.setInt(1, session.accountId);
            ResultSet rs = ps.executeQuery();

            boolean hasOwn = false;
            while (rs.next()) {
                if (!hasOwn) {
                    System.out.println("  Your Custom Items:");
                    System.out.printf("  %-6s %-35s %s%n", "ID", "Name", "Created");
                    RestaurantApp.divider();
                    hasOwn = true;
                }
                System.out.printf("  %-6d %-35s %s%n",
                    rs.getInt("itmid"),
                    rs.getString("name"),
                    rs.getDate("crdate").toString());
            }
            if (!hasOwn) System.out.println("  You have not created any custom items yet.");
        }

        // Show all other custom items
        System.out.println("\n  All Custom Items:");
        String allSql =
            "SELECT m.itmid, m.name, a.f_name, a.l_name FROM MenuItem m " +
            "LEFT JOIN Account a ON m.cr_acc = a.acc_id " +
            "WHERE m.itmtyp = 'C' AND (m.cr_acc != ? OR m.cr_acc IS NULL) ORDER BY m.itmid";
        try (PreparedStatement ps = conn.prepareStatement(allSql)) {
            ps.setInt(1, session.accountId);
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %-35s %s%n", "ID", "Name", "Created by");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                String creator = rs.getString("f_name") != null
                    ? rs.getString("f_name") + " " + rs.getString("l_name")
                    : "unknown";
                System.out.printf("  %-6d %-35s %s%n",
                    rs.getInt("itmid"), rs.getString("name"), creator);
            }
            if (!any) System.out.println("  No other custom items exist.");
        }

        System.out.println();

        // ---- STEP 1: Choose starting point ----
        System.out.println("  How would you like to start?");
        System.out.println("  1. Start from a Standard Item");
        System.out.println("  2. Start from a Custom Item");
        System.out.println("  3. Start from scratch");
        System.out.println("  0. Cancel");

        String startChoice = RestaurantApp.readLine("Select: ");
        if (startChoice == null || startChoice.trim().equals("0") || startChoice.trim().isEmpty()) return;

        int baseItemId = -1;   // -1 means scratch

        switch (startChoice.trim()) {
            case "1":
                baseItemId = pickBaseItem(conn, "S");
                if (baseItemId == -1) return;
                break;
            case "2":
                baseItemId = pickBaseItem(conn, "C");
                if (baseItemId == -1) return;
                break;
            case "3":
                // scratch — no base
                break;
            default:
                System.out.println("Invalid option.");
                return;
        }

        // If a base was chosen, clear and show its ingredients as context
        if (baseItemId != -1) {
            RestaurantApp.clearScreen();
            showItemIngredients(conn, baseItemId);
            System.out.println("\n  Your new item will be built on top of this.");
        }

        // ---- STEP 2: Name the new item ----
        String name = null;
        while (name == null) {
            String input = RestaurantApp.readLine("\nNew item name (or press Enter to cancel): ");
            if (input == null || input.trim().isEmpty()) return;
            input = input.trim();

            String dupeSql = "SELECT itmid FROM MenuItem WHERE LOWER(name) = LOWER(?)";
            try (PreparedStatement ps = conn.prepareStatement(dupeSql)) {
                ps.setString(1, input);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    System.out.println("An item with that name already exists. Choose a different name.");
                    continue;
                }
            }
            name = input;
        }

        // ---- STEP 3: Insert the item shell ----
        String insertSql =
            "INSERT INTO MenuItem (itmid, name, itmtyp, nat_pr, cr_acc, crdate) " +
            "VALUES (item_seq.NEXTVAL, ?, 'C', NULL, ?, SYSDATE)";

        int newItemId = -1;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, new String[]{"itmid"})) {
            ps.setString(1, name);
            ps.setInt(2, session.accountId);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                newItemId = keys.getInt(1);
            } else {
                String fetchSql = "SELECT item_seq.CURRVAL FROM dual";
                try (PreparedStatement ps2 = conn.prepareStatement(fetchSql)) {
                    ResultSet rs2 = ps2.executeQuery();
                    if (rs2.next()) newItemId = rs2.getInt(1);
                }
            }
        }

        if (newItemId == -1) {
            System.out.println("Failed to create item.");
            conn.rollback();
            return;
        }

        // ---- STEP 4: Link the base item if chosen ----
        int baseContCount = 0;
        if (baseItemId != -1) {
            String linkSql = "INSERT INTO MICont (contid, compid, qty) VALUES (?, ?, 1)";
            try (PreparedStatement ps = conn.prepareStatement(linkSql)) {
                ps.setInt(1, newItemId);
                ps.setInt(2, baseItemId);
                ps.executeUpdate();
                baseContCount = 1;
            } catch (SQLException se) {
                RestaurantApp.logError("buildCustomItem-linkBase", se);
                System.out.println("Warning: could not link base item. Continuing.");
            }
        }

        // ---- STEP 5: Add inventory ingredients ----
        RestaurantApp.clearScreen();
        System.out.println("--- Adding Ingredients: " + name + " ---");
        if (baseItemId != -1) {
            showItemIngredients(conn, baseItemId);
            System.out.println();
        }
        int invCount = addInventoryComponents(conn, newItemId);

        // Always require at least one inventory ingredient.
        // A base item alone is not enough — that would just be a renamed copy.
        if (invCount == 0) {
            System.out.println("\nItem must have at least one ingredient. Cancelling.");
            conn.rollback();
            return;
        }

        conn.commit();
        printCustomItemSummary(conn, newItemId, name, session);
        RestaurantApp.readLine("\nPress Enter to continue...");
    }

    // ----------------------------------------------------------------
    // Pick a base item of a given type (S or C), returns itmid or -1
    // ----------------------------------------------------------------
    static int pickBaseItem(Connection conn, String itmtyp) throws SQLException {
        String label = itmtyp.equals("S") ? "Standard" : "Custom";
        System.out.println("\n  Available " + label + " Items:");

        String sql = "SELECT itmid, name FROM MenuItem WHERE itmtyp = ? ORDER BY itmid";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itmtyp);
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %s%n", "ID", "Name");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.printf("  %-6d %s%n", rs.getInt("itmid"), rs.getString("name"));
            }
            if (!any) {
                System.out.println("  No " + label.toLowerCase() + " items available.");
                return -1;
            }
        }

        while (true) {
            int id = RestaurantApp.readInt("Enter item ID to use as base (0 to cancel): ");
            if (id == -1) return -1;

            String check = "SELECT itmid FROM MenuItem WHERE itmid = ? AND itmtyp = ?";
            try (PreparedStatement ps = conn.prepareStatement(check)) {
                ps.setInt(1, id);
                ps.setString(2, itmtyp);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return id;
                System.out.println("Invalid ID. Please choose from the list above.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Show an item's ingredients (read-only context display)
    // ----------------------------------------------------------------
    static void showItemIngredients(Connection conn, int itemId) throws SQLException {
        String nameSql = "SELECT name FROM MenuItem WHERE itmid = ?";
        String itemName = "";
        try (PreparedStatement ps = conn.prepareStatement(nameSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) itemName = rs.getString("name");
        }

        System.out.println("  Base item: " + itemName + " (ID " + itemId + ")");
        System.out.println("  -- What's already in it --");

        // Inventory components
        String invSql =
            "SELECT i.name, c.qty, i.unit FROM MIComp c " +
            "JOIN InvItm i ON c.inv_id = i.inv_id WHERE c.itmid = ?";
        boolean hasAny = false;
        try (PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                hasAny = true;
                System.out.printf("    + %.2f %s of %s%n",
                    rs.getDouble("qty"), rs.getString("unit"), rs.getString("name"));
            }
        }

        // Contained menu items
        String contSql =
            "SELECT m.name, c.qty FROM MICont c " +
            "JOIN MenuItem m ON c.compid = m.itmid WHERE c.contid = ?";
        try (PreparedStatement ps = conn.prepareStatement(contSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                hasAny = true;
                System.out.printf("    + %dx %s%n", rs.getInt("qty"), rs.getString("name"));
            }
        }

        if (!hasAny) System.out.println("    (no components listed)");
    }

    // ----------------------------------------------------------------
    // Add inventory components — returns count added
    // ----------------------------------------------------------------
    static int addInventoryComponents(Connection conn, int itemId) throws SQLException {
        System.out.println("\n--- Add Inventory Ingredients ---");
        System.out.println("Available inventory items:");

        java.util.Map<Integer, String> unitMap = new java.util.LinkedHashMap<>();
        java.util.Map<Integer, String> nameMap = new java.util.LinkedHashMap<>();

        String listSql = "SELECT inv_id, name, unit FROM InvItm ORDER BY inv_id";
        try (PreparedStatement ps = conn.prepareStatement(listSql)) {
            ResultSet rs = ps.executeQuery();
            System.out.printf("  %-6s %-30s %s%n", "ID", "Name", "Unit");
            RestaurantApp.divider();
            while (rs.next()) {
                int id    = rs.getInt("inv_id");
                String nm = rs.getString("name");
                String ut = rs.getString("unit");
                unitMap.put(id, ut);
                nameMap.put(id, nm);
                System.out.printf("  %-6d %-30s %s%n", id, nm, ut);
            }
        }

        System.out.println("\nEnter ingredient ID to add it, or press Enter when done.");

        String insertSql = "INSERT INTO MIComp (itmid, inv_id, qty) VALUES (?, ?, ?)";
        int added = 0;

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            while (true) {
                String idInput = RestaurantApp.readLine("Ingredient ID (or Enter to finish): ");
                if (idInput == null || idInput.trim().isEmpty()) break;

                int invId;
                try {
                    invId = Integer.parseInt(idInput.trim());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid ID.");
                    continue;
                }

                if (!unitMap.containsKey(invId)) {
                    System.out.println("Inventory item not found.");
                    continue;
                }

                String unit     = unitMap.get(invId);
                String itemName = nameMap.get(invId);
                boolean countable = unit.equals("piece") || unit.equals("slice") ||
                                    unit.equals("strip") || unit.equals("leaf") ||
                                    unit.equals("scoop");

                double qty = -1;
                while (qty < 0) {
                    String qtyPrompt = countable
                        ? "How many " + unit + "s of " + itemName + "? "
                        : "How many " + unit + " of " + itemName + "? ";
                    String qtyInput = RestaurantApp.readLine(qtyPrompt);
                    if (qtyInput == null || qtyInput.trim().isEmpty()) break;

                    try {
                        if (countable) {
                            int whole = Integer.parseInt(qtyInput.trim());
                            if (whole <= 0) { System.out.println("Must be at least 1."); continue; }
                            qty = whole;
                        } else {
                            double d = Double.parseDouble(qtyInput.trim());
                            if (d <= 0) { System.out.println("Must be greater than 0."); continue; }
                            qty = d;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println(countable ? "Please enter a whole number." : "Please enter a number.");
                    }
                }

                if (qty < 0) continue;

                try {
                    ps.setInt(1, itemId);
                    ps.setInt(2, invId);
                    ps.setDouble(3, qty);
                    ps.executeUpdate();
                    added++;
                    System.out.println("  Added " + itemName + ".");
                } catch (SQLException se) {
                    if (se.getErrorCode() == 1) {
                        System.out.println("  You already added " + itemName + ". Skipping.");
                    } else {
                        RestaurantApp.logError("addInventoryComponents", se);
                        System.out.println("  Could not add ingredient. Please try again.");
                    }
                }
            }
        }
        return added;
    }

    // ----------------------------------------------------------------
    // Add contained menu items — returns count added
    // Excludes baseItemId from the list (already linked as base)
    // ----------------------------------------------------------------
    static int addContainedItems(Connection conn, int itemId, int baseItemId) throws SQLException {
        System.out.println("\n--- Add Other Menu Items ---");
        System.out.println("You can include existing standard or custom menu items.");
        System.out.println("(Press Enter to skip this section.)");

        // Show available items, excluding the new item itself and the already-linked base
        String listSql;
        List<Object> listParams = new ArrayList<>();

        if (baseItemId != -1) {
            listSql = "SELECT itmid, name, itmtyp FROM MenuItem WHERE itmid != ? AND itmid != ? ORDER BY itmtyp, itmid";
            listParams.add(itemId);
            listParams.add(baseItemId);
        } else {
            listSql = "SELECT itmid, name, itmtyp FROM MenuItem WHERE itmid != ? ORDER BY itmtyp, itmid";
            listParams.add(itemId);
        }

        try (PreparedStatement ps = conn.prepareStatement(listSql)) {
            for (int i = 0; i < listParams.size(); i++) {
                ps.setInt(i + 1, (Integer) listParams.get(i));
            }
            ResultSet rs = ps.executeQuery();
            System.out.printf("  %-6s %-35s %s%n", "ID", "Name", "Type");
            RestaurantApp.divider();
            while (rs.next()) {
                String type = rs.getString("itmtyp").equals("S") ? "Standard" : "Custom";
                System.out.printf("  %-6d %-35s %s%n",
                    rs.getInt("itmid"), rs.getString("name"), type);
            }
        }

        System.out.println("\nEnter item ID and quantity (e.g. 1001 1), or press Enter to skip:");
        String insertSql = "INSERT INTO MICont (contid, compid, qty) VALUES (?, ?, ?)";
        int added = 0;

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            while (true) {
                String input = RestaurantApp.readLine("> ");
                if (input == null || input.trim().isEmpty()) break;

                String[] parts = input.trim().split("\\s+");
                if (parts.length != 2) {
                    System.out.println("Format: <item_id> <quantity>");
                    continue;
                }

                try {
                    int childId = Integer.parseInt(parts[0]);
                    int qty     = Integer.parseInt(parts[1]);

                    if (childId == itemId) {
                        System.out.println("An item cannot contain itself.");
                        continue;
                    }

                    if (childId == baseItemId) {
                        System.out.println("That item is already included as your base.");
                        continue;
                    }

                    if (qty <= 0) {
                        System.out.println("Quantity must be at least 1.");
                        continue;
                    }

                    String checkSql = "SELECT itmid FROM MenuItem WHERE itmid = ?";
                    try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                        check.setInt(1, childId);
                        ResultSet rs = check.executeQuery();
                        if (!rs.next()) {
                            System.out.println("Item not found.");
                            continue;
                        }
                    }

                    ps.setInt(1, itemId);
                    ps.setInt(2, childId);
                    ps.setInt(3, qty);
                    ps.executeUpdate();
                    added++;
                    System.out.println("  Added. Continue or press Enter when done.");

                } catch (NumberFormatException e) {
                    System.out.println("Please enter valid numbers.");
                } catch (SQLException se) {
                    if (se.getErrorCode() == 1) {
                        System.out.println("  That item is already included. Skipping.");
                    } else {
                        RestaurantApp.logError("addContainedItems", se);
                        System.out.println("  Could not add item. Please try again.");
                    }
                }
            }
        }
        return added;
    }

    // ----------------------------------------------------------------
    // Print custom item summary card
    // ----------------------------------------------------------------
    static void printCustomItemSummary(Connection conn, int itemId, String name, Session session) throws SQLException {
        System.out.println("\n=============================");
        System.out.println("   Custom Item Created!");
        System.out.println("=============================");
        System.out.println("  Name:    " + name);
        System.out.println("  Item ID: " + itemId);
        System.out.println("  Creator: " + session.firstName + " " + session.lastName);
        RestaurantApp.divider();

        String invSql =
            "SELECT i.name, c.qty, i.unit FROM MIComp c JOIN InvItm i ON c.inv_id = i.inv_id WHERE c.itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            boolean hasInv = false;
            while (rs.next()) {
                if (!hasInv) {
                    System.out.println("  Ingredients:");
                    hasInv = true;
                }
                System.out.printf("    - %.2f %s of %s%n",
                    rs.getDouble("qty"), rs.getString("unit"), rs.getString("name"));
            }
        }

        String contSql =
            "SELECT m.name, c.qty FROM MICont c JOIN MenuItem m ON c.compid = m.itmid WHERE c.contid = ?";
        try (PreparedStatement ps = conn.prepareStatement(contSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            boolean hasCont = false;
            while (rs.next()) {
                if (!hasCont) {
                    System.out.println("  Contains:");
                    hasCont = true;
                }
                System.out.printf("    - %dx %s%n", rs.getInt("qty"), rs.getString("name"));
            }
        }

        System.out.println("=============================");
        System.out.println("  You can now order this item!");
        System.out.println("=============================");
    }
}
