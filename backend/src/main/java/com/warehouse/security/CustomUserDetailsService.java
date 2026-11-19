
package com.warehouse.security;

import com.warehouse.entity.AppUser;
import com.warehouse.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public CustomUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) {

        AppUser user = appUserRepository.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + login
                ));

        return new CustomUserDetails(user);
    }

    public CustomUserDetails loadUserById(Long id) {
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuário não encontrado: " + id
                ));

        return new CustomUserDetails(user);
    }
}
