package com.example.aigateway.service;

import com.example.aigateway.entity.UserAccount;
import com.example.aigateway.exception.BusinessException;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads user credentials and roles for Spring Security. */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserService userService;

    public CustomUserDetailsService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            UserAccount useraccount = userService.getUserByUsername(username);
            List<String> roles = userService.getRole(useraccount.getId());

            List<GrantedAuthority> authorities = roles.stream()
                    .map(roleName -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + roleName))
                    .toList();

            return User.withUsername(useraccount.getUsername())
                    .password(useraccount.getPasswordHash())
                    .authorities(authorities)
                    .disabled(!useraccount.isEnabled())
                    .build();
        } catch (BusinessException exception) {
            throw new UsernameNotFoundException("Invalid username or password");
        }
    }
}
