// frontend/src/pages/Admin.tsx

import { useState, useEffect } from "react";
import { useAuth } from "react-oidc-context";
import { ShieldAlert, Users, Search, Trash2, Shield, User as UserIcon, Loader2, Mail, X } from "lucide-react";
import toast from 'react-hot-toast';
import '../css/Admin.css';

interface User {
  id: number;
  keycloakId: string;
  username: string;
  email: string;
  role: string;
  score: number;
  preferredLanguage: string;
}

export default function Admin() {
  const auth = useAuth();
  const token = auth.user?.access_token;

  // --- VARIABLE DE ENTORNO DINÁMICA ---
  const baseUrl = import.meta.env.VITE_USER_URL || 'http://localhost:8080';

  const [usuarios, setUsuarios] = useState<User[]>([]);
  const [cargando, setCargando] = useState(true);
  const [busqueda, setBusqueda] = useState("");
  const [esAdminGlobal, setEsAdminGlobal] = useState(false);
  
  const [modalEliminar, setModalEliminar] = useState({ abierto: false, id: 0, nombre: "" });
  const overlayStyle: React.CSSProperties = { position: "fixed", top: 0, left: 0, right: 0, bottom: 0, backgroundColor: "rgba(0,0,0,0.6)", zIndex: 1000, display: "flex", justifyContent: "center", alignItems: "center" };
  const modalStyle: React.CSSProperties = { backgroundColor: "white", padding: "30px", borderRadius: "10px", width: "90%", maxWidth: "500px", position: "relative", boxShadow: "0 10px 25px rgba(0,0,0,0.2)", overflow: "hidden" };
  const btnCerrarStyle: React.CSSProperties = { position: "absolute", top: "15px", right: "15px", background: "none", border: "none", cursor: "pointer", color: "#888" };

  useEffect(() => {
    const verificarAcceso = async () => {
      try {
        const res = await fetch(`${baseUrl}/api/users/me`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) {
          const userData = await res.json();
          const role = userData.role?.trim().toLowerCase();

          if (role === "admin") {
            setEsAdminGlobal(true);
            cargarUsuarios();
          } else {
            setCargando(false);
          }
        }
      } catch (e) {
        console.error("Error verificando permisos", e);
        setCargando(false);
      }
    };

    const cargarUsuarios = async () => {
      try {
        const res = await fetch(`${baseUrl}/api/users`, {
          headers: { 'Authorization': `Bearer ${token}` }
        });
        if (res.ok) {
          const data = await res.json();
          const dataNormalizada = data.map((u: User) => ({
            ...u,
            role: u.role ? u.role.toLowerCase() : "user"
          }));
          setUsuarios(dataNormalizada);
        } else {
          toast.error("Error al cargar la lista de usuarios");
        }
      } catch (e) {
        console.error(e);
        toast.error("Error de conexión con el servidor");
      } finally {
        setCargando(false);
      }
    };

    if (token) verificarAcceso();
  }, [token, baseUrl]);

  // --- FUNCIÓN: CAMBIAR ROL ---
  const cambiarRol = async (idUsuario: number, nuevoRol: string, nombreUsuario: string) => {
    const peticion = fetch(`${baseUrl}/api/users/${idUsuario}`, {
      method: "PATCH",
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ role: nuevoRol }) 
    }).then(async (res) => {
      if (!res.ok) throw new Error("Fallo al actualizar rol");
      setUsuarios(usuariosAct => 
        usuariosAct.map(u => u.id === idUsuario ? { ...u, role: nuevoRol } : u)
      );
      return res;
    });

    toast.promise(
      peticion,
      {
        loading: `Actualizando rol de ${nombreUsuario}...`,
        success: `¡${nombreUsuario} ahora es ${nuevoRol}! `,
        error: `Error al cambiar el rol de ${nombreUsuario} `,
      },
      { style: { backgroundColor: '#1e1e1e', color: '#fff' } }
    );
  };

  // --- FUNCIÓN: CONFIRMAR ELIMINACIÓN ---
  const confirmarEliminacion = async () => {
    const { id, nombre } = modalEliminar;
    
    setModalEliminar({ abierto: false, id: 0, nombre: "" });

    const peticion = fetch(`${baseUrl}/api/users/${id}`, {
      method: "DELETE",
      headers: { 'Authorization': `Bearer ${token}` }
    }).then(async (res) => {
      if (!res.ok) throw new Error("Fallo al eliminar");
      setUsuarios(usuariosAct => usuariosAct.filter(u => u.id !== id));
      return res;
    });

    toast.promise(
      peticion,
      {
        loading: `Eliminando a ${nombre}...`,
        success: `¡Usuario ${nombre} eliminado del sistema! `,
        error: `Error al eliminar a ${nombre} `,
      },
      { style: { backgroundColor: '#1e1e1e', color: '#fff' } }
    );
  };

  const usuariosFiltrados = usuarios.filter(u => 
    u.username.toLowerCase().includes(busqueda.toLowerCase()) || 
    (u.email && u.email.toLowerCase().includes(busqueda.toLowerCase()))
  );

  if (cargando) {
    return (
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", marginTop: "100px", color: "#666" }}>
        <Loader2 size={40} style={{ animation: "spin 1s linear infinite", marginBottom: "20px" }} />
        <h2>Conectando con el panel de control...</h2>
      </div>
    );
  }

  if (!esAdminGlobal) {
    return (
      <div style={{ textAlign: "center", marginTop: "100px", padding: "40px", backgroundColor: "#fff5f5", borderRadius: "10px", border: "1px solid #ffcdd2", color: "#d32f2f" }}>
        <ShieldAlert size={64} style={{ marginBottom: "20px" }} />
        <h1>Acceso Denegado</h1>
        <p>No tienes los permisos necesarios para ver esta página.</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "1200px", margin: "0 auto", paddingBottom: "50px" }}>
      
      {/* Cabecera */}
      <div style={{ marginBottom: "30px", display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
        <div>
          <h1 style={{ fontSize: "2.5rem", color: "#1e1e1e", marginBottom: "10px", display: "flex", alignItems: "center", gap: "15px" }}>
            <Shield color="#ff9800" size={36} /> Panel de Administración
          </h1>
          <p style={{ color: "#666", fontSize: "1.1rem", margin: 0 }}>
            Gestiona los accesos, roles y perfiles de los usuarios de la plataforma.
          </p>
        </div>
        
        {/* Estadísticas rápidas */}
        <div style={{ display: "flex", gap: "20px" }}>
          <div style={{ backgroundColor: "white", padding: "15px 25px", borderRadius: "8px", border: "1px solid #eee", textAlign: "center" }}>
            <span style={{ display: "block", fontSize: "0.9rem", color: "#888", fontWeight: "bold" }}>Total Usuarios</span>
            <span style={{ display: "block", fontSize: "1.8rem", color: "#1e1e1e", fontWeight: "bold" }}>{usuarios.length}</span>
          </div>
          <div style={{ backgroundColor: "white", padding: "15px 25px", borderRadius: "8px", border: "1px solid #eee", textAlign: "center" }}>
            <span style={{ display: "block", fontSize: "0.9rem", color: "#888", fontWeight: "bold" }}>Admins</span>
            <span style={{ display: "block", fontSize: "1.8rem", color: "#ff9800", fontWeight: "bold" }}>{usuarios.filter(u => u.role === "admin").length}</span>
          </div>
        </div>
      </div>

      {/* Buscador */}
      <div style={{ marginBottom: "25px", backgroundColor: "white", padding: "15px", borderRadius: "10px", boxShadow: "0 2px 5px rgba(0,0,0,0.05)", border: "1px solid #eaeaea" }}>
        <div style={{ position: "relative", maxWidth: "400px" }}>
          <Search size={20} style={{ position: "absolute", left: "15px", top: "12px", color: "#888" }} />
          <input 
            type="text" 
            placeholder="Buscar por nombre o email..." 
            value={busqueda} 
            onChange={(e) => setBusqueda(e.target.value)} 
            style={{ width: "100%", padding: "12px 15px 12px 45px", borderRadius: "6px", border: "1px solid #ddd", fontSize: "1rem", outline: "none", boxSizing: "border-box" }} 
          />
        </div>
      </div>

      {/* Tabla de Usuarios */}
      <div style={{ backgroundColor: "white", borderRadius: "10px", boxShadow: "0 4px 6px rgba(0,0,0,0.05)", border: "1px solid #eaeaea", overflow: "hidden" }}>
        <div style={{ overflowX: "auto", width: "100%" }}>          
          <table style={{ width: "100%", minWidth: "800px", borderCollapse: "collapse", textAlign: "left" }}>
            
            <thead style={{ backgroundColor: "#f8f9fa", borderBottom: "2px solid #eee" }}>
              <tr>
                <th style={{ padding: "18px 20px", color: "#555", fontWeight: "bold" }}>Usuario</th>
                <th style={{ padding: "18px 20px", color: "#555", fontWeight: "bold" }}>Email</th>
                <th style={{ padding: "18px 20px", color: "#555", fontWeight: "bold" }}>Lenguaje preferido</th>
                <th style={{ padding: "18px 20px", color: "#555", fontWeight: "bold" }}>Puntuación</th>
                <th style={{ padding: "18px 20px", color: "#555", fontWeight: "bold" }}>Rol</th>
                <th style={{ padding: "18px 20px", color: "#555", fontWeight: "bold", textAlign: "center" }}>Acciones</th>
              </tr>
            </thead>
            
            <tbody>
              {usuariosFiltrados.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ textAlign: "center", padding: "40px", color: "#888" }}>
                    <Users size={48} style={{ opacity: 0.5, marginBottom: "10px" }} />
                    <br/>No se encontraron usuarios.
                  </td>
                </tr>
              ) : (
                usuariosFiltrados.map((user) => (
                  <tr key={user.id} style={{ borderBottom: "1px solid #eee", transition: "background-color 0.2s" }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#fafafa"} onMouseOut={(e) => e.currentTarget.style.backgroundColor = "transparent"}>  
                    <td style={{ padding: "15px 20px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
                        <div style={{ backgroundColor: user.role === 'admin' ? '#fff3e0' : '#f0f0f0', padding: "10px", borderRadius: "50%", color: user.role === 'admin' ? '#ff9800' : '#888' }}>
                          {user.role === 'admin' ? <Shield size={20}/> : <UserIcon size={20}/>}
                        </div>
                        <span style={{ fontWeight: "bold", color: "#333", fontSize: "1.05rem" }}>{user.username}</span>
                      </div>
                    </td>
                    <td style={{ padding: "15px 20px", color: "#666" }}>
                      {user.email ? (
                        <span style={{ display: "flex", alignItems: "center", gap: "6px" }}><Mail size={16} /> {user.email}</span>
                      ) : (
                        <span style={{ color: "#aaa", fontStyle: "italic" }}>Sin email</span>
                      )}
                    </td>
                    <td style={{ padding: "15px 20px" }}>
                      <span style={{ backgroundColor: "#e3f2fd", color: "#1976d2", padding: "4px 10px", borderRadius: "20px", fontSize: "0.85rem", fontWeight: "bold", textTransform: "capitalize" }}>
                        {user.preferredLanguage || "Ninguno"}
                      </span>
                    </td>
                    <td style={{ padding: "15px 20px", fontWeight: "bold", color: "#1e1e1e" }}>
                      {user.score} pts
                    </td>
                    <td style={{ padding: "15px 20px" }}>
                      <select 
                        value={user.role} 
                        onChange={(e) => cambiarRol(user.id, e.target.value, user.username)}
                        className={user.role === 'admin' ? 'select-rol-admin' : 'select-rol-user'}
                      >
                        <option value="user">Usuario normal</option>
                        <option value="admin">Administrador</option>
                      </select>
                    </td>
                    <td style={{ padding: "15px 20px", textAlign: "center" }}>
                      <button 
                        title="Eliminar usuario" 
                        onClick={() => setModalEliminar({ abierto: true, id: user.id, nombre: user.username })}
                        style={{ background: "white", border: "1px solid #ffcdd2", borderRadius: "6px", padding: "8px", cursor: "pointer", color: "#d32f2f", transition: "0.2s" }} 
                        onMouseOver={(e) => {e.currentTarget.style.backgroundColor = "#ffebee"; e.currentTarget.style.borderColor = "#d32f2f"}} 
                        onMouseOut={(e) => {e.currentTarget.style.backgroundColor = "white"; e.currentTarget.style.borderColor = "#ffcdd2"}}
                      >
                        <Trash2 size={18} />
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* MODAL DE CONFIRMACIÓN DE ELIMINACIÓN */}
      {modalEliminar.abierto && (
        <div style={overlayStyle}>
          <div style={modalStyle}>
            <button style={btnCerrarStyle} onClick={() => setModalEliminar({ abierto: false, id: 0, nombre: "" })}><X size={24} /></button>
            <h2 style={{ marginTop: 0, color: "#d32f2f", display: "flex", alignItems: "center", gap: "10px" }}><Trash2 size={24}/> Eliminar Usuario</h2>
            <p style={{ fontSize: "1.1rem", color: "#333", lineHeight: "1.5" }}>
              ¿Estás seguro de que deseas eliminar a <strong>{modalEliminar.nombre}</strong>?
            </p>
            <div style={{ display: "flex", gap: "15px", marginTop: "30px", justifyContent: "flex-end" }}>
              <button onClick={() => setModalEliminar({ abierto: false, id: 0, nombre: "" })} style={{ padding: "10px 20px", borderRadius: "6px", border: "1px solid #ddd", background: "white", cursor: "pointer", fontWeight: "bold" }}>Cancelar</button>
              <button onClick={confirmarEliminacion} style={{ padding: "10px 20px", borderRadius: "6px", border: "none", background: "#d32f2f", color: "white", cursor: "pointer", fontWeight: "bold" }}>Sí, eliminar</button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}