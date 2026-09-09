package com.pocsigmet.controller;

import com.pocsigmet.mongo.UserDocument;
import com.pocsigmet.mongo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ResponseEntity.ok(Map.of("token", token.getToken()));
    }

    @GetMapping("/login-page")
    public ResponseEntity<String> loginPage(HttpServletRequest request) {
        CsrfToken csrf = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        String error = request.getParameter("error") != null ? "<div class=\"error\">Usuário ou senha inválidos.</div>" : "";
        String html = LOGIN_HTML.replace("{{CSRF}}", csrf.getToken()).replace("{{ERROR}}", error);
        return ResponseEntity.ok().header("Content-Type", "text/html;charset=UTF-8").body(html);
    }

    private static final String LOGIN_HTML = """
        <!DOCTYPE html>
        <html lang="pt-BR">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>SIGMET - Login</title>
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: Arial, sans-serif; background: #1a1a2e; display: flex; align-items: center; justify-content: center; height: 100vh; }
            .card { background: #16213e; padding: 2rem; border-radius: 8px; width: 100%; max-width: 360px; box-shadow: 0 4px 20px rgba(0,0,0,0.5); }
            h2 { color: #e0e0e0; text-align: center; margin-bottom: 1.5rem; font-size: 1.3rem; letter-spacing: 1px; }
            label { display: block; color: #aaa; font-size: 0.85rem; margin-bottom: 4px; }
            input[type=text], input[type=password] { width: 100%; padding: 0.6rem 0.8rem; border: 1px solid #0f3460; border-radius: 4px; background: #0f3460; color: #e0e0e0; font-size: 1rem; margin-bottom: 1rem; }
            input:focus { outline: none; border-color: #e94560; }
            button { width: 100%; padding: 0.7rem; background: #e94560; color: #fff; border: none; border-radius: 4px; font-size: 1rem; cursor: pointer; }
            button:hover { background: #c73652; }
            .error { color: #e94560; font-size: 0.85rem; text-align: center; margin-bottom: 1rem; }
          </style>
        </head>
        <body>
          <div class="card">
            <h2>🌦 SIGMET</h2>
            <form method="post" action="/login">
              {{ERROR}}
              <label for="username">Usuário</label>
              <input type="text" id="username" name="username" required autofocus>
              <label for="password">Senha</label>
              <input type="password" id="password" name="password" required>
              <input type="hidden" name="_csrf" value="{{CSRF}}">
              <button type="submit">Entrar</button>
            </form>
          </div>
        </body>
        </html>
        """;

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> register(@RequestParam String username,
                                           @RequestParam String password,
                                           @RequestParam(defaultValue = "USER") String role) {
        if (userRepository.findByUsername(username).isPresent())
            return ResponseEntity.badRequest().body("Usuário já existe");
        userRepository.save(new UserDocument(username, passwordEncoder.encode(password), role));
        return ResponseEntity.ok("Usuário criado");
    }
}
