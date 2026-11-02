package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Color;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ColorRepository extends JpaRepository<Color, Integer> {
    List<Color> findByIdEstadoOrderByNombreAsc(Integer idEstado);
    boolean existsByNombreIgnoreCase(String nombre);
}
