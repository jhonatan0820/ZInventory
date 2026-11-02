package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findFirstByIdUsuario(Long idUsuario);

    @Query("""
            select r
            from PasswordReset r
            where r.token = :token
              and r.idEstado = 4
              and r.expira > current_timestamp
            """)
    Optional<PasswordReset> findValidToken(@Param("token") String token);
}
