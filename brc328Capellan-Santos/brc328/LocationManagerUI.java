import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LocationManagerUI {

    // ----------------------------------------------------------------
    // Entry point called from RestaurantApp
    // ----------------------------------------------------------------
    public static void run(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();

        int locId = selectLocation(conn);
        if (locId == -1) return;

        locationMenu(conn, locId);
    }

    // ----------------------------------------------------------------
    // Location selection
    // ----------------------------------------------------------------
    static int selectLocation(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Select Your Location ---");

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
                System.out.println("No locations found.");
                return -1;
            }
        }

        while (true) {
            int id = RestaurantApp.readInt("Enter location ID (0 to cancel): ");
            if (id == -1) return -1;

            String chk = "SELECT loc_id FROM Location WHERE loc_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(chk)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return id;
                System.out.println("Invalid location ID. Try again.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Main menu loop for a selected location
    // ----------------------------------------------------------------
    static void locationMenu(Connection conn, int locId) throws SQLException {
        while (true) {
            String locName = getLocName(conn, locId);
            RestaurantApp.clearScreen();
            System.out.println("\n=============================");
            System.out.println("  " + locName);
            System.out.println("  Location Manager");
            System.out.println("=============================");
            System.out.println("  1. View Orders");
            System.out.println("  2. Manage Price Overrides");
            System.out.println("  3. Switch Location");
            System.out.println("  4. Back");
            System.out.println("=============================");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim()) {
                case "1": viewOrders(conn, locId);           break;
                case "2": managePriceOverrides(conn, locId); break;
                case "3":
                    int newLoc = selectLocation(conn);
                    if (newLoc != -1) locId = newLoc;
                    break;
                case "4": return;
                default:  System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // 1. View Orders at this location — paginated, with drill-down
    // ----------------------------------------------------------------
    static void viewOrders(Connection conn, int locId) throws SQLException {
        String sql =
            "SELECT o.ord_id, o.placed, o.ordtyp, " +
            "SUM(oi.qty) AS total_qty, " +
            "a.f_name, a.l_name " +
            "FROM Orders o " +
            "LEFT JOIN OrderMenuItem oi ON o.ord_id = oi.ord_id " +
            "LEFT JOIN Account a ON o.acc_id = a.acc_id " +
            "WHERE o.loc_id = ? " +
            "GROUP BY o.ord_id, o.placed, o.ordtyp, a.f_name, a.l_name " +
            "ORDER BY o.placed DESC";

        List<String[]> rows     = new ArrayList<>();
        List<Integer>  orderIds = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int ordId = rs.getInt("ord_id");
                orderIds.add(ordId);

                String type     = rs.getString("ordtyp").equals("O") ? "Online" : "In-person";
                String placed   = rs.getTimestamp("placed").toString().substring(0, 16);
                String customer = rs.getString("f_name") != null
                    ? rs.getString("f_name") + " " + rs.getString("l_name")
                    : "Guest";

                rows.add(new String[]{
                    String.valueOf(ordId), placed, type, customer,
                    String.valueOf(rs.getInt("total_qty"))
                });
            }
        }

        if (rows.isEmpty()) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Orders at " + getLocName(conn, locId) + " ---");
            System.out.println("  No orders found at this location.");
            RestaurantApp.readLine("\nPress Enter to continue...");
            return;
        }

        String[] headers = {"Order #", "Placed", "Type", "Customer", "Items"};
        int[]    widths  = {8, 17, 10, 22, 5};
        int page = 0, pageSize = 12;

        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Orders at " + getLocName(conn, locId) + " ---");

            List<String[]> pageRows = RestaurantApp.getPage(rows, page, pageSize);
            ManagementUI.printTable(headers, widths, pageRows);
            RestaurantApp.printPageControls(page, rows.size(), pageSize);
            System.out.println("  Enter an order number to view its items.");

            String input = RestaurantApp.readLine("> ");
            if (input == null) continue;
            input = input.trim().toUpperCase();

            if (input.equals("B")) {
                return;
            } else if (input.equals("N") && RestaurantApp.hasNextPage(page, rows.size(), pageSize)) {
                page++;
            } else if (input.equals("P") && page > 0) {
                page--;
            } else if (!input.isEmpty()) {
                try {
                    int ordId = Integer.parseInt(input);
                    if (orderIds.contains(ordId)) {
                        viewOrderDetail(conn, ordId, locId);
                    } else {
                        System.out.println("  Order not found at this location.");
                        RestaurantApp.readLine("  Press Enter to continue...");
                    }
                } catch (NumberFormatException e) {
                    // ignore unrecognised input
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // Drill-down: show line items for a single order
    // Uses get_item_price() PL/SQL function for correct custom item
    // pricing — same fix as ManagementUI sales report.
    // ----------------------------------------------------------------
    static void viewOrderDetail(Connection conn, int ordId, int locId) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Order #" + ordId + " ---");

        String hdrSql =
            "SELECT o.placed, o.ordtyp, o.cc_num, " +
            "a.f_name, a.l_name, a.email " +
            "FROM Orders o LEFT JOIN Account a ON o.acc_id = a.acc_id " +
            "WHERE o.ord_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(hdrSql)) {
            ps.setInt(1, ordId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String customer = rs.getString("f_name") != null
                    ? rs.getString("f_name") + " " + rs.getString("l_name")
                      + " (" + rs.getString("email") + ")"
                    : "Guest";
                String type   = rs.getString("ordtyp").equals("O") ? "Online" : "In-person";
                String placed = rs.getTimestamp("placed").toString().substring(0, 16);
                String card   = rs.getString("cc_num");
                String last4  = card.substring(Math.max(0, card.length() - 4));

                System.out.println("  Customer : " + customer);
                System.out.println("  Type     : " + type);
                System.out.println("  Placed   : " + placed);
                System.out.println("  Card     : **** **** **** " + last4);
            }
        }

        // get_item_price() handles both standard items (with local override)
        // and custom items (recursive component sum). Previously COALESCE(loc_pr, nat_pr)
        // returned NULL for custom items, making all custom-item line totals $0.00.
        String itemSql =
            "SELECT m.itmid, m.name, oi.qty, " +
            "get_item_price(m.itmid, ?) AS unit_pr " +
            "FROM OrderMenuItem oi " +
            "JOIN MenuItem m ON oi.itmid = m.itmid " +
            "WHERE oi.ord_id = ?";

        RestaurantApp.divider();
        System.out.printf("  %-6s %-35s %-5s %10s%n", "ID", "Item", "Qty", "Line Total");
        RestaurantApp.divider();

        double subtotal = 0;
        try (PreparedStatement ps = conn.prepareStatement(itemSql)) {
            ps.setInt(1, locId);
            ps.setInt(2, ordId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int    qty      = rs.getInt("qty");
                double unitPr   = rs.getDouble("unit_pr");
                double lineTotal = unitPr * qty;
                subtotal += lineTotal;

                System.out.printf("  %-6d %-35s %-5d %10s%n",
                    rs.getInt("itmid"),
                    rs.getString("name"),
                    qty,
                    String.format("$%.2f", lineTotal));
            }
        }

        RestaurantApp.divider();
        System.out.printf("  %-48s %10s%n",
            "Subtotal (excl. tax):", String.format("$%.2f", subtotal));

        RestaurantApp.readLine("\nPress Enter to go back...");
    }

    // ----------------------------------------------------------------
    // 2. Manage Price Overrides
    // ----------------------------------------------------------------
    static void managePriceOverrides(Connection conn, int locId) throws SQLException {
        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Price Overrides: " + getLocName(conn, locId) + " ---");
            System.out.println("  (Overrides replace the national price at this location only)\n");

            List<String[]> rows = fetchOverrides(conn, locId);
            String[] headers = {"Item ID", "Name", "National Price", "Local Price"};
            int[]    widths  = {7, 35, 14, 11};

            if (rows.isEmpty()) {
                System.out.println("  No price overrides set for this location.");
            } else {
                ManagementUI.printTable(headers, widths, rows);
            }

            System.out.println();
            System.out.println("=============================");
            System.out.println("  A. Add / update override");
            System.out.println("  R. Remove override");
            System.out.println("  B. Back");
            System.out.println("=============================");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim().toUpperCase()) {
                case "A": addOverride(conn, locId);    break;
                case "R": removeOverride(conn, locId); break;
                case "B": return;
                default:  System.out.println("Invalid option.");
            }
        }
    }

    // ----------------------------------------------------------------
    // Fetch current overrides for a location
    // ----------------------------------------------------------------
    static List<String[]> fetchOverrides(Connection conn, int locId) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        String sql =
            "SELECT m.itmid, m.name, m.nat_pr, ml.loc_pr " +
            "FROM MenuItemLocation ml JOIN MenuItem m ON ml.itmid = m.itmid " +
            "WHERE ml.loc_id = ? ORDER BY m.itmid";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String natPr = rs.getObject("nat_pr") != null
                    ? String.format("$%.2f", rs.getDouble("nat_pr"))
                    : "varies";
                rows.add(new String[]{
                    String.valueOf(rs.getInt("itmid")),
                    rs.getString("name"),
                    natPr,
                    String.format("$%.2f", rs.getDouble("loc_pr"))
                });
            }
        }
        return rows;
    }

    // ----------------------------------------------------------------
    // Add (or update) a price override
    // ----------------------------------------------------------------
    static void addOverride(Connection conn, int locId) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Add / Update Price Override ---");

        String listSql =
            "SELECT m.itmid, m.name, m.nat_pr, ml.loc_pr AS existing " +
            "FROM MenuItem m " +
            "LEFT JOIN MenuItemLocation ml ON m.itmid = ml.itmid AND ml.loc_id = ? " +
            "WHERE m.itmtyp = 'S' ORDER BY m.itmid";

        try (PreparedStatement ps = conn.prepareStatement(listSql)) {
            ps.setInt(1, locId);
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %-35s %-12s %s%n",
                "ID", "Name", "Nat. Price", "Current Override");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                String existing = rs.getObject("existing") != null
                    ? String.format("$%.2f", rs.getDouble("existing"))
                    : "(none)";
                System.out.printf("  %-6d %-35s %-12s %s%n",
                    rs.getInt("itmid"),
                    rs.getString("name"),
                    String.format("$%.2f", rs.getDouble("nat_pr")),
                    existing);
            }
            if (!any) {
                System.out.println("  No standard items available.");
                RestaurantApp.readLine("  Press Enter to continue...");
                return;
            }
        }

        int itemId = RestaurantApp.readInt("\nEnter item ID (0 to cancel): ");
        if (itemId == -1) return;

        String chkSql = "SELECT name FROM MenuItem WHERE itmid = ? AND itmtyp = 'S'";
        String itemName = null;
        try (PreparedStatement ps = conn.prepareStatement(chkSql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                System.out.println("  Item not found or is not a standard item.");
                RestaurantApp.readLine("  Press Enter to continue...");
                return;
            }
            itemName = rs.getString("name");
        }

        // Prompt for price with Enter-to-cancel support
        double newPrice = -1;
        while (newPrice < 0) {
            String priceInput = RestaurantApp.readLine(
                "New local price for \"" + itemName + "\" (or press Enter to cancel): $");
            if (priceInput == null || priceInput.trim().isEmpty()) return;
            try {
                double parsed = Double.parseDouble(priceInput.trim());
                if (parsed <= 0) {
                    System.out.println("  Price must be greater than $0.00.");
                } else {
                    newPrice = parsed;
                }
            } catch (NumberFormatException e) {
                System.out.println("  Please enter a valid price (e.g. 5.99).");
            }
        }

        // Upsert — update if exists, insert if not
        String existSql = "SELECT loc_pr FROM MenuItemLocation WHERE itmid = ? AND loc_id = ?";
        boolean exists = false;
        try (PreparedStatement ps = conn.prepareStatement(existSql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, locId);
            ResultSet rs = ps.executeQuery();
            exists = rs.next();
        }

        if (exists) {
            String updSql = "UPDATE MenuItemLocation SET loc_pr = ? WHERE itmid = ? AND loc_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updSql)) {
                ps.setDouble(1, newPrice);
                ps.setInt(2, itemId);
                ps.setInt(3, locId);
                ps.executeUpdate();
            }
            System.out.println("  Override updated.");
        } else {
            String insSql = "INSERT INTO MenuItemLocation (itmid, loc_id, loc_pr) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insSql)) {
                ps.setInt(1, itemId);
                ps.setInt(2, locId);
                ps.setDouble(3, newPrice);
                ps.executeUpdate();
            }
            System.out.println("  Override added.");
        }

        conn.commit();
        System.out.println(
            "  \"" + itemName + "\" is now $" + String.format("%.2f", newPrice) + " at this location.");
        RestaurantApp.readLine("  Press Enter to continue...");
    }

    // ----------------------------------------------------------------
    // Remove a price override
    // ----------------------------------------------------------------
    static void removeOverride(Connection conn, int locId) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Remove Price Override ---");

        List<String[]> overrides = fetchOverrides(conn, locId);
        if (overrides.isEmpty()) {
            System.out.println("  No overrides to remove.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String[] headers = {"Item ID", "Name", "National Price", "Local Price"};
        int[]    widths  = {7, 35, 14, 11};
        ManagementUI.printTable(headers, widths, overrides);

        int itemId = RestaurantApp.readInt("\nEnter item ID to remove override (0 to cancel): ");
        if (itemId == -1) return;

        String chkSql =
            "SELECT m.name FROM MenuItemLocation ml JOIN MenuItem m ON ml.itmid = m.itmid " +
            "WHERE ml.itmid = ? AND ml.loc_id = ?";
        String itemName = null;
        try (PreparedStatement ps = conn.prepareStatement(chkSql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, locId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                System.out.println("  No override found for that item at this location.");
                RestaurantApp.readLine("  Press Enter to continue...");
                return;
            }
            itemName = rs.getString("name");
        }

        String confirm = RestaurantApp.readLine(
            "  Remove override for \"" + itemName + "\"? Reverts to national price. (y/n): ");
        if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("  Cancelled.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String delSql = "DELETE FROM MenuItemLocation WHERE itmid = ? AND loc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(delSql)) {
            ps.setInt(1, itemId);
            ps.setInt(2, locId);
            ps.executeUpdate();
        }

        conn.commit();
        System.out.println(
            "  Override removed. \"" + itemName + "\" now uses the national price.");
        RestaurantApp.readLine("  Press Enter to continue...");
    }

    // ----------------------------------------------------------------
    // Helper — "City, ST" for a location ID
    // ----------------------------------------------------------------
    static String getLocName(Connection conn, int locId) throws SQLException {
        String sql = "SELECT city, state FROM Location WHERE loc_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("city") + ", " + rs.getString("state");
        }
        return "Location " + locId;
    }
}
