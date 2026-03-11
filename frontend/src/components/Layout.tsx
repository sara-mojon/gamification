import { useState, useEffect } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import { Menu, Code, User, Trophy, LogOut, ShieldAlert } from "lucide-react";

export default function Layout() {
  const [isOpen, setIsOpen] = useState(true);
  
  const [isAdmin, setIsAdmin] = useState(false); 
  
  const location = useLocation();
  const auth = useAuth();
  const token = auth.user?.access_token;

  useEffect(() => {
    const sincronizarUsuario = async () => {
      if (auth.isAuthenticated && token) {
        try {
          const response = await fetch('http://localhost:8080/api/users/sync', {
            method: 'POST',
            headers: {
              'Authorization': `Bearer ${token}`,
              'Content-Type': 'application/json'
            }
          });
          
          if (response.ok) {
            const userData = await response.json();
            if (userData.role === "admin") {
              setIsAdmin(true);
            }
          }
        } catch (error) {
          console.error("Error de red:", error);
        }
      }
    };

    sincronizarUsuario();
  }, [auth.isAuthenticated, token]);

  const menuItems = [
    { path: "/retos", name: "Retos", icon: <Code size={20} /> },
    { path: "/perfil", name: "Mi Perfil", icon: <User size={20} /> },
    { path: "/ranking", name: "Clasificación", icon: <Trophy size={20} /> },
  ];

  return (
    <div style={{ display: "flex", height: "100vh", backgroundColor: "#f5f5f5" }}>
      
      <div style={{ width: isOpen ? "250px" : "70px", backgroundColor: "#1e1e1e", color: "white", transition: "width 0.3s", display: "flex", flexDirection: "column" }}>
        <div style={{ padding: "20px", display: "flex", alignItems: "center", justifyContent: isOpen ? "space-between" : "center" }}>
          {isOpen && <h2 style={{ margin: 0, fontSize: "1.2rem", color: "#ff4b4b", fontFamily: "monospace" }}>CODEWARS</h2>}
          <button onClick={() => setIsOpen(!isOpen)} style={{ background: "none", border: "none", color: "white", cursor: "pointer", padding: "5px" }}>
            <Menu size={24} />
          </button>
        </div>

        <nav style={{ flex: 1, marginTop: "20px" }}>
          {menuItems.map((item) => {
            const isActive = location.pathname === item.path;
            return (
              <Link key={item.path} to={item.path} style={{ display: "flex", alignItems: "center", padding: "15px 20px", color: isActive ? "#ff4b4b" : "#ccc", textDecoration: "none", backgroundColor: isActive ? "#2d2d2d" : "transparent", justifyContent: isOpen ? "flex-start" : "center", transition: "background-color 0.2s" }}>
                {item.icon}
                {isOpen && <span style={{ marginLeft: "15px", fontWeight: isActive ? "bold" : "normal" }}>{item.name}</span>}
              </Link>
            );
          })}

          {isAdmin && (
            <div style={{ marginTop: "30px", borderTop: "1px solid #333", paddingTop: "10px" }}>
              <Link to="/admin" style={{ display: "flex", alignItems: "center", padding: "15px 20px", color: location.pathname === "/admin" ? "#ff4b4b" : "#ff9800", textDecoration: "none", backgroundColor: location.pathname === "/admin" ? "#2d2d2d" : "transparent", justifyContent: isOpen ? "flex-start" : "center", transition: "background-color 0.2s" }}>
                <ShieldAlert size={20} />
                {isOpen && <span style={{ marginLeft: "15px", fontWeight: location.pathname === "/admin" ? "bold" : "normal" }}>Panel Admin</span>}
              </Link>
            </div>
          )}
        </nav>

        <div style={{ padding: "15px 0", marginBottom: "10px" }}>
          <button onClick={() => void auth.signoutRedirect()} style={{ display: "flex", alignItems: "center", width: "100%", padding: "15px 20px", backgroundColor: "transparent", border: "none", color: "#ccc", cursor: "pointer", justifyContent: isOpen ? "flex-start" : "center", transition: "background-color 0.2s" }} onMouseOver={(e) => { e.currentTarget.style.backgroundColor = "#2d2d2d"; e.currentTarget.style.color = "white"; }} onMouseOut={(e) => { e.currentTarget.style.backgroundColor = "transparent"; e.currentTarget.style.color = "#ccc"; }}>
            <LogOut size={20} />
            {isOpen && <span style={{ marginLeft: "15px", fontWeight: "normal", fontSize: "1rem" }}>Cerrar Sesión</span>}
          </button>
        </div>
      </div>

      <div style={{ flex: 1, display: "flex", flexDirection: "column" }}>
        <div style={{ height: "65px", backgroundColor: "white", borderBottom: "1px solid #e0e0e0", display: "flex", alignItems: "center", justifyContent: "flex-end", padding: "0 30px", boxShadow: "0 2px 4px rgba(0,0,0,0.02)" }}>
          <span style={{ color: "#555", fontWeight: "500" }}>
            Hola, <span style={{ color: "#1e1e1e", fontWeight: "bold" }}>{auth.user?.profile.preferred_username}</span>
            {isAdmin && <span style={{ marginLeft: "10px", backgroundColor: "#ff9800", color: "white", padding: "2px 8px", borderRadius: "10px", fontSize: "0.8rem", fontWeight: "bold" }}>ADMIN</span>}
          </span>
        </div>
        <div style={{ flex: 1, padding: "40px", overflowY: "auto" }}>
          <Outlet /> 
        </div>
      </div>
    </div>
  );
}