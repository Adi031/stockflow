import { NavLink, Route, Routes, Navigate } from "react-router-dom";
import { useAuth } from "./store/AuthContext";
import Login from "./pages/Login";
import Register from "./pages/Register";
import ProductList from "./pages/ProductList";
import ProductDetail from "./pages/ProductDetail";
import Cart from "./pages/Cart";
import Orders from "./pages/Orders";
import FlashSale from "./pages/FlashSale";
import SellerDashboard from "./pages/SellerDashboard";
import AdminDashboard from "./pages/AdminDashboard";

function Protected({ children, role }: { children: JSX.Element; role?: string }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  const { user, logout } = useAuth();

  if (!user) {
    return (
      <Routes>
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Login />} />
      </Routes>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
          <div className="brand">Stock<span>Flow</span></div>
          <div className="brand-tag">inventory &amp; order platform</div>
        </div>
        <NavLink to="/" end className="nav-link">Products</NavLink>
        <NavLink to="/cart" className="nav-link">Cart</NavLink>
        <NavLink to="/orders" className="nav-link">My Orders</NavLink>
        <NavLink to="/flash-sale" className="nav-link">Flash Sale</NavLink>
        {user.role === "SELLER" && <NavLink to="/seller" className="nav-link">Seller Dashboard</NavLink>}
        {user.role === "ADMIN" && <NavLink to="/admin" className="nav-link">Admin Dashboard</NavLink>}
        <div style={{ marginTop: "auto", paddingTop: 20 }}>
          <div style={{ color: "var(--text-dim)", fontSize: 12, marginBottom: 8 }}>
            Role: {user.role}
          </div>
          <button className="btn secondary" onClick={logout} style={{ width: "100%" }}>Log out</button>
        </div>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<ProductList />} />
          <Route path="/products/:id" element={<ProductDetail />} />
          <Route path="/cart" element={<Cart />} />
          <Route path="/orders" element={<Orders />} />
          <Route path="/flash-sale" element={<FlashSale />} />
          <Route path="/seller" element={<Protected role="SELLER"><SellerDashboard /></Protected>} />
          <Route path="/admin" element={<Protected role="ADMIN"><AdminDashboard /></Protected>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
