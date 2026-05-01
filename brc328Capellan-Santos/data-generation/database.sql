-- Drop all tables
DROP TABLE MenuItemMealType CASCADE CONSTRAINTS;
DROP TABLE OrderMenuItem CASCADE CONSTRAINTS;
DROP TABLE MenuItemIngredient CASCADE CONSTRAINTS;
DROP TABLE MenuItemContains CASCADE CONSTRAINTS;
DROP TABLE MenuItemLocation CASCADE CONSTRAINTS;
DROP TABLE Orders CASCADE CONSTRAINTS;
DROP TABLE CreditCard CASCADE CONSTRAINTS;
DROP TABLE MenuItem CASCADE CONSTRAINTS;
DROP TABLE InventoryItem CASCADE CONSTRAINTS;
DROP TABLE Account CASCADE CONSTRAINTS;
DROP TABLE Location CASCADE CONSTRAINTS;

-- Drop sequences
DROP SEQUENCE acct_seq;
DROP SEQUENCE item_seq;
DROP SEQUENCE ord_seq;

-- Drop triggers
DROP TRIGGER check_item_type_rules;
DROP TRIGGER check_container_is_custom;
DROP TRIGGER update_loyalty_points;

-- Drop function
DROP FUNCTION get_item_price;

-- SEQUENCES

CREATE SEQUENCE acct_seq START WITH 200  INCREMENT BY 1;
CREATE SEQUENCE item_seq START WITH 3000 INCREMENT BY 1;
CREATE SEQUENCE ord_seq  START WITH 20000 INCREMENT BY 1;

-- ENTITY TABLES

-- Location
CREATE TABLE Location (
    loc_id NUMBER(5) PRIMARY KEY,
    addr   VARCHAR2(100),
    stname VARCHAR2(50),
    st_num VARCHAR2(10),
    city   VARCHAR2(50),
    state  CHAR(2),
    zip    CHAR(5),
    tax_rt NUMBER(5,4)
);

-- Account
CREATE TABLE Account (
    acc_id NUMBER(5) PRIMARY KEY,
    f_name VARCHAR2(30),
    m_name VARCHAR2(30),
    l_name VARCHAR2(30),
    email  VARCHAR2(50),
    phone  VARCHAR2(15),
    points NUMBER(8) DEFAULT 0
);

-- CreditCard
-- acc_id is nullable: supports guest/one-time cards not tied to any account.
-- ON DELETE SET NULL: if an account is deleted, the card record is kept
-- but disassociated, preserving any order history referencing it.
CREATE TABLE CreditCard (
    cc_num VARCHAR2(16) PRIMARY KEY,
    type   VARCHAR2(15),
    cvc    CHAR(4),
    expiry CHAR(5),
    acc_id NUMBER(5),
    FOREIGN KEY (acc_id) REFERENCES Account(acc_id) ON DELETE SET NULL
);

-- MenuItem
-- itmtyp = 'S' (Standard) or 'C' (Custom)
-- Standard items: nat_pr is set, cr_acc and crdate are NULL
-- Custom items:   nat_pr is NULL, cr_acc and crdate are set
-- active = 'Y'/'N': soft delete flag. Set to 'N' instead of hard deleting
-- so that historical orders referencing the item are preserved.
CREATE TABLE MenuItem (
    itmid  NUMBER(5) PRIMARY KEY,
    name   VARCHAR2(50) NOT NULL,
    itmtyp CHAR(1) NOT NULL CHECK (itmtyp IN ('S', 'C')),
    nat_pr NUMBER(6,2),
    cr_acc NUMBER(5),
    crdate DATE,
    active CHAR(1) DEFAULT 'Y' NOT NULL CHECK (active IN ('Y', 'N')),
    FOREIGN KEY (cr_acc) REFERENCES Account(acc_id) ON DELETE SET NULL
);

-- InventoryItem
CREATE TABLE InventoryItem (
    inv_id NUMBER(5) PRIMARY KEY,
    name   VARCHAR2(50) NOT NULL,
    basect NUMBER(6,2),
    unit   VARCHAR2(10)
);

