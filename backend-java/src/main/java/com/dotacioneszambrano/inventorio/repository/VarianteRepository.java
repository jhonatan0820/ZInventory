package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Variante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VarianteRepository extends JpaRepository<Variante, Long> {
    List<Variante> findAllByIdProductoAndIdColorAndIdTalla(
            Long idProducto, Integer idColor, Integer idTalla);

    List<Variante> findAllByIdProductoAndIdEstado(Long idProducto, Integer idEstado);

    Optional<Variante> findByIdVarianteAndIdEstado(Long idVariante, Integer idEstado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Variante v where v.idVariante = :idVariante and v.idEstado = 1")
    Optional<Variante> findActiveForUpdate(@Param("idVariante") Long idVariante);

    @Query("select v.idProducto from Variante v where v.idVariante in :varianteIds")
    List<Long> findProductIdsByVariantIds(@Param("varianteIds") Collection<Long> varianteIds);

    @Modifying
    @Query("update Variante v set v.idEstado = :idEstado where v.idVariante in :varianteIds")
    int updateStateByIds(@Param("varianteIds") Collection<Long> varianteIds,
                         @Param("idEstado") Integer idEstado);
}
