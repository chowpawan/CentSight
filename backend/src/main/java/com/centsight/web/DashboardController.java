package com.centsight.web;

import com.centsight.domain.User;
import com.centsight.dto.Dtos.*;
import com.centsight.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only dashboard data. The User comes from the JWT, never from the request body. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final AnalyticsService analytics;

    public DashboardController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/me")
    public Me me(User user) {
        return new Me(user.getId().toString(), user.getEmail(), user.getName(), user.getPictureUrl());
    }

    @GetMapping("/summary")
    public Summary summary(User user) {
        return analytics.summary(user.getId());
    }

    @GetMapping("/months")
    public List<String> months(User user) {
        return analytics.months(user.getId());
    }

    @GetMapping("/spending")
    public Spending spending(User user, @RequestParam String month) {
        if (!month.matches("\\d{4}-\\d{2}")) {
            throw new BadRequestException("month must look like 2026-09");
        }
        return analytics.spending(user.getId(), month);
    }

    @GetMapping("/recurring")
    public Recurring recurring(User user) {
        return analytics.recurring(user.getId());
    }

    @GetMapping("/transactions")
    public List<TxnView> transactions(User user, @RequestParam(defaultValue = "50") int limit) {
        return analytics.transactions(user.getId(), Math.clamp(limit, 1, 500));
    }

    @GetMapping("/items")
    public List<ItemView> items(User user) {
        return analytics.items(user.getId());
    }
}
