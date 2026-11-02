package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Estilo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EstiloRepository extends JpaRepository<Estilo, Long> {
    Optional<Estilo> findFirstByNombre(String nombre);
    Optional<Estilo> findFirstByNombreIgnoreCase(String nombre);
    List<Estilo> findByIdEstadoOrderByNombreAsc(Integer idEstado);
}
