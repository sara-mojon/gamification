// frontend/src/pages/Entrenar.tsx

import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useAuth } from "react-oidc-context";
import Editor from "@monaco-editor/react";
import { ChevronLeft, Play, CheckCircle } from "lucide-react";
import ReactMarkdown from "react-markdown";

// --- TIPOS ---
interface Reto {
  id?: number;
  name: string;
  description: string;
  rank: number;
  isSolved?: boolean;
}

interface ParsedResult {
  status: 'success' | 'error';
  title: string;
  message: string;
  details: string;
}

interface TestDetail {
  status: string;
  name: string;
  error?: string;
}

const obtenerInfoDificultad = (rank: number) => {
  switch (rank) {
    case 8: return { dificultad: "Muy Fácil", color: "#4caf50", tiempo: 10 };   // Verde oscuro
    case 7: return { dificultad: "Fácil", color: "#8bc34a", tiempo: 15 };       // Verde claro
    case 6: return { dificultad: "Normal", color: "#ffc107", tiempo: 20 };      // Amarillo
    case 5: return { dificultad: "Normal-Avanzado", color: "#ff9800", tiempo: 30 }; // Naranja
    case 4: return { dificultad: "Difícil", color: "#f44336", tiempo: 45 };     // Rojo
    case 3: return { dificultad: "Muy Difícil", color: "#d32f2f", tiempo: 60 }; // Rojo oscuro
    default: return { dificultad: "Desconocido", color: "#9e9e9e", tiempo: 20 }; // Gris (Fallback)
  }
};

// --- CONSTANTES ---
const plantillasCodigo = {
  javascript: "// Escribe aquí tu solución en JavaScript...\n\nfunction solution(/* parámetros */) {\n  \n}",
  python: "# Escribe aquí tu solución en Python...\n\ndef solution(/* parámetros */):\n    pass\n",
  java: "// Escribe aquí tu solución en Java...\n// Tu código será inyectado en la clase Main automáticamente.\n\npublic static /* tipo */ solution(/* parámetros */) {\n    \n}",
  c: "// Escribe aquí tu solución en C...\n// No incluyas la función main(), solo tu lógica.\n\n/* tipo */ solution(/* parámetros */) {\n    \n}"
};

const nombresArchivo = {
  javascript: "solution.js",
  python: "solution.py",
  java: "Solution.java",
  c: "main.c"
};

// --- HELPER DE PARSEO ---
const parseTestResult = (rawOutput: string): ParsedResult => {
  const safeOutput = rawOutput || "";
  const jsonMarker = "||JSON_RESULT||";
  const jsonIndex = safeOutput.indexOf(jsonMarker);

  if (jsonIndex !== -1) {
    try {
      const jsonString = safeOutput.substring(jsonIndex + jsonMarker.length).trim();
      const report = JSON.parse(jsonString);
      const userLogs = safeOutput.substring(0, jsonIndex).trim();
      
      const buildDetails = () => {
          let text = userLogs ? `[TU OUTPUT]\n${userLogs}\n\n` : "";
          text += "[RESULTADO DE LOS TESTS]\n";
          report.results.forEach((r: TestDetail) => {
              const icon = r.status === "OK" ? "✅" : "❌";
              text += `${icon} ${r.name}`;
              if (r.status === "FAIL" && r.error) text += `\n   ↳ Error: ${r.error}`;
              text += "\n";
          });
          return text;
      };

      if (report.failed === 0) {
          return {
              status: 'success',
              title: '✅ MISIÓN CUMPLIDA',
              message: `Tests pasados: ${report.passed}/${report.total}.`,
              details: buildDetails()
          };
      } else {
          return {
              status: 'error',
              title: '❌ TESTS FALLIDOS',
              message: `Has fallado ${report.failed} de ${report.total} tests.`,
              details: buildDetails()
          };
      }
    } catch (e) {
      console.error("Error parseando reporte JSON:", e);
    }
  }

  // Si no hay JSON, es que hubo un error de compilación/ejecución
  return {
      status: 'error',
      title: '⚠️ ERROR DEL SISTEMA',
      message: 'El código no pudo ejecutarse o tiene errores de sintaxis.',
      details: `[LOGS DE SISTEMA]\n${safeOutput}`
  };
};

