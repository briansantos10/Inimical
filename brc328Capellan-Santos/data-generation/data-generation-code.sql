-- INIMICAL'S RESTAURANT - SAMPLE DATA

-- 1. LOCATIONS
INSERT INTO Location VALUES (1, '123 Main St',   'Main',   '123', 'Bethlehem',    'PA', '18015', 0.06);
INSERT INTO Location VALUES (2, '456 Broad Ave',  'Broad',  '456', 'Allentown',    'PA', '18102', 0.0625);
INSERT INTO Location VALUES (3, '789 Market St',  'Market', '789', 'Easton',       'PA', '18042', 0.06);
INSERT INTO Location VALUES (4, '321 Park Rd',    'Park',   '321', 'New York',     'NY', '10001', 0.08875);
INSERT INTO Location VALUES (5, '654 Oak Dr',     'Oak',    '654', 'Philadelphia', 'PA', '19102', 0.08);

-- 2. ACCOUNTS
INSERT INTO Account VALUES (101, 'John',  'A',  'Smith',    'jsmith@lehigh.edu',    '610-555-0101', 0);
INSERT INTO Account VALUES (102, 'Sarah', 'B',  'Johnson',  'sjohnson@gmail.com',   '610-555-0102', 0);
INSERT INTO Account VALUES (103, 'Mike',  NULL, 'Williams', 'mwilliams@yahoo.com',  '610-555-0103', 0);
INSERT INTO Account VALUES (104, 'Emily', 'C',  'Brown',    'ebrown@lehigh.edu',    '610-555-0104', 0);
INSERT INTO Account VALUES (105, 'David', 'D',  'Davis',    'ddavis@gmail.com',     '610-555-0105', 0);
INSERT INTO Account VALUES (106, 'Lisa',  NULL, 'Miller',   'lmiller@yahoo.com',    '610-555-0106', 0);
INSERT INTO Account VALUES (107, 'Brian', NULL, 'Capellan', 'brc328@lehigh.edu',    '610-555-0107', 0);

-- 3. CREDIT CARDS
-- Cards linked to accounts
INSERT INTO CreditCard VALUES ('4111111111111111', 'Visa',       '123',  '12/28', 101);
INSERT INTO CreditCard VALUES ('5500000000000004', 'Mastercard', '456',  '06/27', 102);
INSERT INTO CreditCard VALUES ('340000000000009',  'Amex',       '7890', '03/29', 103);
INSERT INTO CreditCard VALUES ('6011000000000004', 'Discover',   '321',  '09/26', 104);
INSERT INTO CreditCard VALUES ('4111111111111112', 'Visa',       '654',  '11/27', 105);
INSERT INTO CreditCard VALUES ('4111111111111115', 'Visa',       '999',  '06/29', 107);

-- One-time / guest cards (no account)
INSERT INTO CreditCard VALUES ('4111111111111113', 'Visa',       '111',  '08/26', NULL);
INSERT INTO CreditCard VALUES ('5500000000000005', 'Mastercard', '222',  '05/27', NULL);

-- 4. INVENTORY ITEMS
INSERT INTO InventoryItem VALUES (1,  'Burger Patty',    2.50, 'piece');
INSERT INTO InventoryItem VALUES (2,  'Cheese Slice',    0.50, 'slice');
INSERT INTO InventoryItem VALUES (3,  'Lettuce',         0.25, 'leaf');
INSERT INTO InventoryItem VALUES (4,  'Tomato',          0.30, 'slice');
INSERT INTO InventoryItem VALUES (5,  'Burger Bun',      0.75, 'piece');
INSERT INTO InventoryItem VALUES (6,  'Ketchup',         0.10, 'oz');
INSERT INTO InventoryItem VALUES (7,  'Mustard',         0.10, 'oz');
INSERT INTO InventoryItem VALUES (8,  'Pickles',         0.15, 'slice');
INSERT INTO InventoryItem VALUES (9,  'Onion',           0.20, 'slice');
INSERT INTO InventoryItem VALUES (10, 'Bacon',           1.00, 'strip');
INSERT INTO InventoryItem VALUES (11, 'French Fry',      0.05, 'oz');
INSERT INTO InventoryItem VALUES (12, 'Ice Cream',       0.75, 'scoop');
INSERT INTO InventoryItem VALUES (13, 'Chocolate Syrup', 0.25, 'oz');
INSERT INTO InventoryItem VALUES (14, 'Whipped Cream',   0.30, 'oz');
INSERT INTO InventoryItem VALUES (15, 'Milk',            0.50, 'cup');
INSERT INTO InventoryItem VALUES (16, 'Ripple Wine',     3.00, 'oz');

