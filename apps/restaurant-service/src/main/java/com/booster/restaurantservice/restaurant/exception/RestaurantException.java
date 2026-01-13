package com.booster.restaurantservice.restaurant.exception;

import com.booster.core.web.exception.CoreException;

public class RestaurantException extends CoreException {

    public RestaurantException() {
        super(RestaurantErrorCode.RESTAURANT_ERROR_CODE);
    }

    public RestaurantException(String message) {
        super(RestaurantErrorCode.RESTAURANT_ERROR_CODE,message);
    }

}
