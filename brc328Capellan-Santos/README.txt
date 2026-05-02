Brian Capellan-Santos
CSE 241 - Database Systems
Inimical's Restaurant Database Project
Spring 2026



HOW TO RUN:
From the top-level directory (brc328Capellan-Santos/):

    java -jar brc328.jar

You will be prompted for your Oracle user ID and password. The program connects
to rocordb01.cse.lehigh.edu:1522/cse241pdb. If login fails, you will be
re-prompted until a valid connection is established.

COMPILATION (if needed)
    cd brc328
    javac -cp ../ojdbc11.jar *.java
    cd ..
    jar cfmv brc328.jar Manifest.txt -C brc328 .



INTERFACE OVERVIEW:
The application opens to a top-level menu with three interfaces. No interface
requires a separate login beyond the Oracle password entered at startup, except
the Customer interface, which offers optional account login.

    1. Customer
    2. Management
    3. Location Manager
    4. Exit



SUGGESTED FIRST EXPERIENCE FOR GRADERS:

Option A: Guest order (fastest path)
1. Select "1. Customer"
2. Select "1. Guest"
3. Select any location
4. Browse the menu and add an item, such as:
      1001 2
5. Press "C" to checkout
6. Enter any 16-digit card number, such as:
      1234-5678-9012-1234
7.Confirm the order and review the printed receipt

Option B: Logged-in order with existing account
1. Select "1. Customer"
2. Select "2. Login"
3. Use the existing account:
      brc328@lehigh.edu
4. Place an order
5. Return to the customer home screen
6. View order history to confirm the order appears with stored line-item prices
7. Open Manage Payment Methods to view or remove saved cards

Option C: Create and order a custom menu item
1. Log in as a registered customer
2. Select "4. Build Custom Item"
3. Choose a starting point: standard item, custom item, or scratch
4. Add at least one inventory ingredient
5. Confirm that the new custom item is created
6. Place a new order and select "View Custom Items" to order it



SUGGESTED TESTS / QUERIES FOR GRADERS:

Customer interface:
- Place a guest order and verify that the order completes without account login.
- Place a logged-in order and verify that the receipt shows 10 loyalty points earned.
- Return to Order History and drill into the new order to confirm line totals, tax, and total.
- Remove a saved payment method and verify that past orders remain visible.

Management interface:
- Run Sales Report by Location with no date filter to see all-time revenue.
- Run Sales Report with a narrow date range to confirm that locations with zero matching orders still appear with $0.00 revenue.
- Run Top-Selling Menu Items with no location filter, then rerun it with a specific location ID to compare chain-wide and location-specific popularity.
- Run Customer Activity Report to view customers sorted by order count and loyalty points.
- Add a new standard menu item with a national price and at least one inventory ingredient. Return to the Customer interface and verify that the new item appears in menu browsing.

Location Manager interface:
- Select location 1.
- View orders and drill into one order by entering its order number.
- Confirm that order detail shows stored line totals from OrderMenuItem.unit_pr.
- Open Manage Price Overrides.
- Add or update a local price override for a standard item.
- Verify that the override appears in the list.
- Remove the override and confirm the item returns to its national price.



INTERFACE 1: CUSTOMER
Accessed from the top-level menu by selecting 1.

Entry options:
=============================
  Welcome to Inimical's
=============================
  1. Guest 
  2. Login
  3. Create Account
  4. Back
=============================

The three entry paths diverge initially before converging at the main menu.

Path A: Selecting "1. Guest" skips authentication entirely and proceeds directly to location selection. Guest orders are always treated as in-person.


Path B: Selecting "2. Login" prompts for an email address. Accounts are identified
by email rather than a numeric ID.

	Enter email: brc328@lehigh.edu

On success, the customer home screen is shown with the account holder's current loyalty points balance.

=============================
  Hello, Brian!
  Points: 150
=============================
  1. Place Order
  2. Order History
  3. Manage Payment Methods
  4. Build Custom Item
  5. Logout
=============================

