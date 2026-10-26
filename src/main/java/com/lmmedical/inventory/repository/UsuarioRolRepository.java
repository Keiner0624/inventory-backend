package com.lmmedical.inventory.repository;

import com.lmmedical.inventory.entity.Rol;
import com.lmmedical.inventory.entity.UsuarioRol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UsuarioRolRepository extends JpaRepository<UsuarioRol, UsuarioRol.Id> {
    boolean existsByRol_Codigo(String codigo);

    @Query("select ur.rol from UsuarioRol ur where ur.usuario.id = :usuarioId order by ur.rol.codigo")
    List<Rol> findRolesByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT ur FROM UsuarioRol ur JOIN fetch ur.rol where ur.id.usuarioId in :usuarioIds")
    List<UsuarioRol> findByUsuarioIdsWithRoles(@Param("usuarioIds") Collection<Long> usuarioIds);

    @Query("select count (ur) from UsuarioRol ur where ur.rol.codigo = 'ADMIN'")
    long contarAdministradores();
}
