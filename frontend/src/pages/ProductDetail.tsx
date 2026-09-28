import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/client";

type Product = {
  id: number; name: string; description: string; price: number; category: string; availableStock: number;
};

export default function ProductDetail() {
  const { id } = useParams();
  const [product, setProduct] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.get<Product>(`/api/products/${id}`).then(setProduct).catch((e) => setError(e.message));
  }, [id]);

  async function addToCart() {
    setError(null);
    setMessage(null);
    try {
      await api.post("/api/cart/items", { productId: Number(id), quantity });
      setMessage("Added to cart.");
    } catch (e: any) {
      setError(e.message);
    }
  }

  if (!product) return <div style={{ color: "var(--text-dim)" }}>Loading...</div>;

  return (
    <div style={{ maxWidth: 560 }}>
      <h1>{product.name}</h1>
      <div className="subtitle">{product.category}</div>
      <p style={{ color: "var(--text-dim)" }}>{product.description || "No description provided."}</p>

      <div className="card" style={{ marginTop: 20 }}>
        <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16 }}>
          <span style={{ fontFamily: "var(--mono)", fontSize: 20 }}>${product.price.toFixed(2)}</span>
          <span className={product.availableStock > 0 ? "stock-tag" : "stock-tag out"}>
            {product.availableStock > 0 ? `${product.availableStock} in stock` : "Out of stock"}
          </span>
        </div>

        {error && <div className="error-banner">{error}</div>}
        {message && <div className="success-banner">{message}</div>}

        <div style={{ display: "flex", gap: 10, alignItems: "flex-end" }}>
          <div className="field" style={{ marginBottom: 0, width: 90 }}>
            <label>Qty</label>
            <input type="number" min={1} max={product.availableStock || 1} value={quantity}
                   onChange={(e) => setQuantity(Number(e.target.value))} />
          </div>
          <button className="btn" onClick={addToCart} disabled={product.availableStock <= 0}>
            Add to cart
          </button>
        </div>
      </div>
    </div>
  );
}
