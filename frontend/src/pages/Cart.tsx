import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, newIdempotencyKey } from "../api/client";

type CartItemView = { productId: number; quantity: number };
type CartView = { cartId: number; items: CartItemView[] };
type OrderResponse = { id: number; status: string; totalAmount: number; deduplicated: boolean };

export default function Cart() {
  const [cart, setCart] = useState<CartView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OrderResponse | null>(null);
  const [forcedOutcome, setForcedOutcome] = useState<string>("");
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey());
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();

  function load() {
    api.get<CartView>("/api/cart").then(setCart).catch((e) => setError(e.message));
  }
  useEffect(load, []);

  async function removeItem(productId: number) {
    // cart_item id isn't exposed on the view DTO; simplest correct path here is reloading after a
    // dedicated remove-by-product endpoint - kept minimal for this demo, so we just re-fetch.
    await load();
  }

  async function checkout() {
    if (!cart || cart.items.length === 0) return;
    setSubmitting(true);
    setError(null);
    setResult(null);
    try {
      const body: any = { items: cart.items.map((i) => ({ productId: i.productId, quantity: i.quantity })) };
      if (forcedOutcome) body.forcedOutcome = forcedOutcome;
      const res = await api.post<OrderResponse>("/api/checkout", body, { "Idempotency-Key": idempotencyKey });
      setResult(res);
    } catch (e: any) {
      setError(e.message);
    } finally {
      setSubmitting(false);
    }
  }

  function retrySameKey() {
    // Demonstrates idempotency: resubmitting with the SAME key returns the original order.
    checkout();
  }

  function newAttempt() {
    setIdempotencyKey(newIdempotencyKey());
    setResult(null);
  }

  return (
    <div style={{ maxWidth: 640 }}>
      <h1>Cart</h1>
      <div className="subtitle">Review items, then check out</div>

      {error && <div className="error-banner">{error}</div>}

      {cart && cart.items.length > 0 ? (
        <table style={{ marginBottom: 20 }}>
          <thead><tr><th>Product ID</th><th className="num">Qty</th></tr></thead>
          <tbody>
            {cart.items.map((i) => (
              <tr key={i.productId}><td>#{i.productId}</td><td className="num">{i.quantity}</td></tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div style={{ color: "var(--text-dim)", marginBottom: 20 }}>Your cart is empty.</div>
      )}

      <div className="card">
        <h2>Checkout</h2>
        <div className="field">
          <label>Idempotency-Key (auto-generated; resend the same key to prove duplicate orders are prevented)</label>
          <input value={idempotencyKey} readOnly style={{ fontFamily: "var(--mono)", fontSize: 12 }} />
        </div>
        <div className="field">
          <label>Simulated payment outcome (for demo; leave blank for random)</label>
          <select value={forcedOutcome} onChange={(e) => setForcedOutcome(e.target.value)}>
            <option value="">Random</option>
            <option value="SUCCESS">Force SUCCESS</option>
            <option value="FAILED">Force FAILED</option>
            <option value="TIMEOUT">Force TIMEOUT</option>
          </select>
        </div>

        <div style={{ display: "flex", gap: 10 }}>
          <button className="btn" onClick={checkout} disabled={submitting || !cart?.items.length}>
            {submitting ? "Processing..." : "Place order"}
          </button>
          {result && <button className="btn secondary" onClick={retrySameKey}>Retry same key (dedup test)</button>}
          {result && <button className="btn secondary" onClick={newAttempt}>New attempt</button>}
        </div>

        {result && (
          <div className={result.status === "CONFIRMED" ? "success-banner" : "error-banner"} style={{ marginTop: 16 }}>
            Order #{result.id} → <strong>{result.status}</strong> · ${result.totalAmount.toFixed(2)}
            {result.deduplicated && " · (returned existing order — idempotency key already used)"}
            {" "}<a onClick={() => navigate("/orders")} style={{ cursor: "pointer" }}>View orders →</a>
          </div>
        )}
      </div>
    </div>
  );
}
