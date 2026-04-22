// NOTES: 
Orders.status column — currently always 'completed', could be dropped or kept as a stub for future work
Orders.pickup column — always NULL, could be dropped since you're scrapping pickup time
Orders.ordtyp column — currently always 'I', keep it since you may still want the online/in-person distinction even without pickup time (it's still meaningful information about how the order was placed)
The check_order_pickup_time trigger — currently a no-op since you never insert ordtyp = 'O', but harmless to leave