From the customer home, selecting "1. Place Order" first prompts for order type (in-person or online) before proceeding to location selection. This option is not shown to guests, who are always in-person. 

Path C: Selecting "3. Create Account" prompts for first name, middle name (optional, press Enter to skip), last name, email, and phone number. Duplicate emails are rejected with a message. On success, the account is created and the customer home screen is shown immediately.


Location Selection (all paths): Both guests and logged-in customers reach this screen. Logged-in customers see it after choosing their order type (online or in-person).

--- Select a Location ---
  ID     City                      Address
-----------------------------
  1      Bethlehem, PA             123 Main St
  2      Allentown, PA             456 Broad St
  ...
Enter location ID (0 to cancel):


Menu Browsing (all paths): After selecting a location, all customers reach the same menu screen.

--- Menu ---
  1. View All Items
  2. Filter by Meal Type
  3. View Custom Items 
  V. View / Manage Cart
  C. Checkout
  X. Cancel Order

1. "View All Items" and 2. "Filter by Meal Type" display items with their effective
price at the selected location. For standard items, this is the national price,
or a local override if one exists for that location. 


3. View custom items: the price is computed recursively from their component costs (inventory base costs and the prices of any contained menu items, with local overrides applied at each level). Prices are resolved by calling get_item_price() per row. 

Performance note:  In a real system, calling a SQL function per row will be costly, but, with a menu of this size, the difference from a direct column lookup is negligible. An alternative for a real production system might cache prices or use a view instead.


V. View / Manage item cart: Enter the item ID and quantity at the prompt:
      > 1001 2

The cart below:
--- Your Cart ---
  #    ID     Item                                Qty        Total
-----------------------------
  1    2001   Beast Burger                 	   1         $20.99
….. 

Shows each item with its line total and a running subtotal before tax. 
Options: 
- R <#> to remove a specific item (e.g. R 2) 
- C clear the entire cart. 
- B return to the menu


To checkout: Pressing "C" from the menu (with a non-empty cart) shows an order summary with subtotal, the location's tax rate applied, and the final total. The customer confirms, then selects or enters a payment method. All orders require a credit card. Loyalty points cannot be redeemed for payment.

At checkout time, the price of each item is captured and stored in the database as unit_pr in the OrderMenuItem table. This ensures that future price changes or location-specific overrides do not retroactively alter historical order totals or revenue reports.

Logged-in customers can choose from any stored card on their account or enter a new one. New cards can optionally be saved to the account. Guests always enter a card manually.

Card numbers are accepted as 16 digits with or without dashes. CVC is not collected at entry time; the schema models it as a nullable field since a real payment processor would handle CVC validation externally rather than storing it in the database. Card type is not inferred from the card number, so all cards entered through the application are stored with type 'one-time' regardless of issuer. 

A few consequences of the current card storage design worth noting: 
- If a user enters a new card but declines to save it to their account, the card is still inserted into the database with acc_id = NULL. It will not appear in their saved cards view but the record persists. This means it can be silently reused by a future guest entering the same number.
- If a guest enters a card number already on file under any account, the system silently reuses the existing record without prompting for expiry or disclosing that the number was recognized. Revealing that a card exists in the system would be a privacy concern. Any malicious user could otherwise probe the database by trying card numbers and observing whether the expiry prompt appears. 

On confirmation of an order, a receipt is printed:

=============================
       ORDER CONFIRMED
=============================
  Order #:   20240
  Type:      In-person
  Location:  Bethlehem
  Card:      **** **** **** 1234
-----------------------------
  Subtotal:                      $20.99
  Tax:                           $1.26
  Total:                         $22.25
-----------------------------
  Loyalty points earned: 10    <- Logged-in customers see this on the receipt.
  Thank you for your order!
=============================

The checkout screen computes the subtotal using the current effective price of each cart item at the selected location. After the customer confirms the order and payment method, those same item prices are stored in OrderMenuItem.unit_pr when the order's line items are inserted. The printed receipt uses the subtotal, tax, and total computed during checkout. 

