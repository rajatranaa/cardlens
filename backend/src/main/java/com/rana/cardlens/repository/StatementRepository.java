package com.rana.cardlens.repository;

import com.rana.cardlens.model.Statement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatementRepository extends JpaRepository<Statement, Integer> {

    // Idempotency: check before inserting for a given card + period.
    Optional<Statement> findByCardIdAndStatementMonthAndStatementYear(
            Integer cardId, Integer statementMonth, Integer statementYear);

    List<Statement> findByStatementMonthAndStatementYear(
            Integer statementMonth, Integer statementYear);
}
