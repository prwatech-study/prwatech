package com.prwatech.skillama.controller;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController("skillamaUserController")
@RequestMapping("/skillama/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userService.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body("Email is already registered");
        }
        return ResponseEntity.ok(userService.register(user));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginRequest) {
        Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isActive()) {
                return ResponseEntity.status(403).body("Account is not activated. Please contact admin.");
            }
            // Password comparison: passwords are stored encoded in DB
            if (userService.validatePassword(loginRequest.getPassword(), user.getPassword())) {
                return ResponseEntity.ok(user); // Replace with JWT in production
            }
        }
        return ResponseEntity.status(401).body("Invalid credentials");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody User user) {
        // Dummy implementation
        return ResponseEntity.ok("Password reset link sent to email (dummy)");
    }

    @GetMapping
    public ResponseEntity<Page<User>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order
    ) {
        boolean desc = order.equalsIgnoreCase("desc");
        return ResponseEntity.ok(userService.findAll(page, size, sortBy, desc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/admin/activate")
    public ResponseEntity<?> activateUser(@RequestParam String email) {
        User activatedUser = userService.activateUser(email);
        if (activatedUser != null) {
            return ResponseEntity.ok("User activated successfully");
        }
        return ResponseEntity.status(404).body("User not found");
    }
    
    @PostMapping("/admin/deactivate")
    public ResponseEntity<?> deactivateUser(@RequestParam String email) {
        User deactivatedUser = userService.deactivateUser(email);
        if (deactivatedUser != null) {
            return ResponseEntity.ok("User deactivated successfully");
        }
        return ResponseEntity.status(404).body("User not found");
    }
    
    @PostMapping("/admin/migrate-passwords")
    public ResponseEntity<?> migratePasswords() {
        return ResponseEntity.ok(userService.migrateAllPasswords());
    }
}
