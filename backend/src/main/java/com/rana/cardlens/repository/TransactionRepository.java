package com.rana.cardlens.repository;

import com.rana.cardlens.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findByStatementIdIn(Collection<Integer> statementIds);

    // Clear a statement's transactions before re-saving (idempotent save).
    void deleteByStatementId(Integer statementId);
}
