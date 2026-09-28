import { useEffect, useState } from "react";
import { api } from "../api/client";

type OrderItemView = { productId: number; quantity: number; unitPrice: number };
type OrderResponse = { id: number; status: string; totalAmount: number; items: OrderItemView[]; createdAt: string };

export default function Orders() {
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<OrderResponse[]>("/api/orders").then(setOrders).catch((e) => setError(e.message));
  }, []);

  return (
    <div>
      <h1>My Orders</h1>
      <div className="subtitle">Lifecycle: PENDING → PAYMENT_PROCESSING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED (or CANCELLED)</div>
      {error && <div className="error-banner">{error}</div>}

      <table>
        <thead>
          <tr><th>Order</th><th>Status</th><th className="num">Total</th><th>Items</th><th>Placed</th></tr>
        </thead>
        <tbody>
          {orders.map((o) => (
            <tr key={o.id}>
              <td className="num">#{o.id}</td>
              <td><span className={`badge ${o.status}`}>{o.status}</span></td>
              <td className="num">${o.totalAmount.toFixed(2)}</td>
              <td>{o.items.map((i) => `#${i.productId} ×${i.quantity}`).join(", ")}</td>
              <td style={{ color: "var(--text-dim)" }}>{new Date(o.createdAt).toLocaleString()}</td>
            </tr>
          ))}
          {orders.length === 0 && (
            <tr><td colSpan={5} style={{ color: "var(--text-dim)" }}>No orders yet.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
