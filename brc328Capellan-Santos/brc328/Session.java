import java.util.ArrayList;
import java.util.List;

public class Session {
    // Account info — null if guest
    Integer accountId;
    String firstName;
    String lastName;

    // Currently selected location
    Integer locId;
    String locCity;

    // Order type for current order: 'I' = in-person, 'O' = online
    // Defaults to in-person; set at the start of placeOrder()
    char ordTyp = 'I';

    // Shopping cart
    List<CartItem> cart;

    public Session() {
        this.accountId = null;
        this.firstName = null;
        this.lastName  = null;
        this.locId     = null;
        this.locCity   = null;
        this.ordTyp    = 'I';
        this.cart      = new ArrayList<>();
    }

    public boolean isLoggedIn() {
        return accountId != null;
    }

    public void clearCart() {
        cart.clear();
    }

    public void logout() {
        accountId = null;
        firstName = null;
        lastName  = null;
        ordTyp    = 'I';
        cart.clear();
        // Keep location — they may want to place another order
    }

    // Add item to cart; if item already exists, increment quantity
    public void addToCart(int itemId, String itemName, int qty) {
        for (CartItem ci : cart) {
            if (ci.itemId == itemId) {
                ci.qty += qty;
                return;
            }
        }
        cart.add(new CartItem(itemId, itemName, qty));
    }

    public boolean cartIsEmpty() {
        return cart.isEmpty();
    }

    // ----------------------------------------------------------------
    // Inner class — one line item in the cart
    // ----------------------------------------------------------------
    public static class CartItem {
        int itemId;
        String itemName;
        int qty;

        CartItem(int itemId, String itemName, int qty) {
            this.itemId   = itemId;
            this.itemName = itemName;
            this.qty      = qty;
        }
    }
}
