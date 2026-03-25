import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import Editor from "@monaco-editor/react";
import { ChevronLeft, Play, CheckCircle } from "lucide-react";
import ReactMarkdown from "react-markdown";

interface Reto {
  id?: number;
  name: string;
  description: string;
  rank?: {
    name: string;
  };
}

const plantillasCodigo = {
  javascript: "// Escribe aquí tu solución en JavaScript...\n\nfunction resolver() {\n  \n}",
  python: "# Escribe aquí tu solución en Python...\n\ndef resolver():\n    pass\n",
  java: "// Escribe aquí tu solución en Java...\n\npublic class Solution {\n    public static void main(String[] args) {\n        \n    }\n}",
  c: "// Escribe aquí tu solución en C...\n\n#include <stdio.h>\n\nint main() {\n    \n    return 0;\n}"
};

const nombresArchivo = {
  javascript: "solution.js",
  python: "solution.py",
  java: "Solution.java",
  c: "main.c"
};

export default function Entrenar() {
  const { id } = useParams();
  const navigate = useNavigate();
  const auth = useAuth();
  const token = auth.user?.access_token;
  
  const [retoActual, setRetoActual] = useState<Reto | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");

  const [lenguaje, setLenguaje] = useState<"javascript" | "python" | "java" | "c">("javascript");;
  const [codigo, setCodigo] = useState(plantillasCodigo.javascript);

  useEffect(() => {
    const cargarDatos = async () => {
      if (!token) return;

      try {
        const reqReto = fetch(`http://localhost:8081/api/challenges/${id}`, {
          method: "GET",
          headers: { "Authorization": `Bearer ${token}` }
        });

        const reqPerfil = fetch(`http://localhost:8080/api/users/me`, {
          method: "GET",
          headers: { "Authorization": `Bearer ${token}` }
        });

        const [resReto, resPerfil] = await Promise.all([reqReto, reqPerfil]);

        if (resReto.ok) {
          const datosReto = await resReto.json();
          setRetoActual(datosReto);
        } else {
          setError("No se pudo cargar el reto. Puede que no exista.");
        }

        if (resPerfil.ok) {
          const datosPerfil = await resPerfil.json();
          if (datosPerfil.preferred_language) {
            const langFav = datosPerfil.preferred_language.toLowerCase() as "javascript" | "python" | "java" | "c";
            
            if (langFav === "javascript" || langFav === "python" || langFav === "java" || langFav === "c") {
              setLenguaje(langFav);
              setCodigo(plantillasCodigo[langFav]);
            }
          }
        }

      } catch {
        setError("Error de red intentando conectar con el servidor.");
      } finally {
        setCargando(false);
      }
    };

    cargarDatos();
  }, [id, token]);

  const cambiarLenguaje = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const nuevoLenguaje = e.target.value as "javascript" | "python" | "java" | "c";
    setLenguaje(nuevoLenguaje);
    setCodigo(plantillasCodigo[nuevoLenguaje]); 
  };

  const ejecutarCodigo = () => {
    alert(`¡Aquí enviaremos el código a Spring Boot para validarlo!\nLenguaje: ${lenguaje}\nTu código:\n` + codigo);
  };

  if (cargando) {
    return <div style={{ padding: "50px", textAlign: "center", color: "#666" }}>Cargando datos del reto... ⏳</div>;
  }

  if (error || !retoActual) {
    return (
      <div style={{ padding: "50px", textAlign: "center", color: "#c62828" }}>
        <h2>Ups...</h2>
        <p>{error}</p>
        <button onClick={() => navigate("/retos")} style={{ padding: "10px 20px", marginTop: "20px", cursor: "pointer" }}>
          Volver a los retos
        </button>
      </div>
    );
  }

  return (
    <div style={{ display: "flex", height: "calc(100vh - 65px)", margin: "-40px" }}>
      
      <div style={{ flex: 1, padding: "40px", backgroundColor: "#f9f9f9", borderRight: "1px solid #ddd", overflowY: "auto", display: "flex", flexDirection: "column" }}>
        <button 
          onClick={() => navigate("/retos")}
          style={{ display: "flex", alignItems: "center", background: "none", border: "none", color: "#666", cursor: "pointer", marginBottom: "20px", padding: 0, fontWeight: "bold" }}
        >
          <ChevronLeft size={20} /> Volver a los retos
        </button>

        <div style={{ display: "flex", alignItems: "center", marginBottom: "20px" }}>
          <h1 style={{ margin: 0, fontSize: "2rem", color: "#1e1e1e", marginRight: "15px" }}>{retoActual.name || "Reto sin título"}</h1>
          <span style={{ backgroundColor: "#4caf50", color: "white", padding: "4px 10px", borderRadius: "20px", fontSize: "0.8rem", fontWeight: "bold" }}>
            {retoActual.rank?.name || "Normal"}
          </span>
        </div>

        <h3 style={{ borderBottom: "2px solid #ddd", paddingBottom: "10px", color: "#333", display: "flex", alignItems: "center" }}>
          <CheckCircle size={18} style={{ marginRight: "10px", color: "#888" }} />
          Descripción del problema
        </h3>
        
        <div style={{ color: "#555", lineHeight: "1.6", fontSize: "1.05rem", marginTop: "10px", overflowWrap: "break-word" }}>
          <ReactMarkdown>
            {retoActual.description || "Sin descripción"}
          </ReactMarkdown>
        </div>

        <div style={{ marginTop: "auto", paddingTop: "30px" }}>
            <div style={{ backgroundColor: "#1e1e1e", color: "#fff", borderRadius: "8px", padding: "20px", minHeight: "150px" }}>
            <h4 style={{ margin: "0 0 10px 0", color: "#aaa", fontSize: "0.9rem", textTransform: "uppercase" }}>Resultado de las pruebas</h4>
            <p style={{ color: "#aaa", fontFamily: "monospace" }}>Aún no has ejecutado tu código...</p>
            </div>
        </div>
      </div>

      <div style={{ flex: "1.5", display: "flex", flexDirection: "column", backgroundColor: "#1e1e1e" }}>
        
        <div style={{ height: "50px", backgroundColor: "#2d2d2d", borderBottom: "1px solid #111", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 20px" }}>
          
          <div style={{ display: "flex", alignItems: "center", gap: "15px" }}>
            <span style={{ color: "#aaa", fontSize: "0.9rem", fontWeight: "bold" }}>LENGUAJE:</span>
            <select 
              value={lenguaje}
              onChange={cambiarLenguaje}
              style={{ 
                backgroundColor: "#1e1e1e", color: "white", border: "1px solid #444", 
                padding: "5px 10px", borderRadius: "4px", fontSize: "0.9rem", outline: "none", cursor: "pointer" 
              }}
            >
              <option value="javascript">JavaScript</option>
              <option value="python">Python</option>
              <option value="java">Java</option>
              <option value="c">C</option>
            </select>
          </div>

          <span style={{ color: "#888", fontFamily: "monospace", fontSize: "0.9rem" }}>
            {nombresArchivo[lenguaje]}
          </span>
        </div>

        <div style={{ flex: 1, paddingTop: "10px" }}>
          <Editor
            height="100%"
            language={lenguaje}
            theme="vs-dark"
            value={codigo}
            onChange={(valor) => setCodigo(valor || "")}
            options={{
              minimap: { enabled: false }, 
              fontSize: 16,
              padding: { top: 15 }
            }}
          />
        </div>

        <div style={{ height: "70px", backgroundColor: "#2d2d2d", borderTop: "1px solid #111", display: "flex", alignItems: "center", justifyContent: "flex-end", padding: "0 20px" }}>
          <button 
            onClick={ejecutarCodigo}
            style={{ 
              display: "flex", alignItems: "center", backgroundColor: "#ff4b4b", color: "white", 
              border: "none", padding: "12px 25px", borderRadius: "6px", fontSize: "1.1rem", 
              fontWeight: "bold", cursor: "pointer", transition: "background-color 0.2s" 
            }}
            onMouseOver={(e) => e.currentTarget.style.backgroundColor = "#d43f3f"}
            onMouseOut={(e) => e.currentTarget.style.backgroundColor = "#ff4b4b"}
          >
            <Play size={20} style={{ marginRight: "10px" }} />
            Ejecutar Código
          </button>
        </div>

      </div>
    </div>
  );
}