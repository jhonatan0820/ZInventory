package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Marca;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface MarcaRepository extends JpaRepository<Marca, Long> {
    Optional<Marca> findFirstByNombreAndIdEstado(String nombre, Integer idEstado);
    Optional<Marca> findFirstByNombreIgnoreCaseAndIdEstado(String nombre, Integer idEstado);
    Optional<Marca> findFirstByNombreIgnoreCase(String nombre);
    List<Marca> findByIdEstadoOrderByNombreAsc(Integer idEstado);
}
