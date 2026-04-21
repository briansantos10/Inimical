-- Drop all tables
DROP TABLE MIMeal CASCADE CONSTRAINTS;
DROP TABLE OrdMItm CASCADE CONSTRAINTS;
DROP TABLE MIComp CASCADE CONSTRAINTS;
DROP TABLE MICont CASCADE CONSTRAINTS;
DROP TABLE MILoc CASCADE CONSTRAINTS;
DROP TABLE Orders CASCADE CONSTRAINTS;
DROP TABLE CCard CASCADE CONSTRAINTS;
DROP TABLE MItem CASCADE CONSTRAINTS;
DROP TABLE InvItm CASCADE CONSTRAINTS;
DROP TABLE Acct CASCADE CONSTRAINTS;
DROP TABLE Loc CASCADE CONSTRAINTS;

-- Drop sequences if they exist
DROP SEQUENCE acct_seq;
DROP SEQUENCE item_seq;
DROP SEQUENCE ord_seq;

-- Sequences for ID generation
CREATE SEQUENCE acct_seq START WITH 200  INCREMENT BY 1;
CREATE SEQUENCE item_seq START WITH 3000 INCREMENT BY 1;
CREATE SEQUENCE ord_seq  START WITH 20000 INCREMENT BY 1;


-- ============================================
-- ENTITY TABLES
-- ============================================

-- Location
CREATE TABLE Loc (
    loc_id NUMBER(5) PRIMARY KEY,
    addr VARCHAR2(100),
    stname VARCHAR2(50),
    st_num VARCHAR2(10),
    city VARCHAR2(50),
    state CHAR(2),
    zip CHAR(5),
    tax_rt NUMBER(5,4)
);

-- -- Account
CREATE TABLE Acct (
    acc_id NUMBER(5) PRIMARY KEY,
    f_name VARCHAR2(30),
    m_name VARCHAR2(30),
    l_name VARCHAR2(30),
    email VARCHAR2(50),
    phone VARCHAR2(15),
    points NUMBER(8) DEFAULT 0
);

-- -- CreditCard
CREATE TABLE CCard (
    cc_num VARCHAR2(16) PRIMARY KEY,
    type VARCHAR2(15),
    cvc CHAR(4),
    expiry CHAR(5),
    acc_id NUMBER(5),
    FOREIGN KEY (acc_id) REFERENCES Acct(acc_id) ON DELETE SET NULL
);

