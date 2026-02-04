package es.masorange.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import es.masorange.backend.config.JwtUtils;
import es.masorange.backend.model.*;
import es.masorange.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;

    public UserService(UserRepository userRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
    }

    public AuthResponseDTO login(LoginRequestDTO body) {
        Optional<User> userOpt = userRepository.findByUsername(body.username());

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (user.getPassword().equals(body.password())) {

                String token = jwtUtils.generateToken(user.getUsername(), user.getRole());

                return new AuthResponseDTO(token, user.getUsername(), user.getRole());
            }
        }

        throw new RuntimeException("Usuario o contraseña incorrectos");
    }

    public BasicResponseDTO signin(SigninRequestDTO body) {
        Optional<User> user = userRepository.findByUsername(body.username());

        if (user.isPresent()) {
            return new BasicResponseDTO("El usuario ya existe", "409");
        }

        User newUser = new User();
        newUser.setUsername(body.username());
        newUser.setPassword(body.password());
        newUser.setEmail(body.email());
        newUser.setNombre(body.nombre());

        newUser.setRole("user");

        userRepository.save(newUser);

        return new BasicResponseDTO("Usuario creado correctamente", "201");
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getUsersList() {
        List<User> users = userRepository.findAllByRole("user");

        return users;
    }

    /*
     * public BasicResponseDTO updateUser(Long id, UserUpdateDTO body) {
     * 
     * }
     */

    public BasicResponseDTO deleteUser(Long id) {

        if (id == null) {
            return new BasicResponseDTO("El ID es inválido", "400");
        }

        if (!userRepository.existsById(id)) {
            return new BasicResponseDTO("El usuario no existe", "404");
        }

        userRepository.deleteById(id);
        return new BasicResponseDTO("Usuario eliminado correctamente", "200");
    }

}
