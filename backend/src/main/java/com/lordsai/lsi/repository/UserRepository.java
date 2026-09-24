package com.lordsai.lsi.repository;

import com.lordsai.lsi.entity.User;
import com.lordsai.lsi.entity.enums.AccountStatus;
import com.lordsai.lsi.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    long countByRoleAndAccountStatus(Role role, AccountStatus status);

    Page<User> findByRole(Role role, Pageable pageable);

    /** Short usernames are the local part of the email, e.g. "admin" for admin@lordsai.com. */
    @Query("select u from User u where lower(u.email) like lower(concat(:username, '@%'))")
    java.util.List<User> findByUsername(@Param("username") String username);

    @Query("""
            select u from User u
            where u.role = :role
              and (lower(u.fullName) like lower(concat('%', :term, '%'))
                   or lower(u.email) like lower(concat('%', :term, '%'))
                   or u.mobile like concat('%', :term, '%'))
            """)
    Page<User> searchByRole(@Param("role") Role role, @Param("term") String term, Pageable pageable);
}