-- MenuItem
CREATE TABLE MItem (
    itmid NUMBER(5) PRIMARY KEY,
    name VARCHAR2(50) NOT NULL,
    itmtyp CHAR(1) NOT NULL CHECK (itmtyp IN ('S', 'C')), -- Standard or Custom  
    nat_pr NUMBER(6,2),

    -- For Standard items: Both are NULL (created by Inimical's, not a customer)
    -- For Custom items: Both should have values (track who created it and when)
    cr_acc NUMBER(5), --  creator account ID (which Account created this item?)
    crdate DATE, --  creation date (when was it created?)

    FOREIGN KEY (cr_acc) REFERENCES Acct(acc_id) ON DELETE SET NULL
);

-- InventoryItem
CREATE TABLE InvItm (
    inv_id NUMBER(5) PRIMARY KEY,
    name VARCHAR2(50) NOT NULL,
    basect NUMBER(6,2),
    unit VARCHAR2(10) -- How you measure this ingredient
);

-- Order
CREATE TABLE Orders (
    ord_id NUMBER(10) PRIMARY KEY,
    placed TIMESTAMP NOT NULL,
    pickup TIMESTAMP,
    ordtyp CHAR(1) NOT NULL CHECK (ordtyp IN ('O', 'I')), -- Online or In-Person
    status VARCHAR2(15) DEFAULT 'pending',
    acc_id NUMBER(5),
    loc_id NUMBER(5) NOT NULL,
    cc_num VARCHAR2(16) NOT NULL,
    FOREIGN KEY (acc_id) REFERENCES Acct(acc_id) ON DELETE SET NULL, -- Account can be deleted without deleting the order, but we lose the info on who placed it
    FOREIGN KEY (loc_id) REFERENCES Loc(loc_id) ON DELETE CASCADE, -- If a location is deleted, we delete all orders associated with it (since they can't be fulfilled)
    FOREIGN KEY (cc_num) REFERENCES CCard(cc_num) ON DELETE CASCADE -- If a credit card is deleted, we delete all orders associated with it (since they can't be paid for)
);

-- ============================================
-- JUNCTION/RELATIONSHIP TABLES
-- ============================================

-- Multi-valued: meal types
CREATE TABLE MIMeal (
    itmid NUMBER(5),
    mltype VARCHAR2(10) CHECK (mltype IN ('lunch', 'dinner', 'dessert')),
    PRIMARY KEY (itmid, mltype),
    FOREIGN KEY (itmid) REFERENCES MItem(itmid) ON DELETE CASCADE -- If a MenuItem is deleted, we delete all its meal type associations (since the item no longer exists)
);

-- MenuItem Composed_of InventoryItem
CREATE TABLE MIComp (
    itmid NUMBER(5),
    inv_id NUMBER(5),
    qty NUMBER(5,2) NOT NULL,
    PRIMARY KEY (itmid, inv_id),
    FOREIGN KEY (itmid) REFERENCES MItem(itmid) ON DELETE CASCADE,
    FOREIGN KEY (inv_id) REFERENCES InvItm(inv_id) ON DELETE CASCADE
);

-- MenuItem Contains MenuItem (recursive)
-- contid = container item (custom item that contains others)
-- compid = component item (menu item being contained)
CREATE TABLE MICont (
    contid NUMBER(5),
    compid NUMBER(5),
    qty NUMBER(3) NOT NULL,
    PRIMARY KEY (contid, compid),
    FOREIGN KEY (contid) REFERENCES MItem(itmid) ON DELETE CASCADE,
    FOREIGN KEY (compid) REFERENCES MItem(itmid) -- 
);
-- Order includes MenuItem
CREATE TABLE OrdMItm (
    ord_id NUMBER(10),
    itmid NUMBER(5),
    qty NUMBER(3) NOT NULL,
    PRIMARY KEY (ord_id, itmid),
    FOREIGN KEY (ord_id) REFERENCES Orders(ord_id) ON DELETE CASCADE,
    FOREIGN KEY (itmid) REFERENCES MItem(itmid) ON DELETE CASCADE
);

-- MenuItem Priced_at Location
CREATE TABLE MILoc (
    itmid NUMBER(5),
    loc_id NUMBER(5),
    loc_pr NUMBER(6,2) NOT NULL,
    PRIMARY KEY (itmid, loc_id),
    FOREIGN KEY (itmid) REFERENCES MItem(itmid) ON DELETE CASCADE,
    FOREIGN KEY (loc_id) REFERENCES Loc(loc_id) ON DELETE CASCADE
);


-- Triggers

-- Enforce Standard vs Custom Item Rules
-- This ensures:
-- Standard items MUST have nat_pr
-- Custom items MUST have cr_acc and crdate
-- Custom items CANNOT have nat_pr
CREATE OR REPLACE TRIGGER check_item_type_rules
BEFORE INSERT OR UPDATE ON MItem
FOR EACH ROW
BEGIN
    -- Standard items must have national price
    IF :NEW.itmtyp = 'S' THEN
        IF :NEW.nat_pr IS NULL THEN
            RAISE_APPLICATION_ERROR(-20001, 'Standard items must have a national price');
        END IF;
        -- Standard items should not have creator info
        IF :NEW.cr_acc IS NOT NULL OR :NEW.crdate IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20002, 'Standard items cannot have creator information');
        END IF;
    
    -- Custom items must have creator info
    ELSIF :NEW.itmtyp = 'C' THEN
        IF :NEW.cr_acc IS NULL THEN
            RAISE_APPLICATION_ERROR(-20003, 'Custom items must have a creator account');
        END IF;
        -- Auto-set creation date if not provided
        IF :NEW.crdate IS NULL THEN
            :NEW.crdate := SYSDATE;
        END IF;
        -- Custom items should not have national price
        IF :NEW.nat_pr IS NOT NULL THEN
            RAISE_APPLICATION_ERROR(-20004, 'Custom items cannot have a national price');
        END IF;
    END IF;
END;
/


-- Only Custom Items Can Contain Other Items
CREATE OR REPLACE TRIGGER check_container_is_custom
BEFORE INSERT OR UPDATE ON MICont
FOR EACH ROW
DECLARE
    v_itmtyp CHAR(1);
BEGIN
    -- Check if container item is custom
    SELECT itmtyp INTO v_itmtyp FROM MItem WHERE itmid = :NEW.contid;
    
    IF v_itmtyp != 'C' THEN
        RAISE_APPLICATION_ERROR(-20005, 'Only custom items can contain other menu items');
    END IF;
END;
/

-- Validate Online Orders Have Pickup Time
CREATE OR REPLACE TRIGGER check_order_pickup_time
BEFORE INSERT OR UPDATE ON Orders
FOR EACH ROW
BEGIN
    -- Online orders must have pickup time
    IF :NEW.ordtyp = 'O' THEN
        IF :NEW.pickup IS NULL THEN
            RAISE_APPLICATION_ERROR(-20006, 'Online orders must have a pickup time');
        END IF;
        -- Pickup time must be in the future
        IF :NEW.pickup <= :NEW.placed THEN
            RAISE_APPLICATION_ERROR(-20007, 'Pickup time must be after order placement time');
        END IF;
    
    -- In-person orders should not have pickup time
    ELSIF :NEW.ordtyp = 'I' THEN
        IF :NEW.pickup IS NOT NULL THEN
            :NEW.pickup := NULL;  -- Auto-clear it
        END IF;
    END IF;
END;
/

-- Update Frequent-Eater Points (Advanced)
-- This automatically awards points when an order is complete
CREATE OR REPLACE TRIGGER update_loyalty_points
AFTER UPDATE ON Orders
FOR EACH ROW
BEGIN
    -- Award points when order status changes to 'completed'
    IF :NEW.status = 'completed' AND :OLD.status != 'completed' THEN
        -- Only for orders with an account (walk-ins don't earn points)
        IF :NEW.acc_id IS NOT NULL THEN
            -- Award 10 points per completed order
            UPDATE Acct 
            SET points = points + 10
            WHERE acc_id = :NEW.acc_id;
        END IF;
    END IF;
END;
/
