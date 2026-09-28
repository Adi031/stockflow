import { useEffect, useState, FormEvent } from "react";
import { api } from "../api/client";

export default function AdminDashboard() {
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [orders, setOrders] = useState<any[]>([]);
  const [sale, setSale] = useState({ productId: "", stockLimit: "5", perUserLimit: "1", startAt: "", endAt: "" });
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activateId, setActivateId] = useState("");

  async function load() {
    try {
      setStats(await api.get("/api/admin/stats"));
      setOrders(await api.get("/api/admin/orders"));
    } catch (e: any) { setError(e.message); }
  }
  useEffect(() => { load(); }, []);

  async function createSale(e: FormEvent) {
    e.preventDefault(); setError(null); setNotice(null);
    try {
      const res: any = await api.post("/api/flash-sales", {
        productId: Number(sale.productId), stockLimit: Number(sale.stockLimit), perUserLimit: Number(sale.perUserLimit),
        startAt: sale.startAt, endAt: sale.endAt,
      });
      setNotice(`Flash sale #${res.id} scheduled. Activate it below to seed Redis and open purchases.`);
    } catch (err: any) { setError(err.message); }
  }

  async function activate() {
    setError(null); setNotice(null);
    try { await api.post(`/api/flash-sales/${activateId}/activate`); setNotice(`Flash sale #${activateId} is live.`); }
    catch (err: any) { setError(err.message); }
  }

  async function setStatus(id: number, status: string) {
    setError(null);
    try { await api.patch(`/api/orders/${id}/status`, { status }); await load(); }
    catch (err: any) { setError(err.message); }
  }

  return (
    <div>
      <h1>Admin Dashboard</h1>
      <div className="subtitle">System statistics, orders, and flash sales</div>
      {error && <div className="error-banner">{error}</div>}
      {notice && <div className="success-banner">{notice}</div>}

      {stats && (
        <table style={{ marginBottom: 28, maxWidth: 420 }}>
          <tbody>
            {Object.entries(stats).map(([k, v]) => (<tr key={k}><td>{k}</td><td className="num">{v}</td></tr>))}
          </tbody>
        </table>
      )}

      <div style={{ display: "flex", gap: 20, flexWrap: "wrap", marginBottom: 28 }}>
        <form onSubmit={createSale} className="card" style={{ width: 360 }}>
          <h2>Schedule flash sale</h2>
          <div className="field"><label>Product ID</label><input value={sale.productId} onChange={(e) => setSale({ ...sale, productId: e.target.value })} required /></div>
          <div style={{ display: "flex", gap: 10 }}>
            <div className="field" style={{ flex: 1 }}><label>Stock</label><input type="number" value={sale.stockLimit} onChange={(e) => setSale({ ...sale, stockLimit: e.target.value })} /></div>
            <div className="field" style={{ flex: 1 }}><label>Per user</label><input type="number" value={sale.perUserLimit} onChange={(e) => setSale({ ...sale, perUserLimit: e.target.value })} /></div>
          </div>
          <div className="field"><label>Starts</label><input type="datetime-local" value={sale.startAt} onChange={(e) => setSale({ ...sale, startAt: e.target.value })} required /></div>
          <div className="field"><label>Ends</label><input type="datetime-local" value={sale.endAt} onChange={(e) => setSale({ ...sale, endAt: e.target.value })} required /></div>
          <button className="btn" type="submit">Schedule</button>
        </form>
        <div className="card" style={{ width: 260, alignSelf: "flex-start" }}>
          <h2>Activate sale</h2>
          <div className="field"><label>Flash sale ID</label><input value={activateId} onChange={(e) => setActivateId(e.target.value)} /></div>
          <button className="btn" onClick={activate} disabled={!activateId}>Activate now</button>
        </div>
      </div>

      <table>
        <thead><tr><th>Order</th><th>User</th><th>Status</th><th className="num">Total</th><th>Advance</th></tr></thead>
        <tbody>
          {orders.map((o) => (
            <tr key={o.id}>
              <td className="num">#{o.id}</td><td className="num">{o.userId}</td>
              <td><span className={`badge ${o.status}`}>{o.status}</span></td>
              <td className="num">${Number(o.totalAmount).toFixed(2)}</td>
              <td style={{ display: "flex", gap: 6 }}>
                {["PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"].map((s) => (
                  <button key={s} className="btn secondary" style={{ padding: "3px 8px", fontSize: 12 }} onClick={() => setStatus(o.id, s)}>{s}</button>
                ))}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