Points are awarded by the update_loyalty_points trigger, which fires when an order's status is updated to 'completed'. In the current implementation, every successful checkout is first inserted with status 'pending' and then immediately updated to 'completed' after the order items are inserted. This causes the trigger to award 10 loyalty points to logged-in customers after each completed checkout.

In a full production system, order status would not be completed immediately at checkout. Instead, a kitchen or fulfillment interface would update orders from 'pending' to 'completed' once the order was actually prepared or picked up.


We can now expand on the other customer home screen options (logged in only): 
=============================
  Hello, Brian!
  Points: 150
=============================
  1. Place Order
  2. Order History
  3. Manage Payment Methods
  4. Build Custom Item
  5. Logout
=============================

2. Order History: Lists all past orders for the logged-in account with placement time, order type (in-person or online), and location. Enter any order number from the list to view that order's full detail: each item ordered, quantity, line total, tax, and final amount paid.

Order detail uses the stored values from OrderMenuItem.unit_pr, so historical order totals remain accurate even if menu prices or location-specific overrides change after the order was placed.


3. Manage Payment Methods: Displays all credit cards stored under the logged-in account. Cards can be added or removed. Adding a card follows the same flow as entering a card at checkout: the card number and expiry date are collected, while CVC is not prompted for the reasons noted in the checkout section above.

When a customer removes a saved card, the card record is not deleted from the database. Instead, the application sets CreditCard.acc_id to NULL. This means the card no longer appears in the customer's saved payment methods, but any past orders that used the card remain intact.

The implementation still checks for pending orders before removal, even though all successful orders are immediately marked completed.


4. Building a custom item: Shows the customer's own custom items and all other custom items created chain-wide before prompting to begin.

  How would you like to start?
  1. Start from a Standard Item
  2. Start from a Custom Item
  3. Start from scratch
  0. Cancel

If a base item is chosen, its direct ingredients and contained items are displayed for reference. Note that this display is not recursive. If the base item itself contains another menu item, that nested item's components are not expanded. The base item is then automatically linked as a contained item in the new custom item.

You can only add to a base item, not remove from it. If you want a completely different combination, start from scratch instead.

Name the new item. Duplicate names are rejected before insertion. At least one inventory ingredient is required. A name-only item or a base item with no additional ingredients is rejected and the transaction is rolled back.

A custom item may use another custom item as its base, which may itself have been built on another, and so on. The building implementation supports adding inventory ingredients on top of a chosen base. Adding additional menu items as manual components is not done in the interface. 

Custom item pricing is computed recursively. The price is the sum of each inventory item's base cost multiplied by quantity, plus the effective price of any contained menu items. Location-specific price overrides are applied at each level of the recursion when contained standard items are priced.

Meal types are not assigned to custom items since the choice is subjective. Custom items are available chain-wide immediately upon creation.



INTERFACE 2: MANAGEMENT
Accessed from the top-level menu by selecting 2. No additional login required beyond the Oracle password entered at startup. 

The management interface provides reporting and limited menu administration. It serves primarily as a reporting and analytics tool for business oversight, while also allowing management to add standard menu items, update national prices, delete menu items, and restore deleted menu items.

Deleting a menu item is implemented as a soft delete. The item remains in the MenuItem table, but its active flag is set to 'N'. This hides it from customer browsing and ordering while preserving records.

=============================
Management Console
=============================
1. Sales Report by Location
2. Top-Selling Menu Items
3. Customer Activity Report
4. Manage Menu Items
5. Back
=============================

All three reports share the following behavior:
- Optional date range filter prompted before each report. Enter dates in YYYY-MM-DD format. Either bound may be left blank to leave that end open. Press Enter on both to see all-time data.
- Paginated output. Use N and P to navigate pages, and B to go back.
    a. Handled in Java after retrieving the result set. For the small sample dataset in this project, this is sufficient. In a production system, pagination would be pushed into SQL using OFFSET/FETCH or ROW_NUMBER() so that only the requested page of rows is returned from the database.


