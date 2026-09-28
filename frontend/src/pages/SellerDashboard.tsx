import { useEffect, useState, FormEvent } from "react";
import { api } from "../api/client";

type Product = { id: number; name: string; price: number; category: string; status: string };

export default function SellerDashboard() {
  const [products, setProducts] = useState<Product[]>([]);
  const [storeName, setStoreName] = useState("");
  const [hasProfile, setHasProfile] = useState(true);
  const [form, setForm] = useState({ name: "", description: "", price: "", category: "", initialStock: "10" });
  const [restock, setRestock] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function load() {
    try {
      await api.get("/api/seller/profile");
      setHasProfile(true);
      setProducts(await api.get<Product[]>("/api/seller/products"));
    } catch { setHasProfile(false); }
  }
  useEffect(() => { load(); }, []);

  async function createProfile(e: FormEvent) {
    e.preventDefault(); setError(null);
    try { await api.post("/api/seller/profile", { storeName }); await load(); }
    catch (err: any) { setError(err.message); }
  }

  async function createProduct(e: FormEvent) {
    e.preventDefault(); setError(null); setNotice(null);
    try {
      await api.post("/api/seller/products", { ...form, price: Number(form.price), initialStock: Number(form.initialStock) });
      setNotice("Product created."); setForm({ name: "", description: "", price: "", category: "", initialStock: "10" });
      await load();
    } catch (err: any) { setError(err.message); }
  }

  async function doRestock(id: number) {
    setError(null);
    try { await api.patch(`/api/seller/products/${id}/restock`, { totalStock: Number(restock[id]) }); setNotice(`Stock updated for #${id}.`); }
    catch (err: any) { setError(err.message); }
  }

  if (!hasProfile) {
    return (
      <div style={{ maxWidth: 420 }}>
        <h1>Open your store</h1>
        {error && <div className="error-banner">{error}</div>}
        <form onSubmit={createProfile} className="card">
          <div className="field"><label>Store name</label><input value={storeName} onChange={(e) => setStoreName(e.target.value)} required /></div>
          <button className="btn" type="submit">Create store</button>
        </form>
      </div>
    );
  }

  return (
    <div>
      <h1>Seller Dashboard</h1>
      <div className="subtitle">Manage products and stock levels</div>
      {error && <div className="error-banner">{error}</div>}
      {notice && <div className="success-banner">{notice}</div>}

      <form onSubmit={createProduct} className="card" style={{ marginBottom: 28, maxWidth: 560 }}>
        <h2>New product</h2>
        <div className="field"><label>Name</label><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required /></div>
        <div className="field"><label>Description</label><input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></div>
        <div style={{ display: "flex", gap: 10 }}>
          <div className="field" style={{ flex: 1 }}><label>Price</label><input type="number" step="0.01" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} required /></div>
          <div className="field" style={{ flex: 1 }}><label>Category</label><input value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} /></div>
          <div className="field" style={{ flex: 1 }}><label>Initial stock</label><input type="number" min={0} value={form.initialStock} onChange={(e) => setForm({ ...form, initialStock: e.target.value })} /></div>
        </div>
        <button className="btn" type="submit">Create product</button>
      </form>

      <table>
        <thead><tr><th>ID</th><th>Name</th><th className="num">Price</th><th>Category</th><th>Set total stock</th></tr></thead>
        <tbody>
          {products.map((p) => (
            <tr key={p.id}>
              <td className="num">#{p.id}</td><td>{p.name}</td><td className="num">${Number(p.price).toFixed(2)}</td><td>{p.category}</td>
              <td style={{ display: "flex", gap: 8 }}>
                <input style={{ width: 90 }} type="number" min={0} value={restock[p.id] ?? ""} onChange={(e) => setRestock({ ...restock, [p.id]: e.target.value })} />
                <button className="btn secondary" onClick={() => doRestock(p.id)} disabled={restock[p.id] === undefined || restock[p.id] === ""}>Update</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
