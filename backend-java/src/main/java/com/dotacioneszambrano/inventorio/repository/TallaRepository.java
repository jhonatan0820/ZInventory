package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Talla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TallaRepository extends JpaRepository<Talla, Integer> {
    List<Talla> findAllByOrderByValorAsc();

    @Query("""
            select t.idTalla as idTalla,
                   t.valor as valor,
                   t.idGenero as idGenero,
                   g.nombre as genero,
                   t.idCategoria as idCategoria,
                   c.nombre as categoria
            from Talla t
            left join Genero g on g.idGenero = t.idGenero
            left join Categoria c on c.idCategoria = t.idCategoria
            where t.idEstado = 1
            order by t.valor
            """)
    List<TallaCatalogProjection> findActiveTallasWithCategoryAndGender();

    List<Talla> findByValorAndIdCategoriaAndIdGeneroAndIdEstado(
            String valor, Integer idCategoria, Integer idGenero, Integer idEstado);
    List<Talla> findByValorIgnoreCaseAndIdCategoriaAndIdGenero(
            String valor, Integer idCategoria, Integer idGenero);

    List<Talla> findDistinctByIdCategoriaAndIdGeneroAndIdEstadoOrderByIdTallaAsc(
            Integer idCategoria, Integer idGenero, Integer idEstado);

    @Query("""
            select distinct t.valor
            from Variante v, Talla t
            where v.idTalla = t.idTalla
              and v.idEstado = 1
              and t.idEstado = 1
              and (:idCategoria is null or t.idCategoria = :idCategoria)
            order by t.valor
            """)
    List<String> findDistinctActiveValuesForActiveVariants(
            @Param("idCategoria") Integer idCategoria);

    @Query("""
            select distinct t.valor
            from Talla t
            where t.idCategoria = :idCategoria
              and t.idEstado = 1
            order by t.valor
            """)
    List<String> findDistinctActiveValuesByCategory(@Param("idCategoria") Integer idCategoria);
}
