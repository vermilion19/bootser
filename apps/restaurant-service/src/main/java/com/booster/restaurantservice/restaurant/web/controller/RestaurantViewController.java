package com.booster.restaurantservice.restaurant.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RestaurantViewController {
    @GetMapping("/admin/restaurants")
    public String adminPage() {
        return "admin-restaurants"; // admin-restaurants.html
    }
}