-- 5. STANDARD MENU ITEMS
-- active defaults to 'Y'. nat_pr required. cr_acc/crdate must be NULL.
INSERT INTO MenuItem VALUES (1001, 'Classic Cheeseburger', 'S', 8.99,  NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1002, 'Deluxe Burger',        'S', 10.99, NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1003, 'Bacon Burger',         'S', 11.99, NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1004, 'French Fries',         'S', 3.99,  NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1005, 'Fripple Milkshake',    'S', 5.99,  NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1006, 'Chocolate Shake',      'S', 4.99,  NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1007, 'Vanilla Shake',        'S', 4.99,  NULL, NULL, 'Y');
INSERT INTO MenuItem VALUES (1008, 'Ripple Milkshake',     'S', 7.99,  NULL, NULL, 'Y');

-- TRIGGER TESTS (standard items):
-- This FAILS: standard item without nat_pr
-- INSERT INTO MenuItem VALUES (1009, 'Bad Item', 'S', NULL, NULL, NULL, 'Y');
-- Error: Standard items must have a national price

-- This FAILS: standard item with creator info
-- INSERT INTO MenuItem VALUES (1010, 'Bad Item', 'S', 5.99, 101, SYSDATE, 'Y');
-- Error: Standard items cannot have creator information

-- 6. MEAL TYPES
INSERT INTO MenuItemMealType VALUES (1001, 'lunch');
INSERT INTO MenuItemMealType VALUES (1001, 'dinner');
INSERT INTO MenuItemMealType VALUES (1002, 'lunch');
INSERT INTO MenuItemMealType VALUES (1002, 'dinner');
INSERT INTO MenuItemMealType VALUES (1003, 'dinner');
INSERT INTO MenuItemMealType VALUES (1004, 'lunch');
INSERT INTO MenuItemMealType VALUES (1004, 'dinner');
INSERT INTO MenuItemMealType VALUES (1005, 'dessert');
INSERT INTO MenuItemMealType VALUES (1006, 'dessert');
INSERT INTO MenuItemMealType VALUES (1007, 'dessert');
INSERT INTO MenuItemMealType VALUES (1008, 'dessert');

-- 7. STANDARD ITEM INGREDIENTS
-- Classic Cheeseburger
INSERT INTO MenuItemIngredient VALUES (1001, 1, 1);   -- 1 patty
INSERT INTO MenuItemIngredient VALUES (1001, 2, 1);   -- 1 cheese
INSERT INTO MenuItemIngredient VALUES (1001, 5, 1);   -- 1 bun
INSERT INTO MenuItemIngredient VALUES (1001, 6, 1);   -- 1 oz ketchup

-- Deluxe Burger
INSERT INTO MenuItemIngredient VALUES (1002, 1, 1);   -- 1 patty
INSERT INTO MenuItemIngredient VALUES (1002, 2, 1);   -- 1 cheese
INSERT INTO MenuItemIngredient VALUES (1002, 3, 2);   -- 2 lettuce
INSERT INTO MenuItemIngredient VALUES (1002, 4, 2);   -- 2 tomato
INSERT INTO MenuItemIngredient VALUES (1002, 5, 1);   -- 1 bun
INSERT INTO MenuItemIngredient VALUES (1002, 8, 3);   -- 3 pickles