-- Orders
-- acc_id is nullable: guest orders have no account link.
-- cc_num is nullable: set to NULL if the card is later disassociated.
-- ON DELETE SET NULL on cc_num: removing a card does not destroy order history.
-- ON DELETE CASCADE on loc_id: if a location is closed and deleted,
-- its orders are deleted too since they can no longer be fulfilled.
CREATE TABLE Orders (
    ord_id NUMBER(10) PRIMARY KEY,
    placed TIMESTAMP NOT NULL,
    ordtyp CHAR(1) NOT NULL CHECK (ordtyp IN ('O', 'I')),
    status VARCHAR2(15) DEFAULT 'pending',
    acc_id NUMBER(5),
    loc_id NUMBER(5) NOT NULL,
    cc_num VARCHAR2(16),
    FOREIGN KEY (acc_id) REFERENCES Account(acc_id) ON DELETE SET NULL,
    FOREIGN KEY (loc_id) REFERENCES Location(loc_id) ON DELETE CASCADE,
    FOREIGN KEY (cc_num) REFERENCES CreditCard(cc_num) ON DELETE SET NULL
);

-- JUNCTION TABLES

-- Multi-valued: meal types per menu item
CREATE TABLE MenuItemMealType (
    itmid  NUMBER(5),
    mltype VARCHAR2(10) CHECK (mltype IN ('lunch', 'dinner', 'dessert')),
    PRIMARY KEY (itmid, mltype),
    FOREIGN KEY (itmid) REFERENCES MenuItem(itmid) ON DELETE CASCADE
);

-- MenuItem Composed_of InventoryItem
CREATE TABLE MenuItemIngredient (
    itmid  NUMBER(5),
    inv_id NUMBER(5),
    qty    NUMBER(5,2) NOT NULL,
    PRIMARY KEY (itmid, inv_id),
    FOREIGN KEY (itmid)  REFERENCES MenuItem(itmid)     ON DELETE CASCADE,
    FOREIGN KEY (inv_id) REFERENCES InventoryItem(inv_id) ON DELETE CASCADE
);

-- MenuItem Contains MenuItem (recursive relationship)
-- contid = the container item (always a custom item, enforced by trigger)
-- compid = the component item being contained
CREATE TABLE MenuItemContains (
    contid NUMBER(5),
    compid NUMBER(5),
    qty    NUMBER(3) NOT NULL,
    PRIMARY KEY (contid, compid),
    FOREIGN KEY (contid) REFERENCES MenuItem(itmid) ON DELETE CASCADE,
    FOREIGN KEY (compid) REFERENCES MenuItem(itmid)
);

-- Order line items
-- unit_pr: price per item at the time the order was placed.
-- Stored at checkout so that future price changes or overrides do not
-- retroactively alter historical order totals or revenue reports.
-- ON DELETE SET NULL on itmid: if a menu item is soft-deleted (active='N')
-- we do not hard-delete it, so this cascade should never fire in practice.
-- It is kept as a safety net only.
CREATE TABLE OrderMenuItem (
    ord_id  NUMBER(10),
    itmid   NUMBER(5),
    qty     NUMBER(3)   NOT NULL,
    unit_pr NUMBER(6,2),
    PRIMARY KEY (ord_id, itmid),
    FOREIGN KEY (ord_id) REFERENCES Orders(ord_id)  ON DELETE CASCADE,
    FOREIGN KEY (itmid)  REFERENCES MenuItem(itmid) ON DELETE SET NULL
);

-- Location-specific price overrides
CREATE TABLE MenuItemLocation (
    itmid  NUMBER(5),
    loc_id NUMBER(5),
    loc_pr NUMBER(6,2) NOT NULL,
    PRIMARY KEY (itmid, loc_id),
    FOREIGN KEY (itmid)  REFERENCES MenuItem(itmid)  ON DELETE CASCADE,
    FOREIGN KEY (loc_id) REFERENCES Location(loc_id) ON DELETE CASCADE
);

-- TRIGGERS

