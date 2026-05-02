import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ManagementUI {

    // Entry point called from RestaurantApp
    public static void run(Connection conn) throws SQLException {
        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n=============================");
            System.out.println("  Management Console");
            System.out.println("=============================");
            System.out.println("  1. Sales Report by Location");
            System.out.println("  2. Top-Selling Menu Items");
            System.out.println("  3. Customer Activity Report");
            System.out.println("  4. Manage Menu Items");
            System.out.println("  5. Back");
            System.out.println("=============================");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim()) {
                case "1":
                    salesReport(conn);
                    break;
                case "2":
                    topSellingItems(conn);
                    break;
                case "3":
                    customerActivity(conn);
                    break;
                case "4":
                    manageMenuItems(conn);
                    break;
                case "5":
                    return;
                default:
                    System.out.println("Invalid option.");
            }
        }
    }

    // Prompt for an optional date range
    // Returns a 2-element array: [startDate, endDate] (either may be null)
    static String[] promptDateRange() {
        System.out.println("\n  Date range filter (press Enter to skip for all-time):");
        String start = RestaurantApp.readLine("  Start date (YYYY-MM-DD): ");
        String end = RestaurantApp.readLine("  End date   (YYYY-MM-DD): ");

        start = (start == null || start.trim().isEmpty()) ? null : start.trim();
        end = (end == null || end.trim().isEmpty()) ? null : end.trim();

        if (start != null && !start.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.out.println("  Warning: start date format not recognised -- ignoring.");
            start = null;
        }
        if (end != null && !end.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.out.println("  Warning: end date format not recognised -- ignoring.");
            end = null;
        }

        return new String[] { start, end };
    }

    // Build a date range fragment for use inside a JOIN ON clause.
    // Used for LEFT JOINs where a WHERE clause would incorrectly
    // eliminate rows with no matching orders (locations with
    // zero orders, accounts with zero orders in the date range).
    static String dateJoinClause(String start, String end) {
        StringBuilder sb = new StringBuilder();
        if (start != null) {
            sb.append(" AND o.placed >= TO_TIMESTAMP(?, 'YYYY-MM-DD')");
        }
        if (end != null) {
            sb.append(" AND o.placed < TO_TIMESTAMP(?, 'YYYY-MM-DD') + INTERVAL '1' DAY");
        }
        return sb.toString();
    }

    // Build a WHERE clause fragment for date range on Orders.placed.
    // Used for INNER JOINs where excluding non-matching rows is correct
    // (Top-Selling Items only cares about items that were ordered).
    static String dateWhereClause(String start, String end, boolean hasWhere) {
        StringBuilder sb = new StringBuilder();
        if (start != null) {
            sb.append(hasWhere ? " AND " : " WHERE ");
            sb.append("o.placed >= TO_TIMESTAMP(?, 'YYYY-MM-DD')");
            hasWhere = true;
        }
        if (end != null) {
            sb.append(hasWhere ? " AND " : " WHERE ");
            sb.append("o.placed < TO_TIMESTAMP(?, 'YYYY-MM-DD') + INTERVAL '1' DAY");
        }
        return sb.toString();
    }

    // Sales Report by Location
    static void salesReport(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Sales Report by Location ---");

        String[] range = promptDateRange();
        String start = range[0],
            end = range[1];

        // Revenue uses stored unit_pr values so reports reflect what was
        // actually paid at checkout, not current menu prices.
        // Date filter goes in the JOIN ON clause so locations with no orders
        // in the date range still appear with ord_count=0 rather than disappearing.
        StringBuilder sql = new StringBuilder(
            "SELECT l.loc_id, l.city, l.state, " +
                "COUNT(DISTINCT o.ord_id) AS ord_count, " +
                "SUM(oi.unit_pr * oi.qty) AS revenue " +
                "FROM Location l " +
                "LEFT JOIN Orders o ON l.loc_id = o.loc_id"
        );
        sql.append(dateJoinClause(start, end));
        sql.append(" LEFT JOIN OrderMenuItem oi ON o.ord_id = oi.ord_id");
        sql.append(
            " GROUP BY l.loc_id, l.city, l.state ORDER BY revenue DESC NULLS LAST, l.loc_id ASC"
        );

        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (start != null) ps.setString(idx++, start);
            if (end != null) ps.setString(idx++, end);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                int ordCount = rs.getInt("ord_count");
                double revenue = rs.getDouble("revenue");
                boolean noRev = rs.wasNull() || revenue == 0;
                double avg = (ordCount > 0 && !noRev) ? revenue / ordCount : 0;

                rows.add(
                    new String[] {
                        String.valueOf(rs.getInt("loc_id")),
                        rs.getString("city") + ", " + rs.getString("state"),
                        String.valueOf(ordCount),
                        noRev ? "$0.00" : String.format("$%.2f", revenue),
                        (ordCount > 0 && !noRev) ? String.format("$%.2f", avg) : "-",
                    }
                );
            }
        }

        String[] headers = { "ID", "Location", "Orders", "Revenue", "Avg Order" };
        int[] widths = { 4, 22, 7, 12, 10 };
        int page = 0,
            pageSize = 10;

        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Sales Report by Location ---");
            printDateRange(start, end);

            List<String[]> pageRows = RestaurantApp.getPage(rows, page, pageSize);
            printTable(headers, widths, pageRows);
            RestaurantApp.printPageControls(page, rows.size(), pageSize);

            String input = RestaurantApp.readLine("> ");
            if (input == null) continue;
            input = input.trim().toUpperCase();

            if (input.equals("B")) return;
            else if (
                input.equals("N") && RestaurantApp.hasNextPage(page, rows.size(), pageSize)
            ) page++;
            else if (input.equals("P") && page > 0) page--;
        }
    }

    // Top-Selling Menu Items
    static void topSellingItems(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Top-Selling Menu Items ---");

        Integer locId = promptLocationFilter(conn);

        String[] range = promptDateRange();
        String start = range[0],
            end = range[1];

        // INNER JOINs are correct here — we only want items that appear in
        // actual orders, so the date WHERE clause is appropriate.
        StringBuilder sql = new StringBuilder(
            "SELECT m.itmid, m.name, m.itmtyp, " +
                "SUM(oi.qty) AS total_qty, COUNT(DISTINCT o.ord_id) AS order_count " +
                "FROM OrderMenuItem oi " +
                "JOIN MenuItem m ON oi.itmid = m.itmid " +
                "JOIN Orders o ON oi.ord_id = o.ord_id"
        );

        boolean hasWhere = false;
        if (locId != null) {
            sql.append(" WHERE o.loc_id = ?");
            hasWhere = true;
        }
        sql.append(dateWhereClause(start, end, hasWhere));
        sql.append(
            " GROUP BY m.itmid, m.name, m.itmtyp ORDER BY total_qty DESC, order_count DESC, m.itmid ASC"
        );

        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (locId != null) ps.setInt(idx++, locId);
            if (start != null) ps.setString(idx++, start);
            if (end != null) ps.setString(idx++, end);
            ResultSet rs = ps.executeQuery();

            int rank = 1;
            while (rs.next()) {
                String type = rs.getString("itmtyp").equals("S") ? "Std" : "Custom";
                rows.add(
                    new String[] {
                        String.valueOf(rank++),
                        String.valueOf(rs.getInt("itmid")),
                        rs.getString("name"),
                        type,
                        String.valueOf(rs.getInt("total_qty")),
                        String.valueOf(rs.getInt("order_count")),
                    }
                );
            }
        }

        if (rows.isEmpty()) {
            System.out.println("\n  No order data found for the selected filters.");
            RestaurantApp.readLine("\nPress Enter to continue...");
            return;
        }

        String[] headers = { "Rank", "ID", "Name", "Type", "Qty Sold", "# Orders" };
        int[] widths = { 4, 6, 32, 6, 9, 9 };
        int page = 0,
            pageSize = 15;

        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Top-Selling Menu Items ---");
            if (locId != null) System.out.println("  Location filter: ID " + locId);
            printDateRange(start, end);

            List<String[]> pageRows = RestaurantApp.getPage(rows, page, pageSize);
            printTable(headers, widths, pageRows);
            RestaurantApp.printPageControls(page, rows.size(), pageSize);

            String input = RestaurantApp.readLine("> ");
            if (input == null) continue;
            input = input.trim().toUpperCase();

            if (input.equals("B")) return;
            else if (
                input.equals("N") && RestaurantApp.hasNextPage(page, rows.size(), pageSize)
            ) page++;
            else if (input.equals("P") && page > 0) page--;
        }
    }

    // Customer Activity Report
    static void customerActivity(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Customer Activity Report ---");

        String[] range = promptDateRange();
        String start = range[0],
            end = range[1];

        // Date filter goes in the JOIN ON clause so accounts with no orders
        // in the date range still appear with ord_count=0 rather than being
        // excluded entirely. A WHERE clause on a LEFT JOIN would eliminate them.
        StringBuilder sql = new StringBuilder(
            "SELECT a.acc_id, a.f_name, a.l_name, a.email, a.points, " +
                "COUNT(o.ord_id) AS ord_count " +
                "FROM Account a " +
                "LEFT JOIN Orders o ON a.acc_id = o.acc_id"
        );
        sql.append(dateJoinClause(start, end));
        sql.append(" GROUP BY a.acc_id, a.f_name, a.l_name, a.email, a.points");
        sql.append(" ORDER BY ord_count DESC, a.points DESC, a.acc_id ASC");

        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (start != null) ps.setString(idx++, start);
            if (end != null) ps.setString(idx++, end);
            ResultSet rs = ps.executeQuery();

            int rank = 1;
            while (rs.next()) {
                rows.add(
                    new String[] {
                        String.valueOf(rank++),
                        String.valueOf(rs.getInt("acc_id")),
                        rs.getString("f_name") + " " + rs.getString("l_name"),
                        rs.getString("email"),
                        String.valueOf(rs.getInt("ord_count")),
                        String.valueOf(rs.getInt("points")),
                    }
                );
            }
        }

        if (rows.isEmpty()) {
            System.out.println("\n  No customer data found.");
            RestaurantApp.readLine("\nPress Enter to continue...");
            return;
        }

        String[] headers = { "Rank", "ID", "Name", "Email", "Orders", "Points" };
        int[] widths = { 4, 6, 22, 28, 7, 7 };
        int page = 0,
            pageSize = 12;

        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Customer Activity Report ---");
            printDateRange(start, end);

            List<String[]> pageRows = RestaurantApp.getPage(rows, page, pageSize);
            printTable(headers, widths, pageRows);
            RestaurantApp.printPageControls(page, rows.size(), pageSize);

            String input = RestaurantApp.readLine("> ");
            if (input == null) continue;
            input = input.trim().toUpperCase();

            if (input.equals("B")) return;
            else if (
                input.equals("N") && RestaurantApp.hasNextPage(page, rows.size(), pageSize)
            ) page++;
            else if (input.equals("P") && page > 0) page--;
        }
    }

    // Print a generic table with given headers, column widths, and rows
    static void printTable(String[] headers, int[] widths, List<String[]> rows) {
        StringBuilder headerLine = new StringBuilder("  ");
        for (int i = 0; i < headers.length; i++) {
            headerLine.append(String.format("%-" + widths[i] + "s ", headers[i]));
        }
        System.out.println(headerLine);
        RestaurantApp.divider();

        if (rows.isEmpty()) {
            System.out.println("  (no data)");
            return;
        }

        for (String[] row : rows) {
            StringBuilder line = new StringBuilder("  ");
            for (int i = 0; i < row.length && i < widths.length; i++) {
                String cell = row[i] != null ? row[i] : "";
                if (cell.length() > widths[i]) cell = cell.substring(0, widths[i] - 1) + "~";
                line.append(String.format("%-" + widths[i] + "s ", cell));
            }
            System.out.println(line);
        }
    }

    // Print the active date range as a subtitle line
    static void printDateRange(String start, String end) {
        if (start != null || end != null) {
            String s = start != null ? start : "beginning";
            String e = end != null ? end : "today";
            System.out.println("  Period: " + s + " to " + e);
        } else {
            System.out.println("  Period: All-time");
        }
        System.out.println();
    }

    // Show the location table and return a chosen loc_id, or null for all
    static Integer promptLocationFilter(Connection conn) throws SQLException {
        System.out.println("\n  Location filter (press Enter to show all locations):");
        System.out.println();

        String sql = "SELECT loc_id, city, state, stname, st_num FROM Location ORDER BY loc_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            System.out.printf("  %-6s %-25s %s%n", "ID", "City", "Address");
            RestaurantApp.divider();
            while (rs.next()) {
                System.out.printf(
                    "  %-6d %-25s %s %s%n",
                    rs.getInt("loc_id"),
                    rs.getString("city") + ", " + rs.getString("state"),
                    rs.getString("st_num"),
                    rs.getString("stname")
                );
            }
        }

        System.out.println();
        String input = RestaurantApp.readLine("  Enter location ID (or press Enter for all): ");
        if (input == null || input.trim().isEmpty()) return null;

        try {
            int id = Integer.parseInt(input.trim());
            String chk = "SELECT loc_id FROM Location WHERE loc_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(chk)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return id;
            }
            System.out.println("  Location not found -- showing all locations.");
        } catch (NumberFormatException e) {
            System.out.println("  Invalid input -- showing all locations.");
        }
        return null;
    }

    // Manage menu items — add, soft-delete, or restore items
    static void manageMenuItems(Connection conn) throws SQLException {
        while (true) {
            RestaurantApp.clearScreen();
            System.out.println("\n--- Manage Menu Items ---");
            System.out.println("  A. Add standard item");
            System.out.println("  U. Update standard item price");
            System.out.println("  D. Delete item");
            System.out.println("  R. Restore deleted item");
            System.out.println("  B. Back");

            String choice = RestaurantApp.readLine("Select: ");
            if (choice == null) continue;

            switch (choice.trim().toUpperCase()) {
                case "A":
                    addStandardItem(conn);
                    break;
                case "U":
                    updateStandardItemPrice(conn);
                    break;
                case "D":
                    deleteMenuItem(conn);
                    break;
                case "R":
                    restoreMenuItem(conn);
                    break;
                case "B":
                    return;
                default:
                    System.out.println("Invalid option.");
                    RestaurantApp.readLine("Press Enter to continue...");
            }
        }
    }

    // Add a new standard menu item
    static void addStandardItem(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Add Standard Menu Item ---");

        String name = RestaurantApp.readLine("Item name (or press Enter to cancel): ");
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();

        String dupeSql = "SELECT itmid FROM MenuItem WHERE LOWER(name) = LOWER(?)";
        try (PreparedStatement ps = conn.prepareStatement(dupeSql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                System.out.println("  An item with that name already exists.");
                RestaurantApp.readLine("  Press Enter to continue...");
                return;
            }
        }

        double price = RestaurantApp.readPositivePrice(
            "National price (or press Enter to cancel): $"
        );
        if (price < 0) return;

        String insertSql =
            "INSERT INTO MenuItem (itmid, name, itmtyp, nat_pr, cr_acc, crdate, active) " +
            "VALUES (item_seq.NEXTVAL, ?, 'S', ?, NULL, NULL, 'Y')";

        int newItemId = -1;

        try (PreparedStatement ps = conn.prepareStatement(insertSql, new String[]{"itmid"})) {
            ps.setString(1, name);
            ps.setDouble(2, price);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                newItemId = keys.getInt(1);
            } else {
                String fetchSql = "SELECT item_seq.CURRVAL FROM dual";
                try (PreparedStatement ps2 = conn.prepareStatement(fetchSql)) {
                    ResultSet rs = ps2.executeQuery();
                    if (rs.next()) newItemId = rs.getInt(1);
                }
            }
        }

        if (newItemId == -1) {
            System.out.println("  Failed to create item.");
            conn.rollback();
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        RestaurantApp.clearScreen();
        System.out.println("--- Add Ingredients: " + name + " ---");

        int invCount = CustomerUI.addInventoryComponents(conn, newItemId);

        if (invCount == 0) {
            System.out.println("\n  Standard item must have at least one ingredient. Cancelling.");
            conn.rollback();
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        conn.commit();

        System.out.println("\n  Standard item added successfully.");
        System.out.println("  Item ID: " + newItemId);
        System.out.println("  Name:    " + name);
        System.out.println("  Price:   $" + String.format("%.2f", price));
        RestaurantApp.readLine("\nPress Enter to continue...");
    }

    // Update national price for an active standard menu item
    static void updateStandardItemPrice(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Update Standard Item Price ---");

        List<Integer> itemIds = printActiveStandardItems(conn);
        if (itemIds.isEmpty()) {
            System.out.println("  No active standard items found.");
            RestaurantApp.readLine("\nPress Enter to continue...");
            return;
        }

        int itemId = RestaurantApp.readInt("\nEnter item ID to update (0 to cancel): ");
        if (itemId == -1) return;

        if (!itemIds.contains(itemId)) {
            System.out.println("  Invalid item ID.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String itemName = getMenuItemName(conn, itemId);
        double newPrice = RestaurantApp.readPositivePrice(
            "New national price for \"" + itemName + "\" (or press Enter to cancel): $"
        );

        if (newPrice < 0) return;

        String confirm = RestaurantApp.readLine(
            "Update national price for \"" +
                itemName +
                "\" to $" +
                String.format("%.2f", newPrice) +
                "? Past orders will not change. (y/n): "
        );

        if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("  Cancelled.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String updateSql =
            "UPDATE MenuItem SET nat_pr = ? " + "WHERE itmid = ? AND itmtyp = 'S' AND active = 'Y'";

        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setDouble(1, newPrice);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }

        conn.commit();

        System.out.println("  Price updated.");
        RestaurantApp.readLine("  Press Enter to continue...");
    }

    // Helper — print active standard items and return their IDs
    static List<Integer> printActiveStandardItems(Connection conn) throws SQLException {
        String sql =
            "SELECT itmid, name, nat_pr FROM MenuItem " +
            "WHERE itmtyp = 'S' AND active = 'Y' " +
            "ORDER BY itmid";

        List<Integer> itemIds = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %-35s %s%n", "ID", "Name", "Current Price");
            RestaurantApp.divider();

            while (rs.next()) {
                int id = rs.getInt("itmid");
                itemIds.add(id);

                System.out.printf(
                    "  %-6d %-35s $%.2f%n",
                    id,
                    rs.getString("name"),
                    rs.getDouble("nat_pr")
                );
            }
        }

        return itemIds;
    }

    // Delete menu item — implemented as soft delete
    static void deleteMenuItem(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Delete Menu Item ---");
        System.out.println(
            "  Note: deleted items are hidden from ordering but kept for order history.\n"
        );

        String sql =
            "SELECT itmid, name, itmtyp FROM MenuItem " +
            "WHERE active = 'Y' ORDER BY itmtyp, itmid";

        List<Integer> itemIds = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %-35s %-8s%n", "ID", "Name", "Type");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                int id = rs.getInt("itmid");
                itemIds.add(id);

                String type = rs.getString("itmtyp").equals("S") ? "Standard" : "Custom";

                System.out.printf("  %-6d %-35s %-8s%n", id, rs.getString("name"), type);
            }

            if (!any) {
                System.out.println("  No active menu items found.");
                RestaurantApp.readLine("\nPress Enter to continue...");
                return;
            }
        }

        int itemId = RestaurantApp.readInt("\nEnter item ID to delete (0 to cancel): ");
        if (itemId == -1) return;

        if (!itemIds.contains(itemId)) {
            System.out.println("  Invalid item ID.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String itemName = getMenuItemName(conn, itemId);

        String confirm = RestaurantApp.readLine(
            "Delete \"" + itemName + "\"? This hides it from ordering but keeps history. (y/n): "
        );

        if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("  Cancelled.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String updateSql = "UPDATE MenuItem SET active = 'N' WHERE itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        }

        conn.commit();

        System.out.println("  Item deleted.");
        RestaurantApp.readLine("  Press Enter to continue...");
    }

    // Restore a soft-deleted menu item
    static void restoreMenuItem(Connection conn) throws SQLException {
        RestaurantApp.clearScreen();
        System.out.println("\n--- Restore Deleted Menu Item ---");

        String sql =
            "SELECT itmid, name, itmtyp FROM MenuItem " +
            "WHERE active = 'N' ORDER BY itmtyp, itmid";

        List<Integer> itemIds = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            System.out.printf("  %-6s %-35s %-8s%n", "ID", "Name", "Type");
            RestaurantApp.divider();

            boolean any = false;
            while (rs.next()) {
                any = true;
                int id = rs.getInt("itmid");
                itemIds.add(id);

                String type = rs.getString("itmtyp").equals("S") ? "Standard" : "Custom";

                System.out.printf("  %-6d %-35s %-8s%n", id, rs.getString("name"), type);
            }

            if (!any) {
                System.out.println("  No deleted menu items found.");
                RestaurantApp.readLine("\nPress Enter to continue...");
                return;
            }
        }

        int itemId = RestaurantApp.readInt("\nEnter item ID to restore (0 to cancel): ");
        if (itemId == -1) return;

        if (!itemIds.contains(itemId)) {
            System.out.println("  Invalid item ID.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String itemName = getMenuItemName(conn, itemId);

        String confirm = RestaurantApp.readLine(
            "Restore \"" + itemName + "\" so it can be ordered again? (y/n): "
        );

        if (confirm == null || !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("  Cancelled.");
            RestaurantApp.readLine("  Press Enter to continue...");
            return;
        }

        String updateSql = "UPDATE MenuItem SET active = 'Y' WHERE itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setInt(1, itemId);
            ps.executeUpdate();
        }

        conn.commit();

        System.out.println("  Item restored.");
        RestaurantApp.readLine("  Press Enter to continue...");
    }

    // Helper — get menu item name
    static String getMenuItemName(Connection conn, int itemId) throws SQLException {
        String sql = "SELECT name FROM MenuItem WHERE itmid = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
        }
        return "Item " + itemId;
    }
}