-- Bacon Burger
INSERT INTO MenuItemIngredient VALUES (1003, 1, 1);   -- 1 patty
INSERT INTO MenuItemIngredient VALUES (1003, 2, 1);   -- 1 cheese
INSERT INTO MenuItemIngredient VALUES (1003, 10, 3);  -- 3 bacon strips
INSERT INTO MenuItemIngredient VALUES (1003, 5, 1);   -- 1 bun

-- French Fries
INSERT INTO MenuItemIngredient VALUES (1004, 11, 8);  -- 8 oz fries

-- Fripple Milkshake
INSERT INTO MenuItemIngredient VALUES (1005, 12, 2);  -- 2 scoops ice cream
INSERT INTO MenuItemIngredient VALUES (1005, 13, 2);  -- 2 oz chocolate
INSERT INTO MenuItemIngredient VALUES (1005, 14, 1);  -- 1 oz whipped cream
INSERT INTO MenuItemIngredient VALUES (1005, 15, 1);  -- 1 cup milk

-- Chocolate Shake
INSERT INTO MenuItemIngredient VALUES (1006, 12, 2);
INSERT INTO MenuItemIngredient VALUES (1006, 13, 2);
INSERT INTO MenuItemIngredient VALUES (1006, 15, 1);

-- Vanilla Shake
INSERT INTO MenuItemIngredient VALUES (1007, 12, 2);
INSERT INTO MenuItemIngredient VALUES (1007, 15, 1);

-- Ripple Milkshake
INSERT INTO MenuItemIngredient VALUES (1008, 12, 2);
INSERT INTO MenuItemIngredient VALUES (1008, 13, 1);
INSERT INTO MenuItemIngredient VALUES (1008, 16, 2);  -- 2 oz Ripple wine
INSERT INTO MenuItemIngredient VALUES (1008, 15, 1);

-- 8. CUSTOM MENU ITEMS
-- active defaults to 'Y'. nat_pr must be NULL. cr_acc required.
INSERT INTO MenuItem VALUES (2001, 'Beast Burger',         'C', NULL, 101, TO_DATE('2026-02-15', 'YYYY-MM-DD'), 'Y');
INSERT INTO MenuItem VALUES (2002, 'Triple Combo',         'C', NULL, 102, TO_DATE('2026-02-16', 'YYYY-MM-DD'), 'Y');
INSERT INTO MenuItem VALUES (2003, 'Mega Fries',           'C', NULL, 103, TO_DATE('2026-02-20', 'YYYY-MM-DD'), 'Y');
INSERT INTO MenuItem VALUES (2004, 'Ultimate Shake',       'C', NULL, 104, TO_DATE('2026-03-01', 'YYYY-MM-DD'), 'Y');
INSERT INTO MenuItem VALUES (2005, 'Protein Power Burger', 'C', NULL, 105, TO_DATE('2026-03-05', 'YYYY-MM-DD'), 'Y');

-- TRIGGER TESTS (custom items):
-- This FAILS: custom item without creator
-- INSERT INTO MenuItem VALUES (2006, 'Bad Custom', 'C', NULL, NULL, NULL, 'Y');
-- Error: Custom items must have a creator account

-- This FAILS: custom item with national price
-- INSERT INTO MenuItem VALUES (2007, 'Bad Custom', 'C', 12.99, 101, SYSDATE, 'Y');
-- Error: Custom items cannot have a national price

-- 9. CUSTOM ITEM CONTENTS (MenuItemContains)
-- Beast Burger = Deluxe Burger + extra ingredients
INSERT INTO MenuItemContains VALUES (2001, 1002, 1);  -- 1x Deluxe Burger
INSERT INTO MenuItemIngredient VALUES (2001, 1, 2);   -- 2 extra patties
INSERT INTO MenuItemIngredient VALUES (2001, 2, 2);   -- 2 extra cheese
INSERT INTO MenuItemIngredient VALUES (2001, 10, 4);  -- 4 bacon strips

-- Triple Combo = Cheeseburger + Fries + Shake
INSERT INTO MenuItemContains VALUES (2002, 1001, 1);  -- 1x Classic Cheeseburger
INSERT INTO MenuItemContains VALUES (2002, 1004, 1);  -- 1x French Fries
INSERT INTO MenuItemContains VALUES (2002, 1006, 1);  -- 1x Chocolate Shake

