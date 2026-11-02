package com.dotacioneszambrano.inventorio.service;

import com.dotacioneszambrano.inventorio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PolicyService {

    public boolean permiteVenta(Double precioVenta, Double precioCompra) {
        if (precioVenta == null || precioCompra == null
                || !Double.isFinite(precioVenta) || !Double.isFinite(precioCompra)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Precios inválidos");
        }

        return BigDecimal.valueOf(precioVenta)
                .compareTo(BigDecimal.valueOf(precioCompra)) >= 0;
    }
}
