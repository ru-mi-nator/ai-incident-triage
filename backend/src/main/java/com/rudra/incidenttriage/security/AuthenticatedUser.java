package com.rudra.incidenttriage.security;

import java.util.Collection;
import java.util.List;

import com.rudra.incidenttriage.domain.entity.User;
import com.rudra.incidenttriage.domain.enums.UserRole;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

	private final Long id;
	private final String name;
	private final String username;
	private String password;
	private final UserRole role;
	private final List<? extends GrantedAuthority> authorities;

	private AuthenticatedUser(
			Long id,
			String name,
			String username,
			String password,
			UserRole role,
			Collection<? extends GrantedAuthority> authorities
	) {
		this.id = id;
		this.name = name;
		this.username = username;
		this.password = password;
		this.role = role;
		this.authorities = List.copyOf(authorities);
	}

	public static AuthenticatedUser from(User user) {
		return new AuthenticatedUser(
				user.getId(),
				user.getName(),
				user.getUsername(),
				user.getPassword(),
				user.getRole(),
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
		);
	}

	public Long id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String username() {
		return username;
	}

	public UserRole role() {
		return role;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public void eraseCredentials() {
		password = null;
	}

	@Override
	public String toString() {
		return "AuthenticatedUser[id=" + id
				+ ", name=" + name
				+ ", username=" + username
				+ ", role=" + role
				+ ", authorities=" + authorities + "]";
	}
}
