import { useState } from "react";
import { api } from "../api/client";

type Stats = { id: number; productId: number; stockLimit: number; perUserLimit: number; status: string; remainingStock: number | null };
type PurchaseResponse = { result: string; orderId: number | null };

export default function FlashSale() {
  const [saleId, setSaleId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [stats, setStats] = useState<Stats | null>(null);
  const [result, setResult] = useState<PurchaseResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function loadStats() {
    setError(null);
    try { setStats(await api.get<Stats>(`/api/flash-sales/${saleId}/stats`)); }
    catch (e: any) { setError(e.message); }
  }

  async function buy() {
    setError(null); setResult(null);
    try {
      setResult(await api.post<PurchaseResponse>(`/api/flash-sales/${saleId}/purchase`, { quantity }));
      await loadStats();
    } catch (e: any) { setError(e.message); }
  }

  return (
    <div style={{ maxWidth: 520 }}>
      <h1>Flash Sale</h1>
      <div className="subtitle">Purchases are gated by an atomic Redis counter, so successful buys can never exceed the sale's stock.</div>
      {error && <div className="error-banner">{error}</div>}
      <div className="card">
        <div className="field"><label>Flash sale ID</label><input value={saleId} onChange={(e) => setSaleId(e.target.value)} /></div>
        <div className="field"><label>Quantity</label><input type="number" min={1} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} /></div>
        <div style={{ display: "flex", gap: 10 }}>
          <button className="btn secondary" onClick={loadStats} disabled={!saleId}>Check stock</button>
          <button className="btn" onClick={buy} disabled={!saleId}>Buy now</button>
        </div>
        {stats && (
          <p style={{ fontFamily: "var(--mono)", fontSize: 13 }}>
            status {stats.status} · remaining {stats.remainingStock ?? "n/a"} / {stats.stockLimit} · limit/user {stats.perUserLimit}
          </p>
        )}
        {result && (
          <div className={result.result === "SUCCESS" ? "success-banner" : "error-banner"}>
            {result.result}{result.orderId ? ` — order #${result.orderId}` : ""}
          </div>
        )}
      </div>
    </div>
  );
}
