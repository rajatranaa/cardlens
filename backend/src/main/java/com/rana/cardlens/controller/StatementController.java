package com.rana.cardlens.controller;

import com.rana.cardlens.model.SaveStatementRequest;
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

    /** Fetch + decrypt statements for the period and return the plain text per
     *  card (no LLM, nothing stored). The Claude app extracts the fields and
     *  then calls the save endpoint below. */
    @PostMapping("/fetch")
    public StatementService.FetchResponse fetch(@RequestParam int month,
                                                @RequestParam int year) {
        return statementService.fetch(month, year);
    }

    /** Persist one statement's extracted fields (validated server-side). */
    @PostMapping
    public StatementService.SaveResult save(@RequestBody SaveStatementRequest req) {
        return statementService.saveStatement(req);
    }

    @GetMapping
    public List<Statement> list(@RequestParam int month, @RequestParam int year) {
        return statementService.byPeriod(month, year);
    }
}
