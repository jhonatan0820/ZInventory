package com.dotacioneszambrano.inventorio.repository;

import com.dotacioneszambrano.inventorio.entity.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    Optional<Producto> findFirstByNombreAndIdCategoriaAndIdGeneroAndIdMarcaAndIdEstilo(
            String nombre, Integer idCategoria, Integer idGenero, Long idMarca, Long idEstilo);
    Optional<Producto> findFirstByNombreIgnoreCaseAndIdCategoriaAndIdGeneroAndIdMarcaAndIdEstilo(
            String nombre, Integer idCategoria, Integer idGenero, Long idMarca, Long idEstilo);

    @Query("""
            select distinct p.idProducto
            from Producto p, Variante v
            where v.idProducto = p.idProducto
              and v.idVariante in :varianteIds
            """)
    List<Long> findIdsWithSelectedVariants(@Param("varianteIds") Collection<Long> varianteIds);

    @Query("""
            select p.idProducto
            from Producto p
            where p.idProducto in :productoIds
              and not exists (
                  select v.idVariante
                  from Variante v
                  where v.idProducto = p.idProducto
                    and v.idEstado = 1
              )
            """)
    List<Long> findIdsWithoutActiveVariants(@Param("productoIds") Collection<Long> productoIds);

    @Query("""
            select p.idProducto, p.nombre, cat.nombre, v.idVariante, m.nombre, e.nombre,
                   c.nombre, t.valor, v.precio, v.stock
            from Variante v
            join Producto p on v.idProducto = p.idProducto
            join Categoria cat on p.idCategoria = cat.idCategoria
            left join Marca m on p.idMarca = m.idMarca
            left join Estilo e on p.idEstilo = e.idEstilo
            left join Color c on v.idColor = c.idColor
            left join Talla t on v.idTalla = t.idTalla
            where v.idEstado = 1
            order by v.idVariante asc
            """)
    List<Object[]> findActiveProductRows();

    @Query("""
            select p.idProducto, p.nombre, cat.nombre, v.idVariante, m.nombre, e.nombre,
                   c.nombre, t.valor, v.precio, v.stock
            from Variante v
            join Producto p on v.idProducto = p.idProducto
            join Categoria cat on p.idCategoria = cat.idCategoria
            left join Marca m on p.idMarca = m.idMarca
            left join Estilo e on p.idEstilo = e.idEstilo
            left join Color c on v.idColor = c.idColor
            left join Talla t on v.idTalla = t.idTalla
            left join Genero g on p.idGenero = g.idGenero
            where p.idEstado = 1 and v.idEstado = 1
              and (:query is null or
                   lower(p.nombre) like lower(concat('%', :query, '%')) or
                   lower(cat.nombre) like lower(concat('%', :query, '%')) or
                   lower(coalesce(m.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(e.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(c.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(t.valor, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(g.nombre, '')) like lower(concat('%', :query, '%')))
              and (:productName is null or lower(p.nombre) like lower(concat('%', :productName, '%')))
              and (:brand is null or lower(coalesce(m.nombre, '')) like lower(concat('%', :brand, '%')))
              and (:style is null or lower(coalesce(e.nombre, '')) like lower(concat('%', :style, '%')))
              and (:color is null or lower(coalesce(c.nombre, '')) like lower(concat('%', :color, '%')))
              and (:size is null or lower(trim(coalesce(t.valor, ''))) = lower(trim(:size)))
              and (:category is null or lower(cat.nombre) like lower(concat('%', :category, '%')))
              and (:gender is null or lower(coalesce(g.nombre, '')) like lower(concat('%', :gender, '%')))
              and (:priceGreaterThan is null or v.precio > :priceGreaterThan)
              and (:priceLessThan is null or v.precio < :priceLessThan)
              and (:priceEquals is null or v.precio = :priceEquals)
            order by p.nombre, v.idVariante
            """)
    List<Object[]> searchActiveInventory(
            @Param("query") String query,
            @Param("productName") String productName,
            @Param("brand") String brand,
            @Param("style") String style,
            @Param("color") String color,
            @Param("size") String size,
            @Param("category") String category,
            @Param("gender") String gender,
            @Param("priceGreaterThan") Double priceGreaterThan,
            @Param("priceLessThan") Double priceLessThan,
            @Param("priceEquals") Double priceEquals,
            Pageable pageable);

    @Query("""
            select count(v.idVariante)
            from Variante v
            join Producto p on v.idProducto = p.idProducto
            join Categoria cat on p.idCategoria = cat.idCategoria
            left join Marca m on p.idMarca = m.idMarca
            left join Estilo e on p.idEstilo = e.idEstilo
            left join Color c on v.idColor = c.idColor
            left join Talla t on v.idTalla = t.idTalla
            left join Genero g on p.idGenero = g.idGenero
            where p.idEstado = 1 and v.idEstado = 1
              and (:query is null or
                   lower(p.nombre) like lower(concat('%', :query, '%')) or
                   lower(cat.nombre) like lower(concat('%', :query, '%')) or
                   lower(coalesce(m.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(e.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(c.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(t.valor, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(g.nombre, '')) like lower(concat('%', :query, '%')))
              and (:productName is null or lower(p.nombre) like lower(concat('%', :productName, '%')))
              and (:brand is null or lower(coalesce(m.nombre, '')) like lower(concat('%', :brand, '%')))
              and (:style is null or lower(coalesce(e.nombre, '')) like lower(concat('%', :style, '%')))
              and (:color is null or lower(coalesce(c.nombre, '')) like lower(concat('%', :color, '%')))
              and (:size is null or lower(trim(coalesce(t.valor, ''))) = lower(trim(:size)))
              and (:category is null or lower(cat.nombre) like lower(concat('%', :category, '%')))
              and (:gender is null or lower(coalesce(g.nombre, '')) like lower(concat('%', :gender, '%')))
              and (:priceGreaterThan is null or v.precio > :priceGreaterThan)
              and (:priceLessThan is null or v.precio < :priceLessThan)
              and (:priceEquals is null or v.precio = :priceEquals)
            """)
    long countActiveInventory(
            @Param("query") String query,
            @Param("productName") String productName,
            @Param("brand") String brand,
            @Param("style") String style,
            @Param("color") String color,
            @Param("size") String size,
            @Param("category") String category,
            @Param("gender") String gender,
            @Param("priceGreaterThan") Double priceGreaterThan,
            @Param("priceLessThan") Double priceLessThan,
            @Param("priceEquals") Double priceEquals);

    @Query("""
            select coalesce(sum(coalesce(v.stock, 0)), 0)
            from Variante v
            join Producto p on v.idProducto = p.idProducto
            join Categoria cat on p.idCategoria = cat.idCategoria
            left join Marca m on p.idMarca = m.idMarca
            left join Estilo e on p.idEstilo = e.idEstilo
            left join Color c on v.idColor = c.idColor
            left join Talla t on v.idTalla = t.idTalla
            left join Genero g on p.idGenero = g.idGenero
            where p.idEstado = 1 and v.idEstado = 1
              and (:query is null or
                   lower(p.nombre) like lower(concat('%', :query, '%')) or
                   lower(cat.nombre) like lower(concat('%', :query, '%')) or
                   lower(coalesce(m.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(e.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(c.nombre, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(t.valor, '')) like lower(concat('%', :query, '%')) or
                   lower(coalesce(g.nombre, '')) like lower(concat('%', :query, '%')))
              and (:productName is null or lower(p.nombre) like lower(concat('%', :productName, '%')))
              and (:brand is null or lower(coalesce(m.nombre, '')) like lower(concat('%', :brand, '%')))
              and (:style is null or lower(coalesce(e.nombre, '')) like lower(concat('%', :style, '%')))
              and (:color is null or lower(coalesce(c.nombre, '')) like lower(concat('%', :color, '%')))
              and (:size is null or lower(trim(coalesce(t.valor, ''))) = lower(trim(:size)))
              and (:category is null or lower(cat.nombre) like lower(concat('%', :category, '%')))
              and (:gender is null or lower(coalesce(g.nombre, '')) like lower(concat('%', :gender, '%')))
              and (:priceGreaterThan is null or v.precio > :priceGreaterThan)
              and (:priceLessThan is null or v.precio < :priceLessThan)
              and (:priceEquals is null or v.precio = :priceEquals)
            """)
    long sumActiveInventoryStock(
            @Param("query") String query,
            @Param("productName") String productName,
            @Param("brand") String brand,
            @Param("style") String style,
            @Param("color") String color,
            @Param("size") String size,
            @Param("category") String category,
            @Param("gender") String gender,
            @Param("priceGreaterThan") Double priceGreaterThan,
            @Param("priceLessThan") Double priceLessThan,
            @Param("priceEquals") Double priceEquals);

    @Query("""
            select p.nombre as productName, cat.nombre as category, v.idVariante as variantId,
                   m.nombre as brand, e.nombre as style, c.nombre as color, t.valor as size,
                   v.precio as price, v.stock as stock
            from Variante v
            join Producto p on v.idProducto = p.idProducto
            join Categoria cat on p.idCategoria = cat.idCategoria
            left join Marca m on p.idMarca = m.idMarca
            left join Estilo e on p.idEstilo = e.idEstilo
            left join Color c on v.idColor = c.idColor
            left join Talla t on v.idTalla = t.idTalla
            where p.idEstado = 1 and v.idEstado = 1 and v.idVariante = :variantId
            """)
    Optional<ActiveVariantDescription> findActiveProductRowByVariantId(
            @Param("variantId") Long variantId);

    interface ActiveVariantDescription {
        String getProductName();
        String getCategory();
        Long getVariantId();
        String getBrand();
        String getStyle();
        String getColor();
        String getSize();
        Double getPrice();
        Integer getStock();
    }

    @Query("""
            select distinct lower(trim(p.nombre))
            from Producto p
            where p.idEstado = 1
            order by lower(trim(p.nombre))
            """)
    List<String> findDistinctActiveNamesNormalized();
}
