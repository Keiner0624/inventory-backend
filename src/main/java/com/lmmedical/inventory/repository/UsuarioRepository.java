package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("select u from Usuario u where u.correo = lower(trim(:correo))")
    Optional<Usuario> findByCorreo(@Param("correo") String correo);
    @Query("select case when count(u) > 0 then true else false end from Usuario u where u.correo = lower(trim(:correo))")
    boolean existsByCorreo(@Param("correo") String correo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);
}
