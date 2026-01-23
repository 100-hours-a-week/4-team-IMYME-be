package com.imyme.mine.domain.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.imyme.mine.domain.card.entity.Card;

import java.util.List;

public record CardListResponse(

    List<CardListItem> cards,

    Pagination pagination

) {

    public record Pagination(

        @JsonProperty("next_cursor")
        String nextCursor,

        @JsonProperty("has_next")
        boolean hasNext,

        int limit

    ) {}

    public static CardListResponse of(List<Card> cards, int limit) {

        boolean hasNext = cards.size() > limit;

        List<Card> resultCards = hasNext ? cards.subList(0, limit) : cards;

        List<CardListItem> items = resultCards.stream()
            .map(CardListItem::from)
            .toList();

        String nextCursor = null;
        if (hasNext && !resultCards.isEmpty()) {
            Card lastCard = resultCards.get(resultCards.size() - 1);
            nextCursor = encodeCursor(lastCard);
        }

        return new CardListResponse(
            items,
            new Pagination(nextCursor, hasNext, limit)
        );
    }

    private static String encodeCursor(Card card) {
        String rawCursor = card.getCreatedAt().toString() + "_" + card.getId();
        return java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(rawCursor.getBytes());
    }
}
