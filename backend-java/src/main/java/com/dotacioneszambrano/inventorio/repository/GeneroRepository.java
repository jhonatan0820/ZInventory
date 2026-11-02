package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Genero;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeneroRepository extends JpaRepository<Genero, Integer> {
    List<Genero> findByIdEstado(Integer idEstado);
}
