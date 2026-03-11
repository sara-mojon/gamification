package es.masorange.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
