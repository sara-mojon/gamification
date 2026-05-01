package es.masorange.backend.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private byte[] cachedBody;
    private Map<String, String[]> parsedParams;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        // 1. Leemos el cuerpo crudo para la firma matemática
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());

        // 2. Preparamos el mapa para guardar los parámetros
        this.parsedParams = new HashMap<>();

        // Copiamos cualquier parámetro que viniera en la URL normal
        this.parsedParams.putAll(super.getParameterMap());

        // 3. Si Slack nos mandó un formulario, lo desencriptamos a mano
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            String body = new String(this.cachedBody, StandardCharsets.UTF_8);
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx != -1) {
                    try {
                        String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
                        String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());

                        this.parsedParams.compute(key, (k, existingValues) -> {
                            if (existingValues == null) {
                                return new String[] { value };
                            }
                            String[] newValues = Arrays.copyOf(existingValues, existingValues.length + 1);
                            newValues[existingValues.length] = value;
                            return newValues;
                        });
                    } catch (Exception e) {
                        // Ignorar errores de parseo
                    }
                }
            }
        }
    }

    public byte[] getCachedBody() {
        return this.cachedBody;
    }

    // Le decimos a Spring que cuando pregunte por los @RequestParam, mire aquí:
    @Override
    public Map<String, String[]> getParameterMap() {
        return Collections.unmodifiableMap(this.parsedParams);
    }

    @Override
    public String getParameter(String name) {
        String[] values = this.parsedParams.get(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(this.parsedParams.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return this.parsedParams.get(name);
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
        return new BufferedReader(new InputStreamReader(byteArrayInputStream));
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream buffer;

        public CachedBodyServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}