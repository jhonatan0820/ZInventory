package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.entity.Variante;
import com.dotacioneszambrano.inventorio.exception.ApiException;
import com.dotacioneszambrano.inventorio.repository.MovimientoInventarioRepository;
import com.dotacioneszambrano.inventorio.repository.VarianteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;

@Service
public class StockService {

    private final VarianteRepository varianteRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final PolicyService policyService;

    public StockService(VarianteRepository varianteRepository,
                        MovimientoInventarioRepository movimientoRepository,
                        PolicyService policyService) {
        this.varianteRepository = varianteRepository;
        this.movimientoRepository = movimientoRepository;
        this.policyService = policyService;
    }

    @Transactional
    public void actualizarStock(Long idVariante, Integer cantidad, Double precioVenta) {
        validateSaleInput(idVariante, cantidad, precioVenta);
        Variante variante = varianteRepository.findActiveForUpdate(idVariante)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada"));
        validateSale(variante, cantidad, precioVenta);
        int stockAnterior = variante.getStock() == null ? 0 : variante.getStock();
        if (cantidad <= 0 || cantidad > stockAnterior) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cantidad inválida");
        }

        int stockNuevo = stockAnterior - cantidad;
        double totalVenta = cantidad * precioVenta;
        variante.setStock(stockNuevo);
        varianteRepository.save(variante);
        movimientoRepository.registrarSalida(
                idVariante, cantidad, stockAnterior, stockNuevo, precioVenta, totalVenta);
    }

    @Transactional(readOnly = true)
    public void validarVenta(Long idVariante, Integer cantidad, Double precioVenta) {
        validateSaleInput(idVariante, cantidad, precioVenta);
        Variante variante = varianteRepository.findByIdVarianteAndIdEstado(idVariante, 1)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada"));
        validateSale(variante, cantidad, precioVenta);
    }

    private void validateSale(Variante variante, Integer cantidad, Double precioVenta) {
        int stockActual = variante.getStock() == null ? 0 : variante.getStock();
        if (cantidad > stockActual) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No hay suficiente stock para esa venta");
        }
        if (!policyService.permiteVenta(precioVenta, variante.getPrecio())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Por políticas de la empresa, no se puede vender por debajo del precio de compra.");
        }
    }

    private static void validateSaleInput(Long idVariante, Integer cantidad, Double precioVenta) {
        if (idVariante == null || idVariante <= 0 || cantidad == null || cantidad <= 0
                || precioVenta == null || !Double.isFinite(precioVenta) || precioVenta <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El producto, la cantidad y el precio de venta deben ser válidos");
        }
    }

    @Transactional
    public void entradaStock(Long idVariante, Integer cantidad) {
        if (idVariante == null || cantidad == null || cantidad <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a cero");
        }
        Variante variante = varianteRepository.findByIdVarianteAndIdEstado(idVariante, 1)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada"));
        agregarCantidad(variante, cantidad);
    }

    @Transactional
    public boolean entradaStock(Long idVariante, Integer cantidad, Double precio) {
        if (idVariante == null || cantidad == null || cantidad <= 0 || precio == null
                || !Double.isFinite(precio) || precio <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cantidad y precio deben ser válidos");
        }
        Variante varianteActual = varianteRepository.findByIdVarianteAndIdEstado(idVariante, 1)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada"));
        var variantesMismaPresentacion = varianteRepository.findAllByIdProductoAndIdColorAndIdTalla(
                varianteActual.getIdProducto(), varianteActual.getIdColor(), varianteActual.getIdTalla());
        BigDecimal precioEntrada = BigDecimal.valueOf(precio);
        Variante varianteMismoPrecio = variantesMismaPresentacion.stream()
                .filter(variante -> variante.getPrecio() != null
                        && BigDecimal.valueOf(variante.getPrecio()).compareTo(precioEntrada) == 0)
                .sorted(Comparator.comparing(
                        variante -> !Integer.valueOf(1).equals(variante.getIdEstado())))
                .findFirst()
                .orElse(null);

        if (varianteMismoPrecio != null) {
            Variante variante = varianteMismoPrecio;
            variante.setIdEstado(1);
            varianteRepository.save(variante);
            entradaStock(variante.getIdVariante(), cantidad);
            return false;
        }

        Variante nuevaVariante = varianteRepository.save(new Variante(
                varianteActual.getIdProducto(), varianteActual.getIdColor(), varianteActual.getIdTalla(),
                precio, cantidad, 1));
        movimientoRepository.registrarEntrada(
                nuevaVariante.getIdVariante(), cantidad, 0, cantidad);
        return true;
    }

    private void agregarCantidad(Variante variante, Integer cantidad) {
        Integer stockActual = variante.getStock();
        int stockAnterior = stockActual == null ? 0 : stockActual;
        int stockNuevo = stockAnterior + cantidad;
        variante.setStock(stockNuevo);
        varianteRepository.save(variante);
        movimientoRepository.registrarEntrada(
                variante.getIdVariante(), cantidad, stockAnterior, stockNuevo);
    }
}