export default function Entrenar() {
  const { id } = useParams();
  const navigate = useNavigate();
  const auth = useAuth();
  const token = auth.user?.access_token;
  
  // --- ESTADOS ---
  const [retoActual, setRetoActual] = useState<Reto | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState("");
  
  const [lenguaje, setLenguaje] = useState<"javascript" | "python" | "java" | "c">("javascript");
  const [codigo, setCodigo] = useState(plantillasCodigo.javascript);
  
  const [submitting, setSubmitting] = useState(false); // <-- AQUÍ AÑADIMOS EL ESTADO
  const [result, setResult] = useState<ParsedResult | null>(null); // Estado para guardar el resultado de Judge0

  // --- CONFIG URIs ---
  const baseUrlUsers = import.meta.env.VITE_USER_URL || 'http://localhost:8080';
  const baseUrlChallenges = import.meta.env.VITE_CHALLENGES_URL || import.meta.env.VITE_USER_URL || 'http://localhost:8081';

  useEffect(() => {
    const cargarDatos = async () => {
      if (!token) return;

      try {
        const reqReto = fetch(`${baseUrlChallenges}/api/challenges/${id}`, {
          method: "GET",
          headers: { "Authorization": `Bearer ${token}` }
        });

        const reqPerfil = fetch(`${baseUrlUsers}/api/users/me`, {
          method: "GET",
          headers: { "Authorization": `Bearer ${token}` }
        });

        const [resReto, resPerfil] = await Promise.all([reqReto, reqPerfil]);

        if (resReto.ok) {
          const datosReto = await resReto.json();
          datosReto.isSolved = datosReto.isSolved ?? datosReto.solved ?? false;
          setRetoActual(datosReto);
        } else {
          setError("No se pudo cargar el reto. Puede que no exista.");
        }

        if (resPerfil.ok) {
          const datosPerfil = await resPerfil.json();
          if (datosPerfil.preferred_language) {
            const langFav = datosPerfil.preferred_language.toLowerCase() as "javascript" | "python" | "java" | "c";
            if (["javascript", "python", "java", "c"].includes(langFav)) {
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
  }, [id, token, baseUrlUsers, baseUrlChallenges]);

  const cambiarLenguaje = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const nuevoLenguaje = e.target.value as "javascript" | "python" | "java" | "c";
    setLenguaje(nuevoLenguaje);
    setCodigo(plantillasCodigo[nuevoLenguaje]); 
    setResult(null); // Limpiamos resultados al cambiar de lenguaje
  };

  const ejecutarCodigo = async () => {
    if (!codigo || !retoActual || !token) return;
    
    setSubmitting(true);
    setResult(null); // Limpiamos resultados anteriores
    
    try {
      const response = await fetch(`${baseUrlChallenges}/api/challenges/${retoActual.id}/submit`, {
        method: 'POST',
        headers: { 
          'Authorization': `Bearer ${token}`, 
          'Content-Type': 'application/json' 
        },
        body: JSON.stringify({ 
          language: lenguaje, 
          sourceCode: codigo 
        })
      });

      if (!response.ok) throw new Error("Fallo en la evaluación");
      
      const rawOutput = await response.text(); // String puro de Spring Boot
      const parsed = parseTestResult(rawOutput); 
      setResult(parsed); 

    } catch (err) {
      console.error(err);
      setResult({
        status: 'error',
        title: '❌ ERROR DE CONEXIÓN',
        message: 'No se pudo contactar con Judge0.',
        details: 'Revisa que los servicios backend y de evaluación estén corriendo.'
      });
    } finally {
      setSubmitting(false);
    }
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
    <div style={{ display: "grid", gridTemplateColumns: "0.75fr 1fr", height: "calc(100vh - 65px)", margin: "-40px", overflow: "hidden" }}>
      
      {/* PANEL IZQUIERDO (Descripción y Resultados) */}
      <div style={{ padding: "40px", backgroundColor: "#f9f9f9", borderRight: "1px solid #ddd", overflowY: "auto", display: "flex", flexDirection: "column" }}>
        <button onClick={() => navigate("/retos")} style={{ display: "flex", alignItems: "center", background: "none", border: "none", color: "#666", cursor: "pointer", marginBottom: "20px", padding: 0, fontWeight: "bold", flexShrink: 0 }}>
          <ChevronLeft size={20} /> Volver a los retos
        </button>

        <div style={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: "10px", marginBottom: "20px", flexShrink: 0 }}>
          <h1 style={{ margin: 0, fontSize: "2rem", color: "#1e1e1e", marginRight: "5px" }}>
            {retoActual.name || "Reto sin título"}
          </h1>
          <span style={{ 
            backgroundColor: obtenerInfoDificultad(retoActual.rank).color, 
            color: "white", padding: "4px 10px", borderRadius: "20px", fontSize: "0.8rem", fontWeight: "bold" 
          }}>
            {obtenerInfoDificultad(retoActual.rank).dificultad}
          </span>
          {retoActual.isSolved && (
            <span style={{ 
              backgroundColor: "#e8f5e9", color: "#2e7d32", border: "1px solid #4caf50",
              padding: "3px 10px", borderRadius: "20px", fontSize: "0.8rem", fontWeight: "bold", 
              display: "flex", alignItems: "center" 
            }}>
              <CheckCircle size={14} style={{ marginRight: "5px" }} /> Completado
            </span>
          )}
        </div>

        <h3 style={{ borderBottom: "2px solid #ddd", paddingBottom: "10px", color: "#333", display: "flex", alignItems: "center", flexShrink: 0 }}>
          <CheckCircle size={18} style={{ marginRight: "10px", color: "#888" }} /> Descripción del problema
        </h3>
        
        <div style={{ color: "#555", lineHeight: "1.6", fontSize: "1.05rem", marginTop: "10px", overflowWrap: "break-word", flexGrow: 1 }}>
          <ReactMarkdown>{retoActual.description || "Sin descripción"}</ReactMarkdown>
        </div>

        {/* RECUADRO DE RESULTADOS DE TESTS */}
        <div style={{ marginTop: "30px", flexShrink: 0 }}>
            <div style={{ 
                backgroundColor: result?.status === 'success' ? '#143118' : (result?.status === 'error' ? '#3d1616' : '#1e1e1e'), 
                color: "#fff", borderRadius: "8px", padding: "20px", minHeight: "150px", 
                border: `1px solid ${result?.status === 'success' ? '#2e7d32' : (result?.status === 'error' ? '#c62828' : '#333')}`
            }}>
                <h4 style={{ margin: "0 0 10px 0", color: "#ddd", fontSize: "0.9rem", textTransform: "uppercase", fontWeight: "bold" }}>
                    {result ? result.title : "Resultado de las pruebas"}
                </h4>
                
                {!result ? (
                    <p style={{ color: "#aaa", fontFamily: "monospace" }}>Aún no has ejecutado tu código...</p>
                ) : (
                    <>
                        <p style={{ fontWeight: "bold", marginBottom: "10px", color: result.status === 'success' ? '#81c784' : '#ef5350' }}>
                            {result.message}
                        </p>
                        {result.details && (
                            <pre style={{ 
                                backgroundColor: "rgba(0,0,0,0.5)", padding: "10px", borderRadius: "4px", 
                                fontSize: "0.85rem", overflowX: "auto", fontFamily: "monospace", color: "#bbb" 
                            }}>
                                {result.details}
                            </pre>
                        )}
                    </>
                )}
            </div>
        </div>
      </div>

      {/* PANEL DERECHO (Editor) */}
      <div style={{ display: "flex", flexDirection: "column", backgroundColor: "#1e1e1e", overflow: "hidden" }}>
        
        <div style={{ height: "50px", backgroundColor: "#2d2d2d", borderBottom: "1px solid #111", display: "flex", alignItems: "center", justifyContent: "space-between", padding: "0 20px", flexShrink: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: "15px" }}>
            <span style={{ color: "#aaa", fontSize: "0.9rem", fontWeight: "bold" }}>LENGUAJE:</span>
            <select value={lenguaje} onChange={cambiarLenguaje} style={{ backgroundColor: "#1e1e1e", color: "white", border: "1px solid #444", padding: "5px 10px", borderRadius: "4px", fontSize: "0.9rem", outline: "none", cursor: "pointer" }}>
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
	
        {/* 2. BLINDAJE DEL EDITOR */}
        <div style={{ flex: 1, position: "relative" }}> 
          <div style={{ position: "absolute", top: 0, bottom: 0, left: 0, right: 0, paddingTop: "10px" }}>
            <Editor height="100%" language={lenguaje} theme="vs-dark" value={codigo} onChange={(valor) => setCodigo(valor || "")} options={{ minimap: { enabled: false }, fontSize: 16, padding: { top: 15 } }} />
          </div>
        </div>

        {/* Botón de Ejecutar */}
        <div style={{ height: "70px", backgroundColor: "#2d2d2d", borderTop: "1px solid #111", display: "flex", alignItems: "center", justifyContent: "flex-end", padding: "0 20px", flexShrink: 0 }}>
          <button 
            onClick={ejecutarCodigo}
            disabled={submitting}
            style={{ 
              display: "flex", alignItems: "center", backgroundColor: submitting ? "#888" : "#ff4b4b", color: "white", 
              border: "none", padding: "12px 25px", borderRadius: "6px", fontSize: "1.1rem", 
              fontWeight: "bold", cursor: submitting ? "not-allowed" : "pointer", transition: "background-color 0.2s" 
            }}
          >
            <Play size={20} style={{ marginRight: "10px" }} />
            {submitting ? "Evaluando..." : "Ejecutar Código"}
          </button>
        </div>

      </div>
    </div>
  );
}