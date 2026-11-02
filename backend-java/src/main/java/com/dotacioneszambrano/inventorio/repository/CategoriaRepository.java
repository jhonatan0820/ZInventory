package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
    List<Categoria> findByIdEstadoOrderByNombreAsc(Integer idEstado);
    boolean existsByNombreIgnoreCase(String nombre);
}
