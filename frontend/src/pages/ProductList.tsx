import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api/client";

type Product = {
  id: number;
  name: string;
  description: string;
  price: number;
  category: string;
  availableStock: number;
};

export default function ProductList() {
  const [products, setProducts] = useState<Product[]>([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<{ content: Product[] }>(`/api/products?query=${encodeURIComponent(query)}&size=24`);
      setProducts(res.content || []);
    } catch (err: any) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(); }, []);

  function stockClass(qty: number) {
    if (qty <= 0) return "stock-tag out";
    if (qty <= 5) return "stock-tag low";
    return "stock-tag";
  }

  return (
    <div>
      <h1>Catalog</h1>
      <div className="subtitle">Browse products across all sellers</div>

      <form onSubmit={(e) => { e.preventDefault(); load(); }} style={{ display: "flex", gap: 8, marginBottom: 24, maxWidth: 400 }}>
        <input placeholder="Search products..." value={query} onChange={(e) => setQuery(e.target.value)} />
        <button className="btn secondary" type="submit">Search</button>
      </form>

      {error && <div className="error-banner">{error}</div>}
      {loading ? (
        <div style={{ color: "var(--text-dim)" }}>Loading...</div>
      ) : (
        <div className="grid">
          {products.map((p) => (
            <Link to={`/products/${p.id}`} key={p.id} className="card" style={{ display: "block", color: "var(--text)" }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>{p.name}</div>
              <div style={{ color: "var(--text-dim)", fontSize: 13, marginBottom: 10 }}>{p.category}</div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontFamily: "var(--mono)" }}>${p.price.toFixed(2)}</span>
                <span className={stockClass(p.availableStock)}>{p.availableStock} left</span>
              </div>
            </Link>
          ))}
          {products.length === 0 && <div style={{ color: "var(--text-dim)" }}>No products found.</div>}
        </div>
      )}
    </div>
  );
}
