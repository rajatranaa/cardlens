package com.rana.cardlens.repository;

import com.rana.cardlens.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, Integer> {
}
