import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/**
 * PortfolioServer —— 纯 JDK 实现的作品集后台 + 管理接口（零依赖、零构建工具）。
 *
 * 启动：javac -encoding UTF-8 PortfolioServer.java && java PortfolioServer
 * 或双击 run.bat。
 *
 * 路由：
 *   GET  /               公开作品集页面（index.html，动态读取 /api/data）
 *   GET  /admin          后台管理界面（admin.html）
 *   GET  /api/data       返回 data.json 内容（公开）
 *   POST /api/data       保存（需登录，提交完整 JSON 覆盖）
 *   POST /api/login      登录，返回 {token}
 *
 * 数据持久化在 data.json（UTF-8）。后台默认账号 admin / admin123，请自行修改。
 */
public class PortfolioServer {
    // 端口可被环境变量覆盖：PORT=8081 java PortfolioServer （避免端口冲突）
    static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    static final String ADMIN_USER = "admin";
    static final String ADMIN_PASS = "admin123";
    static final File DATA_FILE = new File("data.json");
    static final Set<String> SESSIONS = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) throws Exception {
        // 若 data.json 不存在，写一个最小结构兜底（真正的种子内容由 data.json 提供）
        if (!DATA_FILE.exists()) {
            String fallback = "{\"site\":{},\"about\":\"\",\"projects\":[],\"skills\":[]}";
            Files.write(DATA_FILE.toPath(), fallback.getBytes(StandardCharsets.UTF_8));
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", PortfolioServer::handleRoot);
        server.createContext("/admin", PortfolioServer::handleAdmin);
        server.createContext("/api/login", PortfolioServer::handleLogin);
        server.createContext("/api/data", PortfolioServer::handleData);
        server.setExecutor(null);
        server.start();

        System.out.println("Portfolio backend running:");
        System.out.println("  Public site : http://localhost:" + PORT + "/");
        System.out.println("  Admin       : http://localhost:" + PORT + "/admin");
        System.out.println("  Default admin account: " + ADMIN_USER + " / " + ADMIN_PASS);
    }

    static void handleRoot(HttpExchange ex) throws IOException {
        String p = ex.getRequestURI().getPath();
        if (p.equals("/") || p.equals("/index.html")) {
            serveFile(ex, "index.html", "text/html; charset=utf-8");
        } else if (p.equals("/admin.html")) {
            serveFile(ex, "admin.html", "text/html; charset=utf-8");
        } else {
            send(ex, 404, "text/plain; charset=utf-8", "404 Not Found");
        }
    }

    static void handleAdmin(HttpExchange ex) throws IOException {
        serveFile(ex, "admin.html", "text/html; charset=utf-8");
    }

    static void handleLogin(HttpExchange ex) throws IOException {
        if (!ex.getRequestMethod().equals("POST")) {
            send(ex, 405, "text/plain", "Method Not Allowed");
            return;
        }
        try {
            Map<String, Object> m = (Map<String, Object>) Json.parse(body(ex));
            String u = str(m.get("user")), p = str(m.get("pass"));
            if (ADMIN_USER.equals(u) && ADMIN_PASS.equals(p)) {
                String t = token();
                SESSIONS.add(t);
                send(ex, 200, "application/json; charset=utf-8", "{\"token\":\"" + t + "\"}");
            } else {
                send(ex, 401, "application/json; charset=utf-8", "{\"error\":\"bad credentials\"}");
            }
        } catch (Exception e) {
            send(ex, 400, "application/json; charset=utf-8", "{\"error\":\"bad request\"}");
        }
    }

    static void handleData(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equals("GET")) {
            String data = new String(Files.readAllBytes(DATA_FILE.toPath()), StandardCharsets.UTF_8);
            send(ex, 200, "application/json; charset=utf-8", data);
            return;
        }
        if (ex.getRequestMethod().equals("POST")) {
            if (!authOk(ex)) {
                send(ex, 401, "application/json; charset=utf-8", "{\"error\":\"unauthorized\"}");
                return;
            }
            try {
                Object parsed = Json.parse(body(ex));
                if (!(parsed instanceof Map)) throw new Exception("not object");
                String out = Json.stringify(parsed);
                Files.write(DATA_FILE.toPath(), out.getBytes(StandardCharsets.UTF_8));
                send(ex, 200, "application/json; charset=utf-8", "{\"ok\":true}");
            } catch (Exception e) {
                send(ex, 400, "application/json; charset=utf-8", "{\"error\":\"invalid json\"}");
            }
            return;
        }
        send(ex, 405, "text/plain", "Method Not Allowed");
    }

    static boolean authOk(HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        if (h == null) return false;
        if (h.startsWith("Bearer ")) h = h.substring(7);
        return SESSIONS.contains(h);
    }

    static String body(HttpExchange ex) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (InputStream in = ex.getRequestBody()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
        }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    static void serveFile(HttpExchange ex, String name, String type) throws IOException {
        File f = new File(name);
        if (!f.exists()) {
            send(ex, 404, "text/plain; charset=utf-8", "404");
            return;
        }
        byte[] b = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    static void send(HttpExchange ex, int code, String type, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    static String token() {
        SecureRandom r = new SecureRandom();
        byte[] b = new byte[16];
        r.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}

/**
 * 极简 JSON 解析/序列化（仅覆盖本项目用到的类型），便于学习、零依赖。
 * 支持：对象 {}、数组 []、字符串、数字、true/false/null。
 */
class Json {
    public static Object parse(String s) {
        int[] i = {0};
        return val(s, i);
    }

    static void ws(String s, int[] i) {
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i[0]++;
            else break;
        }
    }

    static Object val(String s, int[] i) {
        ws(s, i);
        char c = s.charAt(i[0]);
        if (c == '{') return obj(s, i);
        if (c == '[') return arr(s, i);
        if (c == '"') return str(s, i);
        if (c == 't') { i[0] += 4; return Boolean.TRUE; }
        if (c == 'f') { i[0] += 5; return Boolean.FALSE; }
        if (c == 'n') { i[0] += 4; return null; }
        return num(s, i);
    }

    static Map<String, Object> obj(String s, int[] i) {
        Map<String, Object> m = new LinkedHashMap<>();
        i[0]++; // {
        ws(s, i);
        if (s.charAt(i[0]) == '}') { i[0]++; return m; }
        while (true) {
            ws(s, i);
            String k = str(s, i);
            ws(s, i);
            i[0]++; // :
            Object v = val(s, i);
            m.put(k, v);
            ws(s, i);
            char c = s.charAt(i[0]);
            if (c == ',') { i[0]++; continue; }
            if (c == '}') { i[0]++; break; }
        }
        return m;
    }

    static List<Object> arr(String s, int[] i) {
        List<Object> l = new ArrayList<>();
        i[0]++; // [
        ws(s, i);
        if (s.charAt(i[0]) == ']') { i[0]++; return l; }
        while (true) {
            Object v = val(s, i);
            l.add(v);
            ws(s, i);
            char c = s.charAt(i[0]);
            if (c == ',') { i[0]++; continue; }
            if (c == ']') { i[0]++; break; }
        }
        return l;
    }

    static String str(String s, int[] i) {
        i[0]++; // opening "
        StringBuilder sb = new StringBuilder();
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]);
            if (c == '\\') {
                i[0]++;
                char e = s.charAt(i[0]);
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i[0] + 1, i[0] + 5), 16));
                        i[0] += 4;
                        break;
                    default: sb.append(e);
                }
                i[0]++;
            } else if (c == '"') {
                i[0]++;
                break;
            } else {
                sb.append(c);
                i[0]++;
            }
        }
        return sb.toString();
    }

    static Number num(String s, int[] i) {
        int start = i[0];
        while (i[0] < s.length()) {
            char c = s.charAt(i[0]);
            if ("0123456789+-.eE".indexOf(c) >= 0) i[0]++;
            else break;
        }
        String n = s.substring(start, i[0]);
        if (n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0) return Double.parseDouble(n);
        return Long.parseLong(n);
    }

    public static String stringify(Object o) {
        StringBuilder sb = new StringBuilder();
        str2(o, sb);
        return sb.toString();
    }

    static void str2(Object o, StringBuilder sb) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String) { sb.append('"'); esc((String) o, sb); sb.append('"'); return; }
        if (o instanceof Boolean || o instanceof Number) { sb.append(o.toString()); return; }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"'); esc(e.getKey().toString(), sb); sb.append("\":");
                str2(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object x : (List<?>) o) {
                if (!first) sb.append(',');
                first = false;
                str2(x, sb);
            }
            sb.append(']');
            return;
        }
        sb.append('"'); esc(o.toString(), sb); sb.append('"');
    }

    static void esc(String s, StringBuilder sb) {
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
    }
}
