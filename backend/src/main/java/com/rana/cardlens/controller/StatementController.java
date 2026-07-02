package com.rana.cardlens.controller;

import com.rana.cardlens.model.Statement;
import com.rana.cardlens.service.StatementService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @PostMapping("/sync")
    public List<StatementService.SyncResult> sync(@RequestParam int month,
                                                  @RequestParam int year) {
        return statementService.sync(month, year);
    }

    @GetMapping
    public List<Statement> list(@RequestParam int month, @RequestParam int year) {
        return statementService.byPeriod(month, year);
    }
}