-- Mega Fries = double fries
INSERT INTO MenuItemContains VALUES (2003, 1004, 2);  -- 2x French Fries

-- Ultimate Shake = Fripple + extra toppings
INSERT INTO MenuItemContains VALUES (2004, 1005, 1);  -- 1x Fripple Milkshake
INSERT INTO MenuItemIngredient VALUES (2004, 14, 3);  -- 3 oz whipped cream
INSERT INTO MenuItemIngredient VALUES (2004, 13, 2);  -- 2 oz chocolate

-- Protein Power = Bacon Burger + extra patties
INSERT INTO MenuItemContains VALUES (2005, 1003, 1);  -- 1x Bacon Burger
INSERT INTO MenuItemIngredient VALUES (2005, 1, 3);   -- 3 extra patties

-- TRIGGER TEST (MenuItemContains):
-- This FAILS: standard item cannot be a container
-- INSERT INTO MenuItemContains VALUES (1001, 1002, 1);
-- Error: Only custom items can contain other menu items

-- Meal types for custom items
INSERT INTO MenuItemMealType VALUES (2001, 'dinner');
INSERT INTO MenuItemMealType VALUES (2002, 'lunch');
INSERT INTO MenuItemMealType VALUES (2002, 'dinner');
INSERT INTO MenuItemMealType VALUES (2003, 'lunch');
INSERT INTO MenuItemMealType VALUES (2004, 'dessert');
INSERT INTO MenuItemMealType VALUES (2005, 'dinner');

-- 10. LOCAL PRICE OVERRIDES
-- NYC (loc_id=4) has higher prices
INSERT INTO MenuItemLocation VALUES (1001, 4, 11.99);  -- Classic Cheeseburger $11.99 in NYC
INSERT INTO MenuItemLocation VALUES (1002, 4, 13.99);  -- Deluxe Burger $13.99 in NYC
INSERT INTO MenuItemLocation VALUES (1005, 4, 7.99);   -- Fripple Shake $7.99 in NYC

-- Bethlehem (loc_id=1) has a discount on fries
INSERT INTO MenuItemLocation VALUES (1004, 1, 2.99);   -- Fries $2.99 in Bethlehem

-- 11. ORDERS
-- No pickup column. ordtyp 'I' = in-person, 'O' = online.
-- All orders marked completed so loyalty points trigger fires.
-- Guest orders have acc_id = NULL.

-- Bethlehem (loc_id=1)
INSERT INTO Orders VALUES (10001, TIMESTAMP '2026-03-15 12:30:00', 'I', 'completed', 101, 1, '4111111111111111');
INSERT INTO Orders VALUES (10002, TIMESTAMP '2026-03-15 13:45:00', 'I', 'completed', 102, 1, '5500000000000004');
INSERT INTO Orders VALUES (10003, TIMESTAMP '2026-03-16 18:20:00', 'I', 'completed', NULL, 1, '4111111111111113'); -- guest
INSERT INTO Orders VALUES (10005, TIMESTAMP '2026-03-15 10:00:00', 'O', 'completed', 104, 1, '6011000000000004');
INSERT INTO Orders VALUES (10008, TIMESTAMP '2026-03-18 16:00:00', 'O', 'completed', 102, 1, '5500000000000004');

-- Allentown (loc_id=2)
INSERT INTO Orders VALUES (10006, TIMESTAMP '2026-03-16 14:00:00', 'O', 'completed', 105, 2, '4111111111111112');

-- Easton (loc_id=3)
INSERT INTO Orders VALUES (10004, TIMESTAMP '2026-03-17 11:00:00', 'I', 'completed', 103, 3, '340000000000009');

-- NYC (loc_id=4) -- uses local price overrides
INSERT INTO Orders VALUES (10007, TIMESTAMP '2026-03-17 09:00:00', 'O', 'completed', 101, 4, '4111111111111111');

