package com.bomerp.config;

/** El stock disponible no alcanza para la cantidad pedida (se responde 409). */
public class StockInsuficienteException extends ReglaNegocioException {
    public StockInsuficienteException(String producto, int disponible, int solicitado) {
        super("Stock insuficiente para '" + producto + "': disponible " + disponible
                + ", solicitado " + solicitado);
    }
}