Revenue in the sales report is calculated from the stored OrderMenuItem.unit_pr value multiplied by quantity. This ensures that revenue reflects what customers actually paid at checkout, rather than recomputing totals from current menu prices or current location-specific overrides.

1. Sales report by location:  Shows revenue, order count, and average order value per location, ranked by revenue descending.

2. Top-Selling Menu Items: Shows ordered menu items ranked by total quantity sold, with item ID, name, type (standard or custom), total quantity sold, and number of distinct orders containing the item.

Before the date range prompt, an optional location filter is shown. Press Enter to aggregate across all locations, or enter a location ID to see rankings for that location only.

Items that have never been ordered are excluded because ranking them is not meaningful. If a menu item is later deactivated, its historical sales can still appear in this report because reporting is based on past order data rather than current menu availability.

3. Customer Activity Report: Shows all registered accounts sorted by order count, then loyalty points. The displayed rank is a sequential display rank based on the sorted output, so tied customers still receive separate row numbers rather than shared dense or competition ranks.

4. Manage Menu Items: Allows management to add standard menu items, update national prices for active standard items, delete menu items, and restore deleted menu items.

When adding a standard item, management enters the item name, national price, and at least one inventory ingredient. If no ingredients are added, the item creation is rolled back. This ensures that newly added standard items have both a listed price and an ingredient breakdown.

Meal type assignment was left out of the interface for simplicity. These items still appear in "View All Items" but will not appear under meal-type filters unless meal types are inserted manually in the database.

Updating a national price affects future menu browsing and future orders only. Historical order totals and revenue reports remain unchanged because completed orders use the stored OrderMenuItem.unit_pr value.

Deleting a menu item does not remove it from the database. Instead, MenuItem.active is set to 'N'. Deleted items are hidden from customer browsing, ordering, and custom item creation, but remain available for historical order details and reports. Deleted items can be restored by setting active back to 'Y'.



INTERFACE 3: LOCATION MANAGER

Accessed from the top-level menu by selecting 3. No additional login required beyond the Oracle password entered at startup.

Upon entering, the manager selects their location from a displayed list. All subsequent actions apply to that location. The location can be changed at any time without returning to the top-level menu.

--- Select Your Location ---
  ID     City                      Address
-----------------------------
  1      Bethlehem, PA             123 Main St
  ...
Enter location ID (0 to cancel):

Once a location is selected, the location manager menu is shown:

=============================
  Bethlehem, PA
  Location Manager
=============================
  1. View Orders
  2. Manage Price Overrides
  3. Switch Location
  4. Back
=============================


1. View Orders: Displays a paginated list of all orders placed at the selected location, sorted by placement time descending. Shows order ID, timestamp, order type, customer name (or "Guest" for orders without an account), and total item count.


Enter any order number from the list to see its detailed view:

--- Order #20202 ---
  Placed   : 2026-04-30 15:40
  Type     : Online
  Location : Bethlehem
  Card     : **** **** **** 9999
-----------------------------
  ID     Item                                Qty        Line Total
-----------------------------
  1001   Classic Cheeseburger                 2          $17.98
  2001   Beast Burger                         1          $20.99
-----------------------------
  Subtotal (excl. tax):                                  $38.97
-----------------------------

Line item totals are calculated using the stored OrderMenuItem.unit_pr. This means the location manager order detail reflects the actual prices paid at checkout, even if menu prices or location-specific overrides change later.



DATABASE DESIGN NOTES:

SCHEMA:
Table list: Location, Account, CreditCard, MenuItem, InventoryItem, Orders, MenuItemMealType, MenuItemIngredient, MenuItemContains, MenuItemLocation, OrderMenuItem.

SEQUENCES:
acct_seq  -- Account IDs (starts at 200)
item_seq  -- MenuItem IDs (starts at 3000; standard items use IDs below 3000)
ord_seq   -- Order IDs (starts at 20000)

Sequences are used for ID generation.

