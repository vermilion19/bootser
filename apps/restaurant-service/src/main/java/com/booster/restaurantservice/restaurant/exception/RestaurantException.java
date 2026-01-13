package com.booster.restaurantservice.restaurant.exception;

import com.booster.core.web.exception.CoreException;

public class FullEntryException extends CoreException {

    public FullEntryException() {
        super(RestaurantErrorCode.RESTAURANT_ERROR_CODE);
    }

    public FullEntryException(String message) {
        super(RestaurantErrorCode.RESTAURANT_ERROR_CODE);
    }

}