-- Enforce Standard vs Custom item rules on MenuItem.
-- Standard items MUST have nat_pr and MUST NOT have cr_acc/crdate.
-- Custom items MUST have cr_acc and MUST NOT have nat_pr.
-- crdate is auto-set to SYSDATE if omitted on a custom item insert.
CREATE OR REPLACE TRIGGER check_item_type_rules
BEFORE INSERT OR UPDATE ON MenuItem
FOR EACH ROW
BEGIN
    IF :NEW.itmtyp = 'S' THEN
        IF :NEW.nat_pr IS NULL THEN
            RAISE_APPLICATION_ERROR(-20001, 'Standard items must have a national price');
        END IF;
        IF :NEW.cr_acc IS NOT NULL OR :NEW.crdate IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20002, 'Standard items cannot have creator information');
        END IF;
    ELSIF :NEW.itmtyp = 'C' THEN
        IF :NEW.cr_acc IS NULL THEN
            RAISE_APPLICATION_ERROR(-20003, 'Custom items must have a creator account');
        END IF;
        IF :NEW.crdate IS NULL THEN
            :NEW.crdate := SYSDATE;
        END IF;
        IF :NEW.nat_pr IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20004, 'Custom items cannot have a national price');
        END IF;
    END IF;
END;
/

-- Enforce that only custom items may contain other menu items.
CREATE OR REPLACE TRIGGER check_container_is_custom
BEFORE INSERT OR UPDATE ON MenuItemContains
FOR EACH ROW
DECLARE
    v_itmtyp CHAR(1);
BEGIN
    SELECT itmtyp INTO v_itmtyp FROM MenuItem WHERE itmid = :NEW.contid;
    IF v_itmtyp != 'C' THEN
        RAISE_APPLICATION_ERROR(-20005, 'Only custom items can contain other menu items');
    END IF;
END;
/

-- Award 10 loyalty points when an order transitions to completed.
-- Guest orders (acc_id IS NULL) are skipped.
CREATE OR REPLACE TRIGGER update_loyalty_points
AFTER UPDATE ON Orders
FOR EACH ROW
BEGIN
    IF :NEW.status = 'completed' AND :OLD.status != 'completed' THEN
        IF :NEW.acc_id IS NOT NULL THEN
            UPDATE Account
            SET points = points + 10
            WHERE acc_id = :NEW.acc_id;
        END IF;
    END IF;
END;
/

-- PL/SQL FUNCTION

-- Computes the effective price of any menu item at a given location.
-- For standard items: returns the local override price if one exists,
-- otherwise the national price.
-- For custom items: recursively sums inventory component costs (basect * qty)
-- plus the price of any contained menu items (with local overrides applied
-- at each level of the recursion).
CREATE OR REPLACE FUNCTION get_item_price(p_itmid NUMBER, p_loc_id NUMBER)
RETURN NUMBER IS
    v_itmtyp      CHAR(1);
    v_nat_pr      NUMBER(6,2);
    v_loc_pr      NUMBER(6,2);
    v_total       NUMBER := 0;
    v_child_price NUMBER;
BEGIN
    SELECT itmtyp, nat_pr INTO v_itmtyp, v_nat_pr
    FROM MenuItem WHERE itmid = p_itmid;

    IF v_itmtyp = 'S' THEN
        BEGIN
            SELECT loc_pr INTO v_loc_pr
            FROM MenuItemLocation WHERE itmid = p_itmid AND loc_id = p_loc_id;
            RETURN v_loc_pr;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                RETURN NVL(v_nat_pr, 0);
        END;
    ELSE
        SELECT NVL(SUM(i.basect * c.qty), 0) INTO v_total
        FROM MenuItemIngredient c JOIN InventoryItem i ON c.inv_id = i.inv_id
        WHERE c.itmid = p_itmid;

        FOR rec IN (SELECT compid, qty FROM MenuItemContains WHERE contid = p_itmid) LOOP
            v_child_price := get_item_price(rec.compid, p_loc_id);
            v_total := v_total + (v_child_price * rec.qty);
        END LOOP;

        RETURN v_total;
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN RETURN 0;
END;
/
