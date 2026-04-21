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
                    return; // Guest flow ends back at top-level
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
        // After checkout, guest flow ends — back to top
    }

    // ----------------------------------------------------------------
    // Login via email
    // ----------------------------------------------------------------
    static boolean handleLogin(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Login ---");

        String email = RestaurantApp.readLine("Enter email: ");
        if (email == null || email.trim().isEmpty()) return false;

        String sql = "SELECT acc_id, f_name, l_name FROM Acct WHERE email = ?";
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
        if (firstName == null || firstName.trim().isEmpty()) return false;

        String middleName = RestaurantApp.readLine("Middle name (press Enter to skip): ");

        String lastName = RestaurantApp.readLine("Last name: ");
        if (lastName == null || lastName.trim().isEmpty()) return false;

        String email = RestaurantApp.readLine("Email: ");
        if (email == null || email.trim().isEmpty()) return false;

        // Check email not already in use
        String checkSql = "SELECT acc_id FROM Acct WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, email.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("An account with that email already exists.");
                return false;
            }
        }

        String phone = RestaurantApp.readLine("Phone number: ");
        if (phone == null || phone.trim().isEmpty()) return false;

        String insertSql = "INSERT INTO Acct (acc_id, f_name, m_name, l_name, email, phone, points) " +
                           "VALUES (acct_seq.NEXTVAL, ?, ?, ?, ?, ?, 0)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql,
                new String[]{"acc_id"})) {
            ps.setString(1, firstName.trim());
            ps.setString(2, (middleName == null || middleName.trim().isEmpty()) ? null : middleName.trim());
            ps.setString(3, lastName.trim());
            ps.setString(4, email.trim());
            ps.setString(5, phone.trim());
            ps.executeUpdate();

            // Retrieve generated acc_id
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                session.accountId = keys.getInt(1);
            } else {
                // Fallback: query by email
                String fetchSql = "SELECT acc_id FROM Acct WHERE email = ?";
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
            System.out.println("\n=============================");
            System.out.println("  Hello, " + session.firstName + "!");
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
    // Place Order — location → browse → cart → checkout
    // ----------------------------------------------------------------
    static void placeOrder(Connection conn, Session session) throws SQLException {
        // Step 1: Select location
        if (!selectLocation(conn, session)) return;

        // Step 2: Browse and build cart
        browseItems(conn, session);

        // Step 3: Checkout if cart has items
        if (!session.cartIsEmpty()) {
            checkout(conn, session);
        } else {
            System.out.println("No items in cart. Returning.");
        }
    }

    // ----------------------------------------------------------------
    // Select Location
    // ----------------------------------------------------------------
    static boolean selectLocation(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Select a Location ---");

        String sql = "SELECT loc_id, city, state, stname, st_num FROM Loc ORDER BY loc_id";
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

            String checkSql = "SELECT loc_id, city FROM Loc WHERE loc_id = ?";
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
            System.out.println("\n--- Menu ---");
            System.out.println("  1. View All Items");
            System.out.println("  2. Filter by Meal Type");
            if (session.isLoggedIn()) {
                System.out.println("  3. View Custom Items");
            }
            System.out.println("  V. View Cart");
            System.out.println("  D. Done Browsing");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim().toUpperCase()) {
                case "1":
                    displayItems(conn, session, null, false);
                    promptAddToCart(conn, session);
                    break;
                case "2":
                    filterByMealType(conn, session);
                    break;
                case "3":
                    if (session.isLoggedIn()) {
                        displayItems(conn, session, null, true);
                        promptAddToCart(conn, session);
                    } else {
                        System.out.println("Invalid option.");
                    }
                    break;
                case "V":
                    System.out.println("\n--- Your Cart ---");
                    session.printCart();
                    break;
                case "D":
                    return;
                default:
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
            "FROM MItem m " +
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

            // Verify item exists
            String checkSql = "SELECT name FROM MItem WHERE itmid = ?";
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

        // Display cart with prices
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

        // Tax
        double taxRate = getLocationTaxRate(conn, session.locId);
        double tax     = subtotal * taxRate;
        double total   = subtotal + tax;

        RestaurantApp.divider();
        System.out.printf("  %-42s %10s%n", "Subtotal:", String.format("$%.2f", subtotal));
        System.out.printf("  %-42s %10s%n", String.format("Tax (%.2f%%):", taxRate * 100), String.format("$%.2f", tax));
        System.out.printf("  %-42s %10s%n", "Total:", String.format("$%.2f", total));

        // Confirm
        String confirm = RestaurantApp.readLine("\nConfirm order? (y/n): ");
        if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("Order cancelled.");
            return;
        }

        // Payment
        String ccNum = handlePayment(conn, session);
        if (ccNum == null) {
            System.out.println("Order cancelled.");
            return;
        }

        // Insert order
        Timestamp now = new Timestamp(System.currentTimeMillis());

        String insertOrder =
            "INSERT INTO Orders (ord_id, placed, pickup, ordtyp, status, acc_id, loc_id, cc_num) " +
            "VALUES (ord_seq.NEXTVAL, ?, NULL, 'I', 'pending', ?, ?, ?)";

        int newOrdId = -1;
        try (PreparedStatement ps = conn.prepareStatement(insertOrder,
                new String[]{"ord_id"})) {
            ps.setTimestamp(1, now);
            if (session.isLoggedIn()) ps.setInt(2, session.accountId);
            else                      ps.setNull(2, Types.INTEGER);
            ps.setInt(3, session.locId);
            ps.setString(4, ccNum);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) newOrdId = keys.getInt(1);

            // Fallback if generated keys not supported
            if (newOrdId == -1) {
                String fetchOrd = "SELECT MAX(ord_id) FROM Orders";
                try (PreparedStatement ps2 = conn.prepareStatement(fetchOrd)) {
                    ResultSet rs2 = ps2.executeQuery();
                    if (rs2.next()) newOrdId = rs2.getInt(1);
                }
            }
        }

        // Insert order items
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

        // Mark order completed immediately — triggers loyalty points for logged-in users
        String complete = "UPDATE Orders SET status = 'completed' WHERE ord_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(complete)) {
            ps.setInt(1, newOrdId);
            ps.executeUpdate();
        }

        conn.commit();

        // Receipt
        printReceipt(newOrdId, session, subtotal, tax, total, ccNum);
        session.clearCart();
    }

    // ----------------------------------------------------------------
    // Handle payment — returns cc_num to use
    // ----------------------------------------------------------------
    static String handlePayment(Connection conn, Session session) throws SQLException {
        if (session.isLoggedIn()) {
            // Show stored cards
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
            // Guest — always enter new card
            return enterNewCard(conn, session);
        }
    }

    // ----------------------------------------------------------------
    // Enter a new credit card
    // ----------------------------------------------------------------
    static String enterNewCard(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Enter Card Details ---");
        System.out.println("  Format: xxxx-xxxx-xxxx-xxxx  or  xxxxxxxxxxxxxxxx");

        // Card number with reprompt
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

        // Check if card already exists
        String checkSql = "SELECT cc_num FROM CCard WHERE cc_num = ?";
        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, ccNum);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("Card already on file. Using existing card.");
                return ccNum;
            }
        }

        // Expiry with reprompt
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

        // Save to account or one-time
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
    // Get effective item price (local override or national)
    // ----------------------------------------------------------------
    static double getItemPrice(Connection conn, int itemId, int locId) throws SQLException {
        // For custom items, recursively compute from components
        String typeSql = "SELECT itmtyp, nat_pr FROM MItem WHERE itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return 0;

            String type = rs.getString("itmtyp");

            if (type.equals("S")) {
                // Check local override first
                String locSql = "SELECT loc_pr FROM MILoc WHERE itmid = ? AND loc_id = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(locSql)) {
                    ps2.setInt(1, itemId);
                    ps2.setInt(2, locId);
                    ResultSet rs2 = ps2.executeQuery();
                    if (rs2.next()) return rs2.getDouble("loc_pr");
                }
                return rs.getDouble("nat_pr");
            } else {
                // Custom item: sum up inventory components + contained menu items
                return computeCustomPrice(conn, itemId, locId);
            }
        }
    }

    // ----------------------------------------------------------------
    // Recursively compute custom item price
    // ----------------------------------------------------------------
    static double computeCustomPrice(Connection conn, int itemId, int locId) throws SQLException {
        double total = 0;

        // Inventory components
        String invSql = "SELECT i.basect, c.qty FROM MIComp c JOIN InvItm i ON c.inv_id = i.inv_id WHERE c.itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(invSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                total += rs.getDouble("basect") * rs.getDouble("qty");
            }
        }

        // Contained menu items (recursive)
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
        String sql = "SELECT tax_rt FROM Loc WHERE loc_id = ?";
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
            "SELECT o.ord_id, o.placed, o.ordtyp, o.status, l.city " +
            "FROM Orders o JOIN Loc l ON o.loc_id = l.loc_id " +
            "WHERE o.acc_id = ? ORDER BY o.placed DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, session.accountId);
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-10s %-22s %-10s %-12s %s%n",
                "Order #", "Placed", "Type", "Status", "Location");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                String type = rs.getString("ordtyp").equals("O") ? "Online" : "In-person";
                System.out.printf("  %-10d %-22s %-10s %-12s %s%n",
                    rs.getInt("ord_id"),
                    rs.getTimestamp("placed").toString().substring(0, 16),
                    type,
                    rs.getString("status"),
                    rs.getString("city"));
            }

            if (!any) System.out.println("  No orders found.");
        }

        // Optionally show items for a specific order
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
        // Verify this order belongs to this account
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
            "FROM OrdMItm oi JOIN MItem m ON oi.itmid = m.itmid " +
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
        // Check if card is used in any pending orders
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

    // ----------------------------------------------------------------
    // Build Custom Item
    // ----------------------------------------------------------------
    static void buildCustomItem(Connection conn, Session session) throws SQLException {
        System.out.println("\n--- Build Custom Item ---");

        String name = RestaurantApp.readLine("Item name: ");
        if (name == null || name.trim().isEmpty()) return;

        // Insert the custom item
        String insertSql =
            "INSERT INTO MItem (itmid, name, itmtyp, nat_pr, cr_acc, crdate) " +
            "VALUES (item_seq.NEXTVAL, ?, 'C', NULL, ?, SYSDATE)";

        int newItemId = -1;
        try (PreparedStatement ps = conn.prepareStatement(insertSql, new String[]{"itmid"})) {
            ps.setString(1, name.trim());
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
            return;
        }

        // Add inventory components
        addInventoryComponents(conn, newItemId);

        // Add contained menu items
        addContainedItems(conn, newItemId);

        conn.commit();

        // Summary card
        printCustomItemSummary(conn, newItemId, name.trim(), session);
    }

    // ----------------------------------------------------------------
    // Add inventory components to a custom item
    // ----------------------------------------------------------------
    static void addInventoryComponents(Connection conn, int itemId) throws SQLException {
        System.out.println("\n--- Add Inventory Components ---");
        System.out.println("Available inventory items:");

        String listSql = "SELECT inv_id, name, unit FROM InvItm ORDER BY inv_id";
        try (PreparedStatement ps = conn.prepareStatement(listSql)) {
            ResultSet rs = ps.executeQuery();
            System.out.printf("  %-6s %-30s %s%n", "ID", "Name", "Unit");
            RestaurantApp.divider();
            while (rs.next()) {
                System.out.printf("  %-6d %-30s %s%n",
                    rs.getInt("inv_id"), rs.getString("name"), rs.getString("unit"));
            }
        }

        System.out.println("\nEnter ingredient ID and quantity (e.g. 1 2.5), or press Enter to skip:");
        String insertSql = "INSERT INTO MIComp (itmid, inv_id, qty) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            while (true) {
                String input = RestaurantApp.readLine("> ");
                if (input == null || input.trim().isEmpty()) break;

                String[] parts = input.trim().split("\\s+");
                if (parts.length != 2) {
                    System.out.println("Format: <inv_id> <quantity>");
                    continue;
                }

                try {
                    int    invId = Integer.parseInt(parts[0]);
                    double qty   = Double.parseDouble(parts[1]);

                    // Verify inv_id exists
                    String checkSql = "SELECT inv_id FROM InvItm WHERE inv_id = ?";
                    try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                        check.setInt(1, invId);
                        ResultSet rs = check.executeQuery();
                        if (!rs.next()) {
                            System.out.println("Inventory item not found.");
                            continue;
                        }
                    }

                    ps.setInt(1, itemId);
                    ps.setInt(2, invId);
                    ps.setDouble(3, qty);
                    ps.executeUpdate();
                    System.out.println("Added. Continue or press Enter when done.");

                } catch (NumberFormatException e) {
                    System.out.println("Please enter valid numbers.");
                } catch (SQLException se) {
                    System.out.println("Could not add ingredient: " + se.getMessage());
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Add contained menu items to a custom item
    // ----------------------------------------------------------------
    static void addContainedItems(Connection conn, int itemId) throws SQLException {
        System.out.println("\n--- Add Contained Menu Items ---");
        System.out.println("You can include existing standard or custom menu items.");

        String listSql = "SELECT itmid, name, itmtyp FROM MItem ORDER BY itmtyp, itmid";
        try (PreparedStatement ps = conn.prepareStatement(listSql)) {
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

                    String checkSql = "SELECT itmid FROM MItem WHERE itmid = ?";
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
                    System.out.println("Added. Continue or press Enter when done.");

                } catch (NumberFormatException e) {
                    System.out.println("Please enter valid numbers.");
                } catch (SQLException se) {
                    System.out.println("Could not add item: " + se.getMessage());
                }
            }
        }
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

        // Show inventory components
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

        // Show contained items
        String contSql =
            "SELECT m.name, c.qty FROM MICont c JOIN MItem m ON c.compid = m.itmid WHERE c.contid = ?";
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