TRIGGERS:
  check_item_type_rules
    BEFORE INSERT OR UPDATE ON MenuItem.
    Enforces that standard items always have a national price and no creator
   information. Enforces that custom items have a creator account and no
    national price. Auto-sets crdate to SYSDATE if omitted on a custom item
    insert.

  check_container_is_custom
    BEFORE INSERT OR UPDATE ON MenuItemContains.
    Enforces that only custom items may contain other menu items. Standard
    items cannot be containers.

  update_loyalty_points
    AFTER UPDATE ON Orders.
    Awards 10 loyalty points to the associated account when an order's status
    transitions to 'completed'. Guest orders are skipped because acc_id is NULL.

PL/SQL FUNCTION:
  get_item_price(p_itmid NUMBER, p_loc_id NUMBER) RETURNS NUMBER

    Computes the current effective price of a menu item at a given location.

    For standard items, it returns the location-specific override if one exists;
    otherwise it returns the national price.

    For custom items, it recursively sums inventory component costs
    (basect * qty) and the effective prices of any contained menu items,
    applying location-specific overrides at each level.

    This function is used for current menu display.

    Historical order totals and revenue reports do not depend on this function.
    Once an order is confirmed, each line item's price is stored in
    OrderMenuItem.unit_pr, and historical views use that stored value.

KEY DESIGN DECISIONS:
- CreditCard.acc_id is nullable. This supports guest and one-time cards that are not tied to any customer account.
- Orders.acc_id is nullable. This allows guest orders to be stored without an account link. If an account is deleted, its past orders remain in the database with acc_id set to NULL.
- Orders.cc_num is nullable and uses ON DELETE SET NULL. If a card is later removed or disassociated, order history remains intact.
- OrderMenuItem.unit_pr stores the price per item at checkout time. This preserves historical accuracy even if national prices, location-specific overrides, or custom item component costs change later.
- MenuItem.active is a soft-delete flag. Items can be hidden from normal customer browsing and ordering without being physically removed from the database. This allows historical orders to continue referencing the original menu item.
- MenuItemContains is a self-referential table on MenuItem. contid is the container item and compid is the contained item. This supports nested custom items.
- ON DELETE CASCADE is used where child records are meaningless without the parent, such as meal type rows without a menu item or order line items without an order.
- ON DELETE SET NULL is used where the historical record still has value even if the referenced parent is removed or disassociated, such as orders tied to accounts, orders tied to credit cards, and order line items tied to menu items.

ERROR HANDLING:
- Raw Oracle errors and stack traces are not shown directly to users.
- Unexpected SQL exceptions are logged to error.log with timestamp and stack trace, while a short friendly message is shown to the user.
- Known constraint violations, such as duplicate emails, duplicate custom item ingredients, and invalid IDs, show friendly messages and allow re-entry.


ADDITIONAL INTERFACES 
In a full production system, the following interfaces would also be included:

  Kitchen Display Interface
    Shows pending orders for kitchen staff. Allows staff to update orders as
    they are prepared, ready, picked up, or completed. In the current project,
    successful checkouts are immediately marked 'completed', but a real system
    would let kitchen or fulfillment staff control that status transition.

  Inventory Management Interface
    Allows staff to update inventory stock levels, add new inventory items, and
    set reorder thresholds. This could also support availability checks before
    allowing custom items to be created or ordered.

Full Admin / Role Management Interface
    The current Management interface includes limited menu administration:
    adding standard menu items, updating national prices, and deleting/restoring
    menu items through the active flag. A full admin interface would go further
    by managing locations, user roles, customer accounts, employee permissions,
    and raw table data. It would also include role-based access control, which
    is outside the current project scope.



DATA SOURCES

SQL schema, trigger, function, and sequence code written and reviewed by Brian Capellan-Santos.
- Java source code written and reviewed by Brian Capellan-Santos.
- Sample data (in data-generation-code.sql), including location addresses, customer names, and menu items, was generated with AI assistance for database population purposes.
- README wording, debugging discussion, and some display/pagination guidance were developed with AI assistance and reviewed by Brian Capellan-Santos.
- Oracle JDBC driver: ojdbc11.jar obtained from the Oracle Maven repository.
- No code or data was received from or shared with other students.
