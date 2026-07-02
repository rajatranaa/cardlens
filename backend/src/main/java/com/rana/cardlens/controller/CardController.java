package com.rana.cardlens.controller;

import com.rana.cardlens.model.Card;
import com.rana.cardlens.repository.CardRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardRepository cardRepo;

    public CardController(CardRepository cardRepo) {
        this.cardRepo = cardRepo;
    }

    @GetMapping
    public List<Card> list() {
        return cardRepo.findAll();
    }

    @PostMapping
    public Card register(@RequestBody Card card) {
        // Thin: persist as-is. Note password_template_key must reference an env
        // var NAME; the password formula itself is never sent or stored.
        return cardRepo.save(card);
    }
}