-- Philadelphia (loc_id=5)
INSERT INTO Orders VALUES (10009, TIMESTAMP '2026-03-19 12:00:00', 'I', 'completed', 106, 5, '4111111111111113');
INSERT INTO Orders VALUES (10010, TIMESTAMP '2026-03-19 13:00:00', 'I', 'completed', 107, 5, '4111111111111115');

-- 12. ORDER LINE ITEMS
-- unit_pr = price paid at time of order (snapshot of get_item_price at checkout)
-- This ensures historical totals are not affected by future price changes.

-- Order 10001 (Bethlehem, loc_id=1)
-- 1001: no override at loc 1 -> nat_pr $8.99
-- 1004: Bethlehem override -> $2.99
INSERT INTO OrderMenuItem VALUES (10001, 1001, 2, 8.99);
INSERT INTO OrderMenuItem VALUES (10001, 1004, 2, 2.99);

-- Order 10002 (Bethlehem, loc_id=1)
-- 1002: no override -> $10.99
-- 1006: no override -> $4.99
INSERT INTO OrderMenuItem VALUES (10002, 1002, 1, 10.99);
INSERT INTO OrderMenuItem VALUES (10002, 1006, 1, 4.99);

-- Order 10003 (Bethlehem, loc_id=1) - guest order
-- 1003: no override -> $11.99
-- 1004: Bethlehem override -> $2.99
-- 1007: no override -> $4.99
INSERT INTO OrderMenuItem VALUES (10003, 1003, 1, 11.99);
INSERT INTO OrderMenuItem VALUES (10003, 1004, 1, 2.99);
INSERT INTO OrderMenuItem VALUES (10003, 1007, 1, 4.99);

-- Order 10004 (Easton, loc_id=3)
-- 2001 Beast Burger (custom): Deluxe($10.99) + 2 patties($5.00) + 2 cheese($1.00) + 4 bacon($4.00) = $20.99
INSERT INTO OrderMenuItem VALUES (10004, 2001, 1, 20.99);

-- Order 10005 (Bethlehem, loc_id=1)
-- 2002 Triple Combo (custom): Classic($8.99) + Fries($2.99 override) + Choc Shake($4.99) = $16.97
INSERT INTO OrderMenuItem VALUES (10005, 2002, 1, 16.97);

-- Order 10006 (Allentown, loc_id=2)
-- 1001: no override -> $8.99
-- 1004: no override -> $3.99
INSERT INTO OrderMenuItem VALUES (10006, 1001, 3, 8.99);
INSERT INTO OrderMenuItem VALUES (10006, 1004, 3, 3.99);

-- Order 10007 (NYC, loc_id=4)
-- 1001: NYC override -> $11.99
INSERT INTO OrderMenuItem VALUES (10007, 1001, 1, 11.99);

-- Order 10008 (Bethlehem, loc_id=1)
-- 2004 Ultimate Shake (custom): Fripple($5.99) + 3oz whipped($0.90) + 2oz choc($0.50) = $7.39
INSERT INTO OrderMenuItem VALUES (10008, 2004, 1, 7.39);

-- Order 10009 (Philadelphia, loc_id=5)
-- 1005: no override -> $5.99
-- 1003: no override -> $11.99
INSERT INTO OrderMenuItem VALUES (10009, 1005, 1, 5.99);
INSERT INTO OrderMenuItem VALUES (10009, 1003, 1, 11.99);

-- Order 10010 (Philadelphia, loc_id=5) - Brian's account
-- 1001: no override -> $8.99
-- 1004: no override -> $3.99
INSERT INTO OrderMenuItem VALUES (10010, 1001, 2, 8.99);
INSERT INTO OrderMenuItem VALUES (10010, 1004, 1, 3.99);

-- VERIFY LOYALTY POINTS

UPDATE Account a
SET points = (
    SELECT COUNT(*) * 10
    FROM Orders o
    WHERE o.acc_id = a.acc_id
    AND o.status = 'completed'
)
WHERE EXISTS (
    SELECT 1 FROM Orders o
    WHERE o.acc_id = a.acc_id
);
COMMIT;



