package dev.nikan.ledgerlite.auth;

import dev.nikan.ledgerlite.user.Role;
import dev.nikan.ledgerlite.user.User;
import dev.nikan.ledgerlite.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyUsedException(request.email());
        }

        String hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.email(), hashedPassword, request.fullName(), Role.CUSTOMER);

        return userRepository.save(user);
    }
}