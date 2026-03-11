import { useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import Editor from "@monaco-editor/react";
import { ChevronLeft, Play, CheckCircle, Terminal } from "lucide-react";

export default function Entrenar() {
  const { id } = useParams(); // Sacamos el ID del reto de la URL
  const navigate = useNavigate();
  
  // Estado para guardar el código que escribe el usuario
  const [codigo, setCodigo] = useState("// Escribe aquí tu solución...\n\nfunction resolver() {\n  \n}");

  // Mock de los datos del reto (más adelante haremos un fetch a Spring Boot usando el 'id')
  const retoActual = {
    titulo: "Sumar dos números",
    dificultad: "Fácil",
    descripcion: "Escribe una función que tome dos números enteros como parámetros y devuelva su suma exacta.",
    instrucciones: "Debes crear una función llamada 'sumar' que reciba 'a' y 'b'. No uses librerías externas. Devuelve un número entero."
  };

  const ejecutarCodigo = () => {
    alert("¡Aquí enviaremos el código a Spring Boot para validarlo!\n\nTu código:\n" + codigo);
  };

  return (
    <div style={{ display: "flex", height: "calc(100vh - 65px)", margin: "-40px" }}>
      
      {/* COLUMNA IZQUIERDA: INSTRUCCIONES */}
      <div style={{ flex: 1, padding: "40px", backgroundColor: "#f9f9f9", borderRight: "1px solid #ddd", overflowY: "auto", display: "flex", flexDirection: "column" }}>
        
        {/* Botón de volver */}
        <button 
          onClick={() => navigate("/retos")}
          style={{ display: "flex", alignItems: "center", background: "none", border: "none", color: "#666", cursor: "pointer", marginBottom: "20px", padding: 0, fontWeight: "bold" }}
        >
          <ChevronLeft size={20} /> Volver a los retos
        </button>

        <div style={{ display: "flex", alignItems: "center", marginBottom: "20px" }}>
          <h1 style={{ margin: 0, fontSize: "2rem", color: "#1e1e1e", marginRight: "15px" }}>{retoActual.titulo}</h1>
          <span style={{ backgroundColor: "#4caf50", color: "white", padding: "4px 10px", borderRadius: "20px", fontSize: "0.8rem", fontWeight: "bold" }}>
            {retoActual.dificultad}
          </span>
        </div>

        <h3 style={{ borderBottom: "2px solid #ddd", paddingBottom: "10px", color: "#333", display: "flex", alignItems: "center" }}>
          <CheckCircle size={18} style={{ marginRight: "10px", color: "#888" }} />
          Descripción
        </h3>
        <p style={{ color: "#555", lineHeight: "1.6", fontSize: "1.1rem" }}>{retoActual.descripcion}</p>

        <h3 style={{ borderBottom: "2px solid #ddd", paddingBottom: "10px", color: "#333", display: "flex", alignItems: "center", marginTop: "30px" }}>
          <Terminal size={18} style={{ marginRight: "10px", color: "#888" }} />
          Instrucciones
        </h3>
        <p style={{ color: "#555", lineHeight: "1.6", fontSize: "1.1rem" }}>{retoActual.instrucciones}</p>

        {/* Hueco para la consola (resultados) */}
        <div style={{ marginTop: "auto", backgroundColor: "#1e1e1e", color: "#fff", borderRadius: "8px", padding: "20px", minHeight: "150px" }}>
          <h4 style={{ margin: "0 0 10px 0", color: "#aaa", fontSize: "0.9rem", textTransform: "uppercase" }}>Resultado de las pruebas</h4>
          <p style={{ color: "#aaa", fontFamily: "monospace" }}>Aún no has ejecutado tu código...</p>
        </div>
      </div>

      {/* COLUMNA DERECHA: EDITOR DE CÓDIGO */}
      <div style={{ flex: "1.5", display: "flex", flexDirection: "column", backgroundColor: "#1e1e1e" }}>
        
        {/* Cabecera del editor */}
        <div style={{ height: "50px", backgroundColor: "#2d2d2d", borderBottom: "1px solid #111", display: "flex", alignItems: "center", padding: "0 20px" }}>
          <span style={{ color: "#fff", fontFamily: "monospace" }}>solution.js</span>
        </div>

        {/* El Editor Mágico de Monaco */}
        <div style={{ flex: 1, paddingTop: "10px" }}>
          <Editor
            height="100%"
            defaultLanguage="javascript" // Puedes cambiarlo a java, python, etc.
            theme="vs-dark" // Tema oscuro de VS Code
            value={codigo}
            onChange={(valor) => setCodigo(valor || "")}
            options={{
              minimap: { enabled: false }, // Quitamos el minimapa lateral para ganar espacio
              fontSize: 16,
              padding: { top: 15 }
            }}
          />
        </div>

        {/* Pie del editor (Botón Ejecutar) */}
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