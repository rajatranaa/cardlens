package com.rana.cardlens.controller;

import com.rana.cardlens.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Map<String, Object> dashboard(@RequestParam int month,
                                         @RequestParam int year) {
        return dashboardService.build(month, year);
    }
}
