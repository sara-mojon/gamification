import http from 'k6/http';
import { check, sleep, group } from 'k6';
import encoding from 'k6/encoding';

export const options = {
  stages: [
    { duration: '30s', target: 300 }, // Subida rápida pero más progresiva
    { duration: '5m', target: 500 },  // Mantenemos tu nueva carga máxima
    { duration: '30s', target: 100 },   // Bajada limpia hasta vaciar el servidor
  ],
  thresholds: {
    // Permitimos un 5-6% de fallos porque los estamos PROVOCANDO nosotros
    http_req_failed: ['rate<0.06'],   
    http_req_duration: ['p(95)<2000'], 
  },
};

const BASE_URL = 'https://api.saramg.org/api';
const TOKEN_URL = 'https://auth.saramg.org/realms/masorange-realm/protocol/openid-connect/token';

export function setup() {
  const loginUsername = 'test_load@sara.local';
  const loginRes = http.post(TOKEN_URL, {
    grant_type: 'password',
    client_id: 'gamification-client',
    username: loginUsername,
    password: 'password'
  });

  if (loginRes.status !== 200) throw new Error(`Fallo auth: ${loginRes.status}`);
  
  const token = loginRes.json('access_token');
  const payload = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's'));
  
  return { authToken: token, userId: payload.sub, username: loginUsername };
}

export default function (data) {
  const params = {
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${data.authToken}` },
  };

  const accion = Math.random();

  // --- 5% TRÁFICO KO (Validamos resiliencia de CPU ante excepciones) ---
  if (accion < 0.05) {
    group('Carga de Errores (CPU Overhead)', function () {
      const res = http.post(`${BASE_URL}/gamification/award?userId=fantasma-999&rank=1`, null, params);
      // Solo verificamos que responde rápido y sin caerse (500)
      check(res, { 'resiliencia: error 404 devuelto': (r) => r.status === 404 });
    });
  } 
  // --- 20% TRÁFICO LIGERO (Búsquedas simples) ---
  else if (accion < 0.25) {
    group('Operaciones Ligeras', function () {
      const res = http.get(`${BASE_URL}/gamification/points/config`, params);
      check(res, { 'config ok': (r) => r.status === 200 });
      sleep(1);
    });
  }
  // --- 35% TRÁFICO MEDIO (Lecturas pesadas BBDD) ---
  else if (accion < 0.60) {
    group('Búsquedas Complejas', function () {
      const page = Math.floor(Math.random() * 5); 
      const res = http.get(`${BASE_URL}/challenges?page=${page}&size=10&dificultad=Media`, params);
      check(res, { 'búsqueda filtrada ok': (r) => r.status === 200 });
      sleep(3);
    });
  }
  // --- 40% TRÁFICO MUY PESADO (Escrituras y Transacciones) ---
  else {
    group('Transacciones Críticas', function () {
      const resSubmit = http.post(`${BASE_URL}/challenges/3/submit`, JSON.stringify({
        sourceCode: "print('Carga')", language: "python"
      }), params);
      
      if (resSubmit.status === 200 || resSubmit.status === 201) {
        http.post(`${BASE_URL}/gamification/award?userId=${data.userId}&rank=8`, null, params);
      }
      sleep(8);
    });
  }
}